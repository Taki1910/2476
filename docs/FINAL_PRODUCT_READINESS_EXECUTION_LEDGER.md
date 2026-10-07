# Final Product Readiness execution ledger

## Baseline

- Date: 2026-09-25
- Branch: `codex/remediation-audit-20260913`
- HEAD: `682999daff48fa874b346c2ecde21207b7e32d1f`
- Working tree: intentionally dirty; HEAD alone does not reproduce the approved baseline.
- Isolation ruling: work in this checkout because a clean worktree would omit accepted uncommitted implementation.
- Source-control boundary: no staging, commits, pushes, resets, cleans, reverts or unrelated removal.
- Accepted milestone: `PRODUCT EVIDENCE: PASSED`; reopen only on concrete regression.
- Toolchain baseline: Java 25.0.4, Maven Wrapper 3.9.16, Node 24.14.0, pnpm 11.19.0.

## Progress

| Phase | Problem/evidence | Solution | Files changed | Validation | Remaining risk |
| ----- | ---------------- | -------- | ------------- | ---------- | -------------- |
| Baseline | Git rejected the sandbox identity as dubious ownership; repository belongs to the desktop user and is intentionally dirty | Use per-command `git -c safe.directory=...`; do not alter global config or source-control state | This ledger and the execution plan only | Branch and HEAD matched the supplied baseline; initial and final dirty state preserved; `git diff --check` clean | None |
| Theme | Browser/OS reports `prefers-color-scheme: dark`, while the application has no approved dark palette | Adopt the existing intentional light-only policy: root `color-scheme: light` plus existing semantic light tokens; add no speculative overrides | None | Storefront, login, PDP, native inputs/selects, buttons, reports tables, Product editor and an operations confirmation dialog rendered coherently; computed root scheme `light`, body `rgb(242, 238, 229)` / `rgb(25, 25, 21)`, no tested overflow | Dark theme remains unsupported by design |
| Reporting | Historical concerns around discounted Product Sales, shipping and component void allocation | Kept current ADR-0034 snapshot model after persisted-flow trace; no calculation edit because the suspected defects were not reproducible | None | Backend unit 30/30 and focused `VerticalSlice7ReportingExternalIT` 6/6 passed; representative tests cover ITEM/ORDER/VOUCHER, shipping, rounding, multi-line, POS and SNAPSHOT_V2 voids | P3: report reads trust write-time parent/child allocation consistency; production writers validate it |
| State/navigation | Four current P2 issues were reproduced: cross-tab cart quantity stayed stale; a superseded Order failure replaced the next route's loading state; payment result ignored same-route `attemptId` changes; draft search text prematurely relabelled the unchanged catalog as results | Native `storage` event synchronization for only the active cart owner; local request generations bound Order/payment results to current route identity; catalog result state derives from the submitted route query | `frontend/src/App.vue`, `frontend/src/cart.ts`, `frontend/src/cart.test.ts`, `frontend/src/review-fixes.test.ts`, `frontend/src/cart-flow.test.ts`, `frontend/src/storefront-homepage.test.ts`, `frontend/src/views/CatalogView.vue`, `frontend/src/views/OrderStatusView.vue`, `frontend/src/views/PaymentResultView.vue` | Each defect failed a focused test before the fix; focused state tests green; full frontend 19 files / 204 tests green; live cross-tab cart retest changed the other tab's badge from 2 to 4 | None known; browser storage is same-origin and current-owner scoped |
| UX/responsive | Accepted Product Evidence could have regressed during state fixes | Kept Product Evidence/PDP structure unchanged; corrected only the catalog's draft/submitted search label; reran natural viewport and purchase-order checks | `frontend/src/views/CatalogView.vue` and its tests | 1440/1024/768/~390 had no page-level horizontal overflow; mobile purchase controls remained before Product evidence; Back restored catalog region; search clear restored 18 products; Impeccable detector returned `[]` | P2 staff navigation uses a horizontally scrollable rail at 390/768 and can hide later links; P3 mobile Products management requires a long vertical scroll |
| Human review | Final acceptance requires independent natural journeys | Two reviewers received separate persona scopes without each other's conclusions; findings were reproduced or disputed using session evidence rather than voting | None | Both found factual shopper differentiation, coherent PDP hierarchy, usable native controls and no console errors. One unique draft-label defect was fixed. The other uniquely found the staff-nav discoverability and long Product-editor scroll issues. A manager-session identity observation was contaminated by concurrent shared-browser sign-out/login and was not reproducible; disagreement over light-only support is a policy preference, not a broken state. | Documented P2/P3 only; no P0/P1 |
| Automation/cleanup | Final counts and disposable environment state must be fresh | Ran acceptance against a newly created untouched database after diagnosing prior-database test contamination; stopped demo processes; removed only exact task resources | None | Backend 30/30 unit and 212/212 SQL Server integration; frontend 19/19 files and 204/204 tests; typecheck and 93-module build passed; fresh V1→V29 passed; three exact disposable DBs and temporary JDBC directory removed | None |

