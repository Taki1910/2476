# Product Differentiation Evidence Pilot Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a versioned bilingual ProductPresentation workflow, publish the three approved pilot summaries, compose them into catalog/PDP responses, and honestly test whether they improve human product differentiation.

**Architecture:** A single SQL Server `product_presentation_revision` table belongs to the merchandising module and references Catalog Product by internal FK. A Product-scoped SQL Server lock protects draft creation, revision allocation and atomic publication. Existing catalog browse/detail queries compose the current published summary additively; Vue uses one shared renderer for management preview, cards and PDP.

**Tech Stack:** Java 25, Spring Boot 4.1, Spring JDBC, SQL Server, Flyway, Vue 3, TypeScript 5.9, Vitest, Vite.

**Spec:** `docs/ADR/0038-product-presentation-publication.md`

## Global Constraints

- Preserve the current dirty working tree; do not reset, clean, stage or commit.
- Catalog facts and ProductPresentation editorial content remain separate authorities.
- Reuse `STOREFRONT_MANAGE`; do not add a role or permission.
- Persist only the exact approved VI/EN summaries for Court Classic, Metro Runner and Trail Form.
- Court High, Pace Knit and Trail Edge intentionally have no presentation row.
- Do not add highlights, intended-use fields, rich text, media persistence, product claims, taxonomy aliases, a PIM or a CMS.
- Customer APIs expose only `presentation: null` or `{ summary: { vi, en } }`; management identity/version fields never leak.
- Catalog browse resolves presentations set-wise; no per-card content request or Product-by-Product SQL loop.
- Keep ProductPresentation outside the PDP purchase Decision Cluster.
- `SEARCH VOCABULARY DEFECT — DEFERRED`; do not hard-code `chạy -> Running`.
- Automated engineering success does not establish merchandising success.

## Review Focus

- Whitespace-only or 501-character locale input must persist as `null` or fail before SQL, while internal whitespace remains unchanged; Task 2 asserts stored canonical text.
- A publish response lost on the network must replay to the same current publication without another lifecycle mutation or audit event; Task 2 covers exact replay and replaced-revision conflict.
- Concurrent first-draft requests for one Product must produce one revision, while mutations for distinct Products do not share a lock; Task 2 exercises both cases.
- A draft or retired revision must never leak through browse/detail, and 18-card browse must stay set-based; Task 3 covers multi-Product projection from one browse query.
- Mixed cards must not reserve an empty summary box or push PDP purchase controls down; Tasks 6 and 8 cover rendered absence and real-browser hierarchy.

---

### Task 1: SQL Server schema and upgrade safety

**Files:**
- Create: `backend/src/main/resources/db/migration/V28__product_presentation_publication.sql`
- Create: `backend/src/test/java/com/shoecommerce/FlywayV28UpgradeExternalIT.java`

**Interfaces:**
- Consumes: `catalog_product(id)`, `iam_user_account(public_id)`, Flyway migration V27.
- Produces: `product_presentation_revision` with one-draft, one-published and historical revision invariants.

- [ ] **Step 1: Write the failing V28 upgrade test**

Copy the isolated-database helper pattern from `FlywayV26UpgradeExternalIT`. Add a fresh migration test and a populated-V27 upgrade test:

```java
@Test
void migratesFreshDatabaseThroughV28WithProductPresentationConstraints() throws Exception {
    try (Database database = Database.create("fresh")) {
        database.migrate();
        assertThat(database.count("SELECT COUNT(*) FROM flyway_schema_history WHERE success=1 AND version='28'"))
                .isOne();
        assertThat(database.count("SELECT COUNT(*) FROM sys.tables WHERE name='product_presentation_revision'"))
                .isOne();
        assertThat(database.count("SELECT COUNT(*) FROM sys.indexes WHERE name IN ('UX_product_presentation_one_draft','UX_product_presentation_one_published')"))
                .isEqualTo(2);
    }
}

@Test
void upgradesPopulatedV27WithoutCreatingPresentationRows() throws Exception {
    try (Database database = Database.create("upgrade")) {
        database.migrateTo("27");
        UUID product = UUID.randomUUID();
        database.execute("INSERT INTO catalog_product(public_id,name,entity_version,created_at) VALUES ('"
                + product + "',N'Preserved product',0,SYSUTCDATETIME())");
        database.migrate();
        assertThat(database.count("SELECT COUNT(*) FROM catalog_product WHERE public_id='" + product + "'"))
                .isOne();
        assertThat(database.count("SELECT COUNT(*) FROM product_presentation_revision")).isZero();
    }
}
```

