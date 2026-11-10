# Phase S1 Certification

Status: **S1 PASSED**

## Environment

- Branch: `main`.
- HEAD: `9bdb87df476eea48d937dd7947bce950be361417`.
- Worktree: historically dirty with the accepted Phase A–C3 and S1.1–S1.4 work; no stage, commit, tag, reset, or push was performed. `git diff --check` passed.
- Java: Oracle Java 25.0.4 LTS. Maven: 3.9.11.
- Node.js: 24.14.0. pnpm: 11.19.0.
- SQL Server: `localhost\AN`, 16.0.1200.5 Developer Edition (64-bit), using fresh disposable S1.4 test and live databases.
- Final browser acceptance: Microsoft Edge through the connected browser session, desktop and an explicit 390×844 viewport.
- Cleanup: backend and frontend acceptance servers were stopped and both disposable S1.4 databases were removed after evidence collection.

## S1 Scope

- S1.1: V26 storefront merchandising persistence, permission, API, revision and publication foundation.
- S1.2: staff Storefront Management editor, validation, preview, conflict handling and explicit publication UI.
- S1.3: customer HOME composition using the shared renderer, localization, stale-reference behavior, CTA routing and responsive presentation.
- S1.4: final assets, canonical promotion and HOME fixture, publication, live acceptance and S1 freeze evidence.

## Architecture

S1 keeps ADR-0033 as authority. V26 persists HOME page revisions and ordered typed sections. `STOREFRONT_MANAGE` is enforced by current server authority. Draft replacement and publication retain their transactional and optimistic-concurrency boundaries; publication atomically retires the prior publication and promotes the selected draft. The staff editor and customer HOME share section rendering. Customer composition resolves current Catalog products and the current public Promotion offer at read time while preserving the published section definition.

## Historical Evidence

The following results were accepted before S1.4 and were not newly executed in this slice:

- S1.1: accepted as PASSED by the user before S1.2.
- S1.2: accepted as PASSED by the user before S1.3.
- S1.3: accepted as PASSED in the S1.4 approval.

## S1.4 Changes

- Added four 1448×1086 controlled product assets: Basket Retro, Urban Hiker, Trail Edge and City Loafer.
- Changed only those four `DemoDataBootstrap` media mappings and added matching English/Vietnamese accessible media metadata.
- Added `scripts/setup-s1-storefront.ps1`, which requires an explicit disposable-demo switch, authenticates normally, uses session/CSRF APIs, strictly creates or reuses City Edit 15, resolves current product IDs and prepares the canonical HOME draft without publishing it.
- Extended the focused bootstrap integration test for repeated media-mapping idempotence and added focused frontend media checks.
- Updated the demo runbook with the guarded fixture and manager/customer verification flow.

## Newly Executed Automated Evidence

- `DemoDataBootstrapExternalIT`: 1 test passed against SQL Server; repeated bootstrap preserved historical facts and the four S1.4 mappings.
- Frontend Vitest: 15 files, 147 tests passed, including storefront management, customer homepage and new product-media coverage.
- `pnpm typecheck`: passed.
- `pnpm build`: passed; 84 modules transformed and the production bundle completed.
- PowerShell parser check: passed for `setup-s1-storefront.ps1`.
- Fixture execution on a fresh demo database: first run created/published one canonical City Edit 15 and prepared HOME draft revision `65de8254-cebb-463d-aa98-4521fa3dc5ed`; second run reused the promotion and performed no draft write; the post-publication run created no replacement draft.
- Database evidence after publication: one City Edit 15 row in one family, one HOME revision, four published sections in positions 0–3, and one successful `STOREFRONT_PAGE_PUBLISHED` audit event.

## Manual Browser Evidence

Executed in Edge:

- Signed in normally as `manager.demo` and opened the API-prepared draft in Storefront Management.
- Verified VI and EN previews, exact four-section order, exact Court and Trail product order, City Edit 15 content, and After Dark controlled media.
- Published revision 1 through the real manager UI.
- Verified customer HOME order in both locales.
- Verified hero CTA → After Dark and promotion CTA → the City Edit 15 detail route.
- Verified Court CTA populated catalog search with exactly three Court products; Clear search restored all 18 products.
- Verified the four new assets loaded at their natural 1448×1086 dimensions with distinct URLs and no broken image; browser console had no errors or warnings.
- At an explicit 390×844 viewport, the document width fit the viewport with no horizontal overflow and the hero remained readable with a clean crop.

**MANUAL USER VERIFICATION REQUIRED:** native Edge 200% browser zoom could not be set or proven through the available browser-control surface. No CSS or synthetic page zoom was used as a substitute.

## Canonical Publication

- Published HOME revision: `65de8254-cebb-463d-aa98-4521fa3dc5ed`, revision number 1, entity version 1.
- Section order: `HERO_BANNER` → `PRODUCT_COLLECTION` (Court Originals) → `PROMOTION_SPOTLIGHT` (City Edit 15) → `PRODUCT_COLLECTION` (Beyond the pavement).
- City Edit 15 family: `953d0fa8-1705-40c7-b684-32c3a7063d24`; exactly one published revision was present.
- Audit: one successful `STOREFRONT_PAGE_PUBLISHED` event for the published revision at `2026-09-11 12:14:29.750951` local acceptance time.

## Known Limitations

- Native 200% browser zoom remains explicit user-manual evidence.
- Generated catalog photographs are controlled demo illustrations; selected SKU, color, size and server pricing remain authoritative.
- The repository baseline contains the previously accepted uncommitted phase work, so the certification refers to the complete current worktree and recorded HEAD rather than an isolated commit.

## Deferred

- Additional products, images, section types and campaigns.
- Any migration, API, permission, Promotion implementation, merchandising service or design-system change.
- Production content operations, external media storage and work beyond Phase S1.

## Final Verdict

**S1 PASSED.** All S1.4 completion criteria were demonstrated. The approved completion rule permits native 200% zoom to remain explicitly recorded as outstanding user-manual evidence.
