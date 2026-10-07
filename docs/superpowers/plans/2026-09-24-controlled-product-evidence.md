# Controlled Product Evidence Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Persist and expose the approved five-field Product evidence matrix, add safe Catalog management, and make the six comparison Products factually distinguishable on catalog and PDP surfaces.

**Architecture:** Add five nullable enum-backed columns to the existing Catalog Product aggregate, update them atomically under `CATALOG_MANAGE` with Product optimistic locking and the existing audit writer, and project them additively to public storefront DTOs. Reuse the existing FitProfile for fit/width, use one general Product management page, and render structured facts without generated prose.

**Tech Stack:** Java 25, Spring Boot, JPA/JdbcTemplate, Flyway, SQL Server, Vue 3, TypeScript, Vue Router, Vitest.

**Spec:** `docs/ADR/0039-controlled-product-evidence.md`

## Global Constraints

- Preserve HEAD `682999d`, branch `codex/remediation-audit-20260913`, and all intentional uncommitted work.
- Do not stage, commit, push, reset, clean, or modify unrelated files.
- `NULL` means `UNKNOWN / NOT PROVIDED`; never infer or fabricate evidence.
- Persist only the six values approved in ADR-0039; all other demo Products remain without evidence.
- Fit/width remain FitProfile-owned; color remains Variant-owned; category/collection remain taxonomy.
- Reuse `AuditWriter`; add no audit infrastructure or dependency.
- Keep ProductPresentation secondary and preserve its publication lifecycle.
- Use existing error contracts, server authorization, SQL Server-compatible Flyway, VI/EN labels, and responsive accessibility rules.
- Follow RED -> observed failure -> minimal GREEN for every behavior change.

## Review Focus

- All-null versus partially populated Product evidence must serialize as `null` versus a nullable-field object without fallbacks.
- A stale management save must preserve server state and return the existing `409` conflict contract.
- Unsupported database codes and malformed API enum values must fail at their respective boundaries.
- Products without complete FitProfiles must not receive copied or inferred fit summaries.
- Mobile evidence must not displace size selection, availability, or Add to Cart below an excessive specification block.

---

### Task 1: Domain value object and SQL Server migration

**Files:**
- Create: `backend/src/main/java/com/shoecommerce/catalog/ProductEvidence.java`
- Create: `backend/src/main/resources/db/migration/V29__controlled_product_evidence.sql`
- Create: `backend/src/test/java/com/shoecommerce/FlywayV29UpgradeExternalIT.java`
- Modify: `backend/src/main/java/com/shoecommerce/catalog/Product.java`

**Interfaces:**
- Produces: `ProductEvidence`, its five enums, `Product.evidence()`, and `Product.replaceEvidence(ProductEvidence, long)`.

- [ ] Write `FlywayV29UpgradeExternalIT` first. Upgrade a V28 fixture, assert all five columns are nullable and null for existing rows, write every allowed code, and assert one unsupported code per column is rejected by SQL Server.
- [ ] Run `backend/mvnw.cmd -Dtest=FlywayV29UpgradeExternalIT test` and observe failure because V29 and the columns do not exist.
- [ ] Add `ProductEvidence` as a record with nullable enum fields and `boolean isEmpty()`; use exact ADR-0039 names and values.
- [ ] Add five nullable `@Enumerated(EnumType.STRING)` Product columns and an atomic replacement method that compares `expectedEntityVersion` to `@Version`, throwing `BusinessConflictException("PRODUCT_EVIDENCE_CONFLICT", ...)` on mismatch.
- [ ] Add V29 columns and individual `CHECK` constraints with exact enum codes; do not seed values in Flyway.
- [ ] Re-run the V29 test and the existing V28 upgrade test; both must pass.

### Task 2: Authorized Catalog management contract

**Files:**
- Modify: `backend/src/main/java/com/shoecommerce/catalog/CatalogService.java`
- Modify: `backend/src/main/java/com/shoecommerce/catalog/CatalogController.java`
- Modify: `backend/src/test/java/com/shoecommerce/catalog/VerticalSlice2ExternalIT.java`

**Interfaces:**
- Produces: `GET /api/v1/catalog/products/{productId}/evidence` and `PUT /api/v1/catalog/products/{productId}/evidence`.
- Produces DTO: `{ productId, productName, category, collection, entityVersion, evidence, fitSummary, colors }`.
- Consumes: Task 1 `ProductEvidence` and Product optimistic version.