Add constraint probes that insert an incomplete DRAFT successfully, reject an incomplete PUBLISHED/RETIRED row, reject a second DRAFT, reject a second PUBLISHED, and reject a duplicate `(product_id, revision_number)`.

- [ ] **Step 2: Run the migration test and observe RED**

Run from `backend/` with the configured disposable SQL Server variables:

```powershell
.\mvnw.cmd -Pacceptance -Dit.test=FlywayV28UpgradeExternalIT verify
```

Expected: test discovery/compilation or Flyway assertion fails because V28 does not exist.

- [ ] **Step 3: Add the exact V28 DDL**

Create the table and indexes exactly as accepted in ADR-0038. The lifecycle constraint must include locale completeness:

```sql
CREATE TABLE product_presentation_revision (
    id BIGINT IDENTITY(1,1) NOT NULL CONSTRAINT PK_product_presentation_revision PRIMARY KEY,
    public_id UNIQUEIDENTIFIER NOT NULL CONSTRAINT UQ_product_presentation_revision_public UNIQUE,
    product_id BIGINT NOT NULL,
    revision_number INT NOT NULL,
    status VARCHAR(16) NOT NULL,
    summary_vi NVARCHAR(500) NULL,
    summary_en NVARCHAR(500) NULL,
    entity_version BIGINT NOT NULL CONSTRAINT DF_product_presentation_revision_version DEFAULT 0,
    created_by_account_public_id UNIQUEIDENTIFIER NOT NULL,
    created_at DATETIME2(6) NOT NULL,
    published_at DATETIME2(6) NULL,
    retired_at DATETIME2(6) NULL,
    CONSTRAINT FK_product_presentation_product FOREIGN KEY(product_id) REFERENCES catalog_product(id),
    CONSTRAINT FK_product_presentation_creator FOREIGN KEY(created_by_account_public_id) REFERENCES iam_user_account(public_id),
    CONSTRAINT UQ_product_presentation_revision_number UNIQUE(product_id, revision_number),
    CONSTRAINT CK_product_presentation_revision_number CHECK(revision_number > 0),
    CONSTRAINT CK_product_presentation_version CHECK(entity_version >= 0),
    CONSTRAINT CK_product_presentation_status CHECK(status IN ('DRAFT','PUBLISHED','RETIRED')),
    CONSTRAINT CK_product_presentation_summary_vi CHECK(summary_vi IS NULL OR LEN(LTRIM(RTRIM(summary_vi))) BETWEEN 1 AND 500),
    CONSTRAINT CK_product_presentation_summary_en CHECK(summary_en IS NULL OR LEN(LTRIM(RTRIM(summary_en))) BETWEEN 1 AND 500),
    CONSTRAINT CK_product_presentation_lifecycle CHECK(
        (status='DRAFT' AND published_at IS NULL AND retired_at IS NULL)
        OR (status='PUBLISHED' AND published_at IS NOT NULL AND retired_at IS NULL AND summary_vi IS NOT NULL AND summary_en IS NOT NULL)
        OR (status='RETIRED' AND published_at IS NOT NULL AND retired_at IS NOT NULL AND summary_vi IS NOT NULL AND summary_en IS NOT NULL)
    )
);

CREATE UNIQUE INDEX UX_product_presentation_one_draft
    ON product_presentation_revision(product_id) WHERE status='DRAFT';
CREATE UNIQUE INDEX UX_product_presentation_one_published
    ON product_presentation_revision(product_id) WHERE status='PUBLISHED';
```

- [ ] **Step 4: Run the migration test and observe GREEN**

Run the Step 2 command. Expected: both migration paths and all direct constraint probes pass.

- [ ] **Step 5: Record a no-commit checkpoint**

Run `git diff --check -- backend/src/main/resources/db/migration/V28__product_presentation_publication.sql backend/src/test/java/com/shoecommerce/FlywayV28UpgradeExternalIT.java`. Do not stage or commit.

### Task 2: ProductPresentation lifecycle, concurrency, authorization and management API

**Files:**
- Create: `backend/src/main/java/com/shoecommerce/merchandising/ProductPresentationService.java`
- Create: `backend/src/main/java/com/shoecommerce/merchandising/ProductPresentationController.java`
- Create: `backend/src/test/java/com/shoecommerce/merchandising/ProductPresentationExternalIT.java`

