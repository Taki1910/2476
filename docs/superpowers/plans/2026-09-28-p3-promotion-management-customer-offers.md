# P3 Promotion Management & Customer Offers — Implementation Plan

> **Status:** Approved implementation, current dirty worktree only. Do not stage, commit, merge, push, rebase, reset, or clean.

**Goal:** Add family-centric promotion operations and a truthful claimable-offer/customer-wallet experience without changing promotion arithmetic, revision semantics, order snapshots, usage transitions, payment, cancellation, void, or reporting behavior.

**Architecture:** Keep `PromotionService` as the deterministic calculator and revision command owner. Add only focused read/preview methods and DTOs to the existing promotion services/controllers. Preserve existing revision endpoints for compatibility. Reuse the catalog tables for promotion product references under `PROMOTION_MANAGE`; do not widen `CATALOG_MANAGE`. No migration or dependency is required.

**UI direction:** Continue the repository's Stockroom Proof Sheet system: warm paper, ink, signal pink, ruled/flat surfaces, Archivo, square controls, dense desktop task station, single-column mobile. Use native controls and existing tokens/components.

**Approved constraints:** CODE offers never enter generic public discovery and raw codes never appear publicly. CLAIMABLE is public and single claim/use per family. Discoverability is family-owned. Product target drafts may reference any existing product, with sellability warnings at publish. Preview is server-calculated and side-effect free. Saved-claim evidence survives missing current published revisions.

---

### Task 1: Lock P3 contracts and validation with failing tests

- [ ] Add plain unit coverage for derived lifecycle state, CLAIMABLE per-customer-limit validation, and code normalization preview.
- [ ] Extend SQL-backed promotion acceptance tests for family list/detail, product references, manager claim search, usage metrics, and publish warning evidence.
- [ ] Run focused tests and capture the expected pre-implementation failure/SQL unavailability.

### Task 2: Implement family-centric manager read models

- [ ] Add family list/detail projections with one `asOf`, current published revision, draft revisions, lifecycle state, target summary, acquisition/discoverability, and usage metrics.
- [ ] Add promotion-authorized catalog product references with publication/pricing/sellability evidence.
- [ ] Add paginated/searchable manager claim read model before exposing revoke in the UI.
- [ ] Keep revision command endpoints and arithmetic unchanged.

### Task 3: Implement authoritative, side-effect-free preview

- [ ] Add a preview request using the draft plus server-owned sample product/variant price and optional validated delivery fee context.
- [ ] Reuse the current calculation path with an in-memory draft candidate; do not reserve usage or persist adjustments.
- [ ] Return before/after merchandise, shipping, total, and applied adjustment explanation.
- [ ] Add focused tests for item, order, and free-shipping preview behavior.

### Task 4: Correct customer discovery and wallet evidence

- [ ] Restrict public list/detail to active, public CLAIMABLE families.
- [ ] Add owned-family claim state for first-load offer detail.
- [ ] Project saved claims from current published revision when available, otherwise claimed-revision evidence.
- [ ] Derive Available, Reserved, Used, Expired, Revoked, and Unavailable from claim/redeem evidence.
- [ ] Preserve asymmetric voucher errors and remove the impossible frontend product-not-eligible mapping.
- [ ] Add pagination/load-more tests for offers and wallet.

### Task 5: Build the P3 manager experience

- [ ] Replace the flat revision-first page with a family-centric campaign list and selected campaign detail.
- [ ] Split editing into Campaign, Effect, Eligibility, Acquisition, Schedule, Preview, and Lifecycle sections.
- [ ] Use promotion-authorized product references; show sellability and publish warnings without blocking drafts.
- [ ] Normalize CODE preview in the manager form; never expose CODE publicly.
- [ ] Hide/fix CLAIMABLE per-customer limit to one and reject unsupported private CLAIMABLE configuration.
- [ ] Fix reset/revise/edit initialization by replacing field-by-field stale state with one complete draft factory/hydrator.
- [ ] Add behavioral component tests covering reset, edit, revise, and mode switches.

### Task 6: Build the customer offer/wallet experience

- [ ] Show only active public claimable cards and add load more.
- [ ] Preload owned claim state on detail and show claim controls only to CUSTOMER accounts.
- [ ] Render wallet evidence statuses and conditionally suppress unavailable terms links.
- [ ] Add wallet load more and keep cart selection restricted to available claimed offers.

### Task 7: Verification and certification report

- [ ] Run backend focused/unit suites and frontend component suites.
- [ ] Run frontend typecheck and production build.
- [ ] Run all locally available backend/frontend regressions.
- [ ] Run Impeccable detector once on final changed UI targets and repair relevant findings.
- [ ] Run browser QA for manager and customer paths against the available environment.
- [ ] Run `git diff --check` and final changed-file/migration inventory.
- [ ] If SQL Server is unavailable, report exactly: **P3 IMPLEMENTED — PENDING INTEGRATION CERTIFICATION** and enumerate the unrun SQL-backed gate.
