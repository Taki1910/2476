# P1 Search, Discovery & Product Experience Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Deliver deterministic storefront discovery, truthful zero-result recovery, color-first product selection, and compact cart feedback without changing persistence or P0 commerce behavior.

**Architecture:** Add one pure Java discovery interpreter/ranker used by the existing JDBC storefront read service and one variant-option projector used by product detail. Keep the current REST list endpoint stable and add a discovery response for the catalog. The Vue storefront consumes the read models while pricing, inventory, cart, and checkout remain unchanged.

**Tech Stack:** Java 25, Spring Boot JDBC/REST, JUnit 5, Vue 3, TypeScript, Vitest, existing CSS and i18n utilities.

**Spec:** `docs/superpowers/specs/2026-09-27-p1-search-discovery-product-experience-design.md`

## Global Constraints

- No database migration, persistence entity, LLM, embedding service, or new dependency.
- Preserve exact stored color labels, including composite values.
- Suggestions must be published, priced, and have at least one available variant.
- SKU support remains backend-only unless the input is clearly SKU-shaped.
- Do not modify checkout, delivery, VNPAY, promotion, inventory reservation, or fulfillment behavior.
- Existing P0 work is an uncommitted user-owned baseline; do not overwrite or accidentally commit unrelated changes.

## Review Focus

- `size 41` and SKU-shaped input must never be parsed as a price.
- Weak generic tokens must not outrank an exact or strong product-name match.
- Empty exact results must stay empty while suggestions contain only available products.
- A color change must clear a stale resolved variant while preserving only the requested size.
- Composite stored colors must remain a single visible and accessible option.

---

### Task 1: Deterministic storefront discovery

**Files:**
- Create: `backend/src/main/java/com/shoecommerce/catalog/CatalogDiscovery.java`
- Create: `backend/src/test/java/com/shoecommerce/catalog/CatalogDiscoveryTest.java`
- Modify: `backend/src/main/java/com/shoecommerce/catalog/StorefrontCatalogService.java`
- Modify: `backend/src/main/java/com/shoecommerce/catalog/StorefrontController.java`
- Modify: `backend/src/test/java/com/shoecommerce/catalog/VerticalSlice2CustomerCatalogExternalIT.java`

**Interfaces:**
- Produces: `CatalogDiscovery.interpret(String)`, `CatalogDiscovery.rank(QueryIntent, List<SearchDocument>)`, and `StorefrontCatalogService.discover(String)` returning separate results, suggestions, and interpreted concepts.
- Consumes: existing `ProductSummary`, published variant/pricing SQL, evidence fields, and availability calculation.

- [ ] Add focused failing unit tests for accent-insensitive color/category search, `giày trắng dưới 2 triệu`, accentless input, conservative price forms, `size 41`, exact SKU shape, strong-name precedence, empty results, and eligible suggestions.
- [ ] Run `backend/mvnw.cmd -q -Dtest=CatalogDiscoveryTest test` and confirm failures are caused by missing discovery behavior.
- [ ] Implement the smallest explicit normalization, alias, price-intent, scoring, and suggestion logic using Java standard library only.
- [ ] Re-run the focused unit tests and confirm they pass.
- [ ] Add the failing REST/JDBC contract assertions for the discovery endpoint without weakening the existing browse endpoint.
- [ ] Implement `GET /api/v1/storefront/discovery?q=` and the minimal storefront candidate projection.
- [ ] Run `backend/mvnw.cmd -q -Dtest=CatalogDiscoveryTest test` plus the catalog external test when SQL Server is available; record a skipped external suite truthfully when unavailable.

### Task 2: Product color/size read projection

**Files:**
- Create: `backend/src/main/java/com/shoecommerce/catalog/ProductOptionProjector.java`
- Create: `backend/src/test/java/com/shoecommerce/catalog/ProductOptionProjectorTest.java`
- Modify: `backend/src/main/java/com/shoecommerce/catalog/StorefrontCatalogService.java`
- Modify: `backend/src/test/java/com/shoecommerce/catalog/VerticalSlice2CustomerCatalogExternalIT.java`

