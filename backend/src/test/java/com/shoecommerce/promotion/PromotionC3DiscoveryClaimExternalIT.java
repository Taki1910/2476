package com.shoecommerce.promotion;

import static org.assertj.core.api.Assertions.*;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.*;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.context.ActiveProfiles;
import com.shoecommerce.identity.*;
import com.shoecommerce.platform.api.ResourceNotFoundException;

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@EnabledIfEnvironmentVariable(named="SPRING_DATASOURCE_URL",matches=".+")
class PromotionC3DiscoveryClaimExternalIT {
    @Autowired VoucherService vouchers; @Autowired PromotionService promotions; @Autowired JdbcTemplate jdbc; @Autowired AccountUserDetailsService users;
    @LocalServerPort int port;

    @Test void publicDiscoveryIsBoundedDeterministicAndHidesPrivateStateAndCode() throws Exception {
        var code=offer("CODE",true,"PUBLIC-CODE",-60,3600);var claimable=offer("CLAIMABLE",true,null,-50,3600);var secondClaimable=offer("CLAIMABLE",true,null,-45,3600);
        offer("AUTOMATIC",false,null,-40,3600);offer("CLAIMABLE",false,null,-30,3600);offer("CLAIMABLE",true,null,-7200,-3600);
        var first=vouchers.discover(0,1);var second=vouchers.discover(1,1);
        assertThat(first.items()).hasSize(1);assertThat(first.hasNext()).isTrue();assertThat(second.items()).hasSize(1);
        assertThat(List.of(first.items().getFirst().familyId(),second.items().getFirst().familyId())).containsExactly(secondClaimable.family,claimable.family).doesNotContain(code.family);
        assertThatThrownBy(()->vouchers.detail(code.family)).isInstanceOf(ResourceNotFoundException.class);
        assertThat(VoucherService.PublicOffer.class.getRecordComponents()).extracting(java.lang.reflect.RecordComponent::getName).doesNotContain("normalizedCode","revisionId","revisionNumber","id");
        UUID privateFamily=offer("CLAIMABLE",false,null,-20,3600).family;
        UUID privateCode=offer("CODE",false,"PRIVATE10",-20,3600).family;
        assertThatThrownBy(()->vouchers.detail(privateFamily)).isInstanceOf(ResourceNotFoundException.class).hasMessage("Voucher not found or unavailable.");
        assertThatThrownBy(()->vouchers.detail(privateCode)).isInstanceOf(ResourceNotFoundException.class).hasMessage("Voucher not found or unavailable.");
        assertThatThrownBy(()->vouchers.detail(UUID.randomUUID())).isInstanceOf(ResourceNotFoundException.class).hasMessage("Voucher not found or unavailable.");
        assertThatThrownBy(()->vouchers.discover(0,101)).isInstanceOfSatisfying(com.shoecommerce.platform.api.InvalidRequestException.class,e->assertThat(e.code()).isEqualTo("INVALID_PAGINATION"));
        HttpResponse<String> response=HttpClient.newHttpClient().send(HttpRequest.newBuilder(URI.create("http://localhost:"+port+"/api/v1/storefront/promotions?size=10")).GET().build(),HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).isEqualTo(200);assertThat(response.body()).doesNotContain("PUBLIC-CODE","PRIVATE10",privateCode.toString(),"normalizedCode","claimedRevision");
        HttpResponse<String> privateDetail=HttpClient.newHttpClient().send(HttpRequest.newBuilder(URI.create("http://localhost:"+port+"/api/v1/storefront/promotions/"+privateCode)).GET().build(),HttpResponse.BodyHandlers.ofString());
        assertThat(privateDetail.statusCode()).isEqualTo(404);assertThat(privateDetail.body()).doesNotContain("PRIVATE10");
        HttpResponse<String> guestSaved=HttpClient.newHttpClient().send(HttpRequest.newBuilder(URI.create("http://localhost:"+port+"/api/v1/me/voucher-claims")).GET().build(),HttpResponse.BodyHandlers.ofString());
        assertThat(guestSaved.statusCode()).isEqualTo(401);assertThat(guestSaved.body()).contains("AUTHENTICATION_REQUIRED");
    }

