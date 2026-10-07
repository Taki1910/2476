# Phase C3 final certification and freeze

Certified on 2026-09-10 against SQL Server 2022 Developer 16.0.1190.2. Phase C3 covers voucher codes, claimable offers, selected-voucher pricing, usage lifecycle, customer promotion UX, and their integration with the certified C2 financial pipeline.

## A. Certification baseline

- Branch and HEAD: `main` at `9bdb87df476eea48d937dd7947bce950be361417`.
- Runtime: Oracle Java 25.0.4 LTS, Maven Wrapper 3.9.16, Node.js 24.14.0, pnpm 11.19.0, Spring Boot 4.1.1, Vue 3, TypeScript, and Vite 7.3.6.
- Database: SQL Server `AN\AN`, Developer Edition 16.0.1190.2; acceptance used the `acceptance` profile and browser acceptance used `demo` on separate disposable databases.
- Flyway: exactly 25 migrations, V1 through `V25__quote_voucher_allocation.sql`; no migration exists after V25.
- Before this certification document, the intentional accumulated Phase A-C3 tree had 92 entries: 59 modified and 33 untracked. No commit or push was performed.

## B. Architecture invariants

PASS. `PromotionService` remains the single financial evaluator for automatic promotions and the optional selected customer voucher. `CartQuoteService` calls that evaluator, checkout reconstructs the selection from quote-owned evidence, Order snapshots the resulting adjustments, and `PaymentAttempt.amount` uses the authoritative Order `total_amount`.

There is no voucher calculator, voucher total, parallel checkout, parallel idempotency mechanism, parallel payment lifecycle, `voucher_redemption` table, `voucher_discount` table, or claim `used` field.

## C. Acquisition, revision, and claim

PASS. AUTOMATIC remains implicit and claim-free. CODE is explicitly entered, normalized with NFKC, trim, `Locale.ROOT` uppercase, and `[A-Z0-9][A-Z0-9_-]{2,31}` validation; its identity is family-owned, private codes are usable but undiscoverable, and limits remain revision-scoped. CLAIMABLE requires an explicit save, owns one claim per customer and stable family, and derives family-lifetime use only from RESERVED or REDEEMED redemption rows. RELEASED restores usability; REDEEMED and REVOKED remain consuming/terminal as specified.

Acquisition mode and code are immutable per family. A filtered unique index enforces normalized-code identity and another enforces one PUBLISHED revision per family. SERIALIZABLE publication replacement is atomic. An accepted RETIRED revision remains the accepted authority; INVALIDATED becomes stale; a fresh quote selects the new PUBLISHED revision. No mutable `current_revision_public_id` exists.

Claim creation and replay, including concurrent replay, produce one row. Saving creates no redemption. The stored claimed revision is audit evidence; quoting resolves the current family revision. There is no `expires_at` or `used` claim field, private CLAIMABLE cannot be first-claimed, and revocation does not permit a replacement claim.

## D. Financial pipeline

PASS. The exact order is ITEM automatic, ORDER_AUTOMATIC, selected merchandise voucher, merchandise net, shipping fee, and at most one SHIPPING benefit. A merchandise voucher uses gross merchandise minus ITEM and ORDER_AUTOMATIC discounts as its minimum-spend and discount base; shipping is excluded. ORDER_PERCENT and ORDER_FIXED use the certified integer-VND HALF_UP rounding, cap, and deterministic largest-remainder allocation.

A merchandise voucher may coexist with automatic shipping. Selected FREE_SHIPPING is the sole shipping winner and fails for pickup, zero fee, or ineligibility without falling back to automatic shipping. Quote-only evaluation consumes no usage. NONE, remove, and change each create an authoritative fresh quote.

Real E2E evidence included `1,490,000 - 149,000 - 50,000 = 1,291,000` for CLAIMABLE and `1,000,000 - 50,000 = 950,000` for private CODE. SQL confirmed `Order.total_amount = PaymentAttempt.amount` at 1,291,000 and 950,000 VND.

