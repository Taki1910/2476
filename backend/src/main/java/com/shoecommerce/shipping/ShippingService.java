package com.shoecommerce.shipping;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import com.shoecommerce.identity.AuthorizationPolicy;
import com.shoecommerce.identity.PermissionCode;
import com.shoecommerce.identity.SessionPrincipal;
import com.shoecommerce.platform.api.BusinessConflictException;
import com.shoecommerce.platform.api.InvalidRequestException;

@Service
public class ShippingService {
    private final JdbcTemplate jdbc;
    private final AuthorizationPolicy authorization;
    private final Clock clock;
    private final AddressResolver addresses;

    public ShippingService(JdbcTemplate jdbc, AuthorizationPolicy authorization, Clock clock, AddressResolver addresses) {
        this.jdbc = jdbc; this.authorization = authorization; this.clock = clock; this.addresses = addresses;
    }

    @Transactional(readOnly = true)
    public List<Reference> provinces() {
        return addresses.provinces().stream().map(value -> new Reference(value.code(), value.label())).toList();
    }

    @Transactional(readOnly = true)
    public List<Reference> districts(String provinceCode) {
        return addresses.districts(provinceCode).stream().map(value -> new Reference(value.code(), value.label())).toList();
    }

    @Transactional(readOnly = true)
    public List<Reference> wards(String provinceCode, String districtCode) {
        return addresses.wards(provinceCode, districtCode).stream().map(value -> new Reference(value.code(), value.label())).toList();
    }

    @Transactional(readOnly = true)
    public Quote quote(UUID locationId, UUID branchId, String provinceCode, String districtCode, String wardCode, Instant at) {
        AddressResolver.Location destination = addresses.resolveLocation(provinceCode, districtCode, wardCode);
        List<Quote> matches = jdbc.query("""
                SELECT TOP (2) public_id, zone_code, fee_amount
                FROM shipping_rate_rule
                WHERE status='PUBLISHED' AND destination_province_code=?
                  AND (destination_district_code=? OR destination_district_code IS NULL)
                  AND valid_from<=? AND (valid_to IS NULL OR valid_to>?)
                  AND ((origin_scope='LOCATION' AND origin_location_public_id=?)
                    OR (origin_scope='BRANCH' AND origin_branch_public_id=?) OR origin_scope='GLOBAL')
                ORDER BY
                  CASE
                    WHEN origin_scope='LOCATION' AND destination_district_code IS NOT NULL THEN 1
                    WHEN origin_scope='LOCATION' THEN 2
                    WHEN origin_scope='BRANCH' AND destination_district_code IS NOT NULL THEN 3
                    WHEN origin_scope='BRANCH' THEN 4
                    WHEN destination_district_code IS NOT NULL THEN 5 ELSE 6 END,
                  priority DESC
                """, (rs, row) -> new Quote(rs.getObject(1, UUID.class), rs.getString(2), rs.getLong(3)),
                destination.provinceCode(), destination.districtCode(), Timestamp.from(at), Timestamp.from(at), locationId, branchId);
        if (matches.isEmpty()) throw new BusinessConflictException("SHIPPING_DESTINATION_UNSUPPORTED", "Delivery is not currently available for this destination.");
        return matches.getFirst();
    }

    @Transactional(readOnly = true)
    public void requireQuoteUsable(UUID revisionId) {
        String status = jdbc.query("SELECT status FROM shipping_rate_rule WHERE public_id=?",
                rs -> rs.next() ? rs.getString(1) : null, revisionId);
        if (status == null || "INVALIDATED".equals(status)) {
            throw new BusinessConflictException("SHIPPING_QUOTE_STALE", "Stock or delivery pricing changed. Request a fresh quote.");
        }
    }

    @Transactional(readOnly = true)
    public List<RuleView> rules(SessionPrincipal actor) {
        authorization.requirePermission(actor, PermissionCode.SHIPPING_RATE_MANAGE);
        return jdbc.query("""
                SELECT public_id,family_public_id,revision_number,status,origin_scope,origin_location_public_id,origin_branch_public_id,
                  destination_province_code,destination_district_code,zone_code,fee_amount,priority,valid_from,valid_to
                FROM shipping_rate_rule ORDER BY family_public_id,revision_number DESC
                """, (rs, row) -> new RuleView(rs.getObject(1,UUID.class),rs.getObject(2,UUID.class),rs.getInt(3),rs.getString(4),
                        rs.getString(5),rs.getObject(6,UUID.class),rs.getObject(7,UUID.class),rs.getString(8),rs.getString(9),rs.getString(10),rs.getLong(11),
                        rs.getInt(12),rs.getTimestamp(13).toInstant(),rs.getTimestamp(14)==null?null:rs.getTimestamp(14).toInstant()));
    }
    @Transactional(readOnly=true)
    public List<LocationReference> locations(SessionPrincipal actor){authorization.requirePermission(actor,PermissionCode.SHIPPING_RATE_MANAGE);return jdbc.query("""
      SELECT locations.public_id,locations.code,locations.name,branches.public_id,branches.code
      FROM iam_staff_assignment assignments JOIN org_location locations ON locations.id=assignments.location_id AND locations.enabled=1
      JOIN org_branch branches ON branches.id=assignments.branch_id AND branches.enabled=1
      WHERE assignments.account_id=? AND assignments.active=1 ORDER BY branches.code,locations.code
      """,(rs,row)->new LocationReference(rs.getObject(1,UUID.class),rs.getString(2),rs.getString(3),rs.getObject(4,UUID.class),rs.getString(5)),actor.accountId());}

