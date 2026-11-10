package com.shoecommerce.shipping;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.context.ActiveProfiles;

import com.shoecommerce.identity.AccountUserDetailsService;
import com.shoecommerce.identity.SessionPrincipal;

@SpringBootTest
@ActiveProfiles("test")
@EnabledIfEnvironmentVariable(named="SPRING_DATASOURCE_URL",matches=".+")
class ShippingC1ExternalIT {
    @Autowired JdbcTemplate jdbc;
    @Autowired ShippingService shipping;
    @Autowired AddressResolver addresses;
    @Autowired PasswordEncoder encoder;
    @Autowired AccountUserDetailsService users;

    @BeforeEach
    void retirePublishedRules() {
        jdbc.update("UPDATE shipping_rate_rule SET status='RETIRED',retired_at=SYSUTCDATETIME() WHERE status='PUBLISHED'");
    }

    @Test
    void resolvesDemoHierarchyAndKeepsInvalidAddressDistinctFromUnsupportedDelivery() {
        assertThat(addresses.wards("79", "760"))
                .contains(new AddressResolver.Reference("DEMO-760-01", "Phường Bến Nghé"));
        var resolved = addresses.resolve("79", "760", "DEMO-760-01", " 12 Nguyễn Huệ ");
        assertThat(resolved.addressLine()).isEqualTo("12 Nguyễn Huệ");
        assertThat(resolved.canonicalAddress()).isEqualTo(
                "12 Nguyễn Huệ, Phường Bến Nghé, Quận 1, Thành phố Hồ Chí Minh");
        assertThat(resolved.datasetVersion()).isEqualTo("DEMO-2026");

        assertThatThrownBy(() -> addresses.resolve("00", "760", "DEMO-760-01", "12 Nguyễn Huệ"))
                .isInstanceOf(com.shoecommerce.platform.api.InvalidRequestException.class)
                .satisfies(error -> assertThat(((com.shoecommerce.platform.api.InvalidRequestException) error).code())
                        .isEqualTo("ADDRESS_PROVINCE_UNKNOWN"));
        assertThatThrownBy(() -> addresses.resolve("79", "001", "DEMO-760-01", "12 Nguyễn Huệ"))
                .isInstanceOf(com.shoecommerce.platform.api.InvalidRequestException.class)
                .satisfies(error -> assertThat(((com.shoecommerce.platform.api.InvalidRequestException) error).code())
                        .isEqualTo("ADDRESS_DISTRICT_UNKNOWN"));
        assertThatThrownBy(() -> addresses.resolve("79", "760", "unknown", "12 Nguyễn Huệ"))
                .isInstanceOf(com.shoecommerce.platform.api.InvalidRequestException.class)
                .satisfies(error -> assertThat(((com.shoecommerce.platform.api.InvalidRequestException) error).code())
                        .isEqualTo("ADDRESS_WARD_UNKNOWN"));

        UUID branch = UUID.randomUUID(), location = UUID.randomUUID(); Timestamp now = Timestamp.from(Instant.now());
        createLocation(branch, location, now);
        assertThatThrownBy(() -> shipping.quote(location, branch, "79", "760", "DEMO-760-01", Instant.now()))
                .isInstanceOf(com.shoecommerce.platform.api.BusinessConflictException.class)
                .satisfies(error -> assertThat(((com.shoecommerce.platform.api.BusinessConflictException) error).code())
                        .isEqualTo("SHIPPING_DESTINATION_UNSUPPORTED"));
    }

    @Test
    void conflictingPublicationsAreSerializedAndPublishedBodyIsImmutable() throws Exception {
        SessionPrincipal admin=shippingAdmin(); Instant from=Instant.now().minusSeconds(1),to=from.plusSeconds(3600);
        var a=shipping.createDraft(admin,new ShippingService.Draft(null,1,"GLOBAL",null,"79","760","INTER_PROVINCE",80_000,7,from,to));
        var b=shipping.createDraft(admin,new ShippingService.Draft(null,1,"GLOBAL",null,"79","760","INTER_PROVINCE",90_000,7,from,to));
        CountDownLatch start=new CountDownLatch(1); AtomicInteger successes=new AtomicInteger();
        try(var pool=Executors.newFixedThreadPool(2)) {
            var first=pool.submit(()->publish(start,successes,admin,a.id())); var second=pool.submit(()->publish(start,successes,admin,b.id()));
            start.countDown(); first.get(); second.get();
        }
        assertThat(successes).hasValue(1);
        UUID published=jdbc.queryForObject("SELECT public_id FROM shipping_rate_rule WHERE public_id IN (?,?) AND status='PUBLISHED'",UUID.class,a.id(),b.id());
        try { shipping.editDraft(admin,published,new ShippingService.Draft(null,1,"GLOBAL",null,"79","760","INTER_PROVINCE",1,7,from,to)); }
        catch(RuntimeException expected) { assertThat(expected.getMessage()).contains("immutable"); }
    }