## E. Usage lifecycle

PASS. CLAIMABLE always creates RESERVED evidence at checkout, even without numeric limits. Limited CODE creates RESERVED; unlimited CODE may pay without a redemption row. Successful payment moves RESERVED to REDEEMED exactly once; failed payment keeps RESERVED; unpaid cancellation and payment-window expiry move it to RELEASED without deleting evidence.

Real browser/SQL evidence showed a cancelled unpaid claim Order as RELEASED and reusable, while a paid-then-voided claim Order remained REDEEMED with `released_at` null and no reissue. Automated SQL Server coverage proves the same behavior for limited CODE and provider replay.

## F. Staleness and idempotency

PASS. Checkout rebuilds selected voucher authority from the accepted quote snapshot and rejects expiry, invalidation, revoked claim, consumed family, exhausted global/customer CODE limit, changed minimum spend, changed FREE_SHIPPING fulfillment, changed amount, and changed allocation signature as `VOUCHER_STALE`. The rejection creates no Order, redemption, inventory reservation, or stock mutation and never silently reprices, removes, or substitutes a voucher.

Same account, key, quote, and voucher fingerprint replays one Order and its one reservation/redemption/payment identity even after that voucher is RESERVED. Reusing the key with NONE/CODE/CLAIM, claim, revision, amount, or other voucher evidence changes returns `IDEMPOTENCY_KEY_CONFLICT` without loser effects.

## G. Concurrency

PASS on real SQL Server. The acceptance run exercised equivalent normalized CODE creation, competing publication, double claim, claim/publication, claim replay/revocation, selected-voucher checkout, global and per-customer last use, payment/cancellation, release/new checkout, provider success replay, and inventory/order races. SERIALIZABLE transactions plus ordered family/promotion/redemption locks produced one deterministic authority where required, no expected deadlock, and no partial loser state.

## H. Security and account isolation

PASS. Live HTTP checks returned 200 for guest discovery, 401 for guest claim/admin endpoints, 200 for `manager.demo`, and 403 for operations, cashier, and customer administration attempts. Customer A reading Customer B's Order returned 403. Application-layer authorization retains `PROMOTION_MANAGE` as the administration authority; staff authentication alone grants no customer voucher ownership.

Private CODE is absent from discovery. Customer quote/order contracts and snapshots expose only deterministic masking (`FI••••••••••10` in the paid E2E flow). SQL found zero raw private-code occurrences in quote/order snapshots and audit. The browser URL contained no code, console logs were empty, and source inspection confirmed local/session persistence contains cart, quote/idempotency identifiers, fulfillment, payment key, and locale rather than voucher CODE text.

The A-to-B browser flow showed no A claim, selected voucher, quote, error, code, or Order evidence after logout/login. Returning to A restored only A-owned cart lines and required a fresh voucher selection. Session generation and account-response guards prevent stale responses from crossing accounts.

## I. Historical authority

PASS. Order history reads immutable `commerce_order_adjustment` and item-allocation snapshots. Automated tests keep the response byte-for-byte equivalent after retirement, replacement publication, invalidation, claim revocation, visibility change, and current-name mutation. History does not reconstruct from current promotion/claim state and never requires raw CODE.

## J. Customer UX

PASS in the real browser. Offers and detail expose only public safe content. Save is explicit, shows progress, and settles into idempotent Saved state. My Vouchers is account-owned server data and does not invent USED or cart-eligibility states. CODE sends no request while typing; Apply requests a fresh quote and displays a mask. Apply, remove, and change preserve exactly one selected voucher.

Stale checkout stopped before Order creation, retained the prior total for comparison, fetched a fresh NONE quote, and required deliberate review before checkout was enabled. Order history described automatic offers and vouchers without internal layer names. The implemented “Stockroom Proof Sheet” hierarchy avoids generic coupon grids, gradients, glows, excessive pills, or competing claim/CODE controls. The impeccable detector returned zero findings; no genuine UI defect warranted a frozen-scope edit.