**Interfaces:**
- Consumes: Task 1 table, `AuthorizationPolicy`, `PermissionCode.STOREFRONT_MANAGE`, `AuditWriter`, `Clock`, existing structured API exceptions.
- Produces: `managementState`, `saveDraft`, `publish` and the management JSON contract.

- [ ] **Step 1: Write failing lifecycle and canonicalization integration tests**

Define SQL-backed fixtures for two Products, an authorized principal and a principal without `STOREFRONT_MANAGE`. Add tests equivalent to:

```java
@Test
void createsAnEmptyFirstDraftAndPersistsCanonicalIncompleteText() {
    UUID product = product("first-draft");
    var created = service.saveDraft(manager, product, new SaveDraft(null, null, null, null));
    assertThat(created.revisionNumber()).isOne();
    assertThat(created.entityVersion()).isZero();
    assertThat(created.summaryVi()).isNull();

    var saved = service.saveDraft(manager, product,
            new SaveDraft(created.id(), 0L, "  Hồ sơ  kích cỡ  ", "   "));
    assertThat(saved.summaryVi()).isEqualTo("Hồ sơ  kích cỡ");
    assertThat(saved.summaryEn()).isNull();
    assertThat(jdbc.queryForObject("SELECT summary_vi FROM product_presentation_revision WHERE public_id=?",
            String.class, created.id())).isEqualTo("Hồ sơ  kích cỡ");
}

@Test
void rejectsOverlongCanonicalTextAndIncompletePublication() {
    UUID product = product("validation");
    var draft = service.saveDraft(manager, product, new SaveDraft(null, null, null, null));
    assertThatThrownBy(() -> service.saveDraft(manager, product,
            new SaveDraft(draft.id(), 0L, "x".repeat(501), null)))
            .isInstanceOf(InvalidRequestException.class);
    assertThatThrownBy(() -> service.publish(manager, product,
            new PublishDraft(draft.id(), 0L, null)))
            .isInstanceOf(InvalidRequestException.class)
            .hasMessageContaining("Vietnamese and English");
}
```

Add tests proving: a new draft copies the current publication exactly; stale ID/version updates return `PRODUCT_PRESENTATION_DRAFT_CONFLICT`; no operation is allowed without `STOREFRONT_MANAGE`; published summary/product/revision/creator fields never change through the service.

- [ ] **Step 2: Write failing atomic publication and replay tests**

Cover first publication, replacement, rollback and exact retry:

```java
@Test
void atomicallyReplacesPublicationAndTreatsExactRetryAsSuccess() {
    UUID product = product("replace");
    var v1 = completeDraft(product, "VI one", "EN one");
    var first = service.publish(manager, product, new PublishDraft(v1.id(), v1.entityVersion(), null));
    var v2 = service.saveDraft(manager, product, new SaveDraft(null, null, null, null));
    v2 = service.saveDraft(manager, product,
            new SaveDraft(v2.id(), v2.entityVersion(), "VI two", "EN two"));
    var second = service.publish(manager, product,
            new PublishDraft(v2.id(), v2.entityVersion(), first.id()));

    assertThat(status(first.id())).isEqualTo("RETIRED");
    assertThat(status(second.id())).isEqualTo("PUBLISHED");
    int audits = auditCount(product);
    assertThat(service.publish(manager, product,
            new PublishDraft(v2.id(), v2.entityVersion(), first.id())).id()).isEqualTo(v2.id());
    assertThat(auditCount(product)).isEqualTo(audits);
}
```

Also assert a replay after v2 is replaced conflicts, a mismatched expected current publication conflicts, and an injected failure before commit leaves v1 PUBLISHED and no replacement audit evidence.

- [ ] **Step 3: Write the failing concurrent creation/isolation test**

Use two independent transaction threads synchronized with a `CountDownLatch`. Submit the same create command for Product A twice and one for Product B. Assert Product A has exactly one DRAFT and one caller receives a recoverable conflict; Product B creates its DRAFT without waiting on Product A's application-level latch. Keep the database unique indexes as the final assertions:

```java
assertThat(count("SELECT COUNT(*) FROM product_presentation_revision r JOIN catalog_product p ON p.id=r.product_id WHERE p.public_id=? AND r.status='DRAFT'", productA)).isOne();
assertThat(count("SELECT COUNT(*) FROM product_presentation_revision r JOIN catalog_product p ON p.id=r.product_id WHERE p.public_id=? AND r.status='DRAFT'", productB)).isOne();
```

- [ ] **Step 4: Run the focused integration test and observe RED**

