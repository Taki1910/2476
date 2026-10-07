package com.shoecommerce.promotion;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.Normalizer;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import com.shoecommerce.audit.AuditWriter;
import com.shoecommerce.identity.AuthorizationPolicy;
import com.shoecommerce.identity.PermissionCode;
import com.shoecommerce.identity.SessionPrincipal;
import com.shoecommerce.platform.api.BusinessConflictException;
import com.shoecommerce.platform.api.InvalidRequestException;

@Service
public class PromotionService {
    private static final long MAX_AMOUNT = 9_999_999_999L;
    private final JdbcTemplate jdbc;
    private final AuthorizationPolicy authorization;
    private final AuditWriter audit;
    private final Clock clock;

    public PromotionService(JdbcTemplate jdbc, AuthorizationPolicy authorization, AuditWriter audit, Clock clock) {
        this.jdbc=jdbc; this.authorization=authorization; this.audit=audit; this.clock=clock;
    }

    @Transactional(readOnly=true)
    public List<RuleView> rules(SessionPrincipal actor) {
        authorization.requirePermission(actor,PermissionCode.PROMOTION_MANAGE);
        Map<UUID,Set<UUID>> scopeMap=new HashMap<>();
        jdbc.query("SELECT p.public_id,s.product_public_id FROM promotion p JOIN promotion_product_scope s ON s.promotion_id=p.id ORDER BY p.public_id,s.product_public_id",rs->{scopeMap.computeIfAbsent(rs.getObject(1,UUID.class),ignored->new LinkedHashSet<>()).add(rs.getObject(2,UUID.class));});
        return jdbc.query("""
          SELECT p.public_id,p.family_public_id,revision_number,name,status,layer,effect_type,percentage_value,fixed_amount,
            minimum_spend_amount,buy_quantity,global_usage_limit,per_customer_usage_limit,priority,valid_from,valid_to,
            customer_summary,customer_terms,f.acquisition_mode,f.normalized_code,f.is_publicly_discoverable
          FROM promotion p JOIN promotion_family f ON f.public_id=p.family_public_id ORDER BY p.family_public_id,revision_number DESC
          """,(rs,row)->new RuleView(rs.getObject(1,UUID.class),rs.getObject(2,UUID.class),rs.getInt(3),rs.getString(4),rs.getString(5),
            rs.getString(6),rs.getString(7),decimal(rs,8),number(rs,9),number(rs,10),number(rs,11),number(rs,12),number(rs,13),
            rs.getInt(14),rs.getTimestamp(15).toInstant(),instant(rs,16),rs.getString(17),rs.getString(18),scopeMap.getOrDefault(rs.getObject(1,UUID.class),Set.of()),rs.getString(19),rs.getString(20),rs.getBoolean(21)));
    }

    @Transactional(readOnly=true)
    public CampaignList campaigns(SessionPrincipal actor) {
        authorization.requirePermission(actor,PermissionCode.PROMOTION_MANAGE);
        Instant asOf=clock.instant();
        Map<UUID,List<RuleView>> grouped=rules(actor).stream().collect(Collectors.groupingBy(RuleView::familyId,LinkedHashMap::new,Collectors.toList()));
        Map<UUID,ProductReference> references=productReferences(actor,"").stream().collect(Collectors.toMap(ProductReference::id,reference->reference));
        Map<UUID,UsageMetrics> metrics=usage(grouped.keySet());
        List<CampaignView> items=grouped.entrySet().stream().map(entry->campaign(entry.getKey(),entry.getValue(),asOf,references,metrics.getOrDefault(entry.getKey(),UsageMetrics.empty()))).toList();
        return new CampaignList(asOf,items);
    }

    @Transactional(readOnly=true)
    public CampaignView campaign(SessionPrincipal actor,UUID familyId) {
        authorization.requirePermission(actor,PermissionCode.PROMOTION_MANAGE);
        List<RuleView> revisions=rules(actor).stream().filter(rule->rule.familyId().equals(familyId)).toList();
        if(revisions.isEmpty())throw new com.shoecommerce.platform.api.ResourceNotFoundException("PROMOTION_FAMILY_NOT_FOUND","Promotion family not found.");
        return campaign(familyId,revisions,clock.instant());
    }

    private CampaignView campaign(UUID familyId,List<RuleView> revisions,Instant asOf){
        Set<UUID> ids=revisions.stream().flatMap(rule->rule.productIds().stream()).collect(Collectors.toCollection(LinkedHashSet::new));
        Map<UUID,ProductReference> references=productReferences(ids).stream().collect(Collectors.toMap(ProductReference::id,reference->reference));
        return campaign(familyId,revisions,asOf,references,usage(familyId));
    }

    private CampaignView campaign(UUID familyId,List<RuleView> revisions,Instant asOf,Map<UUID,ProductReference> references,UsageMetrics metrics){
        List<RuleView> ordered=revisions.stream().sorted(Comparator.comparingInt(RuleView::revisionNumber).reversed()).toList();
        RuleView current=ordered.stream().filter(rule->"PUBLISHED".equals(rule.status())).findFirst().orElse(null);
        List<RuleView> drafts=ordered.stream().filter(rule->"DRAFT".equals(rule.status())).toList();
        RuleView representative=current!=null?current:ordered.stream().filter(rule->!"DRAFT".equals(rule.status())).findFirst().orElse(ordered.getFirst());
        boolean publishedEver=ordered.stream().anyMatch(rule->!"DRAFT".equals(rule.status()));
        String state=current!=null?displayState(current.status(),current.validFrom(),current.validTo(),asOf,true):displayState(representative.status(),representative.validFrom(),representative.validTo(),asOf,publishedEver);
        Set<UUID> targetIds=representative.productIds();
        return new CampaignView(familyId,state,asOf,representative.acquisitionMode(),representative.normalizedCode(),representative.publiclyDiscoverable(),current,drafts,ordered,targetIds.stream().map(references::get).filter(Objects::nonNull).toList(),metrics);
    }

    @Transactional(readOnly=true)
    public List<ProductReference> productReferences(SessionPrincipal actor,String query){
        authorization.requirePermission(actor,PermissionCode.PROMOTION_MANAGE);
        String search=query==null?"":query.trim().toLowerCase(Locale.ROOT);
        if(search.length()>80)throw new InvalidRequestException("CATALOG_SEARCH_INVALID","Search query must not exceed 80 characters.");
        String filter=search.isEmpty()?"":" WHERE CHARINDEX(?,LOWER(p.name))>0 OR CHARINDEX(?,LOWER(COALESCE(p.category,'')))>0";
        Object[] args=search.isEmpty()?new Object[0]:new Object[]{search,search};
        return jdbc.query("""
          SELECT p.public_id,p.name,p.category,
            COALESCE(SUM(CASE WHEN v.lifecycle_status='PUBLISHED' THEN 1 ELSE 0 END),0) published_count,
            MIN(CASE WHEN v.lifecycle_status='PUBLISHED' THEN price.amount END) minimum_amount,
            CASE WHEN EXISTS(
              SELECT 1 FROM catalog_product_variant sv
              JOIN pricing_variant_price sp ON sp.variant_id=sv.id AND sp.valid_to IS NULL
              JOIN inventory_balance b ON b.variant_id=sv.id AND b.on_hand-b.reserved>0
              JOIN org_location l ON l.id=b.location_id AND l.enabled=1
              JOIN org_branch branch ON branch.id=l.branch_id AND branch.enabled=1
              WHERE sv.product_id=p.id AND sv.lifecycle_status='PUBLISHED'
            ) THEN 1 ELSE 0 END sellable
          FROM catalog_product p
          LEFT JOIN catalog_product_variant v ON v.product_id=p.id
          LEFT JOIN pricing_variant_price price ON price.variant_id=v.id AND price.valid_to IS NULL
          """+filter+" GROUP BY p.id,p.public_id,p.name,p.category ORDER BY p.name,p.public_id",
          (rs,row)->new ProductReference(rs.getObject(1,UUID.class),rs.getString(2),rs.getString(3),rs.getInt(4),number(rs,5),rs.getBoolean(6)),args);
    }

    @Transactional
    public CampaignView setDiscoverability(SessionPrincipal actor,UUID familyId,VisibilityCommand command){
        authorization.requirePermission(actor,PermissionCode.PROMOTION_MANAGE);
        if(command==null)invalid();
        Family family=lockFamily(familyId);
        validateAcquisitionPolicy(family.mode(),command.publiclyDiscoverable(),null);
        if("AUTOMATIC".equals(family.mode())&&command.publiclyDiscoverable())throw new InvalidRequestException("PROMOTION_ACQUISITION_MODE_INVALID","Automatic promotions cannot be publicly discoverable.");
        if(family.discoverable()!=command.publiclyDiscoverable()){
            jdbc.update("UPDATE promotion_family SET is_publicly_discoverable=? WHERE public_id=?",command.publiclyDiscoverable(),familyId);
            audit.append(actor,"PROMOTION_FAMILY_VISIBILITY_CHANGED","PROMOTION_FAMILY",familyId,null,null,Map.of("publiclyDiscoverable",command.publiclyDiscoverable()));
        }
        return campaign(actor,familyId);
    }

