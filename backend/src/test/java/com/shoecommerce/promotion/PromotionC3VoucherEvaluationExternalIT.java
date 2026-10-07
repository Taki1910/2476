package com.shoecommerce.promotion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import com.shoecommerce.platform.api.InvalidRequestException;

@SpringBootTest
@ActiveProfiles("test")
@EnabledIfEnvironmentVariable(named="SPRING_DATASOURCE_URL",matches=".+")
@Transactional
class PromotionC3VoucherEvaluationExternalIT {
    private static final Instant AT=Instant.parse("2032-01-01T00:00:00Z");
    @Autowired JdbcTemplate jdbc;
    @Autowired PromotionService promotions;
    UUID customer,other,product,variant,branch,location;

    @BeforeEach void fixture(){customer=account();other=account();product=UUID.randomUUID();variant=UUID.randomUUID();branch=UUID.randomUUID();location=UUID.randomUUID();jdbc.update("INSERT INTO catalog_product(public_id,name,entity_version,created_at) VALUES (?,?,0,?)",product,"C3 "+product,Timestamp.from(AT));jdbc.update("INSERT INTO catalog_product_variant(public_id,product_id,sku,size,color,lifecycle_status,entity_version,created_at) SELECT ?,id,?,'40','Black','PUBLISHED',0,? FROM catalog_product WHERE public_id=?",variant,"C3-"+variant,Timestamp.from(AT),product);jdbc.update("INSERT INTO org_branch(public_id,code,name,created_at) VALUES(?,?,?,?)",branch,"B"+shortId(),"Voucher branch",Timestamp.from(AT));jdbc.update("INSERT INTO org_location(public_id,branch_id,code,name,created_at) SELECT ?,id,?,?,? FROM org_branch WHERE public_id=?",location,"L"+shortId(),"Voucher location",Timestamp.from(AT),branch);}

    @Test void codeUsesCurrentPublishedRevisionAndCertifiedMathAllocation(){
        UUID family=family("CODE","SAVE10",true),r1=rule(family,1,"RETIRED","ORDER_PERCENT",new BigDecimal("50"),null,null),r2=rule(family,2,"PUBLISHED","ORDER_PERCENT",new BigDecimal("33.3333"),null,100L);
        var e=evaluate(301,20,PromotionService.VoucherSelection.code("  save10  "),true);
        assertThat(e.merchandiseDiscount()).isEqualTo(100);assertThat(e.total()).isEqualTo(221);
        assertThat(e.adjustments()).singleElement().satisfies(a->{assertThat(a.revisionId()).isEqualTo(r2);assertThat(a.layer()).isEqualTo("VOUCHER_SELECTED");assertThat(a.base()).isEqualTo(301);assertThat(a.acquisitionMode()).isEqualTo("CODE");assertThat(a.maskedCode()).isEqualTo("SA••10");});
        assertThat(e.revisions()).doesNotContain(r1);assertThat(e.allocations()).singleElement().satisfies(a->assertThat(a.amount()).isEqualTo(100));
    }

    @Test void privateCodeEvaluatesButRemainsAbsentFromDiscovery(){
        UUID family=family("CODE","PRIVATE20",false);rule(family,1,"PUBLISHED","ORDER_FIXED",null,25L,null);
        var e=evaluate(100,0,PromotionService.VoucherSelection.code("PRIVATE20"),true);
        assertThat(e.merchandiseDiscount()).isEqualTo(25);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM promotion_family f JOIN promotion p ON p.family_public_id=f.public_id WHERE f.public_id=? AND f.is_publicly_discoverable=1 AND p.status='PUBLISHED'",Integer.class,family)).isZero();
    }

