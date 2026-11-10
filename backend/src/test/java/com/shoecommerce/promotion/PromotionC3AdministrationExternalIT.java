package com.shoecommerce.promotion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import com.shoecommerce.identity.AccountUserDetailsService;
import com.shoecommerce.identity.SessionPrincipal;
import com.shoecommerce.platform.api.BusinessConflictException;
import com.shoecommerce.platform.api.InvalidRequestException;

@SpringBootTest
@ActiveProfiles("test")
@EnabledIfEnvironmentVariable(named="SPRING_DATASOURCE_URL",matches=".+")
class PromotionC3AdministrationExternalIT {
    private static final Instant NOW=Instant.parse("2026-09-07T01:00:00Z");
    @Autowired PromotionService promotions;
    @Autowired JdbcTemplate jdbc;
    @Autowired AccountUserDetailsService users;

    @Test void codeNormalizationFamilyImmutabilityAndVoucherValidation() {
        SessionPrincipal manager=manager();String identity="SAVE"+UUID.randomUUID().toString().substring(0,8).toUpperCase();
        var code=promotions.create(manager,draft(null,1,"CODE","  "+identity.toLowerCase()+"  ",false,"ORDER_FIXED"," Summary "," Terms "));
        assertThat(code.normalizedCode()).isEqualTo(identity);
        assertThat(code.customerSummary()).isEqualTo("Summary");
        assertThat(code.customerTerms()).isEqualTo("Terms");
        promotions.setDiscoverability(manager,code.familyId(),new PromotionService.VisibilityCommand(true));
        var r2=promotions.create(manager,draft(code.familyId(),2,"CODE",identity.toLowerCase(),false,"ORDER_PERCENT","R2","R2 terms"));
        assertThat(r2.normalizedCode()).isEqualTo(identity);
        assertThat(r2.publiclyDiscoverable()).isTrue();

        assertCode(()->promotions.create(manager,draft(null,1,"CODE",identity,false,"ORDER_FIXED","Other","Terms")),"PROMOTION_CODE_ALREADY_EXISTS");
        assertCode(()->promotions.create(manager,draft(code.familyId(),3,"CLAIMABLE",null,false,"ORDER_FIXED","R3","Terms")),"PROMOTION_ACQUISITION_MODE_IMMUTABLE");
        assertCode(()->promotions.create(manager,draft(code.familyId(),3,"CODE",identity+"X",false,"ORDER_FIXED","R3","Terms")),"PROMOTION_ACQUISITION_MODE_IMMUTABLE");
        assertCode(()->promotions.create(manager,draft(null,1,"CODE","ab",false,"ORDER_FIXED","Short","Terms")),"PROMOTION_CODE_INVALID");
        assertCode(()->promotions.create(manager,draft(null,1,"CODE","SAVE!",false,"ORDER_FIXED","Invalid","Terms")),"PROMOTION_CODE_INVALID");
        assertCode(()->promotions.create(manager,draft(null,1,"CODE","A".repeat(33),false,"ORDER_FIXED","Long","Terms")),"PROMOTION_CODE_INVALID");
        assertCode(()->promotions.create(manager,draft(null,1,"CLAIMABLE",null,true,"ITEM_FIXED","Item","Terms")),"PROMOTION_VOUCHER_EFFECT_UNSUPPORTED");
        assertCode(()->promotions.create(manager,draft(null,1,"CLAIMABLE",null,true,"FREE_SHIPPING"," ","Terms")),"PROMOTION_CUSTOMER_SUMMARY_REQUIRED");
        assertCode(()->promotions.create(manager,draft(null,1,"CLAIMABLE",null,true,"FREE_SHIPPING","Summary"," ")),"PROMOTION_CUSTOMER_TERMS_REQUIRED");
        assertCode(()->promotions.create(manager,draft(null,1,"CLAIMABLE",null,true,"FREE_SHIPPING","S".repeat(241),"Terms")),"PROMOTION_CUSTOMER_SUMMARY_INVALID");
        assertCode(()->promotions.create(manager,draft(null,1,"CLAIMABLE",null,true,"FREE_SHIPPING","Summary","T".repeat(1001))),"PROMOTION_CUSTOMER_TERMS_INVALID");
        assertThat(promotions.create(manager,draft(null,1,"CLAIMABLE",null,true,"FREE_SHIPPING","Shipping","Terms")).effectType()).isEqualTo("FREE_SHIPPING");

        var automatic=promotions.create(manager,draft(null,1,null,null,false,"ORDER_FIXED",null,null));
        assertThat(automatic.acquisitionMode()).isEqualTo("AUTOMATIC");
        assertThat(automatic.customerSummary()).isNull();
    }

