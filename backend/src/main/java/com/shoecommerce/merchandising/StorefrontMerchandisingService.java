package com.shoecommerce.merchandising;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import com.shoecommerce.audit.AuditWriter;
import com.shoecommerce.catalog.StorefrontCatalogService;
import com.shoecommerce.identity.AuthorizationPolicy;
import com.shoecommerce.identity.PermissionCode;
import com.shoecommerce.identity.SessionPrincipal;
import com.shoecommerce.platform.api.BusinessConflictException;
import com.shoecommerce.platform.api.InvalidRequestException;
import com.shoecommerce.platform.api.ResourceNotFoundException;
import com.shoecommerce.promotion.VoucherService;

@Service
public class StorefrontMerchandisingService {
    private static final String PAGE = "HOME";
    private static final int MAX_SECTIONS = 8;
    private static final int MAX_PRODUCTS = 8;

    private final JdbcTemplate jdbc;
    private final AuthorizationPolicy authorization;
    private final StorefrontCatalogService catalog;
    private final VoucherService vouchers;
    private final AuditWriter audit;
    private final Clock clock;

    public StorefrontMerchandisingService(JdbcTemplate jdbc, AuthorizationPolicy authorization,
            StorefrontCatalogService catalog, VoucherService vouchers, AuditWriter audit, Clock clock) {
        this.jdbc = jdbc;
        this.authorization = authorization;
        this.catalog = catalog;
        this.vouchers = vouchers;
        this.audit = audit;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public ManagementState managementState(SessionPrincipal actor) {
        authorization.requirePermission(actor, PermissionCode.STOREFRONT_MANAGE);
        return new ManagementState(revision("DRAFT"), revision("PUBLISHED"));
    }

    @Transactional(isolation = Isolation.SERIALIZABLE)
    public RevisionState saveDraft(SessionPrincipal actor, SaveDraft command) {
        authorization.requirePermission(actor, PermissionCode.STOREFRONT_MANAGE);
        List<SectionInput> sections = validate(command);
        lockHome();
        RevisionHeader draft = header("DRAFT");
        UUID revisionId;
        long databaseId;
        int revisionNumber;
        long nextVersion;
        Instant now = clock.instant();
        if (draft == null) {
            if (command.expectedDraftRevisionId() != null || command.expectedEntityVersion() != null) throw conflict();
            revisionId = UUID.randomUUID();
            revisionNumber = jdbc.queryForObject(
                    "SELECT COALESCE(MAX(revision_number),0)+1 FROM storefront_page_revision WITH (UPDLOCK,HOLDLOCK) WHERE page_key=?",
                    Integer.class, PAGE);
            try {
                jdbc.update("""
                        INSERT INTO storefront_page_revision(public_id,page_key,revision_number,status,entity_version,
                            created_by_account_public_id,created_at)
                        VALUES (?,? ,?,'DRAFT',0,?,?)
                        """, revisionId, PAGE, revisionNumber, actor.publicId(), Timestamp.from(now));
            } catch (DuplicateKeyException race) {
                throw conflict();
            }
            databaseId = jdbc.queryForObject(
                    "SELECT id FROM storefront_page_revision WHERE public_id=?", Long.class, revisionId);
            nextVersion = 0;
        } else {
            if (!Objects.equals(command.expectedDraftRevisionId(), draft.publicId())
                    || !Objects.equals(command.expectedEntityVersion(), draft.entityVersion())) throw conflict();
            revisionId = draft.publicId();
            databaseId = draft.id();
            revisionNumber = draft.revisionNumber();
            nextVersion = draft.entityVersion() + 1;
            jdbc.update("DELETE FROM storefront_section WHERE page_revision_id=?", databaseId);
        }
        insertSections(databaseId, sections);
        if (draft != null && jdbc.update("""
                UPDATE storefront_page_revision SET entity_version=?
                WHERE id=? AND status='DRAFT' AND entity_version=?
                """, nextVersion, databaseId, draft.entityVersion()) != 1) throw conflict();
        audit.append(actor, draft == null ? "STOREFRONT_DRAFT_CREATED" : "STOREFRONT_DRAFT_REPLACED",
                "STOREFRONT_PAGE_REVISION", revisionId, null, null,
                Map.of("page", PAGE, "revisionNumber", revisionNumber, "sectionCount", sections.size()));
        return revision("DRAFT");
    }

    @Transactional(isolation = Isolation.SERIALIZABLE)
    public RevisionState clonePublished(SessionPrincipal actor, ClonePublished command) {
        authorization.requirePermission(actor, PermissionCode.STOREFRONT_MANAGE);
        if (command == null || command.expectedPublishedRevisionId() == null) {
            throw invalid("STOREFRONT_PUBLISHED_REQUIRED", "Published HOME revision is required.");
        }
        lockHome();
        if (header("DRAFT") != null) throw conflict();
        RevisionHeader published = header("PUBLISHED");
        if (published == null) {
            throw invalid("STOREFRONT_PUBLISHED_REQUIRED", "There is no published HOME revision to clone.");
        }
        if (!published.publicId().equals(command.expectedPublishedRevisionId())) throw conflict();

        RevisionState source = revision("PUBLISHED");
        UUID revisionId = UUID.randomUUID();
        int revisionNumber = jdbc.queryForObject(
                "SELECT COALESCE(MAX(revision_number),0)+1 FROM storefront_page_revision WITH (UPDLOCK,HOLDLOCK) WHERE page_key=?",
                Integer.class, PAGE);
        Instant now = clock.instant();
        try {
            jdbc.update("""
                    INSERT INTO storefront_page_revision(public_id,page_key,revision_number,status,entity_version,
                        created_by_account_public_id,created_at)
                    VALUES (?,?,?,'DRAFT',0,?,?)
                    """, revisionId, PAGE, revisionNumber, actor.publicId(), Timestamp.from(now));
        } catch (DuplicateKeyException race) {
            throw conflict();
        }
        long databaseId = jdbc.queryForObject(
                "SELECT id FROM storefront_page_revision WHERE public_id=?", Long.class, revisionId);
        insertSections(databaseId, source.sections().stream().map(StorefrontMerchandisingService::input).toList());
        audit.append(actor, "STOREFRONT_DRAFT_CLONED", "STOREFRONT_PAGE_REVISION", revisionId,
                null, null, Map.of("page", PAGE, "revisionNumber", revisionNumber,
                        "sourcePublishedRevisionId", published.publicId(), "sectionCount", source.sections().size()));
        return revision("DRAFT");
    }

    @Transactional(isolation = Isolation.SERIALIZABLE)
    public RevisionState publish(SessionPrincipal actor, PublishDraft command) {
        authorization.requirePermission(actor, PermissionCode.STOREFRONT_MANAGE);
        if (command == null || command.expectedDraftRevisionId() == null || command.expectedEntityVersion() == null) {
            throw invalid("STOREFRONT_PUBLISH_VERSION_REQUIRED", "Draft revision and version are required.");
        }
        lockHome();
        RevisionHeader draft = header("DRAFT");
        RevisionHeader published = header("PUBLISHED");
        if (draft == null || !draft.publicId().equals(command.expectedDraftRevisionId())
                || draft.entityVersion() != command.expectedEntityVersion()
                || !Objects.equals(published == null ? null : published.publicId(), command.expectedPublishedRevisionId())) {
            throw conflict();
        }
        RevisionState candidate = revision("DRAFT");
        validatePublicReferences(candidate.sections());
        Instant now = clock.instant();
        if (published != null) {
            jdbc.update("""
                    UPDATE storefront_page_revision
                    SET status='RETIRED',retired_at=?,entity_version=entity_version+1
                    WHERE id=? AND status='PUBLISHED'
                    """, Timestamp.from(now), published.id());
        }
        if (jdbc.update("""
                UPDATE storefront_page_revision
                SET status='PUBLISHED',published_at=?,entity_version=entity_version+1
                WHERE id=? AND status='DRAFT' AND entity_version=?
                """, Timestamp.from(now), draft.id(), draft.entityVersion()) != 1) throw conflict();
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("page", PAGE);
        details.put("revisionNumber", draft.revisionNumber());
        details.put("previousPublishedRevisionId", published == null ? null : published.publicId());
        audit.append(actor, "STOREFRONT_PAGE_PUBLISHED", "STOREFRONT_PAGE_REVISION", draft.publicId(),
                null, null, details);
        return revision("PUBLISHED");
    }

    public PublishedHomepage homepage() {
        RevisionState published = revision("PUBLISHED");
        if (published == null) return new PublishedHomepage(null, null, List.of());
        Map<UUID, StorefrontCatalogService.ProductSummary> products = new HashMap<>();
        catalog.browse().forEach(product -> products.put(product.id(), product));
        Map<UUID, Optional<VoucherService.PublicOffer>> offers = new HashMap<>();
        List<CustomerSection> resolved = new ArrayList<>();
        for (StoredSection section : published.sections()) {
            if (!section.enabled()) continue;
            Optional<CtaView> cta = resolveCta(section, products, offers);
            if (cta.isEmpty()) continue;
            List<StorefrontCatalogService.ProductSummary> collection = section.productIds().stream()
                    .map(products::get).filter(Objects::nonNull).toList();
            if (section.type() == SectionType.PRODUCT_COLLECTION && collection.isEmpty()) continue;
            VoucherService.PublicOffer offer = null;
            if (section.type() == SectionType.PROMOTION_SPOTLIGHT) {
                offer = offer(section.promotionFamilyId(), offers).orElse(null);
                if (offer == null) continue;
            }
            resolved.add(new CustomerSection(section.sectionId(), section.type(), section.position(),
                    new LocalizedCopy(section.titleVi(), section.titleEn(), section.supportingTextVi(),
                            section.supportingTextEn()),
                    cta.get(), section.mediaKey() == null ? null
                            : new MediaView(section.mediaKey(), section.mediaKey().url()),
                    collection, offer));
        }
        return new PublishedHomepage(published.revisionId(), published.publishedAt(), List.copyOf(resolved));
    }

    private void validatePublicReferences(List<StoredSection> sections) {
        Set<UUID> products = catalog.currentProductIds();
        Map<UUID, Optional<VoucherService.PublicOffer>> offers = new HashMap<>();
        for (StoredSection section : sections) {
            for (UUID productId : section.productIds()) {
                if (!products.contains(productId)) reference("Product", productId);
            }
            if (section.ctaKind() == CtaKind.PRODUCT_DETAIL
                    && !products.contains(section.ctaTargetPublicId())) {
                reference("CTA product", section.ctaTargetPublicId());
            }
            if (section.ctaKind() == CtaKind.PROMOTION_DETAIL
                    && offer(section.ctaTargetPublicId(), offers).isEmpty()) {
                reference("CTA promotion", section.ctaTargetPublicId());
            }
            if (section.promotionFamilyId() != null
                    && offer(section.promotionFamilyId(), offers).isEmpty()) {
                reference("Promotion", section.promotionFamilyId());
            }
        }
    }

    private Optional<CtaView> resolveCta(StoredSection section,
            Map<UUID, StorefrontCatalogService.ProductSummary> products,
            Map<UUID, Optional<VoucherService.PublicOffer>> offers) {
        if (section.ctaKind() == CtaKind.PRODUCT_DETAIL
                && !products.containsKey(section.ctaTargetPublicId())) return Optional.empty();
        if (section.ctaKind() == CtaKind.PROMOTION_DETAIL
                && offer(section.ctaTargetPublicId(), offers).isEmpty()) return Optional.empty();
        return Optional.of(new CtaView(new LocalizedLabel(section.ctaLabelVi(), section.ctaLabelEn()),
                section.ctaKind(), section.ctaTargetPublicId(), section.ctaSearchTerm()));
    }

    private Optional<VoucherService.PublicOffer> offer(UUID familyId,
            Map<UUID, Optional<VoucherService.PublicOffer>> cache) {
        return cache.computeIfAbsent(familyId, id -> {
            try {
                return Optional.of(vouchers.detail(id));
            } catch (ResourceNotFoundException stale) {
                return Optional.empty();
            }
        });
    }

    private void insertSections(long revisionId, List<SectionInput> sections) {
        for (int position = 0; position < sections.size(); position++) {
            SectionInput section = sections.get(position);
            UUID publicId = UUID.randomUUID();
            jdbc.update("""
                    INSERT INTO storefront_section(public_id,page_revision_id,position,section_type,is_enabled,
                        title_vi,title_en,supporting_text_vi,supporting_text_en,cta_label_vi,cta_label_en,
                        cta_kind,cta_target_public_id,cta_search_term,promotion_family_public_id,media_key)
                    VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
                    """, publicId, revisionId, position, section.type().name(), section.enabled(),
                    section.titleVi(), section.titleEn(), section.supportingTextVi(), section.supportingTextEn(),
                    section.ctaLabelVi(), section.ctaLabelEn(), section.ctaKind().name(),
                    section.ctaTargetPublicId(), section.ctaSearchTerm(), section.promotionFamilyId(),
                    section.mediaKey() == null ? null : section.mediaKey().name());
            if (section.type() == SectionType.PRODUCT_COLLECTION) {
                long sectionId = jdbc.queryForObject(
                        "SELECT id FROM storefront_section WHERE public_id=?", Long.class, publicId);
                for (int memberPosition = 0; memberPosition < section.productIds().size(); memberPosition++) {
                    jdbc.update("""
                            INSERT INTO storefront_collection_product(section_id,product_public_id,position)
                            VALUES (?,?,?)
                            """, sectionId, section.productIds().get(memberPosition), memberPosition);
                }
            }
        }
    }

    private List<SectionInput> validate(SaveDraft command) {
        if (command == null || command.sections() == null
                || command.sections().isEmpty() || command.sections().size() > MAX_SECTIONS) {
            throw invalid("STOREFRONT_SECTIONS_INVALID", "HOME requires between 1 and 8 sections.");
        }
        int heroes = 0;
        List<SectionInput> normalized = new ArrayList<>();
        for (SectionInput raw : command.sections()) {
            if (raw == null || raw.type() == null || raw.ctaKind() == null) sectionInvalid();
            if (raw.type() == SectionType.HERO_BANNER && ++heroes > 1) {
                throw invalid("STOREFRONT_HERO_DUPLICATE", "HOME allows at most one hero banner.");
            }
            String titleVi = text(raw.titleVi(), 120, true);
            String titleEn = text(raw.titleEn(), 120, true);
            String supportingVi = text(raw.supportingTextVi(), 500, false);
            String supportingEn = text(raw.supportingTextEn(), 500, false);
            if ((supportingVi == null) != (supportingEn == null)) sectionInvalid();
            String labelVi = text(raw.ctaLabelVi(), 80, true);
            String labelEn = text(raw.ctaLabelEn(), 80, true);
            UUID target = raw.ctaTargetPublicId();
            String search = text(raw.ctaSearchTerm(), 80, false);
            switch (raw.ctaKind()) {
                case PRODUCT_DETAIL, PROMOTION_DETAIL -> {
                    if (target == null || search != null) ctaInvalid();
                }
                case CATALOG_SEARCH -> {
                    if (target != null || search == null) ctaInvalid();
                }
                case OFFERS -> {
                    if (target != null || search != null) ctaInvalid();
                }
            }
            List<UUID> productIds = raw.productIds() == null ? List.of() : new ArrayList<>(raw.productIds());
            if (productIds.stream().anyMatch(Objects::isNull)
                    || new HashSet<>(productIds).size() != productIds.size()) {
                throw invalid("STOREFRONT_COLLECTION_DUPLICATE", "Collection products must be unique.");
            }
            switch (raw.type()) {
                case HERO_BANNER -> {
                    if (raw.mediaKey() == null || raw.promotionFamilyId() != null || !productIds.isEmpty()) sectionInvalid();
                }
                case PRODUCT_COLLECTION -> {
                    if (raw.mediaKey() != null || raw.promotionFamilyId() != null
                            || productIds.isEmpty() || productIds.size() > MAX_PRODUCTS) sectionInvalid();
                }
                case PROMOTION_SPOTLIGHT -> {
                    if (raw.mediaKey() != null || raw.promotionFamilyId() == null || !productIds.isEmpty()
                            || raw.ctaKind() != CtaKind.PROMOTION_DETAIL
                            || !raw.promotionFamilyId().equals(target)) sectionInvalid();
                }
            }
            normalized.add(new SectionInput(raw.type(), raw.enabled(), titleVi, titleEn, supportingVi,
                    supportingEn, labelVi, labelEn, raw.ctaKind(), target, search,
                    raw.promotionFamilyId(), raw.mediaKey(), List.copyOf(productIds)));
        }
        return List.copyOf(normalized);
    }

    private RevisionState revision(String status) {
        RevisionHeader header = header(status);
        if (header == null) return null;
        List<SectionRow> rows = jdbc.query("""
                SELECT id,public_id,position,section_type,is_enabled,title_vi,title_en,
                    supporting_text_vi,supporting_text_en,cta_label_vi,cta_label_en,cta_kind,
                    cta_target_public_id,cta_search_term,promotion_family_public_id,media_key
                FROM storefront_section WHERE page_revision_id=? ORDER BY position
                """, (rs, row) -> sectionRow(rs), header.id());
        List<StoredSection> sections = rows.stream().map(this::section).toList();
        return new RevisionState(header.publicId(), header.revisionNumber(), header.status(),
                header.entityVersion(), header.publishedAt(), sections);
    }

    private StoredSection section(SectionRow row) {
        List<UUID> productIds = jdbc.queryForList("""
                SELECT product_public_id FROM storefront_collection_product
                WHERE section_id=? ORDER BY position
                """, UUID.class, row.id());
        return new StoredSection(row.publicId(), row.position(), row.type(), row.enabled(), row.titleVi(),
                row.titleEn(), row.supportingTextVi(), row.supportingTextEn(), row.ctaLabelVi(), row.ctaLabelEn(),
                row.ctaKind(), row.ctaTargetPublicId(), row.ctaSearchTerm(), row.promotionFamilyId(), row.mediaKey(),
                productIds);
    }

    private static SectionRow sectionRow(ResultSet rs) throws SQLException {
        return new SectionRow(rs.getLong("id"), rs.getObject("public_id", UUID.class), rs.getInt("position"),
                SectionType.valueOf(rs.getString("section_type")), rs.getBoolean("is_enabled"),
                rs.getString("title_vi"), rs.getString("title_en"), rs.getString("supporting_text_vi"),
                rs.getString("supporting_text_en"), rs.getString("cta_label_vi"), rs.getString("cta_label_en"),
                CtaKind.valueOf(rs.getString("cta_kind")), rs.getObject("cta_target_public_id", UUID.class),
                rs.getString("cta_search_term"), rs.getObject("promotion_family_public_id", UUID.class),
                rs.getString("media_key") == null ? null : MediaKey.valueOf(rs.getString("media_key")));
    }

    private RevisionHeader header(String status) {
        return jdbc.query("""
                SELECT id,public_id,revision_number,status,entity_version,published_at
                FROM storefront_page_revision WHERE page_key=? AND status=?
                """, rs -> rs.next() ? new RevisionHeader(rs.getLong("id"), rs.getObject("public_id", UUID.class),
                        rs.getInt("revision_number"), rs.getString("status"), rs.getLong("entity_version"),
                        rs.getTimestamp("published_at") == null ? null : rs.getTimestamp("published_at").toInstant())
                        : null, PAGE, status);
    }

    private void lockHome() {
        jdbc.queryForList("""
                SELECT id FROM storefront_page_revision WITH (UPDLOCK,HOLDLOCK)
                WHERE page_key=? ORDER BY id
                """, Long.class, PAGE);
    }

    private static String text(String value, int max, boolean required) {
        String trimmed = value == null ? null : value.trim();
        if (trimmed != null && trimmed.isEmpty()) trimmed = null;
        if ((required && trimmed == null) || (trimmed != null && trimmed.length() > max)) sectionInvalid();
        return trimmed;
    }

    private static SectionInput input(StoredSection section) {
        return new SectionInput(section.type(), section.enabled(), section.titleVi(), section.titleEn(),
                section.supportingTextVi(), section.supportingTextEn(), section.ctaLabelVi(), section.ctaLabelEn(),
                section.ctaKind(), section.ctaTargetPublicId(), section.ctaSearchTerm(), section.promotionFamilyId(),
                section.mediaKey(), section.productIds());
    }

    private static void reference(String label, UUID id) {
        throw invalid("STOREFRONT_REFERENCE_INVALID", label + " is not currently public: " + id);
    }

    private static BusinessConflictException conflict() {
        return new BusinessConflictException("STOREFRONT_DRAFT_CONFLICT",
                "The HOME draft or publication changed. Reload management state and retry.");
    }

    private static void ctaInvalid() {
        throw invalid("STOREFRONT_CTA_INVALID", "CTA target does not match its kind.");
    }

    private static void sectionInvalid() {
        throw invalid("STOREFRONT_SECTION_INVALID", "Section content does not match its type.");
    }

    private static InvalidRequestException invalid(String code, String message) {
        return new InvalidRequestException(code, message);
    }

    public enum SectionType { HERO_BANNER, PRODUCT_COLLECTION, PROMOTION_SPOTLIGHT }
    public enum CtaKind { PRODUCT_DETAIL, CATALOG_SEARCH, PROMOTION_DETAIL, OFFERS }
    public enum MediaKey {
        COURT_CLASSIC("/products/court-classic.png"),
        METRO_RUNNER("/products/metro-runner.png"),
        STUDIO_LOW("/products/studio-low.png"),
        TRAIL_FORM("/products/trail-form.png"),
        AFTER_DARK("/products/after-dark.png"),
        DAILY_CANVAS("/products/daily-canvas.png"),
        COURT_HIGH("/products/court-high.png"),
        PACE_KNIT("/products/pace-knit.png");
        private final String url;
        MediaKey(String url) { this.url = url; }
        public String url() { return url; }
    }

    public record SaveDraft(UUID expectedDraftRevisionId, Long expectedEntityVersion,
            List<SectionInput> sections) { }
    public record ClonePublished(UUID expectedPublishedRevisionId) { }
    public record PublishDraft(UUID expectedDraftRevisionId, Long expectedEntityVersion,
            UUID expectedPublishedRevisionId) { }
    public record SectionInput(SectionType type, boolean enabled, String titleVi, String titleEn,
            String supportingTextVi, String supportingTextEn, String ctaLabelVi, String ctaLabelEn,
            CtaKind ctaKind, UUID ctaTargetPublicId, String ctaSearchTerm, UUID promotionFamilyId,
            MediaKey mediaKey, List<UUID> productIds) { }
    public record ManagementState(RevisionState draft, RevisionState published) { }
    public record RevisionState(UUID revisionId, int revisionNumber, String status, long entityVersion,
            Instant publishedAt, List<StoredSection> sections) { }
    public record StoredSection(UUID sectionId, int position, SectionType type, boolean enabled,
            String titleVi, String titleEn, String supportingTextVi, String supportingTextEn,
            String ctaLabelVi, String ctaLabelEn, CtaKind ctaKind, UUID ctaTargetPublicId,
            String ctaSearchTerm, UUID promotionFamilyId, MediaKey mediaKey, List<UUID> productIds) { }
    public record PublishedHomepage(UUID publishedRevisionId, Instant publishedAt,
            List<CustomerSection> sections) { }
    public record CustomerSection(UUID sectionId, SectionType type, int position, LocalizedCopy copy,
            CtaView cta, MediaView media, List<StorefrontCatalogService.ProductSummary> products,
            VoucherService.PublicOffer promotion) { }
    public record LocalizedCopy(String titleVi, String titleEn, String supportingTextVi,
            String supportingTextEn) { }
    public record LocalizedLabel(String vi, String en) { }
    public record CtaView(LocalizedLabel label, CtaKind kind, UUID targetPublicId, String searchTerm) { }
    public record MediaView(MediaKey key, String url) { }
    private record RevisionHeader(long id, UUID publicId, int revisionNumber, String status,
            long entityVersion, Instant publishedAt) { }
    private record SectionRow(long id, UUID publicId, int position, SectionType type, boolean enabled,
            String titleVi, String titleEn, String supportingTextVi, String supportingTextEn,
            String ctaLabelVi, String ctaLabelEn, CtaKind ctaKind, UUID ctaTargetPublicId,
            String ctaSearchTerm, UUID promotionFamilyId, MediaKey mediaKey) { }
}
