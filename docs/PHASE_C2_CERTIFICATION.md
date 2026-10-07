# Phase C2 certification

Certified on 2026-09-06 against local SQL Server 16 through Windows Integrated Authentication with Microsoft JDBC Driver 13.4 and its matching x64 native authentication library. All databases and runtime files used below were disposable.

## Context and source snapshot

- Active gate: Phase C2 certification. C1-only regression expansion stopped after the two pending tests below; C3 was not started.
- Branch and HEAD: `main` at `9bdb87df476eea48d937dd7947bce950be361417`.
- Final working tree: intentionally dirty implementation tree with 79 entries: 55 modified and 24 untracked. This includes the accumulated Phase A, B, C1, and C2 implementation/certification files; no commit was created during acceptance.
- Runtime: Oracle Java 25.0.4 LTS x64, Maven 3.9.11, Windows 11 amd64.
- Database: SQL Server `AN\AN`, version 16.0.1190.2; Windows principal `AN\asus`.
- JDBC: Microsoft JDBC Driver 13.4 with matching `mssql-jdbc_auth-13.4.0.x64.dll`, supplied only through the temporary runtime library path.

## C1 regression fixes retained

The later acceptance work retained the genuine fixes for pickup Orders without delivery-only quote evidence, typed SQL Server UUID mapping, delivery full-void accounting with shipping included exactly once, exact LOCATION shipping scope creation, the 768-pixel checkout breakpoint, and shipping-table containment within `.table-shell`.

The two tests that had been in flight are complete: `FlywayV22UpgradeExternalIT` proves populated V21 to V22 backfill (1/1 PASS, 2.135 seconds), while `ShippingC1ExternalIT` proves publication serialization/immutability, six-level specificity and priority, retirement/invalidation semantics, and LOCATION/GLOBAL/Branch authorization (4/4 PASS, 0.714 seconds). The final full profile also reran checkout (29/29), payment/void (24/24), reporting (5/5), and the shipping suite with zero failures. No further C1-only tests were added.

## A. New dedicated test inventory

The final hardening added 11 SQL Server integration cases without creating parallel test infrastructure:

- `VerticalSlice3CheckoutExternalIT`: 5 new cases; 29 total.
- `VnPayPaymentExternalIT`: 3 new cases; 24 total.
- `PromotionC2ExternalIT`: 3 new cases; 8 total.

Frontend hardening added 6 cases, taking the complete frontend suite from 111 to 117 tests. `promotion-c2.test.ts` is the new focused UI contract suite.

## B. Usage lifecycle

PASS. Quote-only and unlimited offers create no redemption. Limited checkout creates exactly one `RESERVED` row per promotion revision and Order, including a promotion applied to multiple lines. Verified success moves it to `REDEEMED`; duplicate provider delivery keeps one redeemed row. Failed payment remains reserved. Unpaid cancellation and reservation expiry move it to `RELEASED`. A successful financial void keeps it redeemed. Replays are idempotent and the database uniqueness constraint is exercised.

## C. Concurrency and rollback

PASS on real SQL Server. Global last-use and per-customer last-use tests overlap the application transactions and allow at most one reservation. The losing transaction leaves no Order, redemption, adjustment, inventory reservation, or balance mutation. The payment/cancellation race finishes only in the documented paid/redeemed or cancelled/released state. Opposite cart ordering tests and the dedicated races completed without deadlock or partial state. A controlled post-validation failure rolls back every financial and inventory effect, and a normal retry succeeds.

## D. Idempotency

PASS. Replaying the same account, key, quote, and fingerprint returns one Order with one redemption and one adjustment/reservation set. Reusing that key with different quote evidence conflicts without consuming capacity.

## E. Staleness

PASS for all six cases: expiry, invalidation, exhausted usage, new higher-priority winner, a newly eligible promotion after a no-discount quote, and ordinary retirement. The first five require a fresh quote; retirement alone preserves accepted-quote semantics.

## F. Financial end to end

The dedicated delivery fixture proves the complete equation in integer VND:

```text
149,000 gross merchandise
-10,000 ITEM
-20,000 ORDER_AUTOMATIC
+80,000 shipping
-80,000 FREE_SHIPPING
=119,000 quote
=119,000 Order
=119,000 PaymentAttempt
```