    @Test void selectedVoucherStacksAfterAutomaticAndNoneDoesNotApplyCodeFamilies(){
        automatic("ORDER_AUTOMATIC","ORDER_FIXED",100L);UUID family=family("CODE","STACK10",true);rule(family,1,"PUBLISHED","ORDER_FIXED",null,250L,800L);
        var none=evaluate(1_000,50,PromotionService.VoucherSelection.none(),true);
        var selected=evaluate(1_000,50,PromotionService.VoucherSelection.code("STACK10"),true);
        assertThat(none.merchandiseDiscount()).isEqualTo(100);assertThat(selected.merchandiseDiscount()).isEqualTo(350);
        assertThat(selected.adjustments()).extracting(PromotionService.Adjustment::layer).containsExactly("ORDER_AUTOMATIC","VOUCHER_SELECTED");
        assertCode(()->evaluate(899,0,PromotionService.VoucherSelection.code("STACK10"),true),"VOUCHER_MINIMUM_SPEND_NOT_MET");
    }

    @Test void percentageRunsAfterItemAndOrderWhileAutomaticShippingRemains(){
        automatic("ITEM","ITEM_FIXED",100L);automatic("ORDER_AUTOMATIC","ORDER_FIXED",50L);automatic("SHIPPING","FREE_SHIPPING",null);
        UUID family=family("CODE","AFTERAUTO",false);rule(family,1,"PUBLISHED","ORDER_PERCENT",new BigDecimal("10"),null,null);
        var e=evaluate(1_000,80,PromotionService.VoucherSelection.code("AFTERAUTO"),true);
        assertThat(e.merchandiseDiscount()).isEqualTo(235);assertThat(e.shippingDiscount()).isEqualTo(80);assertThat(e.total()).isEqualTo(765);
        assertThat(e.adjustments()).extracting(PromotionService.Adjustment::layer).containsExactly("ITEM","ORDER_AUTOMATIC","VOUCHER_SELECTED","SHIPPING");
    }

    @Test void selectedFreeShippingIsTheOnlyShippingWinnerAndNeverFallsBack(){
        automatic("SHIPPING","FREE_SHIPPING",null);UUID family=family("CODE","SHIPME",true);rule(family,1,"PUBLISHED","FREE_SHIPPING",null,null,null);
        var e=evaluate(500,80,PromotionService.VoucherSelection.code("SHIPME"),true);
        assertThat(e.shippingDiscount()).isEqualTo(80);assertThat(e.adjustments()).singleElement().satisfies(a->{assertThat(a.layer()).isEqualTo("SHIPPING");assertThat(a.acquisitionMode()).isEqualTo("CODE");});
        assertCode(()->evaluate(500,80,PromotionService.VoucherSelection.code("SHIPME"),false),"VOUCHER_NOT_FOUND_OR_UNAVAILABLE");
        assertCode(()->evaluate(500,0,PromotionService.VoucherSelection.code("SHIPME"),true),"VOUCHER_NOT_FOUND_OR_UNAVAILABLE");
    }

    @Test void claimIsOwnerScopedAndReplacementRevisionIsAuthority(){
        UUID family=family("CLAIMABLE",null,true),r1=rule(family,1,"RETIRED","ORDER_FIXED",null,10L,null),r2=rule(family,2,"PUBLISHED","ORDER_FIXED",null,40L,null),claim=claim(family,customer,r1,"CLAIMED");
        var e=evaluate(100,0,PromotionService.VoucherSelection.claim(claim),true);
        assertThat(e.merchandiseDiscount()).isEqualTo(40);assertThat(e.adjustments().getFirst().revisionId()).isEqualTo(r2);assertThat(e.adjustments().getFirst().voucherClaimId()).isEqualTo(claim);
        assertCode(()->promotions.evaluate(lines(100),0,AT,Set.of(),false,other,PromotionService.VoucherSelection.claim(claim),true),"VOUCHER_NOT_FOUND_OR_UNAVAILABLE");
        jdbc.update("UPDATE voucher_claim SET status='REVOKED',revoked_at=?,revoked_by_account_public_id=? WHERE public_id=?",Timestamp.from(AT),other,claim);
        assertCode(()->evaluate(100,0,PromotionService.VoucherSelection.claim(claim),true),"VOUCHER_NOT_FOUND_OR_UNAVAILABLE");
    }