    @Test void claimIsNaturallyIdempotentConcurrentAndHasNoCommerceSideEffects() throws Exception {
        SessionPrincipal customer=account("CUSTOMER",false),other=account("CUSTOMER",false);var offer=offer("CLAIMABLE",true,null,-60,3600);
        long orders=count("commerce_order"),reservations=count("inventory_reservation"),redemptions=count("promotion_redemption");
        var first=vouchers.claim(customer,offer.family);var replay=vouchers.claim(customer,offer.family);assertThat(replay.claimId()).isEqualTo(first.claimId());
        assertThat(countClaims(customer.publicId(),offer.family)).isOne();assertThat(audits(first.claimId(),"VOUCHER_CLAIM_CREATED")).isOne();
        var otherClaim=vouchers.claim(other,offer.family);assertThat(otherClaim.claimId()).isNotEqualTo(first.claimId());
        SessionPrincipal concurrent=account("CUSTOMER",false);CountDownLatch ready=new CountDownLatch(2),start=new CountDownLatch(1);List<VoucherService.ClaimView> results;
        try(var executor=Executors.newFixedThreadPool(2)){var a=executor.submit(()->raceClaim(concurrent,offer.family,ready,start));var b=executor.submit(()->raceClaim(concurrent,offer.family,ready,start));assertThat(ready.await(10,TimeUnit.SECONDS)).isTrue();start.countDown();results=List.of(a.get(20,TimeUnit.SECONDS),b.get(20,TimeUnit.SECONDS));}
        assertThat(results).extracting(VoucherService.ClaimView::claimId).containsOnly(results.getFirst().claimId());assertThat(countClaims(concurrent.publicId(),offer.family)).isOne();assertThat(audits(results.getFirst().claimId(),"VOUCHER_CLAIM_CREATED")).isOne();
        assertThat(count("commerce_order")).isEqualTo(orders);assertThat(count("inventory_reservation")).isEqualTo(reservations);assertThat(count("promotion_redemption")).isEqualTo(redemptions);
    }

    @Test void savedOwnershipUnavailableReplayAndRevocationRemainSeparate() throws Exception {
        SessionPrincipal customer=account("CUSTOMER",false),other=account("CUSTOMER",false),manager=account("OPERATIONS",true),operations=account("OPERATIONS",false),cashier=account("CASHIER",false),disabled=account("CUSTOMER",false),stale=account("CUSTOMER",false);var offer=offer("CLAIMABLE",true,null,-60,3600);
        var claim=vouchers.claim(customer,offer.family);vouchers.claim(other,offer.family);
        assertThat(vouchers.saved(customer,0,20).items()).singleElement().satisfies(saved->{assertThat(saved.claimId()).isEqualTo(claim.claimId());assertThat(saved.claimStatus()).isEqualTo("CLAIMED");assertThat(saved.offerAvailability()).isEqualTo("AVAILABLE");});
        assertThatThrownBy(()->vouchers.claim(operations,offer.family)).isInstanceOf(AccessDeniedException.class);assertThatThrownBy(()->vouchers.revoke(customer,claim.claimId())).isInstanceOf(AccessDeniedException.class);assertThatThrownBy(()->vouchers.revoke(operations,claim.claimId())).isInstanceOf(AccessDeniedException.class);assertThatThrownBy(()->vouchers.revoke(cashier,claim.claimId())).isInstanceOf(AccessDeniedException.class);
        jdbc.update("UPDATE iam_user_account SET status='DISABLED' WHERE public_id=?",disabled.publicId());jdbc.update("UPDATE iam_user_account SET auth_version=auth_version+1 WHERE public_id=?",stale.publicId());
        assertThatThrownBy(()->vouchers.claim(disabled,offer.family)).isInstanceOf(AccessDeniedException.class);assertThatThrownBy(()->vouchers.saved(stale,0,20)).isInstanceOf(AccessDeniedException.class);
        CountDownLatch ready=new CountDownLatch(2),start=new CountDownLatch(1);VoucherService.ClaimView revoked;
        try(var executor=Executors.newFixedThreadPool(2)){var replay=executor.submit(()->raceClaim(customer,offer.family,ready,start));var revoke=executor.submit(()->{ready.countDown();start.await();return vouchers.revoke(manager,claim.claimId());});assertThat(ready.await(10,TimeUnit.SECONDS)).isTrue();start.countDown();replay.get(20,TimeUnit.SECONDS);revoked=revoke.get(20,TimeUnit.SECONDS);}
        var repeated=vouchers.revoke(manager,claim.claimId());assertThat(repeated.revokedAt()).isEqualTo(revoked.revokedAt());assertThat(audits(claim.claimId(),"VOUCHER_CLAIM_REVOKED")).isOne();
        jdbc.update("UPDATE promotion SET status='INVALIDATED',invalidated_at=? WHERE family_public_id=? AND status='PUBLISHED'",Timestamp.from(Instant.now()),offer.family);
        assertThat(vouchers.claim(customer,offer.family).claimId()).isEqualTo(claim.claimId());
        assertThat(vouchers.saved(customer,0,20).items()).singleElement().satisfies(saved->{assertThat(saved.claimStatus()).isEqualTo("REVOKED");assertThat(saved.offerAvailability()).isEqualTo("REVOKED");assertThat(saved.offer().name()).startsWith("Offer ");assertThat(saved.termsAvailable()).isFalse();});
        assertThat(countClaims(customer.publicId(),offer.family)).isOne();
    }