```powershell
.\mvnw.cmd -Pacceptance -Dit.test=ProductPresentationExternalIT verify
```

Expected: compilation fails because service/controller records and methods do not exist.

- [ ] **Step 5: Implement the minimal service contract**

Use these exact records and methods:

```java
public record RevisionView(UUID id, int revisionNumber, String status, long entityVersion,
        String summaryVi, String summaryEn, Instant createdAt, Instant publishedAt, Instant retiredAt) { }
public record ManagementState(UUID productId, RevisionView draft, RevisionView published) { }
public record SaveDraft(UUID expectedDraftRevisionId, Long expectedEntityVersion,
        String summaryVi, String summaryEn) { }
public record PublishDraft(UUID expectedDraftRevisionId, long expectedEntityVersion,
        UUID expectedPublishedRevisionId) { }

public ManagementState managementState(SessionPrincipal actor, UUID productId)
public RevisionView saveDraft(SessionPrincipal actor, UUID productId, SaveDraft command)
public RevisionView publish(SessionPrincipal actor, UUID productId, PublishDraft command)
```

For every mutation, begin `@Transactional(isolation = Isolation.SERIALIZABLE)`, require `STOREFRONT_MANAGE`, then lock only the Product scope:

```sql
SELECT id FROM catalog_product WITH (UPDLOCK,HOLDLOCK) WHERE public_id=?
SELECT id,public_id,revision_number,status,entity_version
FROM product_presentation_revision WITH (UPDLOCK,HOLDLOCK)
WHERE product_id=? ORDER BY id
```

On create, require null expectations and null summaries, allocate the next revision under the lock, and copy current PUBLISHED text when it exists. On update, canonicalize text and execute one conditional `UPDATE ... WHERE public_id=? AND product_id=? AND status='DRAFT' AND entity_version=?`; zero rows becomes 409.

Publish follows ADR order. Detect an exact completed retry before returning conflict: requested revision is the current PUBLISHED row, its version equals expected pre-publish version + 1, and no newer publication exists. Return it without writing audit again.

- [ ] **Step 6: Implement the management controller**

Expose exactly:

```java
@GetMapping("/{productId}")
ManagementState state(@AuthenticationPrincipal SessionPrincipal actor, @PathVariable UUID productId)

@PutMapping("/{productId}/draft")
RevisionView save(@AuthenticationPrincipal SessionPrincipal actor, @PathVariable UUID productId,
        @RequestBody SaveDraft command)

@PostMapping("/{productId}/publish")
RevisionView publish(@AuthenticationPrincipal SessionPrincipal actor, @PathVariable UUID productId,
        @RequestBody PublishDraft command)
```

Use base path `/api/v1/operations/product-presentations`. Keep authorization in the service as well as route security. Reuse existing error serialization and CSRF behavior.

- [ ] **Step 7: Run the focused integration test and observe GREEN**

Run the Step 4 command. Expected: lifecycle, concurrency, authorization, HTTP and audit cases pass against SQL Server.

- [ ] **Step 8: Record a no-commit checkpoint**

Run `git diff --check` for the three Task 2 files. Confirm there is no generic repository update method and no new permission.

### Task 3: Additive customer projection without N+1 reads

**Files:**
- Modify: `backend/src/main/java/com/shoecommerce/catalog/StorefrontCatalogService.java`
- Modify: `backend/src/test/java/com/shoecommerce/catalog/VerticalSlice2CustomerCatalogExternalIT.java`

**Interfaces:**
- Consumes: Task 1 table and current browse/detail SQL.
- Produces: nullable `ProductPresentation` on `ProductSummary` and `ProductDetail`.

- [ ] **Step 1: Add failing multi-Product projection tests**

Insert one PUBLISHED, one DRAFT-only and one RETIRED presentation for three visible fixture Products. In one `storefront.browse()` call assert only the published Product has content. Assert `detail()` agrees and JSON contains no management metadata:

```java
assertThat(storefront.browse())
    .filteredOn(product -> pilotIds.contains(product.id()))
    .extracting(ProductSummary::presentation)
    .containsExactlyInAnyOrder(new ProductPresentation(new LocalizedSummary("VI", "EN")), null, null);

JsonNode body = getJson("/api/v1/storefront/products/" + publishedProduct);
assertThat(body.at("/presentation/summary/vi").asText()).isEqualTo("VI");
assertThat(body.at("/presentation").has("entityVersion")).isFalse();
assertThat(body.at("/presentation").has("revisionNumber")).isFalse();
```