- [ ] Add integration tests proving `CATALOG_MANAGE` can read and atomically replace evidence, a customer gets `403`, unknown Product gets `404`, malformed enum gets `400`, and a stale expected version gets `409` without overwriting the winning values.
- [ ] Assert the successful command creates one existing `AuditWriter` event with before/after evidence details; do not assert implementation-private calls.
- [ ] Run the focused test methods and observe the endpoint-not-found failures.
- [ ] Implement CatalogService management read/update methods with application-boundary authorization, Product repository lookup, one transaction, optimistic version validation, and existing audit append.
- [ ] Add typed request/response records and routes to `CatalogController`; use full-replacement `PUT` and the existing API exception contract.
- [ ] Re-run `VerticalSlice2ExternalIT`; all tests must pass.

### Task 3: Public storefront projection and null semantics

**Files:**
- Modify: `backend/src/main/java/com/shoecommerce/catalog/StorefrontCatalogService.java`
- Modify: `backend/src/test/java/com/shoecommerce/catalog/VerticalSlice2CustomerCatalogExternalIT.java`

**Interfaces:**
- Produces public `ProductEvidenceView` on `ProductSummary` and `ProductDetail`.
- Produces list-only `FitSummary(fitTendency, widthProfile)` from an existing complete supported FitProfile.

- [ ] Add tests for: all five null -> `evidence: null`; one known field -> object with four nulls; all five -> exact codes; no derivation from category/name; complete fit -> list `fitSummary`; absent/incomplete fit -> `fitSummary: null`.
- [ ] Extend the existing HTTP contract assertions to prove `evidence` exists additively and exact inventory quantities remain absent.
- [ ] Run focused customer catalog tests and observe missing-field failures.
- [ ] Extend browse/detail SQL with the five Product columns. Centralize `evidence(...)` conversion so both paths share exact all-null behavior.
- [ ] Add a browse-only fit projection using the ADR-0037 supported enums/completeness gate; do not persist or place fit inside ProductEvidence.
- [ ] Re-run `VerticalSlice2CustomerCatalogExternalIT`; all tests must pass.

### Task 4: Approved idempotent demo matrix

**Files:**
- Modify: `backend/src/main/java/com/shoecommerce/demo/DemoDataBootstrap.java`
- Modify: `backend/src/test/java/com/shoecommerce/demo/DemoDataBootstrapExternalIT.java`

**Interfaces:**
- Consumes: V29 columns and the exact ADR-0039 matrix.

- [ ] Add a failing test that seeds twice, selects all five fields for all Products, and asserts exactly the six approved rows while every other demo Product has all five columns null.
- [ ] Mutate one approved row between runs and define the seed ownership behavior explicitly: demo bootstrap restores the approved matrix because these six rows are controlled demo fixtures, unlike operator-owned ProductPresentation content.
- [ ] Run the two focused demo tests and observe missing evidence failures.
- [ ] Extend `ProductSeed` with optional evidence and update the existing idempotent Product update to write those five columns. Encode only the six approved rows.
- [ ] Re-run `DemoDataBootstrapExternalIT`; all tests must pass and existing presentation-preservation behavior must remain green.

### Task 5: Vue contract and general Product management surface

**Files:**
- Create: `frontend/src/product-evidence.ts`
- Create: `frontend/src/product-evidence.test.ts`
- Create: `frontend/src/views/ProductManagementView.vue`
- Create: `frontend/src/product-management.test.ts`
- Modify: `frontend/src/api.ts`
- Modify: `frontend/src/main.ts`
- Modify: `frontend/src/App.vue`
- Modify: `frontend/src/i18n.ts`
- Modify: `frontend/src/styles.css`

**Interfaces:**
- Produces TypeScript code unions, localized option metadata, management DTOs, and API methods matching Tasks 2-3.
- Produces route `/operations/products` guarded by `CATALOG_MANAGE`.

- [ ] Test exact code unions through option arrays, `null` as “Unknown / not provided,” full replacement request shape, server-state reset after save, and conflict preservation/reload behavior.
- [ ] Add component tests for permission-visible navigation, controlled selects, read-only taxonomy/colors/fit context, successful save, and `409` conflict messaging.
- [ ] Run the two focused Vitest files and observe missing-module/component failures.
- [ ] Add API types/methods and the small `product-evidence.ts` vocabulary/label helpers; no generated prose.
- [ ] Implement one general Product management page using the existing ruled operations vocabulary. Save all five fields atomically and require explicit reload after conflict.
- [ ] Add route/nav visibility under `CATALOG_MANAGE`, VI/EN translations, and only the CSS needed by this page.
- [ ] Run focused tests, frontend typecheck, and existing ProductPresentation management tests; all must pass.

### Task 6: Catalog and PDP evidence hierarchy

**Files:**
- Create: `frontend/src/components/ProductEvidenceFacts.vue`
- Modify: `frontend/src/views/CatalogView.vue`
- Modify: `frontend/src/views/ProductView.vue`
- Modify: `frontend/src/cart-flow.test.ts`
- Modify: `frontend/src/styles.css`
- Modify: `frontend/src/i18n.ts`

