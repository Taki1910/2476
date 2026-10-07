package com.shoecommerce.promotion;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import com.shoecommerce.audit.AuditWriter;
import com.shoecommerce.identity.*;
import com.shoecommerce.platform.api.InvalidRequestException;
import com.shoecommerce.platform.api.ResourceNotFoundException;

@Service
public class VoucherService {
    private final JdbcTemplate jdbc; private final AuthorizationPolicy authorization; private final AuditWriter audit; private final Clock clock;
    public VoucherService(JdbcTemplate jdbc,AuthorizationPolicy authorization,AuditWriter audit,Clock clock){this.jdbc=jdbc;this.authorization=authorization;this.audit=audit;this.clock=clock;}

    @Transactional(readOnly=true)
    public Page<PublicOffer> discover(int page,int size){page(page,size);Instant now=clock.instant();List<PublicOffer> rows=jdbc.query("""
      SELECT f.public_id,f.acquisition_mode,p.name,p.customer_summary,p.customer_terms,p.effect_type,p.percentage_value,p.fixed_amount,p.minimum_spend_amount,p.valid_from,p.valid_to
      FROM promotion_family f JOIN promotion p ON p.family_public_id=f.public_id
      WHERE f.acquisition_mode='CLAIMABLE' AND f.is_publicly_discoverable=1 AND p.status='PUBLISHED'
        AND p.valid_from<=? AND (p.valid_to IS NULL OR p.valid_to>?)
      ORDER BY p.valid_from DESC,f.public_id OFFSET ? ROWS FETCH NEXT ? ROWS ONLY
      """,(rs,n)->offer(rs),Timestamp.from(now),Timestamp.from(now),(long)page*size,size+1);return page(rows,page,size);}

    @Transactional(readOnly=true)
    public PublicOffer detail(UUID familyId){Instant now=clock.instant();PublicOffer offer=jdbc.query("""
      SELECT f.public_id,f.acquisition_mode,p.name,p.customer_summary,p.customer_terms,p.effect_type,p.percentage_value,p.fixed_amount,p.minimum_spend_amount,p.valid_from,p.valid_to
      FROM promotion_family f JOIN promotion p ON p.family_public_id=f.public_id
      WHERE f.public_id=? AND f.acquisition_mode='CLAIMABLE' AND f.is_publicly_discoverable=1 AND p.status='PUBLISHED'
        AND p.valid_from<=? AND (p.valid_to IS NULL OR p.valid_to>?)
      """,rs->rs.next()?offer(rs):null,familyId,Timestamp.from(now),Timestamp.from(now));if(offer==null)throw unavailable();return offer;}

    @Transactional(isolation=Isolation.SERIALIZABLE)
    public ClaimView claim(SessionPrincipal actor,UUID familyId){authorization.requireRole(actor,RoleCode.CUSTOMER);lockFamily(familyId);ClaimView existing=owned(actor.publicId(),familyId);if(existing!=null)return existing;Instant now=clock.instant();UUID revision=jdbc.query("""
      SELECT p.public_id FROM promotion_family f JOIN promotion p ON p.family_public_id=f.public_id
      WHERE f.public_id=? AND f.acquisition_mode='CLAIMABLE' AND f.is_publicly_discoverable=1 AND p.status='PUBLISHED'
        AND p.valid_from<=? AND (p.valid_to IS NULL OR p.valid_to>?)
      """,rs->rs.next()?rs.getObject(1,UUID.class):null,familyId,Timestamp.from(now),Timestamp.from(now));if(revision==null)throw unavailable();UUID claim=UUID.randomUUID();jdbc.update("INSERT INTO voucher_claim(public_id,promotion_family_public_id,claimed_revision_public_id,customer_account_public_id,status,claimed_at) VALUES(?,?,?,?,'CLAIMED',?)",claim,familyId,revision,actor.publicId(),Timestamp.from(now));audit.append(actor,"VOUCHER_CLAIM_CREATED","VOUCHER_CLAIM",claim,null,null,Map.of("familyId",familyId,"claimedRevisionId",revision));return owned(actor.publicId(),familyId);}

