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
import org.springframework.security.access.AccessDeniedException;

import com.shoecommerce.identity.AccountUserDetailsService;
import com.shoecommerce.identity.SessionPrincipal;
import com.shoecommerce.platform.api.BusinessConflictException;

@SpringBootTest
@ActiveProfiles("test")
@EnabledIfEnvironmentVariable(named="SPRING_DATASOURCE_URL",matches=".+")
@Transactional
class PromotionC2ExternalIT {
    private static final Instant AT=Instant.parse("2032-01-01T00:00:00Z");
    @Autowired JdbcTemplate jdbc;
    @Autowired PromotionService promotions;
    @Autowired AccountUserDetailsService users;
    UUID creator,productA,productB,variantA1,variantA2,variantB;

    @BeforeEach void fixture(){
        creator=UUID.randomUUID(); productA=UUID.randomUUID();productB=UUID.randomUUID();
        variantA1=UUID.randomUUID();variantA2=UUID.randomUUID();variantB=UUID.randomUUID();
        jdbc.update("INSERT INTO iam_user_account(public_id,login_normalized,password_hash,status,auth_version,entity_version,created_at,updated_at) VALUES (?,?,?,'ENABLED',1,0,?,?)",creator,"c2-"+creator+"@example.test","fixture",Timestamp.from(AT),Timestamp.from(AT));
        product(productA,"A");product(productB,"B");variant(variantA1,productA,"A1","40");variant(variantA2,productA,"A2","41");variant(variantB,productB,"B1","40");
    }

    @Test void itemPercentRoundsOncePerLineAndFixedIsPerUnitWithZeroFloor(){
        rule(UUID.fromString("00000000-0000-0000-0000-000000000011"),"ITEM","ITEM_PERCENT",new BigDecimal("33.3333"),null,null,null,100,Set.of(productA));
        var percent=evaluate(List.of(line(variantA1,productA,3,101)),0);
        assertThat(percent.merchandise()).isEqualTo(303);assertThat(percent.merchandiseDiscount()).isEqualTo(101);
        assertThat(percent.adjustments()).singleElement().satisfies(a->assertThat(a.amount()).isEqualTo(101));
        retireAll();
        rule(UUID.randomUUID(),"ITEM","ITEM_FIXED",null,100_000L,null,null,100,Set.of(productA));
        assertThat(evaluate(List.of(line(variantA1,productA,2,1_000_000)),0).merchandiseDiscount()).isEqualTo(200_000);
        retireAll();
        rule(UUID.randomUUID(),"ITEM","ITEM_FIXED",null,2_000_000L,null,null,100,Set.of(productA));
        var floor=evaluate(List.of(line(variantA1,productA,2,1_000_000)),1);
        assertThat(floor.merchandiseDiscount()).isEqualTo(2_000_000);assertThat(floor.total()).isOne();
    }

    @Test void zeroPayableHasSpecificErrorButFreeLineInPositiveOrderRemainsSupported(){
        rule(UUID.randomUUID(),"ITEM","ITEM_FIXED",null,100_000L,null,null,100,Set.of(productA));
        assertThatThrownBy(() -> evaluate(List.of(line(variantA1,productA,1,100_000)),0))
                .isInstanceOfSatisfying(com.shoecommerce.platform.api.InvalidRequestException.class,
                        failure -> assertThat(failure.code()).isEqualTo("ZERO_PAYABLE_NOT_SUPPORTED"));
        var positive=evaluate(List.of(line(variantA1,productA,1,100_000),line(variantB,productB,1,100_000)),0);
        assertThat(positive.total()).isEqualTo(100_000);
        assertThat(positive.merchandiseDiscount()).isEqualTo(100_000);
    }

