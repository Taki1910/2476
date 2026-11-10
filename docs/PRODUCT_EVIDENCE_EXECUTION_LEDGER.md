# Product Evidence execution ledger

## Baseline

- Date: 2026-09-24
- Branch: `codex/remediation-audit-20260913`
- HEAD: `682999daff48fa874b346c2ecde21207b7e32d1f`
- Working tree: intentionally dirty; HEAD alone does not reproduce the approved baseline.
- Isolation ruling: work in place because a clean worktree would omit the approved uncommitted A-C3/S1/ProductPresentation state.
- Source-control boundary: no staging, commits, pushes, resets or cleans.
- Toolchain: Java 25; do not downgrade or redesign it.

## Approved decisions

- ADR-0039 is the binding Product evidence decision.
- Product owns five nullable controlled facts: intended use, primary surface, upper construction, cut profile and primary sole profile.
- Fit/width are projected from FitProfile; color remains Variant-owned; ProductPresentation remains editorial.
- Exactly six approved demo Products receive evidence.
- No generic attributes, PIM, fallback claims, new audit subsystem or speculative dark theme.
- The natural management owner is one general `/operations/products` Catalog surface because no Vue Product management surface currently exists.

## Progress

| Phase | Problem/evidence | Chosen solution | Files changed | Validation | Remaining risk |
|---|---|---|---|---|---|
| Domain/schema | Five controlled facts require ownership and database enforcement | Product-owned nullable enums plus V29 nullable columns and individual checks | Product, ProductEvidence, V29, focused tests | V28→V29 passed on SQL Server; invalid values and nullable upgrade covered | None identified |
| Management | Product facts need authorized atomic replacement | Catalog API with expected version, JPA flush conflict mapping and AuditWriter event; colors and fit are batch-projected for the list | CatalogController, CatalogService, repository, integration/unit tests | Authorization, 400/403/404/409, no-overwrite and audit passed; two-tab browser conflict reloaded winner; review regressions cover draft snapshot and failed conflict reload | The management response is intentionally unpaginated for the current 18-Product demo; paginate when catalog size makes response volume measurable |
| Projection/demo | Shopper data needs strict null semantics and approved fixtures | Evidence on list/detail, one batch authoritative fit projection, six stable demo Product updates, and cleanup of evidence on other seed-owned Products | StorefrontCatalogService, DemoDataBootstrap, external tests | Full/partial/null projections, supported fit, exact seed, contaminated non-approved Product cleanup and rerun idempotence passed on SQL Server | None identified for the 18-Product demo catalog |
| Frontend | Facts must differentiate without displacing purchase controls | General Product management, combined two-card-fact cap, PDP facts below purchase decision, explicit light scheme and catalog-ready scroll restoration | API types, ProductsView, catalog/PDP, navigation, i18n, CSS | 199 tests, typecheck/build pass; real browser pass at 1440/1024/768/390 with no console errors or horizontal overflow; 390 px back navigation restored the selected card after async reload | No claimed dark theme; existing light palette remains intentionally stable |

## Final verification evidence

- Backend unit: 30 tests, 0 failures, 0 errors, 0 skipped.
- Full SQL Server acceptance: 212 tests, 0 failures, 0 errors, 0 skipped; V1→V29 fresh migration and V28→V29 upgrade both passed.
- Frontend: 19 files / 199 tests passed; typecheck and production build passed (93 modules).
- Browser: exact six-row matrix observed in `/operations/products`; update/restore and stale two-tab conflict exercised; catalog and PDP evidence observed at the required viewports; VI/EN management copy verified. The final 390 px retest confirmed light color scheme, maximum two facts per card, no horizontal overflow, PDP top reset and catalog back-scroll restoration after async data returned.
- Cleanup: task-created SQL Server databases and local browser/backend/frontend processes removed after verification.

## Rulings

- Work in the current checkout; cost if wrong: new edits coexist with the intentional dirty baseline, so every change must remain narrowly scoped and status-reviewed.
- Execute the approved plan inline without its commit steps; user instructions prohibit staging and commits, while RED/GREEN and verification gates remain mandatory.