    @Test void safeResolutionReasonsAndSelectionInvariant(){
        assertCode(()->evaluate(100,0,PromotionService.VoucherSelection.code("UNKNOWN"),true),"VOUCHER_NOT_FOUND_OR_UNAVAILABLE");
        UUID family=family("CODE","OLD10",true);rule(family,1,"PUBLISHED","ORDER_FIXED",null,10L,null,AT.minusSeconds(1));
        assertCode(()->evaluate(100,0,PromotionService.VoucherSelection.code("OLD10"),true),"VOUCHER_EXPIRED");
        assertThatThrownBy(()->new PromotionService.VoucherSelection(PromotionService.VoucherSelection.Type.CODE,null,UUID.randomUUID())).isInstanceOf(IllegalArgumentException.class);
    }

    @Test void claimFamilyUseCountsReservedAndRedeemedButNotReleasedAcrossRevisions(){
        assertClaimUsage("RELEASED",true);assertClaimUsage("RESERVED",false);assertClaimUsage("REDEEMED",false);
    }

    @Test void claimFamilyUseIsCustomerScopedAndCodeLimitsStayRevisionScoped(){
        UUID family=family("CLAIMABLE",null,true),r1=rule(family,1,"RETIRED","ORDER_FIXED",null,10L,null),r2=rule(family,2,"PUBLISHED","ORDER_FIXED",null,20L,null),claimA=claim(family,customer,r1,"CLAIMED"),claimB=claim(family,other,r1,"CLAIMED");redemption(r1,customer,"REDEEMED");
        assertCode(()->evaluate(100,0,PromotionService.VoucherSelection.claim(claimA),true),"VOUCHER_ALREADY_USED");
        assertThat(promotions.evaluate(lines(100),0,AT,Set.of(),false,other,PromotionService.VoucherSelection.claim(claimB),true).adjustments().getFirst().revisionId()).isEqualTo(r2);
        UUID codeFamily=family("CODE","REVISION1",false),codeR1=rule(codeFamily,1,"RETIRED","ORDER_FIXED",null,10L,null),codeR2=rule(codeFamily,2,"PUBLISHED","ORDER_FIXED",null,20L,null);jdbc.update("UPDATE promotion SET per_customer_usage_limit=1 WHERE public_id IN (?,?)",codeR1,codeR2);redemption(codeR1,customer,"REDEEMED");
        assertThat(evaluate(100,0,PromotionService.VoucherSelection.code("REVISION1"),true).adjustments().getFirst().revisionId()).isEqualTo(codeR2);
    }

