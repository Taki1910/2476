package com.shoecommerce.merchandising;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

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
import com.shoecommerce.platform.api.ResourceNotFoundException;

@Service
public class ProductPresentationService {
    private static final int MAX_SUMMARY_LENGTH = 500;

    private final JdbcTemplate jdbc;
    private final AuthorizationPolicy authorization;
    private final AuditWriter audit;
    private final Clock clock;

    public ProductPresentationService(JdbcTemplate jdbc, AuthorizationPolicy authorization,
            AuditWriter audit, Clock clock) {
        this.jdbc = jdbc;
        this.authorization = authorization;
        this.audit = audit;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public ManagementState managementState(SessionPrincipal actor, UUID productId) {
        authorization.requirePermission(actor, PermissionCode.STOREFRONT_MANAGE);
        long databaseProductId = productId(productId);
        List<StoredRevision> revisions = revisions(databaseProductId, false);
        return new ManagementState(productId, view(current(revisions, "DRAFT")),
                view(current(revisions, "PUBLISHED")));
    }

    @Transactional(readOnly = true)
    public List<ProductReference> products(SessionPrincipal actor) {
        authorization.requirePermission(actor, PermissionCode.STOREFRONT_MANAGE);
        return jdbc.query("""
                SELECT public_id,name,category,collection
                FROM catalog_product ORDER BY name,id
                """, (row, index) -> new ProductReference(row.getObject("public_id", UUID.class),
                row.getString("name"), row.getString("category"), row.getString("collection")));
    }

    @Transactional(isolation = Isolation.SERIALIZABLE)
    public RevisionView saveDraft(SessionPrincipal actor, UUID productId, SaveDraft command) {
        authorization.requirePermission(actor, PermissionCode.STOREFRONT_MANAGE);
        if (command == null) throw invalid("PRODUCT_PRESENTATION_DRAFT_INVALID", "Draft command is required.");
        long databaseProductId = lockProduct(productId);
        List<StoredRevision> revisions = revisions(databaseProductId, true);
        StoredRevision draft = current(revisions, "DRAFT");
        if (draft == null) return createDraft(actor, productId, databaseProductId, revisions, command);

        if (command.expectedDraftRevisionId() == null && command.expectedEntityVersion() == null) {
            throw draftExists();
        }
        if (!Objects.equals(command.expectedDraftRevisionId(), draft.publicId())
                || !Objects.equals(command.expectedEntityVersion(), draft.entityVersion())) {
            throw draftConflict();
        }
        String summaryVi = text(command.summaryVi());
        String summaryEn = text(command.summaryEn());
        if (jdbc.update("""
                UPDATE product_presentation_revision
                SET summary_vi=?,summary_en=?,entity_version=entity_version+1
                WHERE public_id=? AND product_id=? AND status='DRAFT' AND entity_version=?
                """, summaryVi, summaryEn, draft.publicId(), databaseProductId, draft.entityVersion()) != 1) {
            throw draftConflict();
        }
        audit.append(actor, "PRODUCT_PRESENTATION_DRAFT_UPDATED", "PRODUCT_PRESENTATION_REVISION",
                draft.publicId(), null, null, details(productId, draft.revisionNumber(), null));
        return revision(draft.publicId());
    }

    private RevisionView createDraft(SessionPrincipal actor, UUID productId, long databaseProductId,
            List<StoredRevision> revisions, SaveDraft command) {
        if (command.expectedDraftRevisionId() != null || command.expectedEntityVersion() != null
                || command.summaryVi() != null || command.summaryEn() != null) {
            throw invalid("PRODUCT_PRESENTATION_DRAFT_CREATE_INVALID",
                    "Create an empty draft before editing its summaries.");
        }
        StoredRevision published = current(revisions, "PUBLISHED");
        int revisionNumber = revisions.stream().map(StoredRevision::revisionNumber)
                .max(Comparator.naturalOrder()).orElse(0) + 1;
        UUID revisionId = UUID.randomUUID();
        Instant now = clock.instant();
        try {
            jdbc.update("""
                    INSERT INTO product_presentation_revision(public_id,product_id,revision_number,status,
                        summary_vi,summary_en,entity_version,created_by_account_public_id,created_at)
                    VALUES (?,?,?,'DRAFT',?,?,0,?,?)
                    """, revisionId, databaseProductId, revisionNumber,
                    published == null ? null : published.summaryVi(), published == null ? null : published.summaryEn(),
                    actor.publicId(), Timestamp.from(now));
        } catch (DuplicateKeyException race) {
            throw draftExists();
        }
        audit.append(actor, "PRODUCT_PRESENTATION_DRAFT_CREATED", "PRODUCT_PRESENTATION_REVISION",
                revisionId, null, null, details(productId, revisionNumber, null));
        return revision(revisionId);
    }

    @Transactional(isolation = Isolation.SERIALIZABLE)
    public RevisionView publish(SessionPrincipal actor, UUID productId, PublishDraft command) {
        authorization.requirePermission(actor, PermissionCode.STOREFRONT_MANAGE);
        if (command == null || command.expectedDraftRevisionId() == null) {
            throw invalid("PRODUCT_PRESENTATION_PUBLISH_VERSION_REQUIRED",
                    "Draft revision and version are required.");
        }
        long databaseProductId = lockProduct(productId);
        List<StoredRevision> revisions = revisions(databaseProductId, true);
        StoredRevision published = current(revisions, "PUBLISHED");
        StoredRevision requested = revisions.stream()
                .filter(row -> row.publicId().equals(command.expectedDraftRevisionId())).findFirst().orElse(null);

        if (requested != null && requested.status().equals("PUBLISHED")
                && requested == published && requested.entityVersion() == command.expectedEntityVersion() + 1
                && replayMatches(requested.publicId(), command.expectedPublishedRevisionId())) {
            return view(requested);
        }

        StoredRevision draft = current(revisions, "DRAFT");
        if (draft == null || !draft.publicId().equals(command.expectedDraftRevisionId())
                || draft.entityVersion() != command.expectedEntityVersion()
                || !Objects.equals(published == null ? null : published.publicId(),
                        command.expectedPublishedRevisionId())) {
            throw publishConflict();
        }
        if (draft.summaryVi() == null || draft.summaryEn() == null) {
            throw invalid("PRODUCT_PRESENTATION_LOCALES_REQUIRED",
                    "Vietnamese and English summaries are required before publication.");
        }

        Instant now = clock.instant();
        if (published != null && jdbc.update("""
                UPDATE product_presentation_revision
                SET status='RETIRED',retired_at=?,entity_version=entity_version+1
                WHERE id=? AND status='PUBLISHED' AND entity_version=?
                """, Timestamp.from(now), published.id(), published.entityVersion()) != 1) {
            throw publishConflict();
        }
        if (jdbc.update("""
                UPDATE product_presentation_revision
                SET status='PUBLISHED',published_at=?,entity_version=entity_version+1
                WHERE id=? AND status='DRAFT' AND entity_version=?
                """, Timestamp.from(now), draft.id(), draft.entityVersion()) != 1) {
            throw publishConflict();
        }

        String action = published == null ? "PRODUCT_PRESENTATION_PUBLISHED" : "PRODUCT_PRESENTATION_REPLACED";
        audit.append(actor, action, "PRODUCT_PRESENTATION_REVISION", draft.publicId(), null, null,
                details(productId, draft.revisionNumber(), published == null ? null : published.publicId()));
        return revision(draft.publicId());
    }

    private long lockProduct(UUID publicId) {
        List<Long> ids = jdbc.queryForList(
                "SELECT id FROM catalog_product WITH (UPDLOCK,HOLDLOCK) WHERE public_id=?", Long.class, publicId);
        if (ids.isEmpty()) throw notFound(publicId);
        return ids.getFirst();
    }

    private long productId(UUID publicId) {
        List<Long> ids = jdbc.queryForList("SELECT id FROM catalog_product WHERE public_id=?", Long.class, publicId);
        if (ids.isEmpty()) throw notFound(publicId);
        return ids.getFirst();
    }

    private List<StoredRevision> revisions(long productId, boolean lock) {
        String hint = lock ? " WITH (UPDLOCK,HOLDLOCK)" : "";
        return jdbc.query("""
                SELECT id,public_id,revision_number,status,entity_version,summary_vi,summary_en,
                    created_at,published_at,retired_at
                FROM product_presentation_revision%s
                WHERE product_id=? ORDER BY id
                """.formatted(hint), ProductPresentationService::storedRevision, productId);
    }

    private RevisionView revision(UUID revisionId) {
        return jdbc.query("""
                SELECT id,public_id,revision_number,status,entity_version,summary_vi,summary_en,
                    created_at,published_at,retired_at
                FROM product_presentation_revision WHERE public_id=?
                """, rows -> rows.next() ? view(storedRevision(rows, 0)) : null, revisionId);
    }

    private boolean replayMatches(UUID revisionId, UUID expectedPrevious) {
        if (expectedPrevious == null) {
            return jdbc.queryForObject("""
                    SELECT COUNT(*) FROM audit_event
                    WHERE resource_type='PRODUCT_PRESENTATION_REVISION' AND resource_public_id=?
                      AND action='PRODUCT_PRESENTATION_PUBLISHED'
                      AND JSON_VALUE(details_json,'$.previousPublishedRevisionId') IS NULL
                    """, Integer.class, revisionId) == 1;
        }
        return jdbc.queryForObject("""
                SELECT COUNT(*) FROM audit_event
                WHERE resource_type='PRODUCT_PRESENTATION_REVISION' AND resource_public_id=?
                  AND action='PRODUCT_PRESENTATION_REPLACED'
                  AND JSON_VALUE(details_json,'$.previousPublishedRevisionId')=?
                """, Integer.class, revisionId, expectedPrevious.toString()) == 1;
    }

    private static StoredRevision current(List<StoredRevision> revisions, String status) {
        return revisions.stream().filter(row -> row.status().equals(status)).findFirst().orElse(null);
    }

    private static StoredRevision storedRevision(ResultSet row, int index) throws SQLException {
        return new StoredRevision(row.getLong("id"), row.getObject("public_id", UUID.class),
                row.getInt("revision_number"), row.getString("status"), row.getLong("entity_version"),
                row.getString("summary_vi"), row.getString("summary_en"), instant(row, "created_at"),
                instant(row, "published_at"), instant(row, "retired_at"));
    }

    private static Instant instant(ResultSet row, String column) throws SQLException {
        Timestamp value = row.getTimestamp(column);
        return value == null ? null : value.toInstant();
    }

    private static RevisionView view(StoredRevision revision) {
        return revision == null ? null : new RevisionView(revision.publicId(), revision.revisionNumber(),
                revision.status(), revision.entityVersion(), revision.summaryVi(), revision.summaryEn(),
                revision.createdAt(), revision.publishedAt(), revision.retiredAt());
    }

    private static String text(String value) {
        String canonical = value == null ? null : value.trim();
        if (canonical != null && canonical.isEmpty()) canonical = null;
        if (canonical != null && canonical.length() > MAX_SUMMARY_LENGTH) {
            throw invalid("PRODUCT_PRESENTATION_SUMMARY_INVALID",
                    "Product presentation summaries must not exceed 500 characters.");
        }
        return canonical;
    }

    private static Map<String, Object> details(UUID productId, int revisionNumber, UUID previousPublished) {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("productId", productId);
        details.put("revisionNumber", revisionNumber);
        details.put("previousPublishedRevisionId", previousPublished);
        return details;
    }

    private static ResourceNotFoundException notFound(UUID productId) {
        return new ResourceNotFoundException("PRODUCT_NOT_FOUND", "Product not found: " + productId);
    }

    private static BusinessConflictException draftConflict() {
        return new BusinessConflictException("PRODUCT_PRESENTATION_DRAFT_CONFLICT",
                "The Product presentation draft changed. Reload management state and retry.");
    }

    private static BusinessConflictException draftExists() {
        return new BusinessConflictException("PRODUCT_PRESENTATION_DRAFT_EXISTS",
                "A Product presentation draft already exists. Reload management state and retry.");
    }

    private static BusinessConflictException publishConflict() {
        return new BusinessConflictException("PRODUCT_PRESENTATION_PUBLISH_CONFLICT",
                "The Product presentation publication changed. Reload management state and retry.");
    }

    private static InvalidRequestException invalid(String code, String message) {
        return new InvalidRequestException(code, message);
    }

    public record RevisionView(UUID id, int revisionNumber, String status, long entityVersion,
            String summaryVi, String summaryEn, Instant createdAt, Instant publishedAt, Instant retiredAt) { }
    public record ManagementState(UUID productId, RevisionView draft, RevisionView published) { }
    public record SaveDraft(UUID expectedDraftRevisionId, Long expectedEntityVersion,
            String summaryVi, String summaryEn) { }
    public record PublishDraft(UUID expectedDraftRevisionId, long expectedEntityVersion,
            UUID expectedPublishedRevisionId) { }
    public record ProductReference(UUID id, String name, String category, String collection) { }
    private record StoredRevision(long id, UUID publicId, int revisionNumber, String status, long entityVersion,
            String summaryVi, String summaryEn, Instant createdAt, Instant publishedAt, Instant retiredAt) { }
}