Use the SQL-backed result across multiple Products as the behavioral guard. Do
not parse Java source text. If an existing reliable query-count mechanism is
available, assert one browse statement; otherwise verify the single composed
query during implementation review.

- [ ] **Step 2: Run the catalog integration test and observe RED**

```powershell
.\mvnw.cmd -Pacceptance -Dit.test=VerticalSlice2CustomerCatalogExternalIT verify
```

Expected: record accessors/constructor shapes fail because presentation is absent.

- [ ] **Step 3: Implement the set-based projection**

Add exact customer records:

```java
public record LocalizedSummary(String vi, String en) { }
public record ProductPresentation(LocalizedSummary summary) { }
```

Append nullable `ProductPresentation presentation` to `ProductSummary` and `ProductDetail` without removing or changing legacy fields.

In browse SQL, left join once:

```sql
LEFT JOIN product_presentation_revision presentation
  ON presentation.product_id = products.id
 AND presentation.status = 'PUBLISHED'
```

Select/group `summary_vi` and `summary_en` with the existing Product aggregation and map null to null. Add the same filtered join to the single-Product detail metadata query. Do not call Task 2 management service from customer reads.

- [ ] **Step 4: Run the catalog integration test and observe GREEN**

Run the Step 2 command. Expected: all old Catalog evidence tests and new presentation cases pass.

- [ ] **Step 5: Record a no-commit checkpoint**

Review the two-file diff and confirm no draft/retired metadata is serialized and browse has no N+1 path.

### Task 4: Idempotent three-present / three-null demo dataset

**Files:**
- Modify: `backend/src/main/java/com/shoecommerce/demo/DemoDataBootstrap.java`
- Modify: `backend/src/test/java/com/shoecommerce/demo/DemoDataBootstrapExternalIT.java`

**Interfaces:**
- Consumes: Task 1 schema, stable demo Product/account IDs.
- Produces: exactly three deterministic PUBLISHED pilot rows on a fresh demo database.

- [ ] **Step 1: Add failing demo bootstrap assertions**

After the first seed, query the six named Products and assert exactly:

```java
assertThat(pilotPresentations()).containsExactlyInAnyOrder(
    tuple("Court Classic", "PUBLISHED", APPROVED_COURT_VI, APPROVED_COURT_EN),
    tuple("Metro Runner", "PUBLISHED", APPROVED_METRO_VI, APPROVED_METRO_EN),
    tuple("Trail Form", "PUBLISHED", APPROVED_TRAIL_VI, APPROVED_TRAIL_EN));
assertThat(presentationCountFor("Court High", "Pace Knit", "Trail Edge")).isZero();
```

Run bootstrap again and assert count/content/public IDs are unchanged. Then modify one seeded summary directly to simulate operator-owned content, run bootstrap, and assert it is not rewritten.

- [ ] **Step 2: Run the focused demo integration test and observe RED**

```powershell
.\mvnw.cmd -Pacceptance -Dit.test=DemoDataBootstrapExternalIT verify
```

Expected: presentation table remains empty.

- [ ] **Step 3: Seed the exact approved content once**

Call `seedProductPresentations(now)` after accounts and catalog exist. Use `uuid("product-presentation-<product-key>-v1")`, resolve Product internal ID from its stable public ID, and use `manager.demo` as creator. Insert revision 1 PUBLISHED only when no history exists for that Product. Copy the three VI/EN strings verbatim from ADR-0038. Do not update an existing row and do not seed the null/control group.

- [ ] **Step 4: Run the demo integration test and observe GREEN**

Run Step 2. Expected: exact three rows, deterministic identities, unchanged second run and preserved operator edit.

- [ ] **Step 5: Record a no-commit checkpoint**

Review only the two Task 4 files and compare each literal string with ADR-0038.

### Task 5: Frontend contracts and API bindings

**Files:**
- Create: `frontend/src/product-presentation.ts`
- Create: `frontend/src/product-presentation.test.ts`
- Modify: `frontend/src/api.ts`

**Interfaces:**
- Consumes: Tasks 2 and 3 JSON contracts.
- Produces: customer/management types, locale selection helper and CSRF API methods.

- [ ] **Step 1: Write failing helper and request-binding tests**

Define fixtures and assert locale/null behavior plus exact request paths:

```ts
expect(presentationSummary({ summary: { vi: 'Tóm tắt', en: 'Summary' } }, 'vi-VN')).toBe('Tóm tắt')
expect(presentationSummary({ summary: { vi: 'Tóm tắt', en: 'Summary' } }, 'en')).toBe('Summary')
expect(presentationSummary(null, 'vi-VN')).toBeNull()

await api.saveProductPresentationDraft('product-1', {
  expectedDraftRevisionId: 'draft-1', expectedEntityVersion: 2,
  summaryVi: 'VI', summaryEn: 'EN',
})
expect(fetchMock.mock.calls[1][0]).toBe('/api/v1/operations/product-presentations/product-1/draft')
expect(fetchMock.mock.calls[1][1].method).toBe('PUT')
```

Also test publish body contains all three expected identifiers and a 409 is surfaced without automatic retry.

- [ ] **Step 2: Run the focused frontend test and observe RED**

```powershell
pnpm test -- product-presentation.test.ts
```

Expected: missing module/types/API methods.

- [ ] **Step 3: Add minimal types and helper**

Define:

```ts
export type LocalizedProductSummary = { vi: string; en: string }
export type ProductPresentation = { summary: LocalizedProductSummary }
export type ProductPresentationRevision = {
  id: string; revisionNumber: number; status: 'DRAFT' | 'PUBLISHED'
  entityVersion: number; summaryVi: string | null; summaryEn: string | null
  createdAt: string; publishedAt: string | null; retiredAt: string | null
}
export type ProductPresentationManagementState = {
  productId: string
  draft: ProductPresentationRevision | null
  published: ProductPresentationRevision | null
}
import type { Locale } from './i18n'
export const presentationSummary = (value: ProductPresentation | null, language: Locale) =>
  value ? value.summary[language === 'vi-VN' ? 'vi' : 'en'] : null
```

Extend `ProductSummary` and `ProductDetail` with `presentation: ProductPresentation | null`. Add `productPresentationState`, `saveProductPresentationDraft` and `publishProductPresentation` to `api`, using existing CSRF/request helpers.

- [ ] **Step 4: Run the focused frontend test and observe GREEN**

Run Step 2. Expected: helper and request contract tests pass.

- [ ] **Step 5: Record a no-commit checkpoint**

Run `git diff --check` for the three Task 5 files. Confirm customer types contain no management fields.

### Task 6: Shared presentation rendering on cards and PDP

**Files:**
- Create: `frontend/src/components/ProductPresentationSummary.vue`
- Modify: `frontend/src/views/CatalogView.vue`
- Modify: `frontend/src/views/ProductView.vue`
- Modify: `frontend/src/cart-flow.test.ts`
- Modify: `frontend/src/i18n.ts`
- Modify: `frontend/src/styles.css`

**Interfaces:**
- Consumes: Task 5 customer type/helper and current reactive locale.
- Produces: one shared null-safe rendering component used by customer and preview surfaces.

- [ ] **Step 1: Add failing shared-renderer and hierarchy tests**

Use Vue SSR for the component and the existing in-memory renderer for card/PDP:

```ts
const published = { summary: { vi: 'Bản VI', en: 'English summary' } }
setLocale('en')
expect(await renderToString(createSSRApp(ProductPresentationSummary, { presentation: published })))
  .toContain('English summary')
expect(await renderToString(createSSRApp(ProductPresentationSummary, { presentation: null })))
  .not.toContain('<p')
```

Add Catalog/PDP cases for one presented Product and one null Product. Assert the card uses a visual two-line class only when content exists; the PDP shows the full summary after `.product-decision`; null state has no summary class/heading. Preserve all existing no-default-size, price-before-selection and CTA-lock assertions.

- [ ] **Step 2: Run focused rendering tests and observe RED**

```powershell
pnpm test -- product-presentation.test.ts cart-flow.test.ts
```

Expected: component and rendered content are absent.

- [ ] **Step 3: Implement the shared renderer**

`ProductPresentationSummary.vue` accepts `presentation: ProductPresentation | null` and optional `compact: boolean`. It computes the current authoritative locale value and renders one `<p>` only when non-null:

```vue
<p v-if="summary" class="product-presentation-summary" :class="{ compact }">{{ summary }}</p>
```

Do not copy Catalog facts or derive a fallback inside the component.

- [ ] **Step 4: Integrate cards and PDP**

On cards, render the component after existing taxonomy with `compact`. On PDP, render it in a distinct evaluation region after the closing `.product-decision` section and before fit evidence, without `compact`. Do not add a heading for the single summary and do not alter the Decision Cluster markup order.