    @Test void claimRejectsWrongOrUnavailableModesAndSerializesWithPublication() throws Exception {
        SessionPrincipal customer=account("CUSTOMER",false),manager=account("OPERATIONS",true);
        for(var offer:List.of(offer("CODE",true,"NOCLAIM",-60,3600),offer("AUTOMATIC",false,null,-60,3600),offer("CLAIMABLE",false,null,-60,3600),offer("CLAIMABLE",true,null,-3600,-60)))assertThatThrownBy(()->vouchers.claim(customer,offer.family)).isInstanceOf(ResourceNotFoundException.class);
        var r1=promotions.create(manager,draft(null,1));promotions.publish(manager,r1.id());var r2=promotions.create(manager,draft(r1.familyId(),2));CountDownLatch ready=new CountDownLatch(2),start=new CountDownLatch(1);
        try(var executor=Executors.newFixedThreadPool(2)){var claim=executor.submit(()->{ready.countDown();start.await();return vouchers.claim(customer,r1.familyId());});var publish=executor.submit(()->{ready.countDown();start.await();return promotions.publish(manager,r2.id(),r1.id());});assertThat(ready.await(10,TimeUnit.SECONDS)).isTrue();start.countDown();var claimed=claim.get(20,TimeUnit.SECONDS);publish.get(20,TimeUnit.SECONDS);UUID evidence=jdbc.queryForObject("SELECT claimed_revision_public_id FROM voucher_claim WHERE public_id=?",UUID.class,claimed.claimId());assertThat(evidence).isIn(r1.id(),r2.id());}
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM promotion WHERE family_public_id=? AND status='PUBLISHED'",Integer.class,r1.familyId())).isOne();
    }

    private VoucherService.ClaimView raceClaim(SessionPrincipal actor,UUID family,CountDownLatch ready,CountDownLatch start)throws Exception{ready.countDown();start.await();return vouchers.claim(actor,family);}
    private Offer offer(String mode,boolean visible,String code,long starts,long ends){UUID family=UUID.randomUUID(),revision=UUID.randomUUID();Instant now=Instant.now();jdbc.update("INSERT INTO promotion_family(public_id,acquisition_mode,normalized_code,is_publicly_discoverable,created_at) VALUES(?,?,?,?,?)",family,mode,code,visible,Timestamp.from(now));jdbc.update("INSERT INTO promotion(family_public_id,public_id,revision_number,name,status,layer,effect_type,fixed_amount,priority,valid_from,valid_to,created_by_account_public_id,created_at,published_at,customer_summary,customer_terms) VALUES(?,?,1,?,'PUBLISHED','ORDER_AUTOMATIC','ORDER_FIXED',100,10,?,?,?,?,?,?,?)",family,revision,"Offer "+family,Timestamp.from(now.plusSeconds(starts)),Timestamp.from(now.plusSeconds(ends)),accountId(),Timestamp.from(now),Timestamp.from(now),mode.equals("AUTOMATIC")?null:"Summary",mode.equals("AUTOMATIC")?null:"Terms");return new Offer(family,revision);}
    private PromotionService.Draft draft(UUID family,int revision){return new PromotionService.Draft(family,revision,"Race","ORDER_AUTOMATIC","ORDER_FIXED",null,100L,null,null,null,null,10,Instant.now().minusSeconds(60),null,Set.of(),"Summary","Terms","CLAIMABLE",null,true);}
    private UUID accountId(){return jdbc.queryForObject("SELECT TOP 1 public_id FROM iam_user_account ORDER BY id",UUID.class);}
    private SessionPrincipal account(String role,boolean manage){UUID id=UUID.randomUUID();String login="c3-"+id+"@example.test";Instant now=Instant.now();jdbc.update("INSERT INTO iam_user_account(public_id,login_normalized,password_hash,status,auth_version,entity_version,created_at,updated_at) VALUES(?,?,?,'ENABLED',1,0,?,?)",id,login,"fixture",Timestamp.from(now),Timestamp.from(now));jdbc.update("INSERT INTO iam_account_role(account_id,role_id) SELECT a.id,r.id FROM iam_user_account a CROSS JOIN iam_role_bundle r WHERE a.public_id=? AND r.code=?",id,role);if(manage)jdbc.update("INSERT INTO iam_account_permission(account_id,permission_id,granted_at) SELECT a.id,p.id,? FROM iam_user_account a CROSS JOIN iam_permission p WHERE a.public_id=? AND p.code='PROMOTION_MANAGE'",Timestamp.from(now),id);return (SessionPrincipal)users.loadUserByUsername(login);}
    private long count(String table){return jdbc.queryForObject("SELECT COUNT_BIG(*) FROM "+table,Long.class);}private int countClaims(UUID customer,UUID family){return jdbc.queryForObject("SELECT COUNT(*) FROM voucher_claim WHERE customer_account_public_id=? AND promotion_family_public_id=?",Integer.class,customer,family);}private int audits(UUID resource,String action){return jdbc.queryForObject("SELECT COUNT(*) FROM audit_event WHERE resource_public_id=? AND action=?",Integer.class,resource,action);}
    private record Offer(UUID family,UUID revision){}
}
