# Final Product Readiness Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Close the remaining theme, financial-correctness, state/navigation, responsive and human-acceptance gaps without reopening accepted Product Evidence work or expanding product scope.

**Architecture:** Audit authoritative persisted facts and current user-visible behavior before changing code. Confirmed defects follow one focused RED → minimal GREEN → regression cycle at the layer that owns the invariant; otherwise the current behavior is recorded as not reproducible. The existing Vue/Spring/SQL Server architecture and Stockroom Proof Sheet visual system remain unchanged.

**Tech Stack:** Java 25, Spring Boot 4.1.1, SQL Server/Flyway V29, Vue 3, TypeScript 5.9, Vue Router 4.6, Vitest 3.2, Vite 7.1.

**Spec:** `C:/Users/asus/.codex/attachments/229d683e-4239-4dd8-b697-06bc2425530f/Văn bản đã dán.txt`

## Global Constraints

- Preserve branch `codex/remediation-audit-20260913`, HEAD `682999daff48fa874b346c2ecde21207b7e32d1f`, and every approved uncommitted change.
- Do not stage, commit, push, reset, clean, revert or remove unrelated files.
- Do not add major features, dependencies, Product facts, accounting conventions or a speculative dark-theme architecture.
- Product Evidence remains accepted unless a concrete regression is observed.
- Use disposable SQL Server databases for destructive acceptance and remove only the exact databases created by this task.
- Report exact automated counts; a planning note or static inspection is not acceptance evidence.

## Review Focus

- OS/browser dark preference must not partially recolor native controls or leave half-dark surfaces.
- A discounted multi-line Delivery order must reconcile paid merchandise, paid shipping and successful component voids without double-subtracting allocations.
- Older asynchronous results must not overwrite a newer route, identity or entity state.
- Browser Back must restore useful Catalog context after asynchronous rendering without preserving unsafe mutation state.
- At approximately 390 px, price, fit, size, availability and Add to Cart must remain ahead of secondary evidence with no horizontal overflow.

---

### Task 1: Lock baseline and execution evidence

**Files:**
- Create: `docs/FINAL_PRODUCT_READINESS_EXECUTION_LEDGER.md`
- Inspect: `docs/PRODUCT_EVIDENCE_EXECUTION_LEDGER.md`

**Interfaces:**
- Consumes: the accepted dirty checkout and milestone specification.
- Produces: one phase-by-phase evidence ledger used by every later task.

- [ ] Record branch, HEAD, dirty-tree rule, Java/Node/package-manager versions and initial process/database state.
- [ ] Record each material issue with observed behavior, reproduction, root cause, solution, changed files, validation and remaining risk.
- [ ] Keep unexecuted checks marked `PENDING`; never convert intent into proof.

### Task 2: Establish and verify the theme policy

**Files:**
- Inspect or modify only if a defect is reproduced: `frontend/src/styles.css`, `frontend/src/App.vue`, affected Vue views.
- Test: the smallest existing frontend test file that owns the reproduced theme/state behavior.

**Interfaces:**
- Consumes: existing semantic CSS variables and `color-scheme` declaration.
- Produces: either documented intentional light-only normalization or a real semantic dark theme only if already naturally supported.

- [ ] Inventory variables, hardcoded colors, media queries, native controls, tables, dialogs, navigation, storefront and operations surfaces.
- [ ] Explicitly test browser/system dark preference and the representative rendered surfaces.
- [ ] If a defect exists, write a focused failing test or reproducible browser check, apply the smallest token/global fix, then rerun the focused check.
- [ ] Verify contrast, focus, disabled, selected, success, warning and error states without redesigning accepted surfaces.

### Task 3: Prove reporting and financial reconciliation

**Files:**
- Inspect: `docs/ADR/0025-authoritative-reporting-and-reconciliation.md`, `docs/ADR/0034-paid-component-reversal-and-reporting.md`, `docs/ADR/0035-voucher-family-claim-usage.md`.
- Inspect or modify only after RED: `backend/src/main/java/com/shoecommerce/reporting/ReportingService.java`, the owning payment/order component code.
- Test: `backend/src/test/java/com/shoecommerce/payment/VerticalSlice7ReportingExternalIT.java` and the focused promotion/shipping/void integration tests.