    @Transactional(readOnly=true)
    public Page<SavedClaim> saved(SessionPrincipal actor,int page,int size){authorization.requireRole(actor,RoleCode.CUSTOMER);page(page,size);Instant now=clock.instant();List<SavedClaim> rows=jdbc.query("""
      SELECT c.public_id,c.status,c.claimed_at,f.public_id,f.acquisition_mode,
        COALESCE(current_revision.name,claimed_revision.name),COALESCE(current_revision.customer_summary,claimed_revision.customer_summary),COALESCE(current_revision.customer_terms,claimed_revision.customer_terms),
        COALESCE(current_revision.effect_type,claimed_revision.effect_type),COALESCE(current_revision.percentage_value,claimed_revision.percentage_value),COALESCE(current_revision.fixed_amount,claimed_revision.fixed_amount),
        COALESCE(current_revision.minimum_spend_amount,claimed_revision.minimum_spend_amount),COALESCE(current_revision.valid_from,claimed_revision.valid_from),COALESCE(current_revision.valid_to,claimed_revision.valid_to),
        CASE
          WHEN c.status='REVOKED' THEN 'REVOKED'
          WHEN EXISTS(SELECT 1 FROM promotion_redemption r JOIN promotion rp ON rp.public_id=r.promotion_revision_public_id WHERE rp.family_public_id=f.public_id AND r.customer_account_public_id=c.customer_account_public_id AND r.status='REDEEMED') THEN 'USED'
          WHEN EXISTS(SELECT 1 FROM promotion_redemption r JOIN promotion rp ON rp.public_id=r.promotion_revision_public_id WHERE rp.family_public_id=f.public_id AND r.customer_account_public_id=c.customer_account_public_id AND r.status='RESERVED') THEN 'RESERVED'
          WHEN current_revision.public_id IS NOT NULL AND f.is_publicly_discoverable=1 AND current_revision.valid_from<=? AND (current_revision.valid_to IS NULL OR current_revision.valid_to>?) THEN 'AVAILABLE'
          WHEN COALESCE(current_revision.valid_to,claimed_revision.valid_to)<=? THEN 'EXPIRED'
          ELSE 'UNAVAILABLE'
        END,
        CASE WHEN current_revision.public_id IS NOT NULL AND f.is_publicly_discoverable=1 AND current_revision.valid_from<=? AND (current_revision.valid_to IS NULL OR current_revision.valid_to>?) THEN 1 ELSE 0 END
      FROM voucher_claim c JOIN promotion_family f ON f.public_id=c.promotion_family_public_id
      JOIN promotion claimed_revision ON claimed_revision.public_id=c.claimed_revision_public_id
      LEFT JOIN promotion current_revision ON current_revision.family_public_id=f.public_id AND current_revision.status='PUBLISHED'
      WHERE c.customer_account_public_id=? ORDER BY c.claimed_at DESC,c.public_id OFFSET ? ROWS FETCH NEXT ? ROWS ONLY
      """,(rs,n)->new SavedClaim(rs.getObject(1,UUID.class),rs.getString(2),rs.getTimestamp(3).toInstant(),offer(rs,4),rs.getString(15),rs.getBoolean(16)),Timestamp.from(now),Timestamp.from(now),Timestamp.from(now),Timestamp.from(now),Timestamp.from(now),actor.publicId(),(long)page*size,size+1);return page(rows,page,size);}

    @Transactional(readOnly=true)
    public ClaimState ownership(SessionPrincipal actor,UUID familyId){
        authorization.requireRole(actor,RoleCode.CUSTOMER);
        ClaimView claim=jdbc.query("SELECT public_id,promotion_family_public_id,claimed_revision_public_id,status,claimed_at,revoked_at FROM voucher_claim WHERE customer_account_public_id=? AND promotion_family_public_id=?",rs->rs.next()?claim(rs):null,actor.publicId(),familyId);
        if(claim==null)return new ClaimState(familyId,false,null,"UNCLAIMED");
        return new ClaimState(familyId,true,claim,availability(actor.publicId(),familyId,claim.claimStatus()));
    }