    @Transactional
    public RuleView createDraft(SessionPrincipal actor, Draft request) {
        authorizeScope(actor, request.originScope(), request.originLocationId());
        validateDraft(request);
        UUID publicId=UUID.randomUUID(), family=request.familyId()==null?UUID.randomUUID():request.familyId();
        int revision = request.revisionNumber()==null ? 1 : request.revisionNumber();
        UUID branchId=null;
        try {
            jdbc.update("""
              INSERT INTO shipping_rate_rule(public_id,family_public_id,revision_number,status,origin_scope,
                origin_location_public_id,origin_branch_public_id,destination_province_code,destination_district_code,
                zone_code,fee_amount,priority,valid_from,valid_to,created_by_account_public_id,created_at)
              VALUES (?,?,?, 'DRAFT', ?, ?, ?, ?,?,?,?,?,?,?,?,?)
              """, publicId,family,revision,request.originScope(),request.originLocationId(),branchId,request.provinceCode(),
                    request.districtCode(),request.zoneCode(),request.feeAmount(),request.priority(),Timestamp.from(request.validFrom()),
                    request.validTo()==null?null:Timestamp.from(request.validTo()),actor.publicId(),Timestamp.from(clock.instant()));
        } catch (DuplicateKeyException duplicate) {
            throw new BusinessConflictException("SHIPPING_RULE_REVISION_EXISTS", "This rule revision already exists.");
        }
        return rule(publicId);
    }

    @Transactional
    public RuleView editDraft(SessionPrincipal actor, UUID id, Draft request) {
        RuleView current=rule(id);
        if(!"DRAFT".equals(current.status())) throw new BusinessConflictException("SHIPPING_RULE_IMMUTABLE","Published shipping revisions are immutable.");
        authorizeScope(actor,current.originScope(),current.originLocationId()); authorizeScope(actor,request.originScope(),request.originLocationId()); validateDraft(request);
        UUID branchId=null;
        jdbc.update("""
          UPDATE shipping_rate_rule SET origin_scope=?,origin_location_public_id=?,origin_branch_public_id=?,
            destination_province_code=?,destination_district_code=?,zone_code=?,fee_amount=?,priority=?,valid_from=?,valid_to=?
          WHERE public_id=? AND status='DRAFT'
          """,request.originScope(),request.originLocationId(),branchId,request.provinceCode(),request.districtCode(),request.zoneCode(),
          request.feeAmount(),request.priority(),Timestamp.from(request.validFrom()),request.validTo()==null?null:Timestamp.from(request.validTo()),id);
        return rule(id);
    }

    @Transactional(isolation = Isolation.SERIALIZABLE)
    public RuleView publish(SessionPrincipal actor, UUID id) {
        authorization.requirePermission(actor, PermissionCode.SHIPPING_RATE_MANAGE);
        RuleView draft=rule(id);
        if (!"DRAFT".equals(draft.status())) throw new BusinessConflictException("SHIPPING_RULE_IMMUTABLE", "Only draft revisions can be published.");
        authorizeScope(actor,draft.originScope(),draft.originLocationId());
        Integer conflicts=jdbc.queryForObject("""
          SELECT COUNT(*) FROM shipping_rate_rule WITH (UPDLOCK,HOLDLOCK,INDEX(IX_shipping_rate_rule_conflict))
          WHERE public_id<>? AND status='PUBLISHED' AND origin_scope=?
            AND ((origin_location_public_id=? ) OR (origin_location_public_id IS NULL AND ? IS NULL))
            AND ((origin_branch_public_id=? ) OR (origin_branch_public_id IS NULL AND ? IS NULL))
            AND destination_province_code=?
            AND ((destination_district_code=?) OR (destination_district_code IS NULL AND ? IS NULL))
            AND priority=? AND valid_from < COALESCE(?,CONVERT(datetime2,'9999-12-31'))
            AND ? < COALESCE(valid_to,CONVERT(datetime2,'9999-12-31'))
          """,Integer.class,id,draft.originScope(),draft.originLocationId(),draft.originLocationId(),draft.originBranchId(),draft.originBranchId(),draft.provinceCode(),
                draft.districtCode(),draft.districtCode(),draft.priority(),draft.validTo()==null?null:Timestamp.from(draft.validTo()),Timestamp.from(draft.validFrom()));
        if (conflicts!=null && conflicts>0) throw new BusinessConflictException("SHIPPING_RULE_OVERLAP", "A conflicting shipping rule is already effective in this period.");
        jdbc.update("UPDATE shipping_rate_rule SET status='PUBLISHED',published_at=? WHERE public_id=? AND status='DRAFT'",Timestamp.from(clock.instant()),id);
        return rule(id);
    }