    private PromotionService.Evaluation evaluate(long amount,long shipping,PromotionService.VoucherSelection selection,boolean delivery){return promotions.evaluate(lines(amount),shipping,AT,Set.of(),false,customer,selection,delivery);}
    private List<PromotionService.Line> lines(long amount){return List.of(new PromotionService.Line(variant,product,1,amount));}
    private UUID account(){UUID id=UUID.randomUUID();jdbc.update("INSERT INTO iam_user_account(public_id,login_normalized,password_hash,status,auth_version,entity_version,created_at,updated_at) VALUES (?,?,?,'ENABLED',1,0,?,?)",id,"v-"+id+"@test","fixture",Timestamp.from(AT),Timestamp.from(AT));return id;}
    private UUID family(String mode,String code,boolean visible){UUID id=UUID.randomUUID();jdbc.update("INSERT INTO promotion_family(public_id,acquisition_mode,normalized_code,is_publicly_discoverable,created_at) VALUES(?,?,?,?,?)",id,mode,code,visible,Timestamp.from(AT));return id;}
    private UUID rule(UUID family,int revision,String status,String type,BigDecimal percent,Long fixed,Long minimum){return rule(family,revision,status,type,percent,fixed,minimum,null);}
    private UUID rule(UUID family,int revision,String status,String type,BigDecimal percent,Long fixed,Long minimum,Instant validTo){return rule(family,revision,status,"FREE_SHIPPING".equals(type)?"SHIPPING":"ORDER_AUTOMATIC",type,percent,fixed,minimum,validTo);}
    private UUID rule(UUID family,int revision,String status,String layer,String type,BigDecimal percent,Long fixed,Long minimum,Instant validTo){UUID id=UUID.randomUUID();jdbc.update("INSERT INTO promotion(family_public_id,public_id,revision_number,name,status,layer,effect_type,percentage_value,fixed_amount,minimum_spend_amount,priority,valid_from,valid_to,created_by_account_public_id,created_at,published_at,retired_at) VALUES(?,?,?, ?,?,?,?,?,?,?,10,?,?,?,?,?,?)",family,id,revision,"Voucher "+revision,status,layer,type,percent,fixed,minimum,Timestamp.from(AT.minusSeconds(60)),validTo==null?null:Timestamp.from(validTo),customer,Timestamp.from(AT),Timestamp.from(AT),"RETIRED".equals(status)?Timestamp.from(AT):null);return id;}
    private UUID automatic(String layer,String type,Long fixed){UUID family=family("AUTOMATIC",null,false),revision=rule(family,1,"PUBLISHED",layer,type,null,fixed,null,null);if("ITEM".equals(layer))jdbc.update("INSERT INTO promotion_product_scope(promotion_id,product_public_id) SELECT id,? FROM promotion WHERE public_id=?",product,revision);return revision;}
    private UUID claim(UUID family,UUID owner,UUID revision,String status){UUID id=UUID.randomUUID();jdbc.update("INSERT INTO voucher_claim(public_id,promotion_family_public_id,customer_account_public_id,claimed_revision_public_id,status,claimed_at,revoked_at) VALUES(?,?,?,?,?,?,NULL)",id,family,owner,revision,status,Timestamp.from(AT));return id;}
    private void assertClaimUsage(String status,boolean eligible){UUID family=family("CLAIMABLE",null,true),r1=rule(family,1,"RETIRED","ORDER_FIXED",null,10L,null),r2=rule(family,2,"PUBLISHED","ORDER_FIXED",null,20L,null),owned=claim(family,customer,r1,"CLAIMED");redemption(r1,customer,status);if(eligible)assertThat(evaluate(100,0,PromotionService.VoucherSelection.claim(owned),true).adjustments().getFirst().revisionId()).isEqualTo(r2);else assertCode(()->evaluate(100,0,PromotionService.VoucherSelection.claim(owned),true),"VOUCHER_ALREADY_USED");}
    private void redemption(UUID revision,UUID owner,String status){UUID reservation=UUID.randomUUID(),order=UUID.randomUUID();jdbc.update("INSERT INTO inventory_reservation(public_id,owner_account_public_id,variant_id,location_id,quantity,status,entity_version,created_at) SELECT ?,?,v.id,l.id,1,'ACTIVE',0,? FROM catalog_product_variant v CROSS JOIN org_location l WHERE v.public_id=? AND l.public_id=?",reservation,owner,Timestamp.from(AT),variant,location);jdbc.update("INSERT INTO commerce_order(public_id,owner_account_public_id,responsible_branch_public_id,reservation_public_id,currency,status,entity_version,created_at,channel,merchandise_amount,shipping_fee_amount,total_amount) VALUES(?,?,?,?,'VND','PENDING_PAYMENT',0,?,'ONLINE',100,0,100)",order,owner,branch,reservation,Timestamp.from(AT));jdbc.update("INSERT INTO promotion_redemption(public_id,promotion_revision_public_id,order_public_id,customer_account_public_id,status,hold_expires_at,reserved_at,redeemed_at,released_at) VALUES(?,?,?,?,?,?,?,?,?)",UUID.randomUUID(),revision,order,owner,status,Timestamp.from(AT.plusSeconds(600)),Timestamp.from(AT),"REDEEMED".equals(status)?Timestamp.from(AT):null,"RELEASED".equals(status)?Timestamp.from(AT):null);}
    private static String shortId(){return UUID.randomUUID().toString().substring(0,8);}
    private static void assertCode(org.assertj.core.api.ThrowableAssert.ThrowingCallable call,String code){assertThatThrownBy(call).isInstanceOf(InvalidRequestException.class).satisfies(e->assertThat(((InvalidRequestException)e).code()).isEqualTo(code));}
}