    @Test void orderRulesUsePostItemBaseAndCapAtThatBase(){
        rule(UUID.randomUUID(),"ITEM","ITEM_FIXED",null,100L,null,null,200,Set.of(productA));
        rule(UUID.randomUUID(),"ORDER_AUTOMATIC","ORDER_PERCENT",new BigDecimal("10"),null,null,null,100,Set.of());
        var percent=evaluate(List.of(line(variantA1,productA,1,1_000)),0);
        assertThat(percent.merchandiseDiscount()).isEqualTo(190);
        assertThat(percent.adjustments().stream().filter(a->a.layer().equals("ORDER_AUTOMATIC")).findFirst().orElseThrow().amount()).isEqualTo(90);
        retireAll();
        rule(UUID.randomUUID(),"ORDER_AUTOMATIC","ORDER_FIXED",null,2_000L,null,null,100,Set.of());
        assertThat(evaluate(List.of(line(variantA1,productA,1,1_000)),1).merchandiseDiscount()).isEqualTo(1_000);
    }

    @Test void minimumBoundariesExcludeShippingAndFreeShippingUsesPostDiscountMerchandise(){
        rule(UUID.randomUUID(),"ORDER_AUTOMATIC","ORDER_FIXED",null,10L,1_000L,null,100,Set.of());
        assertThat(evaluate(List.of(line(variantA1,productA,1,999)),500).merchandiseDiscount()).isZero();
        assertThat(evaluate(List.of(line(variantA1,productA,1,1_000)),500).merchandiseDiscount()).isEqualTo(10);
        assertThat(evaluate(List.of(line(variantA1,productA,1,1_001)),500).merchandiseDiscount()).isEqualTo(10);
        retireAll();
        rule(UUID.randomUUID(),"ITEM","ITEM_FIXED",null,100L,null,null,200,Set.of(productA));
        rule(UUID.randomUUID(),"SHIPPING","FREE_SHIPPING",null,null,900L,null,100,Set.of());
        assertThat(evaluate(List.of(line(variantA1,productA,1,1_000)),80).shippingDiscount()).isEqualTo(80);
        assertThat(evaluate(List.of(line(variantA1,productA,1,1_000)),0).adjustments()).noneMatch(a->a.layer().equals("SHIPPING"));
    }

    @Test void productScopeAggregatesVariantsAndScopedProductsForBuyQuantity(){
        rule(UUID.randomUUID(),"ITEM","ITEM_FIXED",null,50L,null,3L,100,Set.of(productA,productB));
        assertThat(evaluate(List.of(line(variantA1,productA,1,100),line(variantA2,productA,1,100)),0).merchandiseDiscount()).isZero();
        assertThat(evaluate(List.of(line(variantA1,productA,1,100),line(variantA2,productA,1,100),line(variantB,productB,1,100)),0).merchandiseDiscount()).isEqualTo(150);
        UUID outsider=UUID.randomUUID(),outsideVariant=UUID.randomUUID();product(outsider,"outside");variant(outsideVariant,outsider,"OUT","40");
        assertThat(evaluate(List.of(line(variantA1,productA,1,100),line(outsideVariant,outsider,9,100)),0).merchandiseDiscount()).isZero();
    }

    @Test void priorityThenPublicIdSelectsOneWinnerPerLayerAndLayersStack(){
        UUID low=UUID.fromString("00000000-0000-0000-0000-000000000001"),high=UUID.fromString("00000000-0000-0000-0000-000000000002");
        rule(high,"ITEM","ITEM_FIXED",null,20L,null,null,100,Set.of(productA));
        rule(low,"ITEM","ITEM_FIXED",null,10L,null,null,100,Set.of(productA));
        rule(UUID.randomUUID(),"ORDER_AUTOMATIC","ORDER_FIXED",null,5L,null,null,90,Set.of());
        rule(UUID.randomUUID(),"SHIPPING","FREE_SHIPPING",null,null,null,null,80,Set.of());
        var e=evaluate(List.of(line(variantA1,productA,1,100)),30);
        assertThat(e.adjustments()).hasSize(3);
        assertThat(e.adjustments().stream().filter(a->a.layer().equals("ITEM")).findFirst().orElseThrow().revisionId()).isEqualTo(low);
        assertThat(e.merchandiseDiscount()).isEqualTo(15);assertThat(e.shippingDiscount()).isEqualTo(30);assertThat(e.total()).isEqualTo(85);
        assertThat(e.total()).isEqualTo(e.merchandise()-e.merchandiseDiscount()+e.shippingFee()-e.shippingDiscount());
    }