    @Transactional(readOnly=true)
    public Page<ManagedClaim> managedClaims(SessionPrincipal actor,UUID familyId,String query,int page,int size){
        authorization.requirePermission(actor,PermissionCode.PROMOTION_MANAGE);page(page,size);
        String search=query==null?"":query.trim().toLowerCase(Locale.ROOT);
        if(search.length()>80)throw new InvalidRequestException("VOUCHER_CLAIM_SEARCH_INVALID","Search query must not exceed 80 characters.");
        String familyFilter=familyId==null?"":" AND c.promotion_family_public_id=?";
        String searchFilter=search.isEmpty()?"":" AND CHARINDEX(?,LOWER(a.login_normalized))>0";
        List<Object> args=new ArrayList<>();if(familyId!=null)args.add(familyId);if(!search.isEmpty())args.add(search);args.add((long)page*size);args.add(size+1);
        List<ManagedClaim> rows=jdbc.query("""
          SELECT c.public_id,c.promotion_family_public_id,c.claimed_revision_public_id,c.customer_account_public_id,a.login_normalized,c.status,c.claimed_at,c.revoked_at,
            SUM(CASE WHEN r.status='RESERVED' THEN 1 ELSE 0 END) reserved_count,
            SUM(CASE WHEN r.status='REDEEMED' THEN 1 ELSE 0 END) redeemed_count,
            SUM(CASE WHEN r.status='RELEASED' THEN 1 ELSE 0 END) released_count
          FROM voucher_claim c JOIN iam_user_account a ON a.public_id=c.customer_account_public_id
          LEFT JOIN promotion p ON p.family_public_id=c.promotion_family_public_id
          LEFT JOIN promotion_redemption r ON r.promotion_revision_public_id=p.public_id AND r.customer_account_public_id=c.customer_account_public_id
          WHERE 1=1
          """+familyFilter+searchFilter+"""
          GROUP BY c.public_id,c.promotion_family_public_id,c.claimed_revision_public_id,c.customer_account_public_id,a.login_normalized,c.status,c.claimed_at,c.revoked_at
          ORDER BY c.claimed_at DESC,c.public_id OFFSET ? ROWS FETCH NEXT ? ROWS ONLY
          """,(rs,n)->new ManagedClaim(rs.getObject(1,UUID.class),rs.getObject(2,UUID.class),rs.getObject(3,UUID.class),rs.getObject(4,UUID.class),rs.getString(5),rs.getString(6),rs.getTimestamp(7).toInstant(),rs.getTimestamp(8)==null?null:rs.getTimestamp(8).toInstant(),rs.getLong(9),rs.getLong(10),rs.getLong(11)),args.toArray());
        return page(rows,page,size);
    }

    @Transactional(isolation=Isolation.SERIALIZABLE)
    public ClaimView revoke(SessionPrincipal actor,UUID claimId){authorization.requirePermission(actor,PermissionCode.PROMOTION_MANAGE);UUID family=jdbc.query("SELECT promotion_family_public_id FROM voucher_claim WHERE public_id=?",rs->rs.next()?rs.getObject(1,UUID.class):null,claimId);if(family==null)throw new ResourceNotFoundException("VOUCHER_CLAIM_NOT_FOUND","Voucher claim not found.");lockFamily(family);ClaimView claim=jdbc.query("SELECT public_id,promotion_family_public_id,claimed_revision_public_id,status,claimed_at,revoked_at FROM voucher_claim WITH (UPDLOCK,HOLDLOCK) WHERE public_id=?",rs->rs.next()?claim(rs):null,claimId);if(claim==null)throw new ResourceNotFoundException("VOUCHER_CLAIM_NOT_FOUND","Voucher claim not found.");if("REVOKED".equals(claim.claimStatus()))return claim;Instant now=clock.instant();jdbc.update("UPDATE voucher_claim SET status='REVOKED',revoked_at=?,revoked_by_account_public_id=?,entity_version=entity_version+1 WHERE public_id=? AND status='CLAIMED'",Timestamp.from(now),actor.publicId(),claimId);audit.append(actor,"VOUCHER_CLAIM_REVOKED","VOUCHER_CLAIM",claimId,null,null,Map.of("familyId",family));return jdbc.queryForObject("SELECT public_id,promotion_family_public_id,claimed_revision_public_id,status,claimed_at,revoked_at FROM voucher_claim WHERE public_id=?",(rs,n)->claim(rs),claimId);}