Quote and Order adjustment identities and amounts match, and item allocations sum exactly to the parent Order discount. The indivisible two-VND discount across three equal lines allocates `1, 1, 0` deterministically without making a line negative.

Acceptance also found and fixed a genuine C2 defect in discounted financial voids: the old allocation used gross item totals and could exceed the captured discounted Order total. `VoidService` now allocates the captured total proportionally with deterministic largest remainder, and `VoidResultService` validates the same component map. This is product correctness, not an environment workaround.

## G. Authorization and revision administration

PASS. The actual IAM grants confirm `OPERATIONS` does not contain `PROMOTION_MANAGE`. A base `OPERATIONS` account with the direct capability is allowed. Plain operations, cashier, customer, `STAFF_MANAGE_SCOPED`-only, and `IDENTITY_MANAGE`-only principals are denied at the service/API boundary.

DRAFT edit, publish, published immutability, explicit next DRAFT revision, failed duplicate revision atomicity, retirement, invalidation, and publish/retire/invalidate audit evidence pass. Automated host-browser checks additionally verified manager administration and controlled 403 routing for `operations.demo`.

## H. Migration

PASS. A clean database applied all 23 migrations from V1 through V23. `FlywayV23UpgradeExternalIT` reran the populated V22 to V23 path and preserved prior C1 financial data. The earlier `FlywayV19UpgradeExternalIT` fixture change remains a genuine migration-fixture correction: it advances the fixture through V20 before proving V20 to V21 scoped-permission preservation; it is unrelated to acceptance-environment authentication.

## I. Final full SQL Server run

- Unit: 7 suites, 20 tests.
- Integration: 21 suites, 142 tests.
- Total: 162 tests.
- Failures: 0.
- Errors: 0.
- Skips: 0.
- Maven result: `BUILD SUCCESS`.
- Maven duration: 2 minutes 42 seconds; summed integration-suite time 132.979 seconds.

The run used one newly created database, migrated it from empty to V23, and ran the complete acceptance profile in one invocation.

## J. Frontend

PASS: 10 test files, 117 tests, TypeScript typecheck, and production build. Focused coverage includes ITEM, ORDER, and FREE_SHIPPING presentation; pickup without a false shipping saving; stacked arithmetic; stale-offer requote behavior; permission visibility; DRAFT edit; published immutability; and absence of C3 voucher/claim/wallet controls.

## K. Browser acceptance method

**AUTOMATED HOST BROWSER — PASS.** Microsoft Edge ran in the same Windows network namespace as Vite, backend, and SQL Server. Vite listened only on `127.0.0.1:5173`; backend listened only on `127.0.0.1:8080`. The previously attempted isolated in-app browser cannot reach host loopback or the specifically bound LAN address; that boundary is an environment fact, not a product defect.

The host run verified ITEM, ORDER, and FREE_SHIPPING stacking, pickup with zero shipping, manager administration, direct-route denial, stale-offer recovery text, discounted Order creation, and demo payment. The observed delivery calculation was `4,470,000 - 597,000 + 30,000 - 30,000 = 3,873,000 VND`; the Order and paid result both displayed `3,873,000 VND`.

## L. Responsive results

PASS at 1440, 1024, 768, and 390 CSS pixels. Cart/checkout monetary content remained readable and reachable with no document-level horizontal overflow. Promotion administration was exercised at the host-browser desktop view; its table remains in the intended scroll container at narrow breakpoints. No browser console errors were observed, and the temporary viewport override was reset.

## M. Cleanup

PASS. Backend and Vite were stopped; ports 8080 and 5173 have no listener. All `shoe_commerce_c2_*` databases were dropped. The temporary JDBC DLL distribution, ZIP, and runtime logs were deleted. Runtime environment values were process-local. No firewall, SQL Server security, SQL login, server role, or tracked machine-specific host configuration was created or changed.

## N. Remaining risks

Real VNPAY merchant sandbox certification and provider-side reversal remain outside the deterministic demo adapter and require merchant credentials plus callback infrastructure. This is an existing external certification boundary, not an unresolved C2 invariant.

## O. Verdict

**PASS — PHASE C2 CERTIFIED.**

C3 has not started.

`git diff --check` completed successfully; its only output was the repository's existing Git warning that LF files may be written as CRLF on this Windows checkout.