    @Test void largestRemainderAllocationIsExactAndDeterministic(){
        rule(UUID.randomUUID(),"ORDER_AUTOMATIC","ORDER_FIXED",null,2L,null,null,100,Set.of());
        var e=evaluate(List.of(line(variantA1,productA,1,1),line(variantA2,productA,1,1),line(variantB,productB,1,1)),1);
        var repeated=evaluate(List.of(line(variantA1,productA,1,1),line(variantA2,productA,1,1),line(variantB,productB,1,1)),1);
        assertThat(repeated.allocations().stream().map(a->List.of(a.variantId(),a.amount())).toList()).isEqualTo(e.allocations().stream().map(a->List.of(a.variantId(),a.amount())).toList());
        assertThat(e.allocations()).extracting(PromotionService.Allocation::amount).containsExactlyInAnyOrder(1L,1L,0L);
        assertThat(e.allocations()).extracting(PromotionService.Allocation::amount).allMatch(amount->amount>=0);
        assertThat(e.allocations().stream().mapToLong(PromotionService.Allocation::amount).sum()).isEqualTo(2);
    }

    @Test void authorizationMatrixUsesDirectCapabilityRatherThanOperationsRole(){
        SessionPrincipal manager=account("manager", "OPERATIONS",true),operations=account("operations","OPERATIONS",false),cashier=account("cashier","CASHIER",false),customer=account("customer","CUSTOMER",false),staffOnly=account("staff","OPERATIONS",false),identityOnly=account("identity","ADMINISTRATOR",false);
        direct(staffOnly,"STAFF_MANAGE_SCOPED");direct(identityOnly,"IDENTITY_MANAGE");
        assertThat(promotions.rules(principal(manager.getUsername()))).isNotNull();
        for(SessionPrincipal denied:List.of(operations,cashier,customer,principal(staffOnly.getUsername()),principal(identityOnly.getUsername())))assertThatThrownBy(()->promotions.rules(denied)).isInstanceOf(AccessDeniedException.class);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM iam_role_permission rp JOIN iam_role_bundle r ON r.id=rp.role_id JOIN iam_permission p ON p.id=rp.permission_id WHERE r.code='OPERATIONS' AND p.code='PROMOTION_MANAGE'",Integer.class)).isZero();
    }

    @Test void draftRevisionLifecycleIsImmutableAuditedAndAtomic(){
        SessionPrincipal manager=account("revision","OPERATIONS",true);var draft=draft(null,1,"Draft one");var created=promotions.create(manager,draft);
        assertThat(promotions.edit(manager,created.id(),draft(null,1,"Edited draft")).name()).isEqualTo("Edited draft");
        var published=promotions.publish(manager,created.id());assertThat(published.status()).isEqualTo("PUBLISHED");
        assertThatThrownBy(()->promotions.edit(manager,created.id(),draft(null,1,"illegal"))).isInstanceOf(BusinessConflictException.class);
        assertThat(jdbc.queryForObject("SELECT name FROM promotion WHERE public_id=?",String.class,created.id())).isEqualTo("Edited draft");
        var next=promotions.create(manager,draft(created.familyId(),2,"Revision two"));assertThat(next.status()).isEqualTo("DRAFT");
        assertThatThrownBy(()->promotions.publish(manager,promotions.create(manager,draft(created.familyId(),2,"duplicate")).id())).isInstanceOf(BusinessConflictException.class);
        promotions.transition(manager,created.id(),"RETIRED");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM audit_event WHERE resource_public_id=? AND action IN ('PROMOTION_PUBLISHED','PROMOTION_RETIRED')",Integer.class,created.id())).isEqualTo(2);
        var invalid=promotions.create(manager,draft(null,1,"Invalidate"));promotions.publish(manager,invalid.id());promotions.transition(manager,invalid.id(),"INVALIDATED");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM audit_event WHERE resource_public_id=? AND action='PROMOTION_INVALIDATED'",Integer.class,invalid.id())).isOne();
    }