**Interfaces:**
- Consumes: immutable Order/OrderItem discount and shipping snapshots, successful Payment/CashTender facts and successful VoidAllocation facts.
- Produces: reconciled net-sales, product-sales and component breakdown evidence.

- [ ] Trace exact formulas for merchandise gross/net, ITEM/ORDER/VOUCHER allocations, shipping gross/discount/net, POS and SNAPSHOT_V2/LEGACY_V1 voids.
- [ ] Run transaction-level SQL integration cases covering multi-line rounding, free shipping, full/partial component reversal, cancellation and POS.
- [ ] For any failure, preserve the incorrect result as RED, change only the owning calculation/query, and prove GREEN plus broader payment/reporting regression.
- [ ] Record representative equations against persisted snapshots, including reversal-only date behavior.

### Task 4: Reproduce state and navigation candidates

**Files:**
- Inspect or modify only after RED: `frontend/src/session.ts`, `frontend/src/navigation.ts`, `frontend/src/main.ts`, `frontend/src/api.ts`, `frontend/src/cart.ts`, relevant views.
- Test: `frontend/src/session.test.ts`, `frontend/src/navigation.test.ts`, `frontend/src/api.test.ts`, `frontend/src/cart-flow.test.ts`, `frontend/src/review-fixes.test.ts` and the owning view test.

**Interfaces:**
- Consumes: route identity, request identity, session generation, browser storage/BroadcastChannel and existing mutation guards.
- Produces: current-route/current-entity state only; stale work is ignored or cancelled locally.

- [ ] Inspect existing coverage for payment result, stale Order/Product/Variant results, logout, search clear, cart persistence/multi-tab, auth transitions, Back/Forward, duplicate submit, cancellation and management save races.
- [ ] Execute natural browser reproductions for the candidate issues that can affect current users.
- [ ] Fix only confirmed failures with a failing focused test first; prefer existing request/entity generation guards over new global state.
- [ ] Rerun the complete frontend suite after every confirmed state fix.

### Task 5: Run focused responsive and UX regression

**Files:**
- Inspect or modify only on observed regression: existing storefront, Product, Cart, Checkout, Orders, Reports and operations Vue/CSS files.

**Interfaces:**
- Consumes: accepted Product Evidence/PDP/Catalog behavior.
- Produces: viewport-specific observations and only evidence-backed narrow fixes.

- [ ] Use full browser viewports at 1440, 1024, 768 and approximately 390 px.
- [ ] Check horizontal overflow, containers, cards, forms, navigation, tables, dialogs, Product management, Reports, PDP, Cart, Checkout, Orders and operations.
- [ ] Recheck maximum two Catalog facts, mobile PDP purchase order and asynchronous Catalog scroll restoration.
- [ ] Run the Impeccable detector once over any changed UI targets after the final UI edit.

### Task 6: Conduct independent human-style critiques

**Files:**
- Update: `docs/FINAL_PRODUCT_READINESS_EXECUTION_LEDGER.md`

**Interfaces:**
- Consumes: the same running SQL-backed application without sharing reviewer conclusions.
- Produces: two independent critique reports plus root reconciliation of shared, unique and subjective findings.

- [ ] Reviewer A covers first-time, typical-commerce and desktop comparison journeys.
- [ ] Reviewer B covers mobile-first, direct-PDP ad-link and staff operations journeys.
- [ ] Classify every objective finding P0–P3; retain subjective disagreements as disagreements.
- [ ] Reproduce any P0/P1 claim independently before changing code.

### Task 7: Run final automated acceptance and cleanup

**Files:**
- Update: `docs/FINAL_PRODUCT_READINESS_EXECUTION_LEDGER.md`

**Interfaces:**
- Consumes: the final working tree.
- Produces: fresh exact counts and a freeze recommendation.

- [ ] Run backend unit tests and full `-Pacceptance` SQL Server integration against a new disposable database.
- [ ] Run frontend Vitest, typecheck and production build; record files/tests and Vite module count.
- [ ] Validate fresh V1→V29 migration and every relevant populated upgrade test; schema changes, if none, do not manufacture a new migration gate.
- [ ] Run `git diff --check` with the repository passed as a per-command safe directory.
- [ ] Stop only task-created processes, drop only exact task-created disposable databases and confirm ports/database cleanup.
- [ ] Review final diff across correctness, simplicity, architecture, security and performance before issuing the A–K report.