**Interfaces:**
- Produces: grouped color options with nested size options, resolved variant details, availability, price, and same-size available alternative colors.
- Consumes: the existing ordered `List<VariantView>` from product detail.

- [ ] Add failing tests for color grouping, size availability, one composite color option, same-size alternatives, and no alternative when all matching sizes are unavailable.
- [ ] Run `backend/mvnw.cmd -q -Dtest=ProductOptionProjectorTest test` and confirm expected failures.
- [ ] Implement the pure projector and append its projection to `ProductDetail`; retain flat variants only for Fit Assistant compatibility.
- [ ] Re-run focused tests and the existing catalog test set.

### Task 3: Catalog discovery experience

**Files:**
- Modify: `frontend/src/api.ts`
- Modify: `frontend/src/api.test.ts`
- Modify: `frontend/src/views/CatalogView.vue`
- Modify: `frontend/src/cart-flow.test.ts`
- Modify: `frontend/src/i18n.ts`
- Modify: `frontend/src/i18n.test.ts`
- Modify: `frontend/src/styles.css`

**Interfaces:**
- Consumes: `GET /storefront/discovery?q=` with `results`, `suggestions`, and interpreted concepts.
- Produces: truthful matched-results and recovery-suggestions states while preserving the existing empty-query homepage/catalog path.

- [ ] Add failing API and component tests for encoded discovery queries, name/color/category searches, no exact result, available suggestions, reset/edit behavior, and independent VI/EN copy.
- [ ] Run the focused Vitest files and confirm expected failures.
- [ ] Implement the discovery API types and catalog states; remove SKU teaching and long instructional prose.
- [ ] Repair product-card framing, overflow, hierarchy, and responsive suggestion layout using existing truthful media.
- [ ] Re-run the focused tests and frontend typecheck.

### Task 4: Color-first selection and compact cart feedback

**Files:**
- Modify: `frontend/src/api.ts`
- Modify: `frontend/src/views/ProductView.vue`
- Modify: `frontend/src/App.vue`
- Modify: `frontend/src/cart-flow.test.ts`
- Modify: `frontend/src/i18n.ts`
- Modify: `frontend/src/i18n.test.ts`
- Modify: `frontend/src/styles.css`

**Interfaces:**
- Consumes: grouped product options from Task 2 and the existing flat variants only for `FitAssistant`.
- Produces: explicit selected color, requested size, resolved variant, alternate-color recovery, compact toast, and cart-badge acknowledgment.

- [ ] Add failing component tests for composite colors, color-first rendering, size changes, stale variant clearing, requested unavailable size recovery, explicit alternative color choice, disabled Add to cart, compact toast, badge update, and VI/EN rendering.
- [ ] Run the focused Vitest files and confirm expected failures.
- [ ] Implement the state model and accessible controls without changing cart storage or checkout behavior.
- [ ] Remove the header Search link, SKU display, persistent success panel, and redundant quantity copy.
- [ ] Add short non-blocking motion with a reduced-motion override and harden the layout at approximately 390px.
- [ ] Run focused tests, all frontend tests, typecheck, build, and the Impeccable detector once.

### Task 5: Regression and evidence

**Files:**
- Modify only if a failing regression reveals a P1-caused defect with a new failing test first.

**Interfaces:**
- Consumes: all prior tasks.
- Produces: verification evidence and the completion report; no new feature surface.

- [ ] Run the full backend unit suite and report SQL-backed skips separately.
- [ ] Run the full frontend Vitest suite, typecheck, and production build.
- [ ] Exercise the six approved browser scenarios on desktop and approximately 390px when the browser/runtime is available.
- [ ] Verify the final diff contains no migration, dependency, or P0 semantic change.
- [ ] Request one fresh whole-change code review and resolve Critical/Important findings through RED→GREEN tests.