    @Test
    void exactLocationDistrictWinsAndRetiredQuoteRemainsUsableUntilInvalidated() {
        SessionPrincipal admin=shippingAdmin(); UUID branch=UUID.randomUUID(),location=UUID.randomUUID(); Timestamp now=Timestamp.from(Instant.now());
        jdbc.update("INSERT INTO org_branch(public_id,code,name,enabled,created_at) VALUES (?, ?, 'Shipping branch',1,?)",branch,"SHP-"+branch.toString().substring(0,8),now);
        jdbc.update("INSERT INTO org_location(public_id,branch_id,code,name,enabled,created_at) SELECT ?,id,?,'Shipping location',1,? FROM org_branch WHERE public_id=?",location,"LOC-"+location.toString().substring(0,8),now,branch);
        rule(admin.publicId(),"GLOBAL",null,null,"79",null,10_000,100,now);
        UUID exact=rule(admin.publicId(),"LOCATION",location,null,"79","760",30_000,1,now);
        var quote=shipping.quote(location,branch,"79","760","DEMO-760-01",Instant.now());
        assertThat(quote.revisionId()).isEqualTo(exact); assertThat(quote.feeAmount()).isEqualTo(30_000);
        jdbc.update("UPDATE shipping_rate_rule SET status='RETIRED',retired_at=SYSUTCDATETIME() WHERE public_id=?",exact);
        shipping.requireQuoteUsable(exact);
        jdbc.update("UPDATE shipping_rate_rule SET status='INVALIDATED',retired_at=NULL,invalidated_at=SYSUTCDATETIME() WHERE public_id=?",exact);
        try { shipping.requireQuoteUsable(exact); throw new AssertionError("Invalidated quote accepted"); }
        catch(com.shoecommerce.platform.api.BusinessConflictException expected){assertThat(expected.code()).isEqualTo("SHIPPING_QUOTE_STALE");}
    }

    @Test
    void quoteUsesSixLevelSpecificityThenPriority() {
        SessionPrincipal admin=shippingAdmin(); UUID branch=UUID.randomUUID(),location=UUID.randomUUID(); Timestamp now=Timestamp.from(Instant.now());
        createLocation(branch,location,now);
        UUID locDistrictLow=rule(admin.publicId(),"LOCATION",location,null,"48","492",11,1,now);
        UUID locDistrictHigh=rule(admin.publicId(),"LOCATION",location,null,"48","492",12,2,now);
        UUID locProvince=rule(admin.publicId(),"LOCATION",location,null,"48",null,22,100,now);
        UUID branchDistrict=rule(admin.publicId(),"BRANCH",null,branch,"48","492",33,100,now);
        UUID branchProvince=rule(admin.publicId(),"BRANCH",null,branch,"48",null,44,100,now);
        UUID globalDistrict=rule(admin.publicId(),"GLOBAL",null,null,"48","492",55,100,now);
        UUID globalProvince=rule(admin.publicId(),"GLOBAL",null,null,"48",null,66,100,now);
        for (var expected : new Object[][]{{locDistrictHigh,12L},{locDistrictLow,11L},{locProvince,22L},{branchDistrict,33L},{branchProvince,44L},{globalDistrict,55L},{globalProvince,66L}}) {
            var quote=shipping.quote(location,branch,"48","492","DEMO-492-01",Instant.now());
            assertThat(quote.revisionId()).isEqualTo(expected[0]); assertThat(quote.feeAmount()).isEqualTo(expected[1]);
            jdbc.update("UPDATE shipping_rate_rule SET status='RETIRED',retired_at=SYSUTCDATETIME() WHERE public_id=?",quote.revisionId());
        }
    }

    @Test
    void administrationEnforcesExactLocationAndGlobalAuthorityAndRejectsBranchScope() {
        UUID branch=UUID.randomUUID(),own=UUID.randomUUID(),foreign=UUID.randomUUID(); Timestamp now=Timestamp.from(Instant.now());
        createLocation(branch,own,now); jdbc.update("INSERT INTO org_location(public_id,branch_id,code,name,enabled,created_at) SELECT ?,id,?,'Foreign location',1,? FROM org_branch WHERE public_id=?",foreign,"LOC-"+foreign.toString().substring(0,8),now,branch);
        SessionPrincipal scoped=account(own,"SHIPPING_RATE_MANAGE"), identityOnly=account(null,"IDENTITY_MANAGE"), staffOnly=account(own,"STAFF_MANAGE_SCOPED"), admin=shippingAdmin();
        var ownDraft=new ShippingService.Draft(null,1,"LOCATION",own,"48","492","INTER_PROVINCE",1,1,Instant.now(),null);
        assertThat(shipping.createDraft(scoped,ownDraft).originLocationId()).isEqualTo(own);
        assertThatThrownBy(()->shipping.createDraft(scoped,new ShippingService.Draft(null,1,"LOCATION",foreign,"48","492","INTER_PROVINCE",1,1,Instant.now(),null))).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(()->shipping.createDraft(scoped,new ShippingService.Draft(null,1,"GLOBAL",null,"48",null,"INTER_PROVINCE",1,1,Instant.now(),null))).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(()->shipping.createDraft(identityOnly,new ShippingService.Draft(null,1,"GLOBAL",null,"48",null,"INTER_PROVINCE",1,1,Instant.now(),null))).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(()->shipping.createDraft(staffOnly,ownDraft)).isInstanceOf(AccessDeniedException.class);
        assertThat(shipping.createDraft(admin,new ShippingService.Draft(null,1,"GLOBAL",null,"48",null,"INTER_PROVINCE",1,1,Instant.now(),null)).originScope()).isEqualTo("GLOBAL");
        assertThatThrownBy(()->shipping.createDraft(admin,new ShippingService.Draft(null,1,"BRANCH",null,"48",null,"INTER_PROVINCE",1,1,Instant.now(),null))).isInstanceOf(AccessDeniedException.class);
    }