## Material issue records

### P2 — Cross-tab cart state was stale

- Observed behavior: changing a line from quantity 3 to 4 in one tab left the other tab's header at `Cart 2` after waiting.
- Reproduction: two same-origin tabs sharing the active cart owner; mutate quantity in tab B and observe tab A.
- Root cause: cart persistence used owner-scoped `localStorage`, but `App.vue` did not listen for the browser's cross-document `storage` event.
- Fix: apply valid events only for `storageKey(cart.owner)` through existing `restoreCart`; do not re-persist or create a broadcast abstraction.
- Validation: RED tests for listener lifecycle and active-owner application; focused 24/24 GREEN; live retest updated the other tab to `Cart 4` within 500 ms; full 204/204 GREEN.

### P2 — Superseded Order request owned loading/error state

- Observed behavior: after route A → B, a late failure from A cleared B's loading state and showed `Order unavailable` while B was still pending.
- Reproduction: deferred A and B requests; reject A after navigating to B but before resolving B.
- Root cause: only the successful assignment checked route identity; `catch` and `finally` were unconditional.
- Fix: a component-local monotonically increasing request generation guards success, failure and completion.
- Validation: focused test was RED with the stale error, then GREEN; B's result renders and A cannot change its state; full 204/204 GREEN.

### P2 — Payment result ignored a new attempt on the same route

- Observed behavior: changing `/payment/result?attemptId=old` to `attemptId=next` reused the component but issued no request for `next`.
- Reproduction: deferred old attempt, push the same route with a new query, assert the next authoritative attempt/order is requested.
- Root cause: refresh ran only on mount and had no route-query watcher or request identity.
- Fix: watch `attemptId`, clear the old timer/result, force a route-bound refresh, and ignore superseded async stages.
- Validation: focused test was RED with only the `old` call, then GREEN with the `next` order; full 204/204 GREEN.

### P2 — Draft search text mislabelled an unchanged catalog

- Observed behavior: typing `Court` without submitting immediately changed the heading to `Search results` while all 18 products remained visible.
- Reproduction: load the full catalog, type into the search field, and inspect the heading before submit.
- Root cause: presentation state used the editable input value rather than the submitted `q` route query that owns the loaded result set.
- Fix: derive result labels, empty-state copy and featured-card state from the submitted route query.
- Validation: focused test was RED with the premature label, then GREEN; full 204/204 GREEN.

### Environment-only verification events

- The first focused SQL run could not load the Microsoft JDBC native authentication DLL in the Maven test fork. No business assertion ran. Setting `JAVA_TOOL_OPTIONS=-Djava.library.path=...` fixed the fork and the rerun passed.
- The first final `clean verify` attempt could not delete the JAR because the task's live demo backend still held it for human review. No tests ran. Stopping the review server resolved the lock.
- A later full run used a database already populated by focused tests. Maven `clean` does not clear an external database; the test provider's process-local transaction counter collided with persisted test rows, and the fail-closed void path produced `UNKNOWN`. An untouched database reproduced no failures: 212/212 integration tests passed. No product code was changed for this environment-only event.
- Disposable databases `shoe_final_readiness_20260925_1026`, `shoe_final_readiness_browser_20260925_1027`, and `shoe_final_readiness_clean_20260925_1530` were dropped and verified absent. The task-created JDBC helper directory was removed.
