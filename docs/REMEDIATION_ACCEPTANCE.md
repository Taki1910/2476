# Remediation evidence — 2026-09-14

Status: **IN PROGRESS — automated gates passed; remaining acceptance work is explicit.**
Approval: current user approval of the2026-09-13 remediation master plan and D1–D6.
No historical certificate is being rewritten or retroactively approved.

## Snapshot and commands

Branch `codex/remediation-audit-20260913`, HEAD
`9bdb87df476eea48d937dd7947bce950be361417` plus preserved A–C3/S1 working-tree changes
and this remediation. HEAD alone is insufficient. Original source backup and
SHA256 manifest remain in `artifacts/remediation-baseline/source/` and
`source-manifest.csv`. Current evidence is in `artifacts/remediation-acceptance/`.

The disposable runner creates an exact random database name, runs
`backend/mvnw.cmd -o -Pacceptance -Dit.test=*ExternalIT clean verify`, and drops
that exact database in finally. Backend log records creation identity/cleanup.
Native Windows SQL authentication uses the signed Microsoft JDBC13.4 DLL through
process-local java.library.path. Java25, Maven3.9.16, Node24.14, pnpm11.19;
frontend lockfile is pnpm-lock.yaml. No new dependency was added.

| Gate | Result | Evidence |
|---|---|---|
| Surefire | 8 suites,25 tests,0 failures/errors/skips | backend-summary.json; surefire-reports |
| SQL ExternalIT | 30 suites,193 tests,0 failures/errors/skips | backend.log; failsafe-reports; clean verify exit0 |
| Fresh migration | V1–V27, Hibernate validation | backend.log |
| Populated upgrade | V26→V27, old financial facts retained; invalid component constraints rejected | FlywayV27UpgradeExternalIT |
| Frontend | 16 files,171 tests PASS | frontend-test.log; exit0 |
| TypeScript/build | vue-tsc -b and Vite PASS | frontend-build.log; exit0 |
| Lint | No lint script exists; not claimed | frontend/package.json |

Counts above come only from reports copied after clean verify, not old target XML.
Container-only tests are not included. Provider tests use deterministic fixtures;
this is not VNPAY certification or production deployment acceptance.

## Browser observations on SQL-backed demo

Database `shoe_remediation_browser_20260913` was checked empty before startup.
Backend uses demo profile, V27 and deterministic local payment/void adapter.
Browser origin localhost:5173. All actions below were executed by the agent on
the real SPA; request responses were not mocked. Addresses/phone were synthetic.

| Case | Observed result |
|---|---|
| Login429 | Five incorrect attempts then sixth denied; EN879seconds shown, switches immediately to VI879giây. Generic account-independent message. HTTP recovery/Retry-After semantics separately verified with controlled clock tests. |
| Reporting baseline | Current-day net6,260,000; one financial exception remains visible and excluded. |
| Discounted Pickup | Order937c020d-6af2-4903-856d-c313599f5c78, two Court Classic variants, gross2,980,000 minus298,000 =2,682,000. UI paid→cancelled/refunded. SQL: two SNAPSHOT_V2 ORDER_ITEM allocations1,341,000 each, SUCCEEDED. |
| Discounted Delivery | Order7d8beefa-28a9-4b54-be39-5534f41e9b6a, gross1,490,000 minus149,000 plus80,000 shipping =1,421,000. UI paid→cancelled/refunded. SQL: ORDER_ITEM1,341,000 and SHIPPING80,000, both SUCCEEDED. |
| Combined reporting | Receipts10,363,000; reversals4,103,000; net6,260,000 returns to baseline. Merchandise reversals4,023,000; shipping reversals80,000. VI/EN show the same server amounts. |
| Responsive report390px | Long label initially clipped; fixed pseudo-element wrapping in styles.css. Screenshot recheck shows full label; document width375 within390viewport. |
| Browser console | No error-level entries observed after these flows. |

SQL correlation files: browser-pickup-sql.txt and browser-delivery-sql.txt.
The browser monetary fixtures intentionally reuse existing demo prices/offers;
they validate the same invariants as B1/B2, not the illustrative150,000/130,000
amounts in the plan. No business-data SQL mutation was used to fabricate these flows.

## Findings and remaining work

| Finding | State | Evidence / remaining gate |
|---|---|---|
| F1 reporting | Implemented, closing review pending | SQL date split, discount/shipping/legacy regression; browser Pickup/Delivery totals; complete combined voucher matrix/review |
| F2 void allocation | Implemented, closing review pending | Paid reader integrity, V27 constraints/upgrade, free-item/retry/legacy tests; browser component-level SQL |
| F3 C3 authority | Resolved in current documentation | Accepted ADR-0035 and domain/lifecycle/promotion links; no retroactive approval |
| F4 zero payable | Implemented per D4 | ZERO_PAYABLE_NOT_SUPPORTED; free line allowed within positive order; C2 SQL and bilingual frontend tests |
| F5 login throttle | Implemented and verified | Unit concurrency/capacity/boundary, HTTP success/failure/CSRF/spoof/recovery, full security regression and browser VI/EN |
| F6 stale documentation | In closing review | Current pointers, historical quantity-one label and discount-aware shipping equation, accepted ADR index |
| F7 reproducibility | In progress | Original backup preserved, isolated clean full run logged; final source manifest/diff and cleanup still required |
| F8 API index | Implemented, consistency review pending | API README/COMMERCE/REPORTING/IDENTITY/OPERATIONS cover all controllers and their authority/DTO/consumer/test sources |

Still outstanding: complete B3 voucher/free-shipping flow, B4 physical issue guard,
B5 full account/scope browser matrix, B7 date split browser checks, B8 storefront
publish, B9 fitting, B10 POS; responsive1024/768 and native200% zoom/keyboard checks;
combined M7 financial end-to-end evidence; final financial/security/source review,
final manifest and disposable process/database cleanup. Existing SQL coverage is
not relabelled as browser coverage. These are not waived or marked PASS.

## Design adjustments retained

Paid-reader tests reuse VnPayPaymentExternalIT checkout fixtures rather than create
a duplicate test class. Synthetic integrity snapshots are explicitly named and
kept separate from real checkout/void tests. No extra ledger, dependency, Redis or
distributed infrastructure. Login capacity scanning remains bounded O(10,000)
under one process lock, as accepted in ADR-0036. Legacy amounts remain unchanged;
legacy SKU attribution stays explicitly unallocated.

No commit, push or deployment has been performed. Never roll an older binary back
over SNAPSHOT_V2/SHIPPING data without evaluating compatibility; use forward fix or
recreate only the named disposable environment.