    private PromotionService.Evaluation evaluate(List<PromotionService.Line> lines,long shipping){return promotions.evaluate(lines,shipping,AT,Set.of(),false,creator);}
    private PromotionService.Line line(UUID variant,UUID product,long qty,long unit){return new PromotionService.Line(variant,product,qty,unit);}
    private void product(UUID id,String name){jdbc.update("INSERT INTO catalog_product(public_id,name,entity_version,created_at) VALUES (?,?,0,?)",id,"C2 "+name+" "+id,Timestamp.from(AT));}
    private void variant(UUID id,UUID product,String sku,String size){jdbc.update("INSERT INTO catalog_product_variant(public_id,product_id,sku,size,color,lifecycle_status,entity_version,created_at) SELECT ?,id,?,?,'Black','PUBLISHED',0,? FROM catalog_product WHERE public_id=?",id,"C2-"+sku+"-"+id,size,Timestamp.from(AT),product);}
    private void retireAll(){jdbc.update("UPDATE promotion SET status='RETIRED',retired_at=? WHERE created_by_account_public_id=? AND status='PUBLISHED'",Timestamp.from(AT),creator);}
    private PromotionService.Draft draft(UUID family,int revision,String name){return new PromotionService.Draft(family,revision,name,"ORDER_AUTOMATIC","ORDER_FIXED",null,10L,null,null,null,null,10,AT.minusSeconds(1),null,Set.of(),null,null,null,null,null);}
    private SessionPrincipal account(String label,String role,boolean promotion){UUID id=UUID.randomUUID();String login="c2-"+label+"-"+id+"@example.test";jdbc.update("INSERT INTO iam_user_account(public_id,login_normalized,password_hash,status,auth_version,entity_version,created_at,updated_at) VALUES(?,?,?,'ENABLED',1,0,?,?)",id,login,"fixture",Timestamp.from(AT),Timestamp.from(AT));jdbc.update("INSERT INTO iam_account_role(account_id,role_id) SELECT a.id,r.id FROM iam_user_account a CROSS JOIN iam_role_bundle r WHERE a.public_id=? AND r.code=?",id,role);SessionPrincipal actor=principal(login);if(promotion){direct(actor,"PROMOTION_MANAGE");actor=principal(login);}return actor;}
    private void direct(SessionPrincipal actor,String permission){jdbc.update("INSERT INTO iam_account_permission(account_id,permission_id,granted_at) SELECT a.id,p.id,? FROM iam_user_account a CROSS JOIN iam_permission p WHERE a.public_id=? AND p.code=?",Timestamp.from(AT),actor.publicId(),permission);}
    private SessionPrincipal principal(String login){SessionPrincipal actor=(SessionPrincipal)users.loadUserByUsername(login);actor.eraseCredentials();return actor;}
    private void rule(UUID id,String layer,String type,BigDecimal percent,Long fixed,Long minimum,Long buy,int priority,Set<UUID> scope){
        UUID family=UUID.randomUUID();jdbc.update("INSERT INTO promotion_family(public_id,acquisition_mode,normalized_code,is_publicly_discoverable,created_at) VALUES(?,'AUTOMATIC',NULL,0,?)",family,Timestamp.from(AT));
        jdbc.update("INSERT INTO promotion(family_public_id,public_id,revision_number,name,status,layer,effect_type,percentage_value,fixed_amount,minimum_spend_amount,buy_quantity,priority,valid_from,created_by_account_public_id,created_at,published_at) VALUES (?,?,1,?,'PUBLISHED',?,?,?,?,?,?,?, ?,?,?,?)",family,id,"C2 "+type,layer,type,percent,fixed,minimum,buy,priority,Timestamp.from(AT.minusSeconds(60)),creator,Timestamp.from(AT),Timestamp.from(AT));
        for(UUID product:scope)jdbc.update("INSERT INTO promotion_product_scope(promotion_id,product_public_id) SELECT id,? FROM promotion WHERE public_id=?",product,id);
    }
}