Add CSS line clamp only to `.product-presentation-summary.compact`. Do not set a fixed/minimum height on cards or create an empty wrapper for null content. Add only interface labels needed by the management surface; approved Product copy remains server data, never `i18n.ts`.

- [ ] **Step 5: Run focused rendering tests and observe GREEN**

Run Step 2. Expected: VI/EN, null state and existing purchase-flow tests pass.

- [ ] **Step 6: Record a no-commit checkpoint**

Review the Task 6 diff and confirm price, selection, availability and Add to cart precede presentation in DOM order.

### Task 7: Minimal ProductPresentation management UI

**Files:**
- Create: `frontend/src/views/ProductPresentationsView.vue`
- Modify: `frontend/src/product-presentation.test.ts`
- Modify: `frontend/src/main.ts`
- Modify: `frontend/src/App.vue`
- Modify: `frontend/src/i18n.ts`
- Modify: `frontend/src/styles.css`

**Interfaces:**
- Consumes: Task 5 API/types, Task 6 shared renderer, existing `api.products()` selector and `STOREFRONT_MANAGE` session permission.
- Produces: `/operations/product-presentations` edit/preview/publish workflow.

- [ ] **Step 1: Add failing permission, preview and conflict tests**

Assert route/menu ownership from raw source and shared preview from SSR/in-memory rendering:

```ts
expect(mainSource).toContain("path: '/operations/product-presentations'")
expect(mainSource).toContain("permission: 'STOREFRONT_MANAGE'")
expect(appSource).toContain('to="/operations/product-presentations"')
expect(viewSource).toContain('<ProductPresentationSummary')
expect(viewSource).not.toContain('v-html')
```

Mock management state and verify: selecting a Product loads its state; Edit sends the null-expectation create command when no draft exists; Save sends exact draft ID/version; Publish refuses unsaved local changes and sends the previewed ID/version/current publication ID; 409 keeps local edits and offers reload.

- [ ] **Step 2: Run focused management tests and observe RED**

```powershell
pnpm test -- product-presentation.test.ts
```

Expected: route/view/workflow assertions fail.

- [ ] **Step 3: Implement the bounded management view**

Build only:

```text
Product selector -> current published/draft status -> VI/EN textareas
-> shared local preview -> Save draft -> Publish exact saved draft
```

Use `api.products()` for the pilot selector. On Edit without a draft, call PUT with all four nullable fields null, then populate the editor from the returned copied/empty draft. Disable mutation controls while a request is active. Track dirty state; publication is disabled until the saved draft matches the preview. Map locale/length errors to inline copy and 409 to a preserve-edits recovery state.

- [ ] **Step 4: Add route, menu, translations and bounded styling**

Register route metadata with `STOREFRONT_MANAGE`; show the menu only for that permission. Reuse current operations form, status and preview styles. Add no product CRUD, history browser, scheduling, media, rich text, arbitrary blocks or drag/drop.

- [ ] **Step 5: Run focused management tests and observe GREEN**

Run Step 2. Expected: permission, exact command evidence, preview reuse and conflict recovery pass.

- [ ] **Step 6: Record a no-commit checkpoint**

Review the six Task 7 files and confirm preview does not publish or duplicate customer markup.

### Task 8: Full automated verification and dirty-tree audit

**Files:**
- Verify all files above; modify only a listed owning file when a failing check demonstrates a concrete defect.

**Interfaces:**
- Consumes: Tasks 1–7.
- Produces: fresh backend, SQL Server, frontend, build and scope evidence.

- [ ] **Step 1: Run backend unit verification**

```powershell
cd backend
.\mvnw.cmd test
```

Expected: all Surefire tests pass.

- [ ] **Step 2: Run targeted SQL Server verification**

```powershell
.\mvnw.cmd -Pacceptance '-Dit.test=FlywayV28UpgradeExternalIT,ProductPresentationExternalIT,VerticalSlice2CustomerCatalogExternalIT,DemoDataBootstrapExternalIT' clean verify
```

Expected: migration, lifecycle/concurrency, projection and demo suites pass against configured SQL Server without environment-caused skips.

- [ ] **Step 3: Run the full SQL Server acceptance profile**

```powershell
.\mvnw.cmd -Pacceptance clean verify
```

Expected: all Failsafe integration suites pass.

- [ ] **Step 4: Run complete frontend verification**

```powershell
cd ..\frontend
pnpm test
pnpm typecheck
pnpm build
```

Expected: all Vitest suites, Vue TypeScript checks and Vite production build pass.

- [ ] **Step 5: Audit scope and preserved dirty state**