## K. Accessibility, responsive behavior, and VI/EN

PASS at 1440, 1024, 768, and 390 CSS pixels across Offers, detail, My Vouchers, cart controls/applied state, stale recovery, and Order history. There was no document-level horizontal overflow or clipped interactive control. Keyboard activation, skip link, one main landmark, semantic headings, visible labels, status/alert semantics, long-term wrapping, and native controls were verified.

The 3px focus ring and key foreground/background pairs measured 4.95:1 through 15.23:1. Real-data flows verified Offers/Ưu đãi, Saved/Đã lưu, Applied/Đã áp dụng, Apply code/Áp dụng mã, Remove offer/Gỡ ưu đãi, stale/error messaging, and history in English and Vietnamese. No untranslated customer-facing C3 copy was observed.

## L. Migration

PASS. A fresh disposable database applied V1 through V25 with 25 successful Flyway rows and no partial state. `FlywayV24UpgradeExternalIT` covers populated pre-C3 upgrade, rollback on duplicate published revisions, family/code constraints, and claim ownership/lifecycle constraints. V25 created only deterministic quote-item allocation evidence with its unique quote/variant/layer key. Schema inspection found only `promotion`, `promotion_family`, `promotion_product_scope`, `promotion_redemption`, and `voucher_claim` in this domain.

## M. Reporting and regression

PASS. Reporting suites reconcile online payment, cancellation, paid void, revenue, discount totals, and historical item allocations without multiplying Order totals. Full acceptance reran C1 shipping and C2 automatic-promotion/financial behavior with zero failures. No reporting or C2 semantics changed during certification.

## N. Test inventory

- Backend unit gate: 7 suites, 20 tests, 0 failures/errors/skips.
- SQL Server acceptance gate: 26 suites, 176 tests, 0 failures/errors/skips; `BUILD SUCCESS` in 2 minutes 57 seconds.
- Frontend: 12 files, 125 tests, plus successful TypeScript typecheck and production build (77 modules).
- Total automated tests: 321.
- Real-browser mandatory flows: 7 of 7 (CLAIMABLE paid, private CODE paid, remove/change, stale, unpaid release/reuse, paid void, and account switch).
- `git diff --check`: PASS after the certification artifact was added.

## O. Defects found or corrected

No product defect was found or corrected during final certification. Reusing the acceptance-suite database initially caused the demo bootstrap to report inconsistent intentionally-mutated test inventory; switching to the required separate fresh disposable E2E database resolved the fixture/environment mismatch without a product change.

The final UI critique was also performed directly against real screens and static source. The optional independent critique workers could not return because the account's delegated-agent usage limit was exhausted; the detector, browser matrix, accessibility checks, and primary review all completed and found no release defect.

## P. Deferred scope

The following remain outside C3: best-voucher auto-selection, multiple simultaneous customer vouchers, ITEM voucher effects, claim issuance limits, voucher reissue after refund/void, code aliases/rotation, family-lifetime CODE policy, campaign editor, banner authoring, AI artwork, Storefront Editor, and Phase D.

## Q. Cleanup

PASS. Backend and both Vite processes were stopped; ports 8080, 5173, and 5174 have no listener. Both disposable `shoe_commerce_c3_final_*` databases were dropped. SQL inspection found zero matching databases and zero C3 SQL logins. The browser tab was closed; no browser harness, temporary script, credential file, JDBC authentication copy, login, server-role grant, or repository-local certification runtime artifact remains.

## R. Git status

Only this certification artifact was added by the final-certification run: `docs/PHASE_C3_CERTIFICATION.md`. The unrelated accumulated Phase A-C3 dirty state was preserved. No source, migration, or test file was changed during final certification; no commit or push was made.

## S. Final verdict

`C3 FINAL CERTIFICATION: PASSED / FROZEN`