    @Test void replacementIsAtomicAndConcurrentPublisherGetsStableConflict() throws Exception {
        SessionPrincipal manager=manager();
        var r1=promotions.create(manager,draft(null,1,"CLAIMABLE",null,true,"ORDER_FIXED","R1","Terms"));
        promotions.publish(manager,r1.id(),null);
        assertCode(()->promotions.edit(manager,r1.id(),draft(r1.familyId(),1,"CLAIMABLE",null,true,"ORDER_FIXED","Changed","Terms")),"PROMOTION_REVISION_NOT_DRAFT");
        var r2=promotions.create(manager,draft(r1.familyId(),2,"CLAIMABLE",null,true,"ORDER_FIXED","R2","Terms"));
        var r3=promotions.create(manager,draft(r1.familyId(),3,"CLAIMABLE",null,true,"ORDER_PERCENT","R3","Terms"));
        jdbc.update("UPDATE promotion SET customer_summary=NULL WHERE public_id=?",r2.id());
        assertCode(()->promotions.publish(manager,r2.id(),r1.id()),"PROMOTION_CUSTOMER_SUMMARY_REQUIRED");
        assertThat(jdbc.queryForObject("SELECT status FROM promotion WHERE public_id=?",String.class,r1.id())).isEqualTo("PUBLISHED");
        jdbc.update("UPDATE promotion SET customer_summary='R2' WHERE public_id=?",r2.id());
        CountDownLatch ready=new CountDownLatch(2),start=new CountDownLatch(1);
        List<Object> outcomes;
        try(var executor=Executors.newFixedThreadPool(2)){
            var a=executor.submit(()->race(manager,r2.id(),r1.id(),ready,start));
            var b=executor.submit(()->race(manager,r3.id(),r1.id(),ready,start));
            assertThat(ready.await(10,TimeUnit.SECONDS)).isTrue();start.countDown();outcomes=List.of(a.get(20,TimeUnit.SECONDS),b.get(20,TimeUnit.SECONDS));
        }
        assertThat(outcomes.stream().filter(PromotionService.RuleView.class::isInstance)).hasSize(1);
        assertThat(outcomes.stream().filter(BusinessConflictException.class::isInstance).map(BusinessConflictException.class::cast).map(BusinessConflictException::code)).containsExactly("PROMOTION_PUBLICATION_CONFLICT");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM promotion WHERE family_public_id=? AND status='PUBLISHED'",Integer.class,r1.familyId())).isOne();
        assertThat(jdbc.queryForObject("SELECT status FROM promotion WHERE public_id=?",String.class,r1.id())).isEqualTo("RETIRED");
    }

    @Test void concurrentEquivalentCodesPersistAtMostOneFamily() throws Exception {
        SessionPrincipal manager=manager();String suffix=UUID.randomUUID().toString().replace("-","").substring(0,8).toUpperCase();CountDownLatch ready=new CountDownLatch(2),start=new CountDownLatch(1);List<Object> outcomes;
        try(var executor=Executors.newFixedThreadPool(2)){
            var a=executor.submit(()->createCode(manager," c"+suffix+" ",ready,start));
            var b=executor.submit(()->createCode(manager,"C"+suffix.toLowerCase(),ready,start));
            assertThat(ready.await(10,TimeUnit.SECONDS)).isTrue();start.countDown();outcomes=List.of(a.get(20,TimeUnit.SECONDS),b.get(20,TimeUnit.SECONDS));
        }
        assertThat(outcomes.stream().filter(PromotionService.RuleView.class::isInstance)).hasSize(1);
        assertThat(outcomes.stream().filter(BusinessConflictException.class::isInstance).map(BusinessConflictException.class::cast).map(BusinessConflictException::code)).containsExactly("PROMOTION_CODE_ALREADY_EXISTS");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM promotion_family WHERE normalized_code=?",Integer.class,"C"+suffix)).isOne();
    }

    private Object createCode(SessionPrincipal actor,String code,CountDownLatch ready,CountDownLatch start)throws Exception{ready.countDown();start.await(10,TimeUnit.SECONDS);try{return promotions.create(actor,draft(null,1,"CODE",code,false,"ORDER_FIXED","Race","Terms"));}catch(BusinessConflictException e){return e;}}
    private Object race(SessionPrincipal actor,UUID target,UUID expected,CountDownLatch ready,CountDownLatch start)throws Exception{ready.countDown();start.await(10,TimeUnit.SECONDS);try{return promotions.publish(actor,target,expected);}catch(BusinessConflictException e){return e;}}
    private PromotionService.Draft draft(UUID family,Integer revision,String mode,String code,boolean discoverable,String effect,String summary,String terms){String layer=effect.startsWith("ITEM_")?"ITEM":effect.startsWith("ORDER_")?"ORDER_AUTOMATIC":"SHIPPING";return new PromotionService.Draft(family,revision,"C3 "+UUID.randomUUID(),layer,effect,effect.endsWith("PERCENT")?BigDecimal.TEN:null,effect.endsWith("FIXED")?100L:null,null,null,null,null,10,NOW.minusSeconds(1),null,Set.of(),summary,terms,mode,code,discoverable);}
    private SessionPrincipal manager(){UUID id=UUID.randomUUID();String login="c3-admin-"+id+"@example.test";Timestamp now=Timestamp.from(NOW);jdbc.update("INSERT INTO iam_user_account(public_id,login_normalized,password_hash,status,auth_version,entity_version,created_at,updated_at) VALUES(?,?,?,'ENABLED',1,0,?,?)",id,login,"fixture",now,now);jdbc.update("INSERT INTO iam_account_permission(account_id,permission_id,granted_at) SELECT a.id,p.id,? FROM iam_user_account a CROSS JOIN iam_permission p WHERE a.public_id=? AND p.code='PROMOTION_MANAGE'",now,id);return (SessionPrincipal)users.loadUserByUsername(login);}
    private static void assertCode(Runnable action,String code){assertThatThrownBy(action::run).satisfies(error->{String actual=error instanceof InvalidRequestException invalid?invalid.code():error instanceof BusinessConflictException conflict?conflict.code():"";assertThat(actual).isEqualTo(code);});}
}