    private void publish(CountDownLatch start,AtomicInteger successes,SessionPrincipal admin,UUID id){try{start.await();shipping.publish(admin,id);successes.incrementAndGet();}catch(Exception ignored){}}
    private SessionPrincipal shippingAdmin(){UUID id=UUID.randomUUID();String login="shipping-"+id+"@example.com";Timestamp now=Timestamp.from(Instant.now());
        jdbc.update("INSERT INTO iam_user_account(public_id,login_normalized,password_hash,status,auth_version,entity_version,created_at,updated_at) VALUES (?,?,?,'ENABLED',1,0,?,?)",id,login,encoder.encode("Correct-Horse-42"),now,now);
        jdbc.update("INSERT INTO iam_account_role(account_id,role_id) SELECT accounts.id,roles.id FROM iam_user_account accounts CROSS JOIN iam_role_bundle roles WHERE accounts.public_id=? AND roles.code='ADMINISTRATOR'",id);
        jdbc.update("INSERT INTO iam_account_permission(account_id,permission_id,granted_at) SELECT accounts.id,permissions.id,? FROM iam_user_account accounts CROSS JOIN iam_permission permissions WHERE accounts.public_id=? AND permissions.code='SHIPPING_RATE_MANAGE'",now,id);
        return (SessionPrincipal)users.loadUserByUsername(login);
    }
    private SessionPrincipal account(UUID location,String...permissions){UUID id=UUID.randomUUID();String login="shipping-"+id+"@example.com";Timestamp now=Timestamp.from(Instant.now());
        jdbc.update("INSERT INTO iam_user_account(public_id,login_normalized,password_hash,status,auth_version,entity_version,created_at,updated_at) VALUES (?,?,?,'ENABLED',1,0,?,?)",id,login,encoder.encode("Correct-Horse-42"),now,now);
        for(String permission:permissions) jdbc.update("INSERT INTO iam_account_permission(account_id,permission_id,granted_at) SELECT a.id,p.id,? FROM iam_user_account a CROSS JOIN iam_permission p WHERE a.public_id=? AND p.code=?",now,id,permission);
        if(location!=null) jdbc.update("INSERT INTO iam_staff_assignment(public_id,account_id,branch_id,location_id,active,created_at,updated_at) SELECT NEWID(),a.id,l.branch_id,l.id,1,?,? FROM iam_user_account a CROSS JOIN org_location l WHERE a.public_id=? AND l.public_id=?",now,now,id,location);
        return (SessionPrincipal)users.loadUserByUsername(login);
    }
    private void createLocation(UUID branch,UUID location,Timestamp now){
        jdbc.update("INSERT INTO org_branch(public_id,code,name,enabled,created_at) VALUES (?, ?, 'Shipping branch',1,?)",branch,"SHP-"+branch.toString().substring(0,8),now);
        jdbc.update("INSERT INTO org_location(public_id,branch_id,code,name,enabled,created_at) SELECT ?,id,?,'Shipping location',1,? FROM org_branch WHERE public_id=?",location,"LOC-"+location.toString().substring(0,8),now,branch);
    }
    private UUID rule(UUID actor,String scope,UUID location,UUID branch,String province,String district,long fee,int priority,Timestamp now){UUID id=UUID.randomUUID();
        jdbc.update("INSERT INTO shipping_rate_rule(public_id,family_public_id,revision_number,status,origin_scope,origin_location_public_id,origin_branch_public_id,destination_province_code,destination_district_code,zone_code,fee_amount,priority,valid_from,created_by_account_public_id,created_at,published_at) VALUES (?,?,1,'PUBLISHED',?,?,?,?,?,'INTER_PROVINCE',?,?,?, ?,?,?)",
          id,UUID.randomUUID(),scope,location,branch,province,district,fee,priority,Timestamp.from(now.toInstant().minusSeconds(1)),actor,now,now);return id;}
}
