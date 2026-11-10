package com.shoecommerce.promotion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
@EnabledIfEnvironmentVariable(named = "SPRING_DATASOURCE_URL", matches = ".+")
class PromotionC3FoundationExternalIT {
    private static final Instant NOW=Instant.parse("2026-09-07T00:00:00Z");
    @Autowired JdbcTemplate jdbc;

    @Test
    void familyCodeAndPublishedRevisionConstraintsAreDatabaseEnforced() {
        UUID account=account(),automatic=family("AUTOMATIC",null),code=family("CODE","SAVE10"),other=family("AUTOMATIC",null);
        revision(account,code,1,"PUBLISHED");

        assertThatThrownBy(()->family("CODE","SAVE10")).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(()->family("CODE","save10")).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(()->revision(account,code,2,"PUBLISHED")).isInstanceOf(DataIntegrityViolationException.class);
        revision(account,other,1,"PUBLISHED");

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM promotion_family WHERE public_id IN (?,?,?) AND normalized_code IS NULL",Integer.class,automatic,other,UUID.randomUUID())).isEqualTo(2);
    }

    @Test
    void claimOwnershipRevisionAndLifecycleConstraintsAreDatabaseEnforced() {
        UUID owner=account(),otherOwner=account(),family=family("CLAIMABLE",null),otherFamily=family("CLAIMABLE",null);
        UUID revision=revision(owner,family,1,"PUBLISHED"),otherRevision=revision(owner,otherFamily,1,"PUBLISHED");
        claim(owner,family,revision,"CLAIMED",null,null);
        claim(otherOwner,family,revision,"CLAIMED",null,null);
        claim(owner,otherFamily,otherRevision,"CLAIMED",null,null);

        assertThatThrownBy(()->claim(owner,family,revision,"CLAIMED",null,null)).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(()->claim(UUID.randomUUID(),family,revision,"CLAIMED",null,null)).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(()->claim(account(),family,otherRevision,"CLAIMED",null,null)).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(()->claim(account(),family,revision,"REVOKED",null,null)).isInstanceOf(DataIntegrityViolationException.class);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sys.columns WHERE object_id=OBJECT_ID('voucher_claim') AND name IN ('expires_at','used','remaining_uses','redemption_id','discount_amount')",Integer.class)).isZero();
    }

    @Test
    void quoteSnapshotAcceptsVoucherLayerWithoutChangingAutomaticDefaults() {
        UUID owner=account(),family=family("CLAIMABLE",null),revision=revision(owner,family,1,"PUBLISHED"),quote=UUID.randomUUID();
        claim(owner,family,revision,"CLAIMED",null,null);
        UUID claim=jdbc.queryForObject("SELECT public_id FROM voucher_claim WHERE customer_account_public_id=? AND promotion_family_public_id=?",UUID.class,owner,family);
        jdbc.update("INSERT INTO pricing_cart_quote(public_id,owner_account_id,quoted_at,expires_at,merchandise_amount,merchandise_discount_amount,total_amount) SELECT ?,id,?,?,1000,100,900 FROM iam_user_account WHERE public_id=?",quote,Timestamp.from(NOW),Timestamp.from(NOW.plusSeconds(900)),owner);
        jdbc.update("INSERT INTO pricing_cart_quote_adjustment(public_id,quote_public_id,promotion_family_public_id,promotion_revision_public_id,revision_number,name_snapshot,effect_type,layer,qualifying_base_amount,fixed_amount,applied_amount,priority,applied_at,acquisition_mode,voucher_claim_public_id) VALUES(?,?,?,?,1,'Saved offer','ORDER_FIXED','VOUCHER_SELECTED',1000,100,100,1,?,'CLAIMABLE',?)",UUID.randomUUID(),quote,family,revision,Timestamp.from(NOW),claim);

        assertThat(jdbc.queryForObject("SELECT acquisition_mode FROM pricing_cart_quote_adjustment WHERE quote_public_id=?",String.class,quote)).isEqualTo("CLAIMABLE");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sys.columns WHERE object_id=OBJECT_ID('commerce_order_item_adjustment') AND name IN ('acquisition_mode','masked_code_snapshot','voucher_claim_public_id')",Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT definition FROM sys.check_constraints WHERE name='CK_item_adjustment_layer'",String.class)).contains("VOUCHER_ALLOCATION");
    }

    private UUID account(){UUID id=UUID.randomUUID();jdbc.update("INSERT INTO iam_user_account(public_id,login_normalized,password_hash,status,auth_version,entity_version,created_at,updated_at) VALUES(?,?,?,'ENABLED',1,0,?,?)",id,"c3-"+id+"@example.test","fixture",Timestamp.from(NOW),Timestamp.from(NOW));return id;}
    private UUID family(String mode,String code){UUID id=UUID.randomUUID();jdbc.update("INSERT INTO promotion_family(public_id,acquisition_mode,normalized_code,is_publicly_discoverable,created_at) VALUES(?,?,?,?,?)",id,mode,code,"CLAIMABLE".equals(mode),Timestamp.from(NOW));return id;}
    private UUID revision(UUID creator,UUID family,int number,String status){UUID id=UUID.randomUUID();Timestamp published="PUBLISHED".equals(status)?Timestamp.from(NOW):null;jdbc.update("INSERT INTO promotion(family_public_id,public_id,revision_number,name,status,layer,effect_type,fixed_amount,priority,valid_from,created_by_account_public_id,created_at,published_at) VALUES(?,?,?,?,?,'ORDER_AUTOMATIC','ORDER_FIXED',100,1,?,?,?,?)",family,id,number,"C3 foundation",status,Timestamp.from(NOW.minusSeconds(1)),creator,Timestamp.from(NOW),published);return id;}
    private void claim(UUID owner,UUID family,UUID revision,String status,Instant revokedAt,UUID revoker){jdbc.update("INSERT INTO voucher_claim(public_id,promotion_family_public_id,claimed_revision_public_id,customer_account_public_id,status,claimed_at,revoked_at,revoked_by_account_public_id,entity_version) VALUES(?,?,?,?,?,?,?,?,0)",UUID.randomUUID(),family,revision,owner,status,Timestamp.from(NOW),revokedAt==null?null:Timestamp.from(revokedAt),revoker);}
}