    private void lockFamily(UUID id){jdbc.query("SELECT public_id FROM promotion_family WITH (UPDLOCK,HOLDLOCK) WHERE public_id=?",rs->rs.next()?rs.getObject(1,UUID.class):null,id);}
    private ClaimView owned(UUID customer,UUID family){return jdbc.query("SELECT public_id,promotion_family_public_id,claimed_revision_public_id,status,claimed_at,revoked_at FROM voucher_claim WITH (UPDLOCK,HOLDLOCK) WHERE customer_account_public_id=? AND promotion_family_public_id=?",rs->rs.next()?claim(rs):null,customer,family);}
    private String availability(UUID customer,UUID family,String claimStatus){
        if("REVOKED".equals(claimStatus))return "REVOKED";
        String redemption=jdbc.query("SELECT TOP 1 r.status FROM promotion_redemption r JOIN promotion p ON p.public_id=r.promotion_revision_public_id WHERE p.family_public_id=? AND r.customer_account_public_id=? AND r.status IN ('REDEEMED','RESERVED') ORDER BY CASE r.status WHEN 'REDEEMED' THEN 0 ELSE 1 END",rs->rs.next()?rs.getString(1):null,family,customer);
        if("REDEEMED".equals(redemption))return "USED";if("RESERVED".equals(redemption))return "RESERVED";
        Instant now=clock.instant();Integer available=jdbc.queryForObject("SELECT COUNT(*) FROM promotion_family f JOIN promotion p ON p.family_public_id=f.public_id WHERE f.public_id=? AND f.acquisition_mode='CLAIMABLE' AND f.is_publicly_discoverable=1 AND p.status='PUBLISHED' AND p.valid_from<=? AND (p.valid_to IS NULL OR p.valid_to>?)",Integer.class,family,Timestamp.from(now),Timestamp.from(now));
        if(available!=null&&available>0)return "AVAILABLE";
        Integer expired=jdbc.queryForObject("SELECT COUNT(*) FROM voucher_claim c JOIN promotion p ON p.public_id=c.claimed_revision_public_id WHERE c.promotion_family_public_id=? AND c.customer_account_public_id=? AND p.valid_to<=?",Integer.class,family,customer,Timestamp.from(now));
        return expired!=null&&expired>0?"EXPIRED":"UNAVAILABLE";
    }
    private static ClaimView claim(java.sql.ResultSet rs)throws java.sql.SQLException{return new ClaimView(rs.getObject(1,UUID.class),rs.getObject(2,UUID.class),rs.getString(4),rs.getTimestamp(5).toInstant(),rs.getTimestamp(6)==null?null:rs.getTimestamp(6).toInstant());}
    private static PublicOffer offer(java.sql.ResultSet rs)throws java.sql.SQLException{return offer(rs,1);}
    private static PublicOffer offer(java.sql.ResultSet rs,int i)throws java.sql.SQLException{return new PublicOffer(rs.getObject(i,UUID.class),rs.getString(i+1),rs.getString(i+2),rs.getString(i+3),rs.getString(i+4),rs.getString(i+5),rs.getBigDecimal(i+6),nullableLong(rs,i+7),nullableLong(rs,i+8),rs.getTimestamp(i+9)==null?null:rs.getTimestamp(i+9).toInstant(),rs.getTimestamp(i+10)==null?null:rs.getTimestamp(i+10).toInstant(),"CLAIMABLE".equals(rs.getString(i+1)));}
    private static Long nullableLong(java.sql.ResultSet rs,int i)throws java.sql.SQLException{long value=rs.getLong(i);return rs.wasNull()?null:value;}
    private static void page(int page,int size){if(page<0||size<1||size>100)throw new InvalidRequestException("INVALID_PAGINATION","Page must be non-negative and size must be between 1 and 100.");}
    private static <T> Page<T> page(List<T> rows,int page,int size){boolean next=rows.size()>size;return new Page<>(next?List.copyOf(rows.subList(0,size)):List.copyOf(rows),page,size,next);}
    private static ResourceNotFoundException unavailable(){return new ResourceNotFoundException("VOUCHER_NOT_FOUND_OR_UNAVAILABLE","Voucher not found or unavailable.");}
    public record Page<T>(List<T> items,int page,int size,boolean hasNext){}
    public record PublicOffer(UUID familyId,String acquisitionMode,String name,String customerSummary,String customerTerms,String effectType,java.math.BigDecimal percentageValue,Long fixedAmount,Long minimumSpendAmount,Instant validFrom,Instant validTo,boolean claimable){}
    public record ClaimView(UUID claimId,UUID familyId,String claimStatus,Instant claimedAt,Instant revokedAt){}
    public record SavedClaim(UUID claimId,String claimStatus,Instant claimedAt,PublicOffer offer,String offerAvailability,boolean termsAvailable){}
    public record ClaimState(UUID familyId,boolean claimed,ClaimView claim,String offerAvailability){}
    public record ManagedClaim(UUID claimId,UUID familyId,UUID claimedRevisionId,UUID customerId,String customerLogin,String claimStatus,Instant claimedAt,Instant revokedAt,long reserved,long redeemed,long released){}
}