    @Transactional(readOnly=true)
    public PreviewResult preview(SessionPrincipal actor,PreviewRequest request){
        authorization.requirePermission(actor,PermissionCode.PROMOTION_MANAGE);
        if(request==null||request.draft()==null||request.productId()==null||request.quantity()<1||request.quantity()>10||request.shippingFeeAmount()<0||request.shippingFeeAmount()>MAX_AMOUNT)invalid();
        Draft draft=request.draft();
        Family family=draft.familyId()==null?newFamily(draft):family(draft.familyId());
        assertFamilyInput(draft,family);validateVoucher(draft,family);validateRule(draft);
        PreviewProduct product=jdbc.query("""
          SELECT TOP 1 p.public_id,p.name,v.public_id,v.sku,price.amount
          FROM catalog_product p JOIN catalog_product_variant v ON v.product_id=p.id AND v.lifecycle_status='PUBLISHED'
          JOIN pricing_variant_price price ON price.variant_id=v.id AND price.valid_to IS NULL
          WHERE p.public_id=? ORDER BY price.amount,v.public_id
          """,rs->rs.next()?new PreviewProduct(rs.getObject(1,UUID.class),rs.getString(2),rs.getObject(3,UUID.class),rs.getString(4),rs.getLong(5)):null,request.productId());
        if(product==null)throw new InvalidRequestException("PROMOTION_PREVIEW_PRODUCT_UNAVAILABLE","Choose a product with a published priced variant for preview.");
        UUID familyId=draft.familyId()==null?UUID.nameUUIDFromBytes("promotion-preview-family".getBytes(java.nio.charset.StandardCharsets.UTF_8)):draft.familyId();
        UUID revisionId=UUID.nameUUIDFromBytes("promotion-preview-revision".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        Candidate candidate=new Candidate(0,revisionId,familyId,draft.revisionNumber()==null?1:draft.revisionNumber(),draft.name().trim(),"DRAFT",draft.layer(),draft.effectType(),draft.percentageValue(),draft.fixedAmount(),draft.minimumSpendAmount(),draft.buyQuantity(),null,null,draft.priority(),draft.productIds());
        Line line=new Line(product.variantId(),product.productId(),request.quantity(),product.unitPriceAmount());
        // Preview deliberately bypasses ownership/usage lookups while preserving the exact calculator arithmetic.
        Selected selected="AUTOMATIC".equals(family.mode())?null:new Selected(candidate,"CODE","CODE".equals(family.mode())?mask(family.code()):null,null);
        Instant asOf=clock.instant();
        Evaluation result=calculate(List.of(line),request.shippingFeeAmount(),asOf,actor.publicId(),request.shippingFeeAmount()>0,"AUTOMATIC".equals(family.mode())?List.of(candidate):List.of(),selected,false);
        return new PreviewResult(asOf,product,request.quantity(),result.merchandise(),result.merchandiseDiscount(),result.shippingFee(),result.shippingDiscount(),result.total(),result.adjustments().stream().map(a->new PreviewAdjustment(a.name(),a.type(),a.layer(),a.base(),a.amount())).toList());
    }

    private List<ProductReference> productReferences(Set<UUID> ids){
        if(ids.isEmpty())return List.of();
        return ids.stream().map(id->jdbc.query("""
          SELECT p.public_id,p.name,p.category,
            (SELECT COUNT(*) FROM catalog_product_variant v WHERE v.product_id=p.id AND v.lifecycle_status='PUBLISHED'),
            (SELECT MIN(price.amount) FROM catalog_product_variant v JOIN pricing_variant_price price ON price.variant_id=v.id AND price.valid_to IS NULL WHERE v.product_id=p.id AND v.lifecycle_status='PUBLISHED'),
            CASE WHEN EXISTS(SELECT 1 FROM catalog_product_variant v JOIN pricing_variant_price price ON price.variant_id=v.id AND price.valid_to IS NULL JOIN inventory_balance b ON b.variant_id=v.id AND b.on_hand-b.reserved>0 WHERE v.product_id=p.id AND v.lifecycle_status='PUBLISHED') THEN 1 ELSE 0 END
          FROM catalog_product p WHERE p.public_id=?
          """,rs->rs.next()?new ProductReference(rs.getObject(1,UUID.class),rs.getString(2),rs.getString(3),rs.getInt(4),number(rs,5),rs.getBoolean(6)):null,id)).filter(Objects::nonNull).toList();
    }

    private UsageMetrics usage(UUID familyId){
        return jdbc.query("""
          SELECT
            (SELECT COUNT_BIG(*) FROM voucher_claim c WHERE c.promotion_family_public_id=? AND c.status='CLAIMED'),
            (SELECT COUNT_BIG(*) FROM voucher_claim c WHERE c.promotion_family_public_id=? AND c.status='REVOKED'),
            (SELECT COUNT_BIG(*) FROM promotion_redemption r JOIN promotion p ON p.public_id=r.promotion_revision_public_id WHERE p.family_public_id=? AND r.status='RESERVED'),
            (SELECT COUNT_BIG(*) FROM promotion_redemption r JOIN promotion p ON p.public_id=r.promotion_revision_public_id WHERE p.family_public_id=? AND r.status='REDEEMED'),
            (SELECT COUNT_BIG(*) FROM promotion_redemption r JOIN promotion p ON p.public_id=r.promotion_revision_public_id WHERE p.family_public_id=? AND r.status='RELEASED'),
            (SELECT COUNT_BIG(DISTINCT applied.order_public_id) FROM (
              SELECT a.order_public_id FROM commerce_order_adjustment a JOIN commerce_order o ON o.public_id=a.order_public_id AND o.status='PAID' WHERE a.promotion_family_public_id=?
              UNION SELECT o.public_id FROM commerce_order_item_adjustment a JOIN commerce_order_item i ON i.public_id=a.order_item_public_id JOIN commerce_order o ON o.id=i.order_id AND o.status='PAID' WHERE a.promotion_family_public_id=?
            ) applied),
            COALESCE((SELECT SUM(applied.amount) FROM (
              SELECT a.applied_amount amount FROM commerce_order_adjustment a JOIN commerce_order o ON o.public_id=a.order_public_id AND o.status='PAID' WHERE a.promotion_family_public_id=? AND a.layer='SHIPPING'
              UNION ALL SELECT a.applied_amount FROM commerce_order_item_adjustment a JOIN commerce_order_item i ON i.public_id=a.order_item_public_id JOIN commerce_order o ON o.id=i.order_id AND o.status='PAID' WHERE a.promotion_family_public_id=?
            ) applied),0)
          """,rs->rs.next()?new UsageMetrics(rs.getLong(1),rs.getLong(2),rs.getLong(3),rs.getLong(4),rs.getLong(5),rs.getLong(6),rs.getLong(7)):UsageMetrics.empty(),familyId,familyId,familyId,familyId,familyId,familyId,familyId,familyId,familyId);
    }

    private Map<UUID,UsageMetrics> usage(Set<UUID> familyIds){
        if(familyIds.isEmpty())return Map.of();
        String markers=String.join(",",Collections.nCopies(familyIds.size(),"?"));
        return jdbc.query("""
          WITH claim_stats AS (
            SELECT promotion_family_public_id family_id,
              SUM(CASE WHEN status='CLAIMED' THEN 1 ELSE 0 END) claimed,
              SUM(CASE WHEN status='REVOKED' THEN 1 ELSE 0 END) revoked
            FROM voucher_claim GROUP BY promotion_family_public_id
          ), redemption_stats AS (
            SELECT p.family_public_id family_id,
              SUM(CASE WHEN r.status='RESERVED' THEN 1 ELSE 0 END) reserved,
              SUM(CASE WHEN r.status='REDEEMED' THEN 1 ELSE 0 END) redeemed,
              SUM(CASE WHEN r.status='RELEASED' THEN 1 ELSE 0 END) released
            FROM promotion_redemption r JOIN promotion p ON p.public_id=r.promotion_revision_public_id GROUP BY p.family_public_id
          ), paid_adjustments AS (
            SELECT a.promotion_family_public_id family_id,a.order_public_id,CASE WHEN a.layer='SHIPPING' THEN a.applied_amount ELSE 0 END amount
            FROM commerce_order_adjustment a JOIN commerce_order o ON o.public_id=a.order_public_id AND o.status='PAID'
            UNION ALL
            SELECT a.promotion_family_public_id,o.public_id,a.applied_amount
            FROM commerce_order_item_adjustment a JOIN commerce_order_item i ON i.public_id=a.order_item_public_id JOIN commerce_order o ON o.id=i.order_id AND o.status='PAID'
          ), paid_stats AS (
            SELECT family_id,COUNT_BIG(DISTINCT order_public_id) paid_orders,SUM(amount) discount_granted FROM paid_adjustments GROUP BY family_id
          )
          SELECT f.public_id,COALESCE(c.claimed,0),COALESCE(c.revoked,0),COALESCE(r.reserved,0),COALESCE(r.redeemed,0),COALESCE(r.released,0),COALESCE(p.paid_orders,0),COALESCE(p.discount_granted,0)
          FROM promotion_family f LEFT JOIN claim_stats c ON c.family_id=f.public_id LEFT JOIN redemption_stats r ON r.family_id=f.public_id LEFT JOIN paid_stats p ON p.family_id=f.public_id
          WHERE f.public_id IN ("""+markers+")",rs->{Map<UUID,UsageMetrics> result=new HashMap<>();while(rs.next())result.put(rs.getObject(1,UUID.class),new UsageMetrics(rs.getLong(2),rs.getLong(3),rs.getLong(4),rs.getLong(5),rs.getLong(6),rs.getLong(7),rs.getLong(8)));return result;},familyIds.toArray());
    }

    @Transactional(isolation=Isolation.SERIALIZABLE)
    public RuleView create(SessionPrincipal actor,Draft d) {
        authorization.requirePermission(actor,PermissionCode.PROMOTION_MANAGE);
        if(d==null)invalid();
        UUID id=UUID.randomUUID(),family=d.familyId()==null?UUID.randomUUID():d.familyId(); Family authority;
        try {
          authority=d.familyId()==null?newFamily(d):lockFamily(family);assertFamilyInput(d,authority);validateVoucher(d,authority);validateRule(d);
          if(d.familyId()==null){jdbc.update("INSERT INTO promotion_family(public_id,acquisition_mode,normalized_code,is_publicly_discoverable,created_at) VALUES(?,?,?,?,?)",family,authority.mode(),authority.code(),authority.discoverable(),Timestamp.from(clock.instant()));audit.append(actor,"PROMOTION_FAMILY_CREATED","PROMOTION_FAMILY",family,null,null,Map.of("acquisitionMode",authority.mode(),"publiclyDiscoverable",authority.discoverable()));}
          int expected=jdbc.queryForObject("SELECT COALESCE(MAX(revision_number),0)+1 FROM promotion WITH (UPDLOCK,HOLDLOCK) WHERE family_public_id=?",Integer.class,family);
          int revision=d.revisionNumber()==null?expected:d.revisionNumber();if(revision!=expected)throw new BusinessConflictException("PROMOTION_REVISION_EXISTS","The requested promotion revision is not the next revision.");
          jdbc.update("""
          INSERT INTO promotion(family_public_id,public_id,revision_number,name,status,layer,effect_type,percentage_value,fixed_amount,
            minimum_spend_amount,buy_quantity,global_usage_limit,per_customer_usage_limit,priority,valid_from,valid_to,
            created_by_account_public_id,created_at,customer_summary,customer_terms)
          VALUES(?,?,?,?,'DRAFT',?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
          """,family,id,revision,d.name().trim(),d.layer(),d.effectType(),d.percentageValue(),d.fixedAmount(),d.minimumSpendAmount(),
            d.buyQuantity(),d.globalUsageLimit(),d.perCustomerUsageLimit(),d.priority(),Timestamp.from(d.validFrom()),timestamp(d.validTo()),
            actor.publicId(),Timestamp.from(clock.instant()),trim(d.customerSummary()),trim(d.customerTerms()));
          replaceScopes(id,d.productIds());
          audit.append(actor,"PROMOTION_REVISION_CREATED","PROMOTION",id,null,null,Map.of("familyId",family,"revision",revision));
        } catch(DuplicateKeyException e){if("CODE".equals(d.acquisitionMode()))throw new BusinessConflictException("PROMOTION_CODE_ALREADY_EXISTS","That promotion code is already in use.");throw new BusinessConflictException("PROMOTION_REVISION_EXISTS","This promotion revision already exists.");}
        return rule(id);
    }

    @Transactional
    public RuleView edit(SessionPrincipal actor,UUID id,Draft d) {
        authorization.requirePermission(actor,PermissionCode.PROMOTION_MANAGE); RuleView current=rule(id);
        if(d==null)invalid();
        if(!"DRAFT".equals(current.status())) throw new BusinessConflictException("PROMOTION_REVISION_NOT_DRAFT","Only draft promotion revisions can be edited.");
        Family authority=lockFamily(current.familyId());assertFamilyInput(d,authority);validateVoucher(d,authority);validateRule(d);
        jdbc.update("""
          UPDATE promotion SET name=?,layer=?,effect_type=?,percentage_value=?,fixed_amount=?,minimum_spend_amount=?,buy_quantity=?,
            global_usage_limit=?,per_customer_usage_limit=?,priority=?,valid_from=?,valid_to=?,customer_summary=?,customer_terms=?
            WHERE public_id=? AND status='DRAFT'
          """,d.name().trim(),d.layer(),d.effectType(),d.percentageValue(),d.fixedAmount(),d.minimumSpendAmount(),d.buyQuantity(),
            d.globalUsageLimit(),d.perCustomerUsageLimit(),d.priority(),Timestamp.from(d.validFrom()),timestamp(d.validTo()),
            d.customerSummary(),d.customerTerms(),id);
        replaceScopes(id,d.productIds()); return rule(id);
    }

    @Transactional(isolation=Isolation.SERIALIZABLE)
    public RuleView publish(SessionPrincipal actor,UUID id) {return publish(actor,id,null);}

    @Transactional(isolation=Isolation.SERIALIZABLE)
    public RuleView publish(SessionPrincipal actor,UUID id,UUID expectedPublishedRevisionId) {
        authorization.requirePermission(actor,PermissionCode.PROMOTION_MANAGE); RuleView observed=rule(id);lockFamily(observed.familyId());
        List<UUID> locked=jdbc.queryForList("SELECT public_id FROM promotion WITH (UPDLOCK,HOLDLOCK) WHERE family_public_id=? ORDER BY public_id",UUID.class,observed.familyId());
        if(!locked.contains(id))throw new IllegalArgumentException("Promotion not found");
        RuleView target=rule(id);if(!"DRAFT".equals(target.status()))throw new BusinessConflictException("PROMOTION_REVISION_NOT_DRAFT","Only a draft promotion revision can be published.");
        UUID current=jdbc.query("SELECT public_id FROM promotion WHERE family_public_id=? AND status='PUBLISHED'",rs->rs.next()?rs.getObject(1,UUID.class):null,target.familyId());
        if(!Objects.equals(expectedPublishedRevisionId,current)&&expectedPublishedRevisionId!=null)throw new BusinessConflictException("PROMOTION_PUBLICATION_CONFLICT","The published revision changed. Refresh and try again.");
        Family authority=family(target.familyId());validateRule(target.draft());validateVoucher(target.draft(),authority);Instant now=clock.instant();
        if(current!=null)jdbc.update("UPDATE promotion SET status='RETIRED',retired_at=? WHERE public_id=? AND status='PUBLISHED'",Timestamp.from(now),current);
        if(jdbc.update("UPDATE promotion SET status='PUBLISHED',published_at=? WHERE public_id=? AND status='DRAFT'",Timestamp.from(now),id)!=1)throw new BusinessConflictException("PROMOTION_PUBLICATION_CONFLICT","The promotion revision changed. Refresh and try again.");
        audit.append(actor,current==null?"PROMOTION_PUBLISHED":"PROMOTION_REPLACED","PROMOTION",id,null,null,current==null?Map.of("familyId",target.familyId(),"revision",target.revisionNumber()):Map.of("familyId",target.familyId(),"revision",target.revisionNumber(),"retiredRevisionId",current));
        return rule(id);
    }

    @Transactional(isolation=Isolation.SERIALIZABLE)
    public RuleView transition(SessionPrincipal actor,UUID id,String state) {
        authorization.requirePermission(actor,PermissionCode.PROMOTION_MANAGE); RuleView r=rule(id);lockFamily(r.familyId());jdbc.queryForList("SELECT public_id FROM promotion WITH (UPDLOCK,HOLDLOCK) WHERE family_public_id=? ORDER BY public_id",UUID.class,r.familyId());r=rule(id);
        if(!"PUBLISHED".equals(r.status())||!("RETIRED".equals(state)||"INVALIDATED".equals(state)))
            throw new BusinessConflictException("PROMOTION_TRANSITION","This promotion transition is not allowed.");
        Instant now=clock.instant(); String column="RETIRED".equals(state)?"retired_at":"invalidated_at";
        jdbc.update("UPDATE promotion SET status=?,"+column+"=? WHERE public_id=? AND status='PUBLISHED'",state,Timestamp.from(now),id);
        audit.append(actor,"PROMOTION_"+state,"PROMOTION",id,null,null,Map.of("familyId",r.familyId(),"revision",r.revisionNumber()));
        return rule(id);
    }

    public Evaluation evaluate(List<Line> lines,long shippingFee,Instant at,Set<UUID> accepted,boolean lock,UUID customer) {
        return evaluate(lines,shippingFee,at,accepted,lock,customer,VoucherSelection.none(),true);
    }

    public Evaluation evaluate(List<Line> lines,long shippingFee,Instant at,Set<UUID> accepted,boolean lock,UUID customer,VoucherSelection selection,boolean delivery) {
        List<Candidate> candidates=candidates(at,accepted,lock);
        return calculate(lines,shippingFee,at,customer,delivery,candidates,resolve(selection,customer,at,lock),lock);
    }

    public Evaluation revalidate(List<Line> lines,long shippingFee,Instant at,List<Adjustment> accepted,UUID customer,boolean delivery) {
        Adjustment voucher=accepted.stream().filter(a->!"AUTOMATIC".equals(a.acquisitionMode())).findFirst().orElse(null);
        Set<UUID> automatic=accepted.stream().filter(a->"AUTOMATIC".equals(a.acquisitionMode())).map(Adjustment::revisionId).collect(Collectors.toSet());
        try {
            lockFamilies(at,automatic,voucher==null?null:voucher.familyId());
            List<Candidate> candidates=candidates(at,automatic,true);
            Selected selected=voucher==null?null:resolveAccepted(voucher,customer,at);
            return calculate(lines,shippingFee,at,customer,delivery,candidates,selected,true);
        } catch (InvalidRequestException | org.springframework.dao.EmptyResultDataAccessException stale) {
            throw new BusinessConflictException("VOUCHER_STALE","The selected voucher is no longer available. Review a fresh quote before checkout.");
        }
    }

    private Evaluation calculate(List<Line> lines,long shippingFee,Instant at,UUID customer,boolean delivery,List<Candidate> candidates,Selected selected,boolean lock) {
        Map<UUID,Long> gross=new LinkedHashMap<>(); long merchandise=0;
        for(Line line:lines){long value=Math.multiplyExact(line.unitPrice(),line.quantity());gross.put(line.variantId(),value);merchandise=Math.addExact(merchandise,value);}
        List<Adjustment> item=new ArrayList<>(); Map<UUID,Long> itemDiscount=new HashMap<>();
        for(Line line:lines) for(Candidate c:candidates) if("ITEM".equals(c.layer())&&c.products().contains(line.productId())&&eligible(c,lines,customer,lock)) {
            long base=gross.get(line.variantId()), amount=amount(c,base,line.unitPrice(),line.quantity());
            if(amount>0){item.add(c.adjustment(line.variantId(),base,amount,qualifyingQuantity(c,lines),at));itemDiscount.put(line.variantId(),amount);} break;
        }
        long itemTotal=item.stream().mapToLong(Adjustment::amount).sum(); long afterItem=merchandise-itemTotal;
        Adjustment order=null;
        for(Candidate c:candidates) if("ORDER_AUTOMATIC".equals(c.layer())&&minimum(c,afterItem)&&available(c,customer,lock)) {long amount=amount(c,afterItem,afterItem,1);if(amount>0)order=c.adjustment(null,afterItem,amount,null,at);break;}
        long orderDiscount=order==null?0:order.amount(), automaticDiscount=Math.addExact(itemTotal,orderDiscount), voucherBase=merchandise-automaticDiscount;
        Adjustment voucher=null;List<Allocation> voucherAllocations=List.of();
        if(selected!=null){Candidate c=selected.candidate();if(!minimum(c,voucherBase))throw voucher("VOUCHER_MINIMUM_SPEND_NOT_MET");if("CLAIMABLE".equals(selected.mode())&&familyUsed(c.familyId(),customer,lock))throw voucher("VOUCHER_ALREADY_USED");if(!available(c,customer,lock))throw voucher("VOUCHER_USAGE_LIMIT_REACHED");if("FREE_SHIPPING".equals(c.type())){if(!delivery||shippingFee<=0)throw voucher("VOUCHER_NOT_FOUND_OR_UNAVAILABLE");}else{long value=amount(c,voucherBase,voucherBase,1);if(value<=0)throw voucher("VOUCHER_NOT_FOUND_OR_UNAVAILABLE");voucher=c.adjustment(null,voucherBase,value,null,at,selected);Map<UUID,Long> allocatedDiscount=new HashMap<>(itemDiscount);if(order!=null)for(Allocation a:allocations(lines,gross,itemDiscount,order))allocatedDiscount.merge(a.variantId(),a.amount(),Long::sum);voucherAllocations=allocations(lines,gross,allocatedDiscount,voucher);}}
        long voucherDiscount=voucher==null?0:voucher.amount(),merchandiseDiscount=Math.addExact(automaticDiscount,voucherDiscount), net=merchandise-merchandiseDiscount;
        Adjustment shipping=null;
        if(selected!=null&&"FREE_SHIPPING".equals(selected.candidate().type()))shipping=selected.candidate().adjustment(null,shippingFee,shippingFee,null,at,selected);
        else if(shippingFee>0) for(Candidate c:candidates) if("SHIPPING".equals(c.layer())&&minimum(c,net)&&available(c,customer,lock)){shipping=c.adjustment(null,shippingFee,shippingFee,null,at);break;}
        List<Adjustment> all=new ArrayList<>(item); if(order!=null)all.add(order);if(voucher!=null)all.add(voucher); if(shipping!=null)all.add(shipping);
        long shippingDiscount=shipping==null?0:shipping.amount(),total=Math.addExact(net,shippingFee-shippingDiscount);
        if(total==0) throw new InvalidRequestException("ZERO_PAYABLE_NOT_SUPPORTED","Checkout with a zero total is not supported yet.");
        if(total<0||total>MAX_AMOUNT) throw new InvalidRequestException("CART_AMOUNT_LIMIT","Cart total exceeds the supported payment limit.");
        Set<UUID> limited=all.stream().map(Adjustment::revisionId).filter(id->candidates.stream().anyMatch(c->c.id().equals(id)&&(c.globalLimit()!=null||c.customerLimit()!=null))).collect(Collectors.toSet());
        if(selected!=null&&("CLAIMABLE".equals(selected.mode())||selected.candidate().globalLimit()!=null||selected.candidate().customerLimit()!=null))limited.add(selected.candidate().id());
        List<Allocation> allAllocations=new ArrayList<>(allocations(lines,gross,itemDiscount,order));allAllocations.addAll(voucherAllocations);
        return new Evaluation(merchandise,merchandiseDiscount,shippingFee,shippingDiscount,total,List.copyOf(all),List.copyOf(allAllocations),limited);
    }

    private Selected resolveAccepted(Adjustment evidence,UUID customer,Instant at) {
        if(!Set.of("CODE","CLAIMABLE").contains(evidence.acquisitionMode()))throw voucher("VOUCHER_NOT_FOUND_OR_UNAVAILABLE");
        String claimJoin="CLAIMABLE".equals(evidence.acquisitionMode())?" JOIN voucher_claim c WITH (UPDLOCK,HOLDLOCK) ON c.promotion_family_public_id=f.public_id AND c.public_id=? AND c.customer_account_public_id=? AND c.status='CLAIMED'":"";
        List<Object> args=new ArrayList<>();if("CLAIMABLE".equals(evidence.acquisitionMode())){args.add(evidence.voucherClaimId());args.add(customer);}args.add(evidence.revisionId());args.add(evidence.familyId());args.add(Timestamp.from(at));args.add(Timestamp.from(at));
        Selected selected=jdbc.query("SELECT p.id,p.public_id,p.family_public_id,p.revision_number,p.name,p.status,p.layer,p.effect_type,p.percentage_value,p.fixed_amount,p.minimum_spend_amount,p.buy_quantity,p.global_usage_limit,p.per_customer_usage_limit,p.priority FROM promotion_family f JOIN promotion p WITH (UPDLOCK,HOLDLOCK) ON p.family_public_id=f.public_id"+claimJoin+" WHERE f.acquisition_mode=? AND p.public_id=? AND p.family_public_id=? AND p.status IN ('PUBLISHED','RETIRED') AND p.valid_from<=? AND (p.valid_to IS NULL OR p.valid_to>?)",
                rs->rs.next()?new Selected(candidate(rs),evidence.acquisitionMode(),evidence.maskedCode(),evidence.voucherClaimId()):null,
                withMode(args,evidence.acquisitionMode()).toArray());
        if(selected==null)throw voucher("VOUCHER_NOT_FOUND_OR_UNAVAILABLE");return selected;
    }

    private static List<Object> withMode(List<Object> args,String mode){args.add("CLAIMABLE".equals(mode)?2:0,mode);return args;}

    private void lockFamilies(Instant at,Set<UUID> automatic,UUID selectedFamily){
        Set<UUID> families=new java.util.TreeSet<>();
        families.addAll(jdbc.queryForList("SELECT DISTINCT family_public_id FROM promotion p JOIN promotion_family f ON f.public_id=p.family_public_id WHERE f.acquisition_mode='AUTOMATIC' AND p.status='PUBLISHED' AND p.valid_from<=? AND (p.valid_to IS NULL OR p.valid_to>?)",UUID.class,Timestamp.from(at),Timestamp.from(at)));
        if(!automatic.isEmpty())families.addAll(jdbc.queryForList("SELECT family_public_id FROM promotion WHERE public_id IN ("+String.join(",",java.util.Collections.nCopies(automatic.size(),"?"))+")",UUID.class,automatic.toArray()));
        if(selectedFamily!=null)families.add(selectedFamily);
        for(UUID family:families){lockFamily(family);jdbc.queryForList("SELECT public_id FROM promotion WITH (UPDLOCK,HOLDLOCK) WHERE family_public_id=? ORDER BY public_id",UUID.class,family);}
    }

    private Selected resolve(VoucherSelection selection,UUID customer,Instant at,boolean lock){if(selection==null||selection.type()==VoucherSelection.Type.NONE)return null;String hint=lock?" WITH (UPDLOCK,HOLDLOCK)":"";if(selection.type()==VoucherSelection.Type.CODE){String code;try{code=normalizeCode(selection.code());}catch(InvalidRequestException ignored){throw voucher("VOUCHER_NOT_FOUND_OR_UNAVAILABLE");}Selected found=jdbc.query(("SELECT p.id,p.public_id,p.family_public_id,p.revision_number,p.name,p.status,p.layer,p.effect_type,p.percentage_value,p.fixed_amount,p.minimum_spend_amount,p.buy_quantity,p.global_usage_limit,p.per_customer_usage_limit,p.priority,f.normalized_code FROM promotion_family f JOIN promotion p"+hint+" ON p.family_public_id=f.public_id WHERE f.acquisition_mode='CODE' AND f.normalized_code=? AND p.status='PUBLISHED' AND p.valid_from<=? AND (p.valid_to IS NULL OR p.valid_to>?)"),rs->rs.next()?new Selected(candidate(rs),"CODE",mask(rs.getString(16)),null):null,code,Timestamp.from(at),Timestamp.from(at));if(found==null){if(expiredCode(code,at))throw voucher("VOUCHER_EXPIRED");throw voucher("VOUCHER_NOT_FOUND_OR_UNAVAILABLE");}return found;}UUID claimId=selection.claimId();if(claimId==null)throw voucher("VOUCHER_NOT_FOUND_OR_UNAVAILABLE");Selected found=jdbc.query(("SELECT p.id,p.public_id,p.family_public_id,p.revision_number,p.name,p.status,p.layer,p.effect_type,p.percentage_value,p.fixed_amount,p.minimum_spend_amount,p.buy_quantity,p.global_usage_limit,p.per_customer_usage_limit,p.priority,c.public_id FROM voucher_claim c JOIN promotion_family f ON f.public_id=c.promotion_family_public_id JOIN promotion p"+hint+" ON p.family_public_id=f.public_id WHERE c.public_id=? AND c.customer_account_public_id=? AND c.status='CLAIMED' AND f.acquisition_mode='CLAIMABLE' AND f.is_publicly_discoverable=1 AND p.status='PUBLISHED' AND p.valid_from<=? AND (p.valid_to IS NULL OR p.valid_to>?)"),rs->rs.next()?new Selected(candidate(rs),"CLAIMABLE",null,rs.getObject(16,UUID.class)):null,claimId,customer,Timestamp.from(at),Timestamp.from(at));if(found==null){if(expiredClaim(claimId,customer,at))throw voucher("VOUCHER_EXPIRED");throw voucher("VOUCHER_NOT_FOUND_OR_UNAVAILABLE");}return found;}
    private Candidate candidate(java.sql.ResultSet rs)throws java.sql.SQLException{UUID id=rs.getObject(2,UUID.class);return new Candidate(rs.getLong(1),id,rs.getObject(3,UUID.class),rs.getInt(4),rs.getString(5),rs.getString(6),rs.getString(7),rs.getString(8),decimal(rs,9),number(rs,10),number(rs,11),number(rs,12),number(rs,13),number(rs,14),rs.getInt(15),scopes(id));}
    private boolean familyUsed(UUID family,UUID customer){return familyUsed(family,customer,false);}
    private boolean familyUsed(UUID family,UUID customer,boolean lock){String hint=lock?" WITH (UPDLOCK,HOLDLOCK)":"";return Boolean.TRUE.equals(jdbc.queryForObject("SELECT CASE WHEN EXISTS(SELECT 1 FROM promotion_redemption r"+hint+" JOIN promotion p ON p.public_id=r.promotion_revision_public_id WHERE p.family_public_id=? AND r.customer_account_public_id=? AND r.status IN ('RESERVED','REDEEMED')) THEN 1 ELSE 0 END",Boolean.class,family,customer));}
    private boolean expiredCode(String code,Instant at){return jdbc.queryForObject("SELECT COUNT(*) FROM promotion_family f JOIN promotion p ON p.family_public_id=f.public_id WHERE f.acquisition_mode='CODE' AND f.is_publicly_discoverable=1 AND f.normalized_code=? AND p.status='PUBLISHED' AND p.valid_to<=?",Integer.class,code,Timestamp.from(at))>0;}
    private boolean expiredClaim(UUID claim,UUID customer,Instant at){return jdbc.queryForObject("SELECT COUNT(*) FROM voucher_claim c JOIN promotion_family f ON f.public_id=c.promotion_family_public_id JOIN promotion p ON p.family_public_id=f.public_id WHERE c.public_id=? AND c.customer_account_public_id=? AND c.status='CLAIMED' AND f.acquisition_mode='CLAIMABLE' AND p.status='PUBLISHED' AND p.valid_to<=?",Integer.class,claim,customer,Timestamp.from(at))>0;}
    private static InvalidRequestException voucher(String code){return new InvalidRequestException(code,"The selected voucher is not available for this order.");}
    static String mask(String code){if(code.length()<=4)return "•".repeat(code.length()-1)+code.substring(code.length()-1);return code.substring(0,2)+"•".repeat(code.length()-4)+code.substring(code.length()-2);}

    private List<Candidate> candidates(Instant at,Set<UUID> accepted,boolean lock) {
        String hint=lock?" WITH (UPDLOCK,HOLDLOCK)":"";
        return jdbc.query("""
          SELECT p.id,p.public_id,p.family_public_id,p.revision_number,p.name,p.status,p.layer,p.effect_type,p.percentage_value,p.fixed_amount,
            p.minimum_spend_amount,p.buy_quantity,p.global_usage_limit,p.per_customer_usage_limit,p.priority
          FROM promotion p%s JOIN promotion_family f ON f.public_id=p.family_public_id
          WHERE f.acquisition_mode='AUTOMATIC' AND p.status IN ('PUBLISHED','RETIRED') AND p.valid_from<=? AND (p.valid_to IS NULL OR p.valid_to>?)
          ORDER BY p.priority DESC,p.public_id ASC
          """.formatted(hint),(rs,row)->{UUID id=rs.getObject(2,UUID.class);return new Candidate(rs.getLong(1),id,rs.getObject(3,UUID.class),rs.getInt(4),rs.getString(5),rs.getString(6),rs.getString(7),rs.getString(8),decimal(rs,9),number(rs,10),number(rs,11),number(rs,12),number(rs,13),number(rs,14),rs.getInt(15),scopes(id));},Timestamp.from(at),Timestamp.from(at)).stream()
          .filter(c->"PUBLISHED".equals(c.status())||accepted.contains(c.id())).toList();
    }

    private boolean eligible(Candidate c,List<Line> lines,UUID customer){return eligible(c,lines,customer,false);}
    private boolean eligible(Candidate c,List<Line> lines,UUID customer,boolean lock){return minimum(c,lines.stream().filter(l->c.products().contains(l.productId())).mapToLong(l->Math.multiplyExact(l.unitPrice(),l.quantity())).sum())&&
      (c.buyQuantity()==null||qualifyingQuantity(c,lines)>=c.buyQuantity())&&available(c,customer,lock);}
    private boolean minimum(Candidate c,long base){return c.minimumSpend()==null||base>=c.minimumSpend();}
    private Long qualifyingQuantity(Candidate c,List<Line> lines){if(c.buyQuantity()==null)return null;return lines.stream().filter(l->c.products().contains(l.productId())).mapToLong(Line::quantity).sum();}
    private boolean available(Candidate c,UUID customer){return available(c,customer,false);}
    private boolean available(Candidate c,UUID customer,boolean lock){
        if(c.globalLimit()==null&&c.customerLimit()==null)return true;
        String hint=lock?" WITH (UPDLOCK,HOLDLOCK)":"";
        Long global=jdbc.queryForObject("SELECT COUNT_BIG(*) FROM promotion_redemption"+hint+" WHERE promotion_revision_public_id=? AND status IN ('RESERVED','REDEEMED')",Long.class,c.id());
        if(c.globalLimit()!=null&&global>=c.globalLimit())return false;
        Long own=jdbc.queryForObject("SELECT COUNT_BIG(*) FROM promotion_redemption"+hint+" WHERE promotion_revision_public_id=? AND customer_account_public_id=? AND status IN ('RESERVED','REDEEMED')",Long.class,c.id(),customer);
        return c.customerLimit()==null||own<c.customerLimit();
    }
    private static long amount(Candidate c,long base,long unit,long quantity){
        if(c.type().endsWith("PERCENT"))return BigDecimal.valueOf(base).multiply(c.percentage()).divide(BigDecimal.valueOf(100),0,RoundingMode.HALF_UP).min(BigDecimal.valueOf(base)).longValueExact();
        if("ITEM_FIXED".equals(c.type()))return Math.multiplyExact(Math.min(c.fixed(),unit),quantity);
        if("ORDER_FIXED".equals(c.type()))return Math.min(c.fixed(),base);
        return base;
    }

    private static List<Allocation> allocations(List<Line> lines,Map<UUID,Long> gross,Map<UUID,Long> itemDiscount,Adjustment order){
        if(order==null)return List.of(); long totalBase=lines.stream().mapToLong(l->gross.get(l.variantId())-itemDiscount.getOrDefault(l.variantId(),0L)).sum();
        record Part(Line line,long base,long floor,BigDecimal remainder){}
        List<Part> parts=new ArrayList<>();long used=0;
        for(Line l:lines){long base=gross.get(l.variantId())-itemDiscount.getOrDefault(l.variantId(),0L);BigDecimal exact=BigDecimal.valueOf(order.amount()).multiply(BigDecimal.valueOf(base)).divide(BigDecimal.valueOf(totalBase),12,RoundingMode.DOWN);long floor=exact.setScale(0,RoundingMode.DOWN).longValueExact();used+=floor;parts.add(new Part(l,base,floor,exact.subtract(BigDecimal.valueOf(floor))));}
        parts.sort(Comparator.comparing(Part::remainder).reversed().thenComparing(p->p.line().variantId()));long remaining=order.amount()-used;Map<UUID,Long> extras=new HashMap<>();for(int i=0;i<remaining;i++)extras.merge(parts.get(i).line().variantId(),1L,Long::sum);
        return lines.stream().map(l->new Allocation(l.variantId(),order,parts.stream().filter(p->p.line().variantId().equals(l.variantId())).findFirst().orElseThrow().floor()+extras.getOrDefault(l.variantId(),0L))).toList();
    }

    @Transactional
    public void storeQuote(UUID quoteId,Evaluation e){
        for(Adjustment a:e.adjustments())insertAdjustment("pricing_cart_quote_adjustment","quote_public_id","variant_public_id",quoteId,a.variantId(),a);
        for(Allocation a:e.allocations())jdbc.update("INSERT INTO pricing_cart_quote_item_adjustment(public_id,quote_public_id,variant_public_id,parent_quote_adjustment_public_id,promotion_family_public_id,promotion_revision_public_id,layer,applied_amount) VALUES(?,?,?,?,?,?,?,?)",UUID.randomUUID(),quoteId,a.variantId(),a.parent().adjustmentId(),a.parent().familyId(),a.parent().revisionId(),allocationLayer(a.parent()),a.amount());
    }
    @Transactional(readOnly=true)
    public List<Adjustment> quoteAdjustments(UUID quoteId){return jdbc.query("""
      SELECT public_id,promotion_family_public_id,promotion_revision_public_id,revision_number,name_snapshot,effect_type,layer,
        qualifying_base_amount,percentage_value,fixed_amount,applied_amount,priority,qualifying_quantity,buy_quantity,applied_at,variant_public_id,
        acquisition_mode,masked_code_snapshot,voucher_claim_public_id
      FROM pricing_cart_quote_adjustment WHERE quote_public_id=? ORDER BY layer,variant_public_id
      """,(rs,row)->adjustment(rs),quoteId);}
    @Transactional(readOnly=true)
    public List<OrderAdjustment> orderAdjustments(UUID orderId){return jdbc.query("""
      SELECT a.promotion_family_public_id,a.promotion_revision_public_id,a.revision_number,a.name_snapshot,a.effect_type,a.layer,
        a.qualifying_base_amount,a.percentage_value,a.fixed_amount,a.applied_amount,a.applied_at,a.acquisition_mode,
        a.masked_code_snapshot,a.voucher_claim_public_id
      FROM commerce_order_adjustment a WHERE a.order_public_id=? ORDER BY a.layer
      """,(rs,row)->new OrderAdjustment(rs.getObject(1,UUID.class),rs.getObject(2,UUID.class),rs.getInt(3),rs.getString(4),
        rs.getString(5),rs.getString(6),rs.getLong(7),decimal(rs,8),number(rs,9),rs.getLong(10),rs.getTimestamp(11).toInstant(),
        rs.getString(12),rs.getString(13),rs.getObject(14,UUID.class),jdbc.query("""
          SELECT i.variant_public_id,a.layer,a.applied_amount FROM commerce_order_item_adjustment a
          JOIN commerce_order_item i ON i.public_id=a.order_item_public_id
          WHERE a.parent_order_adjustment_public_id=(SELECT public_id FROM commerce_order_adjustment WHERE order_public_id=? AND promotion_revision_public_id=?)
          ORDER BY i.variant_public_id
          """,(ar,n)->new OrderAllocation(ar.getObject(1,UUID.class),ar.getString(2),ar.getLong(3)),orderId,rs.getObject(2,UUID.class))),orderId);}
    @Transactional(readOnly=true)
    public String quoteVoucherFingerprint(UUID customer,UUID quoteId){
        Integer owned=jdbc.queryForObject("SELECT COUNT(*) FROM pricing_cart_quote q JOIN iam_user_account a ON a.id=q.owner_account_id WHERE q.public_id=? AND a.public_id=?",Integer.class,quoteId,customer);
        if(owned==null||owned==0)throw new com.shoecommerce.platform.api.ResourceNotFoundException("PRICE_QUOTE_NOT_FOUND","Price quote not found.");
        return quoteAdjustments(quoteId).stream().filter(a->!"AUTOMATIC".equals(a.acquisitionMode())).map(Adjustment::voucherFingerprint).findFirst().orElse("NONE");
    }
    @Transactional(readOnly=true)
    public List<String> quoteSignature(UUID quoteId){
        List<String> signature=new ArrayList<>(quoteAdjustments(quoteId).stream().map(Adjustment::signature).toList());
        signature.addAll(jdbc.query("SELECT promotion_revision_public_id,layer,variant_public_id,applied_amount FROM pricing_cart_quote_item_adjustment WHERE quote_public_id=?",(rs,row)->rs.getObject(1,UUID.class)+"|"+rs.getString(2)+"|"+rs.getObject(3,UUID.class)+"|"+rs.getLong(4),quoteId));
        return signature.stream().sorted().toList();
    }
    public void storeOrder(UUID orderId,UUID customer,Instant holdUntil,Evaluation e,Map<UUID,UUID> itemIds){
        Map<UUID,UUID> parentIds=new HashMap<>();
        for(Adjustment a:e.adjustments()) if(!"ITEM".equals(a.layer())){insertAdjustment("commerce_order_adjustment","order_public_id",null,orderId,null,a);parentIds.put(a.revisionId(),a.adjustmentId());}
        for(Adjustment a:e.adjustments())if("ITEM".equals(a.layer()))insertItem(itemIds.get(a.variantId()),null,a,"ITEM",a.amount());
        for(Allocation a:e.allocations())insertItem(itemIds.get(a.variantId()),parentIds.get(a.parent().revisionId()),a.parent(),allocationLayer(a.parent()),a.amount());
        for(UUID revision:e.limitedRevisions()){
            jdbc.update("""
              INSERT INTO promotion_redemption(public_id,promotion_revision_public_id,order_public_id,customer_account_public_id,status,hold_expires_at,reserved_at)
              VALUES(?,?,?,?, 'RESERVED',?,?)
              """,UUID.randomUUID(),revision,orderId,customer,Timestamp.from(holdUntil),Timestamp.from(clock.instant()));
        }
    }
    private static String allocationLayer(Adjustment parent){return "VOUCHER_SELECTED".equals(parent.layer())?"VOUCHER_ALLOCATION":"ORDER_ALLOCATION";}
    public void lockUsage(UUID orderId){jdbc.queryForList("SELECT id FROM promotion_redemption WITH (UPDLOCK,HOLDLOCK) WHERE order_public_id=?",Long.class,orderId);}
    public void redeem(UUID orderId,Instant at){jdbc.update("UPDATE promotion_redemption SET status='REDEEMED',redeemed_at=? WHERE order_public_id=? AND status='RESERVED'",Timestamp.from(at),orderId);}
    public void release(UUID orderId,Instant at){jdbc.update("UPDATE promotion_redemption SET status='RELEASED',released_at=? WHERE order_public_id=? AND status='RESERVED'",Timestamp.from(at),orderId);}

    private void insertAdjustment(String table,String ownerColumn,String variantColumn,UUID owner,UUID variant,Adjustment a){
        String columns=variantColumn==null?"":" ,"+variantColumn; String marker=variantColumn==null?"":" ,?";
        List<Object> args=new ArrayList<>(List.of(a.adjustmentId(),owner));if(variantColumn!=null)args.add(variant);args.addAll(Arrays.asList(a.familyId(),a.revisionId(),a.revision(),a.name(),a.type(),a.layer(),a.base(),a.percentage(),a.fixed(),a.amount(),a.priority(),a.qualifyingQuantity(),a.buyQuantity(),Timestamp.from(a.appliedAt()),a.acquisitionMode(),a.maskedCode(),a.voucherClaimId()));
        jdbc.update("INSERT INTO "+table+"(public_id,"+ownerColumn+columns+",promotion_family_public_id,promotion_revision_public_id,revision_number,name_snapshot,effect_type,layer,qualifying_base_amount,percentage_value,fixed_amount,applied_amount,priority,qualifying_quantity,buy_quantity,applied_at,acquisition_mode,masked_code_snapshot,voucher_claim_public_id) VALUES(?,?"+marker+",?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",args.toArray());
    }
    private void insertItem(UUID item,UUID parent,Adjustment a,String layer,long amount){jdbc.update("""
      INSERT INTO commerce_order_item_adjustment(public_id,order_item_public_id,parent_order_adjustment_public_id,promotion_family_public_id,
        promotion_revision_public_id,revision_number,name_snapshot,effect_type,layer,qualifying_base_amount,percentage_value,fixed_amount,
        applied_amount,priority,qualifying_quantity,buy_quantity,applied_at) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
      """,UUID.randomUUID(),item,parent,a.familyId(),a.revisionId(),a.revision(),a.name(),a.type(),layer,a.base(),a.percentage(),a.fixed(),amount,a.priority(),a.qualifyingQuantity(),a.buyQuantity(),Timestamp.from(a.appliedAt()));}
    private Adjustment adjustment(java.sql.ResultSet rs)throws java.sql.SQLException{return new Adjustment(rs.getObject(1,UUID.class),rs.getObject(2,UUID.class),rs.getObject(3,UUID.class),rs.getInt(4),rs.getString(5),rs.getString(6),rs.getString(7),rs.getLong(8),decimal(rs,9),number(rs,10),rs.getLong(11),rs.getInt(12),number(rs,13),number(rs,14),rs.getTimestamp(15).toInstant(),rs.getObject(16,UUID.class),rs.getString(17),rs.getString(18),rs.getObject(19,UUID.class));}
    private RuleView rule(UUID id){return rulesRaw(id).stream().findFirst().orElseThrow(()->new IllegalArgumentException("Promotion not found"));}
    private List<RuleView> rulesRaw(UUID id){return jdbc.query("SELECT p.public_id,p.family_public_id,revision_number,name,status,layer,effect_type,percentage_value,fixed_amount,minimum_spend_amount,buy_quantity,global_usage_limit,per_customer_usage_limit,priority,valid_from,valid_to,customer_summary,customer_terms,f.acquisition_mode,f.normalized_code,f.is_publicly_discoverable FROM promotion p JOIN promotion_family f ON f.public_id=p.family_public_id WHERE p.public_id=?",(rs,row)->new RuleView(rs.getObject(1,UUID.class),rs.getObject(2,UUID.class),rs.getInt(3),rs.getString(4),rs.getString(5),rs.getString(6),rs.getString(7),decimal(rs,8),number(rs,9),number(rs,10),number(rs,11),number(rs,12),number(rs,13),rs.getInt(14),rs.getTimestamp(15).toInstant(),instant(rs,16),rs.getString(17),rs.getString(18),scopes(id),rs.getString(19),rs.getString(20),rs.getBoolean(21)),id);}
    private Set<UUID> scopes(UUID id){return new LinkedHashSet<>(jdbc.queryForList("SELECT product_public_id FROM promotion_product_scope WHERE promotion_id=(SELECT id FROM promotion WHERE public_id=?) ORDER BY product_public_id",UUID.class,id));}
    private void replaceScopes(UUID id,Set<UUID> products){jdbc.update("DELETE FROM promotion_product_scope WHERE promotion_id=(SELECT id FROM promotion WHERE public_id=?)",id);for(UUID product:products)jdbc.update("INSERT INTO promotion_product_scope(promotion_id,product_public_id) SELECT id,? FROM promotion WHERE public_id=?",product,id);}
    private void validateRule(Draft d){
        if(d==null||d.name()==null||d.name().isBlank()||d.name().length()>120||d.priority()==null||d.validFrom()==null||d.productIds()==null)invalid();
        Set<String> types=Set.of("ITEM_PERCENT","ITEM_FIXED","ORDER_PERCENT","ORDER_FIXED","FREE_SHIPPING");if(!types.contains(d.effectType()))invalid();
        String expected=d.effectType().startsWith("ITEM_")?"ITEM":d.effectType().startsWith("ORDER_")?"ORDER_AUTOMATIC":"SHIPPING";if(!expected.equals(d.layer()))invalid();
        boolean percent=d.effectType().endsWith("PERCENT");boolean fixed=d.effectType().endsWith("FIXED");
        if(percent&&(d.percentageValue()==null||d.percentageValue().signum()<=0||d.percentageValue().compareTo(BigDecimal.valueOf(100))>0)||!percent&&d.percentageValue()!=null)invalid();
        if(fixed&&(d.fixedAmount()==null||d.fixedAmount()<=0)||!fixed&&d.fixedAmount()!=null)invalid();
        if("ITEM".equals(d.layer())&&d.productIds().isEmpty()||!"ITEM".equals(d.layer())&&!d.productIds().isEmpty()||d.buyQuantity()!=null&&(!"ITEM".equals(d.layer())||d.buyQuantity()<=0))invalid();
        if(d.minimumSpendAmount()!=null&&d.minimumSpendAmount()<0||d.globalUsageLimit()!=null&&d.globalUsageLimit()<=0||d.perCustomerUsageLimit()!=null&&d.perCustomerUsageLimit()<=0||d.validTo()!=null&&!d.validTo().isAfter(d.validFrom()))invalid();
        for(UUID product:d.productIds())if(product==null||Boolean.FALSE.equals(jdbc.queryForObject("SELECT CASE WHEN EXISTS(SELECT 1 FROM catalog_product WHERE public_id=?) THEN 1 ELSE 0 END",Boolean.class,product)))invalid();
    }
    private Family newFamily(Draft d){
        String mode=d.acquisitionMode()==null?"AUTOMATIC":d.acquisitionMode();if(!Set.of("AUTOMATIC","CODE","CLAIMABLE").contains(mode))throw new InvalidRequestException("PROMOTION_ACQUISITION_MODE_INVALID","Promotion acquisition mode is invalid.");
        String code="CODE".equals(mode)?normalizeCode(d.normalizedCode()):null;if(!"CODE".equals(mode)&&d.normalizedCode()!=null&&!d.normalizedCode().isBlank())throw new InvalidRequestException("PROMOTION_CODE_INVALID","Only CODE promotion families may have a code.");
        boolean discoverable=Boolean.TRUE.equals(d.publiclyDiscoverable());if("AUTOMATIC".equals(mode)&&discoverable)throw new InvalidRequestException("PROMOTION_ACQUISITION_MODE_INVALID","Automatic promotions cannot be publicly discoverable.");return new Family(mode,code,discoverable);
    }
    private void assertFamilyInput(Draft d,Family family){
        if(d.acquisitionMode()!=null&&!d.acquisitionMode().equals(family.mode()))throw new BusinessConflictException("PROMOTION_ACQUISITION_MODE_IMMUTABLE","Promotion acquisition mode cannot be changed.");
        if(d.normalizedCode()!=null&&!normalizeCode(d.normalizedCode()).equals(family.code()))throw new BusinessConflictException("PROMOTION_ACQUISITION_MODE_IMMUTABLE","Promotion code cannot be changed.");
        if("AUTOMATIC".equals(family.mode())&&Boolean.TRUE.equals(d.publiclyDiscoverable()))throw new InvalidRequestException("PROMOTION_ACQUISITION_MODE_INVALID","Automatic promotions cannot be publicly discoverable.");
    }
    private void validateVoucher(Draft d,Family family){
        validateAcquisitionPolicy(family.mode(),family.discoverable(),d.perCustomerUsageLimit());
        if("AUTOMATIC".equals(family.mode()))return;
        if(!Set.of("ORDER_PERCENT","ORDER_FIXED","FREE_SHIPPING").contains(d.effectType()))throw new InvalidRequestException("PROMOTION_VOUCHER_EFFECT_UNSUPPORTED","This effect is not supported for CODE or CLAIMABLE promotions.");
        String summary=trim(d.customerSummary()),terms=trim(d.customerTerms());
        if(summary==null||summary.isBlank())throw new InvalidRequestException("PROMOTION_CUSTOMER_SUMMARY_REQUIRED","Customer summary is required.");
        if(summary.length()>240)throw new InvalidRequestException("PROMOTION_CUSTOMER_SUMMARY_INVALID","Customer summary must be 240 characters or fewer.");
        if(terms==null||terms.isBlank())throw new InvalidRequestException("PROMOTION_CUSTOMER_TERMS_REQUIRED","Customer terms are required.");
        if(terms.length()>1000)throw new InvalidRequestException("PROMOTION_CUSTOMER_TERMS_INVALID","Customer terms must be 1000 characters or fewer.");
    }
    private Family lockFamily(UUID id){jdbc.queryForObject("SELECT public_id FROM promotion_family WITH (UPDLOCK,HOLDLOCK) WHERE public_id=?",UUID.class,id);return family(id);}
    private Family family(UUID id){return jdbc.queryForObject("SELECT acquisition_mode,normalized_code,is_publicly_discoverable FROM promotion_family WHERE public_id=?",(rs,row)->new Family(rs.getString(1),rs.getString(2),rs.getBoolean(3)),id);}
    static String normalizeCode(String value){if(value==null)throw new InvalidRequestException("PROMOTION_CODE_INVALID","Promotion code is required.");String normalized=Normalizer.normalize(value,Normalizer.Form.NFKC).trim().toUpperCase(Locale.ROOT);if(!normalized.matches("[A-Z0-9][A-Z0-9_-]{2,31}"))throw new InvalidRequestException("PROMOTION_CODE_INVALID","Promotion code must contain 3 to 32 letters, numbers, underscores, or hyphens.");return normalized;}
    static void validateAcquisitionPolicy(String mode,boolean discoverable,Long perCustomerLimit){
        if("CLAIMABLE".equals(mode)&&!discoverable)throw new InvalidRequestException("PROMOTION_CLAIMABLE_MUST_BE_PUBLIC","Claimable promotions must be publicly discoverable.");
        if("CLAIMABLE".equals(mode)&&perCustomerLimit!=null&&perCustomerLimit>1)throw new InvalidRequestException("PROMOTION_CLAIMABLE_SINGLE_USE","Claimable promotions are limited to one use per customer family.");
    }
    static String displayState(String status,Instant validFrom,Instant validTo,Instant asOf,boolean publishedEver){
        if("PUBLISHED".equals(status)){if(validFrom.isAfter(asOf))return "SCHEDULED";if(validTo!=null&&!validTo.isAfter(asOf))return "ENDED";return "ACTIVE";}
        if("RETIRED".equals(status)||"INVALIDATED".equals(status))return status;
        return publishedEver?"RETIRED":"DRAFT";
    }
    private static String trim(String value){return value==null?null:value.trim();}
    private static void invalid(){throw new InvalidRequestException("INVALID_PROMOTION","Promotion configuration is invalid.");}
    private static Timestamp timestamp(Instant value){return value==null?null:Timestamp.from(value);} private static Instant instant(java.sql.ResultSet rs,int i)throws java.sql.SQLException{return rs.getTimestamp(i)==null?null:rs.getTimestamp(i).toInstant();}
    private static Long number(java.sql.ResultSet rs,int i)throws java.sql.SQLException{long value=rs.getLong(i);return rs.wasNull()?null:value;}private static BigDecimal decimal(java.sql.ResultSet rs,int i)throws java.sql.SQLException{return rs.getBigDecimal(i);}

    public record Line(UUID variantId,UUID productId,long quantity,long unitPrice){}
    public record Draft(UUID familyId,Integer revisionNumber,String name,String layer,String effectType,BigDecimal percentageValue,Long fixedAmount,Long minimumSpendAmount,Long buyQuantity,Long globalUsageLimit,Long perCustomerUsageLimit,Integer priority,Instant validFrom,Instant validTo,Set<UUID> productIds,String customerSummary,String customerTerms,String acquisitionMode,String normalizedCode,Boolean publiclyDiscoverable){}
    public record RuleView(UUID id,UUID familyId,int revisionNumber,String name,String status,String layer,String effectType,BigDecimal percentageValue,Long fixedAmount,Long minimumSpendAmount,Long buyQuantity,Long globalUsageLimit,Long perCustomerUsageLimit,int priority,Instant validFrom,Instant validTo,String customerSummary,String customerTerms,Set<UUID> productIds,String acquisitionMode,String normalizedCode,boolean publiclyDiscoverable){Draft draft(){return new Draft(familyId,revisionNumber,name,layer,effectType,percentageValue,fixedAmount,minimumSpendAmount,buyQuantity,globalUsageLimit,perCustomerUsageLimit,priority,validFrom,validTo,productIds,customerSummary,customerTerms,acquisitionMode,normalizedCode,publiclyDiscoverable);}}
    public record PublishCommand(UUID expectedPublishedRevisionId){}
    public record VisibilityCommand(boolean publiclyDiscoverable){}
    public record CampaignList(Instant asOf,List<CampaignView> items){}
    public record CampaignView(UUID familyId,String state,Instant asOf,String acquisitionMode,String normalizedCode,boolean publiclyDiscoverable,RuleView currentPublished,List<RuleView> drafts,List<RuleView> revisions,List<ProductReference> targetProducts,UsageMetrics usage){}
    public record ProductReference(UUID id,String name,String category,int publishedVariantCount,Long minimumAmount,boolean sellable){}
    public record UsageMetrics(long claimed,long revoked,long reserved,long redeemed,long released,long paidAppliedOrders,long discountGranted){static UsageMetrics empty(){return new UsageMetrics(0,0,0,0,0,0,0);}}
    public record PreviewRequest(Draft draft,UUID productId,long quantity,long shippingFeeAmount){}
    public record PreviewProduct(UUID productId,String productName,UUID variantId,String sku,long unitPriceAmount){}
    public record PreviewAdjustment(String name,String effectType,String layer,long baseAmount,long discountAmount){}
    public record PreviewResult(Instant asOf,PreviewProduct product,long quantity,long merchandiseAmount,long merchandiseDiscountAmount,long shippingFeeAmount,long shippingDiscountAmount,long totalAmount,List<PreviewAdjustment> adjustments){}
    private record Family(String mode,String code,boolean discoverable){}
    private record Candidate(long databaseId,UUID id,UUID familyId,int revision,String name,String status,String layer,String type,BigDecimal percentage,Long fixed,Long minimumSpend,Long buyQuantity,Long globalLimit,Long customerLimit,int priority,Set<UUID> products){Adjustment adjustment(UUID variant,long base,long amount,Long qualifying,Instant at){return new Adjustment(UUID.randomUUID(),familyId,id,revision,name,type,layer,base,percentage,fixed,amount,priority,qualifying,buyQuantity,at,variant,"AUTOMATIC",null,null);}Adjustment adjustment(UUID variant,long base,long amount,Long qualifying,Instant at,Selected selected){return new Adjustment(UUID.randomUUID(),familyId,id,revision,name,type,"FREE_SHIPPING".equals(type)?"SHIPPING":"VOUCHER_SELECTED",base,percentage,fixed,amount,priority,qualifying,buyQuantity,at,variant,selected.mode(),selected.maskedCode(),selected.claimId());}}
    private record Selected(Candidate candidate,String mode,String maskedCode,UUID claimId){}
    public record VoucherSelection(Type type,String code,UUID claimId){public enum Type{NONE,CODE,CLAIM}public VoucherSelection{if(type==null||type==Type.NONE&&(code!=null||claimId!=null)||type==Type.CODE&&(code==null||claimId!=null)||type==Type.CLAIM&&(claimId==null||code!=null))throw new IllegalArgumentException("Invalid voucher selection");}public static VoucherSelection none(){return new VoucherSelection(Type.NONE,null,null);}public static VoucherSelection code(String code){return new VoucherSelection(Type.CODE,code,null);}public static VoucherSelection claim(UUID claim){return new VoucherSelection(Type.CLAIM,null,claim);}}
    public record Adjustment(UUID adjustmentId,UUID familyId,UUID revisionId,int revision,String name,String type,String layer,long base,BigDecimal percentage,Long fixed,long amount,int priority,Long qualifyingQuantity,Long buyQuantity,Instant appliedAt,UUID variantId,String acquisitionMode,String maskedCode,UUID voucherClaimId){public String signature(){return revisionId+"|"+layer+"|"+(variantId==null?"":variantId)+"|"+amount;}public String voucherFingerprint(){return acquisitionMode+"|"+familyId+"|"+(voucherClaimId==null?"":voucherClaimId)+"|"+revisionId+"|"+layer+"|"+amount;}}
    public record OrderAdjustment(UUID familyId,UUID revisionId,int revision,String name,String effectType,String layer,long qualifyingBaseAmount,BigDecimal percentageValue,Long fixedAmount,long appliedAmount,Instant appliedAt,String acquisitionMode,String maskedCode,UUID claimId,List<OrderAllocation> allocations){}
    public record OrderAllocation(UUID variantId,String layer,long appliedAmount){}
    public record Allocation(UUID variantId,Adjustment parent,long amount){}
    public record Evaluation(long merchandise,long merchandiseDiscount,long shippingFee,long shippingDiscount,long total,List<Adjustment> adjustments,List<Allocation> allocations,Set<UUID> limitedRevisions){public Set<UUID> revisions(){return adjustments.stream().map(Adjustment::revisionId).collect(Collectors.toSet());}public List<String> signatures(){return adjustments.stream().map(Adjustment::signature).sorted().toList();}public List<String> signature(){List<String>s=new ArrayList<>(signatures());s.addAll(allocations.stream().map(a->a.parent().revisionId()+"|"+allocationLayer(a.parent())+"|"+a.variantId()+"|"+a.amount()).toList());return s.stream().sorted().toList();}}
}