    @Transactional
    public RuleView transition(SessionPrincipal actor, UUID id, String target) {
        authorization.requirePermission(actor, PermissionCode.SHIPPING_RATE_MANAGE);
        RuleView current=rule(id); authorizeScope(actor,current.originScope(),current.originLocationId());
        if (!"PUBLISHED".equals(current.status()) || !("RETIRED".equals(target)||"INVALIDATED".equals(target)))
            throw new BusinessConflictException("SHIPPING_RULE_TRANSITION", "This shipping-rule transition is not allowed.");
        Instant now=clock.instant(); String timeColumn="RETIRED".equals(target)?"retired_at":"invalidated_at";
        jdbc.update("UPDATE shipping_rate_rule SET status=?, "+timeColumn+"=? WHERE public_id=? AND status='PUBLISHED'",target,Timestamp.from(now),id);
        return rule(id);
    }

    private RuleView rule(UUID id) {
        List<RuleView> rows=jdbc.query("""
          SELECT public_id,family_public_id,revision_number,status,origin_scope,origin_location_public_id,origin_branch_public_id,
            destination_province_code,destination_district_code,zone_code,fee_amount,priority,valid_from,valid_to
          FROM shipping_rate_rule WHERE public_id=?
          """,(rs,row)->new RuleView(rs.getObject(1,UUID.class),rs.getObject(2,UUID.class),rs.getInt(3),rs.getString(4),rs.getString(5),
             rs.getObject(6,UUID.class),rs.getObject(7,UUID.class),rs.getString(8),rs.getString(9),rs.getString(10),rs.getLong(11),rs.getInt(12),
             rs.getTimestamp(13).toInstant(),rs.getTimestamp(14)==null?null:rs.getTimestamp(14).toInstant()),id);
        if(rows.isEmpty()) throw new IllegalArgumentException("Shipping rule not found"); return rows.getFirst();
    }

    private void authorizeScope(SessionPrincipal actor,String scope,UUID locationId) {
        authorization.requirePermission(actor,PermissionCode.SHIPPING_RATE_MANAGE);
        if("GLOBAL".equals(scope)) authorization.requirePermission(actor,PermissionCode.IDENTITY_MANAGE);
        else if("LOCATION".equals(scope) && locationId!=null) authorization.requireLocationAccess(actor,locationId);
        else throw new AccessDeniedException("C1 administration supports exact Location or global origin only");
    }
    private void validateProvince(String province) {
        addresses.districts(province);
    }
    private void validateDestination(String province,String district) { addresses.wards(province,district); }
    private void validateDraft(Draft r){if(r.districtCode()==null)validateProvince(r.provinceCode());else validateDestination(r.provinceCode(),r.districtCode());if(r.feeAmount()<0||r.priority()==null||r.validFrom()==null||
      !("LOCATION".equals(r.originScope())||"GLOBAL".equals(r.originScope()))||!("LOCAL_CORE".equals(r.zoneCode())||"LOCAL_OUTER".equals(r.zoneCode())||"INTER_PROVINCE".equals(r.zoneCode()))||
      (r.validTo()!=null&&!r.validTo().isAfter(r.validFrom()))) throw new InvalidRequestException("INVALID_SHIPPING_RULE","Shipping rule is invalid.");}

    public record Reference(String code,String label){}
    public record LocationReference(UUID id,String code,String name,UUID branchId,String branchCode){}
    public record Quote(UUID revisionId,String zoneCode,long feeAmount){}
    public record Draft(UUID familyId,Integer revisionNumber,String originScope,UUID originLocationId,String provinceCode,
      String districtCode,String zoneCode,long feeAmount,Integer priority,Instant validFrom,Instant validTo){}
    public record RuleView(UUID id,UUID familyId,int revisionNumber,String status,String originScope,UUID originLocationId,UUID originBranchId,
      String provinceCode,String districtCode,String zoneCode,long feeAmount,int priority,Instant validFrom,Instant validTo){}
}
