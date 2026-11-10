# P2B Catalog Creation & Lifecycle Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans and superpowers:test-driven-development task-by-task. This work continues in the current intentional dirty worktree; do not commit, stage, merge, push, rebase, reset, clean, or discard files.

**Goal:** Let authorized managers create and resume incomplete products, enforce active EU color/size uniqueness, publish with on-hand evidence, and retire/restore variants without damaging orders, inventory, pricing, or reporting.

**Architecture:** Keep Product and ProductVariant in Catalog, price versions in Pricing, balances/movements in Inventory, and editorial content in ProductPresentation. SQL Server remains the final authority for active option uniqueness. Variant locks serialize retire with checkout and POS; established orders and reservations retain their existing lifecycle.

**Tech Stack:** Java 25, Spring Boot/JPA/JdbcTemplate, Flyway, SQL Server, Vue 3, TypeScript, Vitest.

**Spec:** Approved user instruction dated 2026-09-28 and `docs/BUSINESS_RULES.md`.

## Global Constraints

- Preserve the current dirty P0/P1/P2A baseline and all user-owned changes.
- No new dependency, Product lifecycle, size-system column, Colorway, brand, description, Product-level price, direct inventory writes, or SKU/size/color editing.
- SKU is globally unique and immutable; active option identity is Product + normalized color + implicit-EU normalized numeric size.
- Lifecycle is exactly DRAFT → PUBLISHED/RETIRED, PUBLISHED → RETIRED, RETIRED → DRAFT.
- Publication requires current positive price and positive on-hand at an enabled location; availability remains `onHand - reserved`.
- SQL-backed and real-backend browser certification must be reported as pending when the environment is unavailable.

## Review Focus

- SQL collation and Java normalization must agree for `Black`/case variants and remain accent-sensitive for `Đen` versus `Den`.
- Concurrent option creation and RETIRED restore must surface semantic option conflicts, never raw constraint names.
- Retire versus checkout/POS must linearize at the variant lock without cancelling an already-established transaction.
- Expired ADOPTED holds must release after retirement.
- Product optimistic conflicts must preserve the latest server state and never overwrite silently.

---

### Task 1: Persist approved catalog invariants

**Files:**
- Create: `backend/src/main/resources/db/migration/V31__variant_sale_lifecycle.sql`
- Create: `backend/src/main/resources/db/migration/V32__normalized_active_variant_option.sql`
- Create: `backend/src/test/java/com/shoecommerce/FlywayV32UpgradeExternalIT.java`
- Modify: `backend/src/main/java/com/shoecommerce/catalog/ProductVariant.java`
- Modify: `backend/src/main/java/com/shoecommerce/catalog/ProductVariantRepository.java`
- Test: `backend/src/test/java/com/shoecommerce/catalog/CatalogServiceManagementTest.java`

**Interfaces:**
- Produces normalized color/size helpers, RETIRED state transitions, a filtered SQL unique index, and semantic option conflict detection.

- [ ] Add failing normalization/lifecycle tests and observe RED.
- [ ] Implement ProductVariant validation and transition methods minimally; observe GREEN.
- [ ] Add V31 and guarded V32 plus SQL external upgrade/refusal/collation tests.
- [ ] Run targeted backend unit tests; SQL tests run only when an isolated SQL Server is configured.

### Task 2: Add resumable Product and Variant commands

**Files:**
- Modify: `backend/src/main/java/com/shoecommerce/catalog/Product.java`
- Modify: `backend/src/main/java/com/shoecommerce/catalog/ProductRepository.java`
- Modify: `backend/src/main/java/com/shoecommerce/catalog/CatalogService.java`
- Modify: `backend/src/main/java/com/shoecommerce/catalog/CatalogController.java`
- Test: `backend/src/test/java/com/shoecommerce/catalog/CatalogServiceManagementTest.java`

**Interfaces:**
- Produces create/update Product, create Variant, publish/retire/restore commands with expected versions and stable semantic conflicts.

- [ ] Add failing Product edit, duplicate SKU/option, transition, restore-conflict, readiness, and permission tests; observe RED.
- [ ] Implement the smallest commands and DTOs; map known SQL constraints without exposing their names.
- [ ] Run targeted catalog unit tests and observe GREEN.

### Task 3: Serialize retirement with commerce and preserve hold expiry

**Files:**
- Modify: `backend/src/main/java/com/shoecommerce/catalog/ProductVariantRepository.java`
- Modify: `backend/src/main/java/com/shoecommerce/order/CustomerOrderService.java`
- Modify: `backend/src/main/java/com/shoecommerce/pos/PosService.java`
- Modify: `backend/src/main/java/com/shoecommerce/order/CheckoutHoldExpiryService.java`
- Test: relevant catalog/order/POS external suites and focused unit tests where SQL is not required.

**Interfaces:**
- Consumes locked ProductVariant rows and `published()`.
- Produces checkout/POS/retire linearization and lifecycle-independent hold expiry discovery.

- [ ] Add failing tests for locked sale eligibility and retired hold expiry; observe RED where runnable.
- [ ] Lock variants in canonical database-ID order before checkout stock reservation and before POS issuance.
- [ ] Remove publication filtering from hold-expiry lookup.
- [ ] Run targeted unit suites; leave SQL concurrency evidence pending if no SQL Server is configured.

### Task 4: Update manager projections and resumable UI

**Files:**
- Modify: `backend/src/main/java/com/shoecommerce/catalog/CatalogManagementService.java`
- Modify: `frontend/src/api.ts`
- Modify: `frontend/src/views/ProductsView.vue`
- Modify: `frontend/src/views/ProductManagementDetailView.vue`
- Modify: `frontend/src/i18n.ts`
- Modify: `frontend/src/styles.css`
- Test: `frontend/src/api.test.ts`, `frontend/src/manager-catalog.test.ts`

**Interfaces:**
- Produces Product/Variant entity versions, RETIRED counts/status, on-hand readiness, creation/edit/add-variant/retire/restore controls, and honest permission states.

- [ ] Add failing API and component contract tests; observe RED.
- [ ] Implement create Product from the list and identity/variant/lifecycle controls on Product detail.
- [ ] Reuse existing price, inventory, evidence, and presentation operations; add deliberate retire confirmation and restore-to-DRAFT copy.
- [ ] Run frontend tests, typecheck, and production build; observe GREEN.

### Task 5: Documentation, regression, and fixed-baseline review

**Files:**
- Create: `docs/ADR/0041-catalog-active-option-and-sale-lifecycle.md`
- Modify: `docs/ADR/README.md`
- Modify: `docs/BUSINESS_RULES.md`
- Modify: `docs/DOMAIN_MODEL.md`
- Modify: `docs/LIFECYCLES.md`
- Modify: `docs/API/OPERATIONS.md`

**Interfaces:**
- Records the approved invariant, history limitation, transaction lock, and integration-certification boundary.

- [ ] Update authoritative docs without adding unapproved behavior.
- [ ] Run backend unit suite, frontend suite/typecheck/build, `git diff --check`, and changed-file/migration inventory.
- [ ] Attempt configured SQL external suites and real-backend browser smoke only when their actual dependencies exist; report limitations truthfully.
- [ ] Perform one final fixed-baseline code review and resolve Critical/Important findings with RED→GREEN tests.