Capture `git status --short`, `git diff --stat`, and focused diffs for every plan file. Compare against the baseline captured before this phase. Confirm no staging/commit, search synonym, new asset, product claim, permission, PIM/CMS field or unrelated cleanup was introduced.

### Task 9: Real-browser validation and blind human pilot

**Files:**
- No source edits unless browser evidence reveals a defect owned by Tasks 5–7; fixes return through a failing regression test first.

**Interfaces:**
- Consumes: verified demo stack and exact six pilot Products.
- Produces: desktop/mobile usability evidence and an independent merchandising result.

- [ ] **Step 1: Start a fresh disposable demo environment**

Follow `docs/DEMO.md`: run the backend with the `demo` profile against a fresh disposable SQL Server database and run `pnpm dev`. Verify database evidence before browsing: exactly three PUBLISHED ProductPresentation rows for Court Classic, Metro Runner and Trail Form, and zero rows for Court High, Pace Knit and Trail Edge.

- [ ] **Step 2: Validate the management workflow in a real browser**

As `manager.demo`, test desktop and approximately 390 px:

1. open Product Presentations;
2. select a published pilot Product and confirm current VI/EN state;
3. create a copied draft without changing the publication;
4. save an incomplete locale and confirm draft save succeeds but publish is blocked;
5. complete both locales, preview VI and EN using the shared renderer, save and publish;
6. open a stale second editor and confirm publish conflicts rather than publishing unseen edits;
7. restore the canonical test database by recreating the disposable demo database, not by rewriting published content.

- [ ] **Step 3: Validate customer rendering and hierarchy**

At desktop and approximately 390 px, inspect all six Products in VI and EN. Record:

- exact three cards/PDPs render approved content and exact three render no summary element;
- no fixed empty summary space exists on null cards;
- card clamp does not truncate stored/PDP content;
- price, size, availability and Add to cart remain before presentation;
- Fit Assistant remains optional;
- mixed card heights are either acceptable or supported by a specific observed defect before any alignment fix;
- search `chạy` remains deferred and no alias was added.

- [ ] **Step 4: Run two independent, unanchored reviewer sessions**

Do not tell either reviewer the group membership, expected result, approved wording rationale or the other reviewer's conclusions. Give each reviewer the same neutral task:

> Browse the storefront naturally across Court, Running and Trail products. Stop if you would realistically leave. After inspecting three or four products, without reopening them, explain which was which, why you might open or choose each one, and what evidence—not just image or price—made them different.

Reviewer A adopts a need-based comparison shopper on desktop. Reviewer B browses naturally on approximately 390 px mobile as a returning research shopper. Both may express subjective reactions and may stop early.

For each reviewer record, without interpretation during the session:

- products opened and browsing order;
- whether interest lasted longer;
- recalled distinctions;
- reason to open/choose;
- reliance on price/image;
- confusion, distrust or genericness;
- whether each Product remained distinguishable after pages were closed.

For every recalled distinction, record exactly one primary observed source and
any secondary source from: `IMAGE`, `PRICE`, `CATEGORY / COLLECTION`,
`FIT GUIDANCE`, `PRODUCT PRESENTATION SUMMARY`, `OTHER OBSERVED EVIDENCE`.
The presentation and null groups are confounded because only the presentation
group has authoritative fit profiles; group-level recall differences cannot be
claimed as causal evidence for ProductPresentation.

- [ ] **Step 5: Synthesize without majority voting**

Only after both reviews finish, compare:

1. findings both independently noticed;
2. findings noticed by only one;
3. subjective taste differences;
4. strong cross-review UX signals;
5. technically correct UI that still feels poor to use or look at.

Explain disagreements from persona, device, browsing order or attention when evidence supports it. Compare presentation and null groups without revealing them retroactively as a success cue. Ask whether the summary itself contributed a distinction beyond image, price, category/collection and Fit Guidance.

- [ ] **Step 6: Assign the honest final status**

Use exactly one:

- `PILOT PASSED` only if engineering works and reviewers notice or remember meaningful merchandising value attributable to the published summary itself;
- `ENGINEERING PASSED — CONTENT DEPTH INSUFFICIENT` when architecture/rendering work but conservative summaries do not materially improve differentiation;
- `PILOT FAILED` when architecture or experience itself fails.

Do not rewrite the approved summaries after review. If content depth is insufficient, identify missing authoritative fact classes—such as verified construction, material, intended use, cushioning geometry or terrain capability—without inventing values.