**Interfaces:**
- Consumes: public evidence and FitProfile projections from Task 3.
- Produces one reusable semantic fact renderer with compact catalog and progressive PDP modes.

- [ ] Add failing render tests showing Court Classic/Court High, Metro Runner/Pace Knit and Trail Form/Trail Edge remain distinguishable from structured facts; unknown fit is omitted; no claim comes from name/category/image.
- [ ] Add PDP tests proving a compact high-signal group appears before variant selection, the complete fact set is progressively disclosed, and the existing fit table/assistant remain secondary without auto-selecting size.
- [ ] Add tests proving catalog cards no longer use ProductPresentation prose as comparison content and PDP retains it as secondary editorial context.
- [ ] Run focused `cart-flow.test.ts` cases and observe missing-fact failures.
- [ ] Implement `ProductEvidenceFacts.vue` using `<dl>`/`<details>` semantics, localized code labels, and omission of null facts.
- [ ] Render at most three high-signal facts on cards. On PDP keep the compact facts near the purchase decision and detailed facts collapsible so mobile size/CTA remain prominent.
- [ ] Re-run focused tests and the full frontend suite.

### Task 7: Navigation, responsive, and theme hardening

**Files:**
- Create: `frontend/src/navigation.ts`
- Create: `frontend/src/navigation.test.ts`
- Modify: `frontend/src/main.ts`
- Modify: `frontend/src/styles.css`

**Interfaces:**
- Produces Vue Router scroll behavior that returns `savedPosition` for browser history, hash positioning for hashes, and top reset for new navigation.

- [ ] Write navigation tests for saved browser-back position, hash navigation, and ordinary top reset; observe failure before the helper exists.
- [ ] Implement the pure scroll behavior helper and wire it into Vue Router without route-specific state hacks.
- [ ] Set explicit `color-scheme: light` because the current product has no approved dark palette; verify system dark mode cannot create a broken pseudo-dark theme.
- [ ] Run navigation tests, frontend typecheck and production build.
- [ ] Inspect real 1440, 1024, 768 and 390 viewports for catalog, PDP and Product management: no horizontal overflow, readable density, visible focus, 44px targets, and size/CTA prominence. Perform one batched CSS correction pass, then one confirmation pass only.

### Task 8: Correctness backlog audit and confirmed fixes only

**Files:**
- Inspect: `backend/src/main/java/com/shoecommerce/reporting/ReportingService.java`
- Inspect: `backend/src/test/java/com/shoecommerce/payment/VerticalSlice7ReportingExternalIT.java`
- Inspect: `frontend/src/views/PaymentResultView.vue`, `frontend/src/session.ts`, `frontend/src/cart.ts`
- Modify only files for defects reproduced by an observed failing test.

**Interfaces:**
- No speculative production interface.

- [ ] Run current reporting tests that cover allocated item discounts, shipping revenue, successful void allocation and net sales. Record each result in the execution ledger.
- [ ] Run current frontend tests for stale Product requests, payment result navigation, cart/account storage events, logout navigation and search state.
- [ ] Reproduce browser-back catalog scroll naturally using Task 7 behavior.
- [ ] For each confirmed defect only, invoke systematic debugging, add the smallest failing regression test at the public seam, observe RED, apply the root-cause fix, and observe GREEN. Record non-reproducible findings without source changes.
- [ ] Re-run the complete affected suite after any fix.

### Task 9: Full verification and human acceptance

**Files:**
- Modify: `docs/PRODUCT_EVIDENCE_EXECUTION_LEDGER.md`
- Do not modify product code unless verification reveals a confirmed defect with a failing regression test.

**Interfaces:**
- Produces the evidence for the required final report sections A-K.

- [ ] Run backend unit tests and record exact counts.
- [ ] Run the full SQL Server integration suite, including Flyway V29 upgrade, and record exact pass/fail/skip counts.
- [ ] Run the full frontend suite, typecheck and production build; record exact counts.
- [ ] Confirm ProductPresentation lifecycle regressions remain green and only six demo Products contain evidence.
- [ ] Perform one human-style funnel pass for each required persona: first-time shopper, typical shopper, desktop comparison shopper, 390px mobile shopper, and direct-PDP ad visitor.
- [ ] For each persona independently answer whether they can explain why one approved Product fits their need better than another using site facts rather than imagery.
- [ ] Audit every visible evidence phrase back to one approved code or existing FitProfile value; any unsupported phrase is a release blocker.
- [ ] Run the Impeccable detector once over changed frontend targets and address Critical/Important findings only through tested fixes.
- [ ] Use verification-before-completion and code-review-and-quality before issuing the exact A-K final report and verdict.
