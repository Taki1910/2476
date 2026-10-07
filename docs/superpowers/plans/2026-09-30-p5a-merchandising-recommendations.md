# P5A Merchandising Recommendations Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add deterministic, evidence-led storefront recommendations, a safe clone-from-published workflow, and an operational manager UI without changing publication semantics or adding a migration.

**Architecture:** A protected `MerchandisingRecommendationService` assembles catalog, global enabled-location availability, immutable sales, Product-scoped promotion, presentation readiness, and HOME placement projections. Existing draft/save/publish remains authoritative; frontend recommendation actions change only local draft state. Public Hero keeps its route but loses internal metrics and candidates.

**Tech Stack:** Java 21, Spring Boot, JdbcTemplate, SQL Server, JUnit/AssertJ, Vue 3, TypeScript, Vitest, Vite.

**Spec:** `C:/Users/asus/.codex/attachments/a2d087ae-ba79-4e28-94d6-46ee23c5d07d/Văn bản đã dán.txt`

## Global Constraints

- Preserve the current dirty branch and worktree; no commit, stage, reset, clean, rebase, push, or merge.
- P5A is deterministic, explainable, manager-controlled, and authorized by `STOREFRONT_MANAGE`.
- No LLM/provider, new dependency, migration, media redesign, history browser, branch-specific HOME, or deferred recommendation type.
- Inventory evidence aggregates only enabled locations under enabled branches, independent of staff assignment.
- Recommendation never mutates a draft or publishes; Apply changes frontend-local state only.
- Existing draft/publish version fences, SERIALIZABLE publication, audit, preview, and section model remain intact.

## Review Focus

- Partial item voids must not produce invented unit counts or bestseller ranking.
- Search/filter/page validation must fail safely and deterministically at boundary values.
- Scheduled/order/shipping promotions must never be labeled active Product recommendations.
- Clone must reject stale/missing publication and preserve source immutability.
- Public Hero serialization must contain no sales, inventory, recommendation, or candidate evidence.

---

### Task 1: Clone current published HOME into a new draft

**Files:**
- Modify: `backend/src/main/java/com/shoecommerce/merchandising/StorefrontMerchandisingService.java`
- Modify: `backend/src/main/java/com/shoecommerce/merchandising/StorefrontManagementController.java`
- Modify: `backend/src/test/java/com/shoecommerce/merchandising/StorefrontMerchandisingExternalIT.java`

**Interfaces:**
- Consumes: current `RevisionState`, section tables, HOME lock and `STOREFRONT_MANAGE` policy.
- Produces: `clonePublished(actor, ClonePublished(expectedPublishedRevisionId)) -> RevisionState` and `POST /api/v1/operations/storefront/homepage/draft-from-published`.

- [ ] Write failing integration tests for successful exact copy, missing publication, existing draft, stale publication, permissions, section/member order, and immutable source.
- [ ] Run the focused external test and confirm RED when SQL Server is available; otherwise record the environment skip and continue with compile-backed implementation.
- [ ] Implement the SERIALIZABLE clone command by locking HOME, re-reading current state, inserting a new revision and copied sections through the existing section writer.
- [ ] Run focused backend tests/compile and confirm GREEN or document SQL-gated status.

### Task 2: Protected merchandising candidate read model

**Files:**
- Create: `backend/src/main/java/com/shoecommerce/merchandising/MerchandisingRecommendationService.java`
- Create: `backend/src/main/java/com/shoecommerce/merchandising/MerchandisingRecommendationController.java`
- Create: `backend/src/test/java/com/shoecommerce/merchandising/MerchandisingRecommendationServiceTest.java`
- Create: `backend/src/test/java/com/shoecommerce/merchandising/MerchandisingRecommendationExternalIT.java`

**Interfaces:**
- Consumes: catalog Product/Variant/price, enabled branch/location inventory, immutable order/payment/POS/void evidence, published Product presentation, Product-scoped promotion revisions, HOME draft/published sections.
- Produces: `candidates(actor, query, type, page, size) -> CandidatePage` at `GET /api/v1/operations/storefront/candidates`.

- [ ] Write failing unit tests for filter/rank/tie-break/paging and incomplete partial-void evidence.
- [ ] Run focused unit tests and confirm RED.
- [ ] Implement normalized input validation, safe projections, deterministic reason codes, readiness classes, placement evidence, and on-demand paging.
- [ ] Write SQL-backed tests for 30-day/future boundaries, online/POS facts, full/partial void, global availability, lifecycle/price readiness, active/scheduled/non-Product promotions, placement, and permissions.
- [ ] Run focused unit and SQL-gated tests; confirm GREEN or record SQL environment skip.

### Task 3: Remove internal evidence from public Hero

**Files:**
- Modify: `backend/src/main/java/com/shoecommerce/catalog/StorefrontCatalogService.java`
- Modify: `backend/src/test/java/com/shoecommerce/catalog/VerticalSlice2CustomerCatalogExternalIT.java`
- Modify: `frontend/src/api.ts`
- Modify: `frontend/src/api.test.ts`
- Modify: `docs/DEMO.md`
- Modify: `docs/FRONTEND_ARCHITECTURE.md`

**Interfaces:**
- Consumes: customer-safe Product merchandising fields only.
- Produces: stable `/api/v1/storefront/hero` route with selected public Product summaries and no candidate/metric fields.

- [ ] Write failing public/private projection assertions proving sensitive field absence.
- [ ] Run focused backend/frontend tests and confirm RED.
- [ ] Replace public Hero DTO/query with customer-safe selection and update in-repo contracts/docs.
- [ ] Run focused tests and confirm GREEN.

### Task 4: Frontend API and local Apply-to-draft behavior

**Files:**
- Modify: `frontend/src/api.ts`
- Modify: `frontend/src/merchandising.ts`
- Modify: `frontend/src/storefront-management.test.ts`

**Interfaces:**
- Consumes: protected `CandidatePage`, manager-safe promotions, clone endpoint, current draft helpers.
- Produces: typed API methods and `applyCandidateToDraft` that changes only one local PRODUCT_COLLECTION.

- [ ] Write failing tests for candidate query encoding, clone CSRF/version payload, local Apply, duplicate/full collection behavior, removal, and no publish call.
- [ ] Run focused Vitest file and confirm RED.
- [ ] Implement minimal types/API/helpers.
- [ ] Run focused Vitest file and confirm GREEN.

### Task 5: Operational manager discovery and selector UX

**Files:**
- Modify: `frontend/src/views/StorefrontManagementView.vue`
- Modify: `frontend/src/styles.css`
- Modify: `frontend/src/i18n.ts`
- Modify: `frontend/src/storefront-management.test.ts`
- Modify: `frontend/public/products/README.md`

**Interfaces:**
- Consumes: Task 4 API/helpers and incumbent Storefront editor/preview.
- Produces: searchable/filterable evidence rows, Apply-to-draft, clone-from-live, rich Product/Promotion selection, explicit active/scheduled labels, and responsive keyboard-operable UI.

- [ ] Write failing behavioral/source-render tests for search/filter, evidence/warnings/placements, Apply/manual removal, clone conflict, selectors, accessibility copy, and publication separation.
- [ ] Run focused frontend tests and confirm RED.
- [ ] Implement the incumbent square ruled-surface design, natural VI/EN copy, loading/error/empty/focus/disabled states, and 390px layout.
- [ ] Correct verified media README count only; do not alter runtime media.
- [ ] Run focused tests, typecheck and production build; confirm GREEN.
- [ ] Run the Impeccable detector once over changed UI targets and resolve material findings in one pass.

### Task 6: Regression and evidence inventory

**Files:**
- Modify only files required by failures attributable to P5A.

**Interfaces:**
- Consumes: Tasks 1–5.
- Produces: fresh verification evidence and completion report with SQL/browser limitations clearly separated.

- [ ] Run full backend unit suite.
- [ ] Run SQL-backed external suites when configured.
- [ ] Run full frontend suite, typecheck, and production build.
- [ ] Run browser QA against the actual backend/database when available; never substitute mock evidence.
- [ ] Run `git diff --check`, changed-file inventory, and verify migration count remains zero.
- [ ] Perform a fixed-baseline self-review because this task may not create or use subagents without explicit user authorization.

