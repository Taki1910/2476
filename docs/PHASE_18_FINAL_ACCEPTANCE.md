# Phase 18 Final Acceptance

Status: Terra integration acceptance: PASSED. Sol independent final product certification: PASSED. The certified application baseline is `439d94c5b4f820d1088f82979570aa023f56d07b`; this log preserves the Phase 18 evidence and does not claim production certification.

## Pickup end-to-end evidence — SC-078B097E

Environment: local clean Phase 18 demo database `shoe_commerce_phase18_demo_20260904`; local frontend and backend; deterministic repository demo-payment adapter. No production payment provider, real credentials, direct SQL data mutation, cancellation, or refund action was used.

| Checkpoint | Browser evidence | Read-only database evidence |
| --- | --- | --- |
| Before fulfillment | Operations queue showed `SC-078B097E` as Pickup, 2 variants / 3 units, at `DEMO-A / DEMO-FLOOR`; detail exposed only **Tiếp nhận và bắt đầu chuẩn bị**. | Order `PAID`; fulfillment `PENDING`; `picking_started_at`, `prepared_at`, and `handed_over_at` null. Reservations: two `COMMITTED`, three units. No handover movements. |
| PICKING | The staff action succeeded. Detail changed to **ĐANG LẤY HÀNG**, displayed accepted time, and exposed only **Đánh dấu sẵn sàng giao**. | `PICKING` at `2026-09-04 05:29:28.339244 UTC`; no handover movements. Balances remained `DEMO-AD-39` 4 on hand / 3 reserved and `DEMO-CC-40` 5 on hand / 2 reserved. |
| PREPARED | The staff action succeeded. Detail changed to **SẴN SÀNG** and exposed only **Xác nhận đã giao khách**. | `PREPARED` at `2026-09-04 05:29:48.325537 UTC`, attributed to the operations account. No handover movement; reservations remained `COMMITTED`, so stock was not finalised early. |
| HANDED_OVER | The staff confirmation dialog warned that the action exports all stock and blocks direct cancellation. After confirmation, the terminal screen showed **ĐÃ GIAO**, all three timeline points, and no further fulfillment action. | `HANDED_OVER` at `2026-09-04 05:30:15.469703 UTC`, with a persisted handover idempotency key and staff attribution. Reservations became two `CONSUMED` rows / three units. |
| Stock finalisation | Terminal staff state said stock had been exported exactly once. | `PICKUP_HANDOVER` movements: `DEMO-AD-39` quantity 2, on-hand delta -2, reserved delta -2; `DEMO-CC-40` quantity 1, on-hand delta -1, reserved delta -1. Final balances: AD-39 2 on hand / 1 reserved / 1 available; CC-40 4 on hand / 1 reserved / 3 available. |
| Customer tracking | My Orders showed **Đã nhận hàng** for `SC-078B097E`; detail showed the paid state, Pickup location, 2 variants / 3 units, amount `5.270.000 ₫`, and accepted / ready / handed-over timeline. | Order remained `PAID`; fulfillment remained `HANDED_OVER`. |
| Refresh / terminal protection | Reloading both terminal staff and customer views showed terminal state and no handover or cancellation control. | Post-refresh: exactly two handover movement rows totaling three units, both at `2026-09-04 05:30:15.469703 UTC`; no duplicate reservation or stock effect. The Phase 18 acceptance suite also covers same-key replay and rejects a different-key replay without adding a movement. |

The actual cancellation/refund path was deliberately not invoked after handover because it was outside the authorization for this acceptance action.

## Scenario C — Delivery E2E (payment checkpoint)

Status: payment accepted; fulfillment has not started. This is a new order and does not modify Pickup evidence `SC-078B097E`.

| Checkpoint | Browser evidence | Read-only database evidence |
| --- | --- | --- |
| Delivery cart and quote | Customer started with an empty cart, selected two sellable variants through the storefront, and received an authoritative quote. `DEMO-CC-39` (size 39, White / Black) was 1 × 1,490,000 VND; `DEMO-AD-39` (size 39, Black / Pink) was 1 × 1,890,000 VND. | Server-authoritative subtotal: 3,380,000 VND. |
| Delivery checkout | Customer selected **Delivery** and entered synthetic-only recipient, phone, address, and note data. The UI required all non-note snapshot fields. Delivery fee displayed as 0 VND, yielding total 3,380,000 VND. | New order `SC-95A6C138` / `95a6c138-d663-4a7a-b249-e79ec75e17ab`: owner `customer.demo`, `DELIVERY`, `PENDING`, fee 0. Snapshot fields persisted; only field lengths are recorded here (receiver 22, phone 10, address 35, note 34) to avoid needless data disclosure. Two reservations / two units were `ADOPTED` before payment. |
| Local demo payment | The explicitly authorized **Thanh toán qua VNPAY** action produced the local payment result page: **Payment confirmed**, `SC-95A6C138`, 2 variants / 2 units, 3,380,000 VND, Paid. No real credential or external charge was used. | One `VNPAY` PaymentAttempt `08db96e3-208c-40bb-be7f-c8ca22c4c09a`, `SUCCEEDED`, amount 3,380,000, response/status `00`; order moved to `PAID` at `2026-09-04 05:50:25.567283 UTC`. Demo payment applies its verified deterministic result directly; the test-profile `payment_provider_event` receipt table therefore has no row for this local demo adapter. |
| Post-payment invariants | Callback revisit showed the same success result. My Orders and detail showed **Paid — waiting for preparation**, **Delivery**, fee 0, and **Waiting for acceptance**; Delivery-specific receiver/address/note labels were present and no Pickup wording appeared. | Exactly one payment and one successful attempt totaling 3,380,000 VND. Both reservations are `COMMITTED` / two units. Order item snapshots and total remain CC-39 1,490,000 + AD-39 1,890,000 + fee 0 = 3,380,000. Balances remain unfinalized (`DEMO-AD-39` 2 on hand / 2 reserved; `DEMO-CC-39` 8 on hand / 2 reserved); zero `DELIVERY_DISPATCH` movements exist. |

### Fulfillment completion

| Checkpoint | Browser evidence | Read-only database evidence |
| --- | --- | --- |
| PICKING | Operations opened only `SC-95A6C138` and selected **Accept & start preparing**. The staff screen changed to `PICKING`, displayed `ACCEPTED`, and exposed only **Mark ready for dispatch**. | `PICKING` at `2026-09-04 08:38:36.975221 UTC`; reservations remained two `COMMITTED` / two units; AD-39 stayed 2 on hand / 2 reserved and CC-39 8 on hand / 2 reserved; zero dispatch movements. |
| PREPARED | **Mark ready for dispatch** succeeded. The staff screen changed to `PREPARED`, displayed `READY`, and exposed only **Dispatch complete order**. | `PREPARED` at `2026-09-04 08:39:22.186625 UTC`, with operations-staff attribution. Reservations and balances were unchanged; zero dispatch movements. |
| OUT_FOR_DELIVERY / inventory gate | The dispatch confirmation warned that it issues physical stock and prevents direct cancellation. On confirmation, UI changed to `OUT FOR DELIVERY`, displayed `DISPATCHED`, stated stock was issued exactly once, and exposed **Mark delivered**. | `OUT_FOR_DELIVERY` at `2026-09-04 08:40:12.873985 UTC`, with dispatch idempotency key and staff attribution. Reservations moved to two `CONSUMED` / two units. Exactly two `DELIVERY_DISPATCH` movements / two units: AD-39 1 with on-hand -1 and reserved -1; CC-39 1 with on-hand -1 and reserved -1. Balances became AD-39 1 / 1 / 0 available and CC-39 7 / 1 / 6 available. |
| Dispatch refresh protection | Reloading the staff fulfillment page retained only the `Mark delivered` terminal-next action. | After refresh, still exactly two dispatch movement rows / two units at the same dispatch timestamp; reservations remained `CONSUMED`; balances did not change. Existing acceptance integration coverage also verifies same-key dispatch replay is idempotent without another movement. |
| DELIVERED | The completion dialog was confirmed through the normal staff UI. Terminal staff state was `DELIVERED` with ACCEPTED → READY → DISPATCHED → DELIVERED timeline and explicit direct-cancellation block. | `DELIVERED` at `2026-09-04 08:41:22.936785 UTC`, with delivery idempotency key and staff attribution. The delivery snapshot remains populated with the same recorded field lengths. Dispatch movements remained exactly two / two units; reservations remained `CONSUMED`; AD-39 remained 1 / 1 and CC-39 7 / 1. No second deduction occurred. |
| Customer tracking | Customer order detail showed **Delivered**, **Delivery**, paid status, amount 3,380,000 VND, fee 0, and full accepted / ready / dispatched / delivered timeline. A refresh did not change it; My Orders listed `SC-95A6C138` as **Delivered**. | The terminal fulfillment is `DELIVERED`; the order remains `PAID`; two item snapshots and the persisted synthetic delivery snapshot remain intact. |
| Reporting / reconciliation | Branch-scoped report contained an **Online capture / Succeeded** entry for order public ID `95a6c138-…` at 3,380,000 VND. Current inventory table and recent movement list showed both Delivery dispatch effects. | Reporting correlation agrees with the payment, order total, and two dispatch movements. The one existing review-required reconciliation exception is for unrelated `SC-AD73AFBB`, not this Delivery order. |

The local redirect currently lands on `localhost` from the `127.0.0.1` storefront, requiring a same-machine re-login to read the result. The underlying payment succeeded exactly once; this is the previously identified local frontend-result-origin configuration issue, not a duplicate or a payment-state defect. No cancellation or refund action has been performed for this delivered order.

## Scenario D — expired-reservation negative-path checkpoint

`SC-04D9C632` is retained as evidence only and is not the cancellation/refund test order. Its two Pickup reservations expired at `2026-09-04 09:37:00.309969 UTC`. Two normal customer payment attempts from the order UI were rejected before an attempt could be created. Read-only verification immediately afterwards found `PENDING_PAYMENT`, fulfillment `PICKUP / PENDING`, two `ADOPTED` reservation rows / two units, zero `payment` rows, zero `payment_attempt` rows, and zero `PICKUP_HANDOVER` stock movements. A later read-only check found its normal expiry handling had transitioned it to `CANCELLED`; no payment, payment attempt, stock movement, or manual data change occurred. This records the intended rejection path: expired reservation → real payment initiation rejected → no payment or attempt → no stock movement.

## Scenario D — legal pre-handover cancellation / refund

`SC-84339DEF` was created separately through the storefront as one Pickup line: `DEMO-DC-38`, size 38, Natural / Navy, one unit, 990,000 VND. It was paid using the local deterministic demo VNPAY adapter, then cancelled before any fulfillment progression. No real payment or refund credentials or external money movement were used.

| Checkpoint | Browser evidence | Read-only database evidence |
| --- | --- | --- |
| Paid cancellation checkpoint | Payment result showed **Payment confirmed** for 990,000 VND. My Orders showed **Paid — waiting for preparation** and order detail exposed **Cancel before pickup**. | Capture `DDDE9433-77D6-4B1D-8F22-2639390246B0`: `SUCCEEDED`, VNPAY response/status `00`, 990,000 VND. One payment and one successful attempt. Fulfillment was `PICKUP / PENDING`; reservation `3B63A763-83B6-488F-A1A6-BF0877CE0407` was `COMMITTED`, one unit; DC-38 was 9 on hand / 1 reserved / 8 available; no handover movement. |
| Customer cancellation | The customer selected **Cancel before pickup** and confirmed the dialog warning that every item would be cancelled and a full VNPAY refund requested. The terminal screen became **Cancelled — payment refunded**, with `Order cancelled`, Pickup progress `Cancelled`, and `Payment refunded`. | Order `CANCELLED` and Pickup fulfillment `CANCELLED` at `2026-09-04 10:02:32.976381 UTC`. `picking_started_at`, `prepared_at`, and `handed_over_at` remain null. |
| Financial reversal | Customer UI states VNPAY confirmed the full refund; staff fulfillment detail shows **Financial reversal: Succeeded**. | One `SUCCEEDED` Void operation `B8D278F6-D60C-45BE-A6F3-C563DEE3FB82`, requested 990,000 VND; one generation-1 `SUCCEEDED` VoidAttempt `98599AE3-DFBB-4A85-BEF8-80051051939F`, response/status `00`, provider transaction `D14bb8e83a0e47a280acf34a78e4ddb7`; one `SUCCEEDED` ORDER_ITEM allocation of 990,000 VND. The original capture remains immutable and `SUCCEEDED`. |
| Inventory restoration | Staff terminal view says the cancellation won, items remain at the same location, and stock is sellable again. | Reservation became `CANCELLED_RESTORED`. DC-38 changed 9 / 1 / 8 to 9 on hand / 0 reserved / 9 available. Exactly one `CANCELLATION_RESTORE` movement (`BB91AC36-88FF-4AF2-BEB6-480ACF9EE905`): quantity 1, on-hand delta 0, reserved delta -1. No `PICKUP_HANDOVER` movement exists. |
| Refresh / terminal protection | Refreshing My Orders retained **Cancelled — payment refunded**. The staff detail is **No longer actionable**, with no fulfillment-progress action exposed. | Post-refresh: one Void operation, one VoidAttempt, one allocation totaling 990,000 VND, one cancellation-restoration movement / one unit, and no handover movement. Existing `VnPayPaymentExternalIT` also verifies same-key cancellation replay returns the same reversal and does not issue another provider call or restoration movement. |
| Reporting / reconciliation | Branch report shows DC-38 gross 990,000 VND, void 990,000 VND, net 0; the recent movement list shows **Cancellation restore** with on-hand 0 / reserved -1. Financial reconciliation lists both the 990,000 VND online capture and matching successful Void with net effect -990,000 VND. | The report’s one reconciliation exception is the pre-existing unrelated review-required order `SC-AD73AFBB`; this cancellation introduced no additional exception. |

### Scenario D2 — illegal post-terminal cancellation proof

An isolated real SQL Server acceptance fixture was used so that the preserved browser-demo orders were not modified: `VnPayPaymentExternalIT#postHandoverCancellationWithNewKeyIsRejectedWithoutMutation` against `shoe_commerce_phase18_terra_fix_20260904`. It creates a fresh paid Pickup order, progresses it through `PICKING` → `PREPARED` → `HANDED_OVER`, then submits a cancellation through the production `PickupCancellationService` with the new idempotency key `new-post-handover-cancel-key`.

| Checkpoint | Observed integration evidence |
| --- | --- |
| Rejection | The cancellation was rejected as `BusinessConflictException` with exact code `FULFILLMENT_ALREADY_ISSUED`; this is the normal domain guard, not a database or test bypass. |
| Terminal state unchanged | Before/after assertions confirm order `PAID`, Pickup fulfillment `HANDED_OVER`, original capture `SUCCEEDED`, and reservation `CONSUMED`. |
| Inventory unchanged | The post-handover fixture balance remained 0 on-hand / 0 reserved / 0 available. Total stock movements remained 1: exactly one `PICKUP_HANDOVER`; `CANCELLATION_RESTORE` remained 0. |
| Financial unchanged | Void/reversal operations remained 0; VoidAttempts remained 0; the deterministic void-provider call count was unchanged. No refund or duplicate payment effect was created. |
| Verification run | `./mvnw.cmd -Pacceptance '-Dit.test=VnPayPaymentExternalIT#postHandoverCancellationWithNewKeyIsRejectedWithoutMutation' verify` completed with 20 unit tests and 1 Failsafe integration test passing (0 failures, 0 errors). |

Scenario D is **PASS**: the legal pre-handover cancellation/refund path and the illegal post-handover rejection path are both covered. No Scenario E POS or other financial action was performed.

## Scenario E — POS end-to-end

Environment: the same local Vue / Spring Boot / SQL Server Phase 18 demo database. The cash sale was completed through the normal POS browser by the demo cashier; no SQL business-data mutation, fake-sale endpoint, or payment-provider path was used.

| Checkpoint | Browser evidence | Read-only database / automated evidence |
| --- | --- | --- |
| Cashier scope | `cashier.demo` reached the POS workspace. Only `DEMO-01` and `DEMO-02` at `Demo Sales Floor` were offered; the cashier used `DEMO-01`. Direct navigation to `/operations/reports` as this cashier returned the controlled **403 Access denied** workspace. | Account is `ENABLED`, role `CASHIER`, permissions `POS_SELL` and `ORDER_VIEW_SCOPED`, with the active exact-location assignment `DEMO-FLOOR`; it has no reporting grant. |
| Shift | There was no open cashier shift initially. The real POS opened shift `DCE59332-7573-4A6F-AFF6-43D85E651481` on `DEMO-01`; the receipt and reopened POS view both showed the current shift and expected cash. | Shift is `OPEN`, opened `2026-09-04 10:42:54.378410 UTC`, at `DEMO-FLOOR`; expected cash is exactly 990,000 VND. No duplicate shift was created. It remains open deliberately: closing it is not needed for this sale proof and would be an additional state change. |
| Authoritative one-pair sale | The POS page communicates the one-pair / exact-cash flow. The completed browser receipt showed `DEMO-DC-38`, size 38, 990,000 VND, **Exact cash**, immediate handover, and order `b8fd49b5-f850-4ee2-93c6-416f9a7b837a`. | Immediately before the sale, the current price was 990,000 VND and balance was 9 on hand / 0 reserved / 9 available. The resulting immutable item snapshot is one `DEMO-DC-38`, size 38, 990,000 VND. The real limited POS UI exposes confirmation only after exact SKU lookup; quantity/price are not browser-authored sale inputs. |
| Sale and receipt facts | The in-browser receipt identified the sale as complete and handed over, with total 990,000 VND. | `POS` Order `B8FD49B5-F850-4EE2-93C6-416F9A7B837A` is `PAID`; POS sale `C3EE9E26-AAE0-424B-920F-9CCD60A059EF`; CashTender `EA64D1B2-0B7F-411A-84A1-C4BCEBD599BF`; `CASH`, 990,000 VND; Pickup fulfillment `HANDED_OVER`. Exactly one sale, cash tender, and order-linked POS movement exist. Audit has one `POS_CASH_SALE` and one `POS_HANDOVER`. |
| Inventory and channel isolation | The POS receipt confirmed immediate handover. | `DEMO-DC-38` changed exactly from 9 / 0 / 9 to 8 on hand / 0 reserved / 8 available. Exactly one `POS_CASH_SALE` movement `A675448D-61C3-4B60-9CF8-3752DA2DF976` has quantity 1, on-hand -1, reserved 0, and links the intended register and shift. The POS order has no reservation; no active ecommerce reservation exists for this SKU. |
| Refresh / receipt persistence | Opening POS again kept the current shift, `DEMO-01`, and expected cash 990,000 VND, but intentionally returned to the next-sale screen rather than issuing another transaction. The immediate receipt was visible before reopening. | Reopen caused no second sale, tender, movement, or inventory change. Receipt facts are durably persisted and available from the scoped POS receipt resource; the current SPA does not expose a separate historical receipt-reopen route after reload. |
| Reporting and reconciliation | Manager browser reporting for `DEMO-FLOOR` on 2026-09-04 showed POS cash 990,000 VND; `DEMO-DC-38` shows POS 990,000 VND; inventory row shows 8 / 0 / 8; recent movement shows **Pos cash sale** with on-hand -1 / reserved 0. The reconciliation table lists this order as **Pos cash / Accepted**, 990,000 VND, net +990,000 VND. | The report is coherent: gross 16,890,000 VND, POS 990,000 VND, successful voids 990,000 VND, net 15,900,000 VND. The sole exception is the pre-existing unrelated `ad73afbb-…` review-required payment, not this POS transaction. |
| Automated correlation | — | Targeted real-SQL-Server `VerticalSlice6PosExternalIT` passed 10/10; its coverage includes cashier/register scope, exact authoritative price, one-pair cash sale, same-key replay, key conflict, shift open/close races, POS-vs-online final-unit races, and transactional rollback. The same run’s 20 unit tests also passed. |

Scenario E is **PASS**. No further cash, POS, payment, refund, or shift-close operation was performed after this evidence. Scenario F may now consolidate the existing Pickup, Delivery, cancellation, and POS reporting evidence without creating another transaction.

## Scenario F — reporting / reconciliation consolidation

No new order, payment, refund, inventory, or POS action was created for this scenario. Evidence below is a read-only correlation of the completed Phase 18 flows in `DEMO-A / DEMO-FLOOR`, using the report interval 2026-09-04 `[00:00, 24:00)` in `Asia/Ho_Chi_Minh`.

| Scenario | Financial evidence | Net contribution |
| --- | --- | ---: |
| B — Pickup `SC-078B097E` | Successful online capture 5,270,000 VND; Pickup handed over. | 5,270,000 VND |
| C — Delivery `SC-95A6C138` | Successful online capture 3,380,000 VND; fee 0; Delivery delivered. | 3,380,000 VND |
| D — cancelled Pickup `SC-84339DEF` | Successful online capture 990,000 VND and matching successful void 990,000 VND. | 0 VND |
| E — POS cash | Accepted cash tender 990,000 VND; immediate handover. | 990,000 VND |
| **Phase 18 correlated total** | 9,640,000 VND online + 990,000 VND POS − 990,000 VND void. | **9,640,000 VND** |

The manager report rendered the same accounting equation: online successful 15,900,000 VND + POS cash 990,000 VND − successful voids 990,000 VND = net sales 15,900,000 VND. The higher all-report total includes pre-existing bootstrap/demo facts outside the Phase 18 scenarios.

| Order | Stock finalisation | Read-only movement evidence |
| --- | --- | --- |
| `SC-078B097E` | Pickup handover | 2 `PICKUP_HANDOVER` rows / 3 units: AD-39 −2 on-hand / −2 reserved; CC-40 −1 / −1. |
| `SC-95A6C138` | Delivery dispatch | 2 `DELIVERY_DISPATCH` rows / 2 units: AD-39 −1 / −1; CC-39 −1 / −1. |
| `SC-84339DEF` | Cancellation release | 1 `CANCELLATION_RESTORE` row / 1 unit: DC-38 0 on-hand / −1 reserved. |
| POS sale `B8FD49B5-…` | Immediate cash handover | 1 `POS_CASH_SALE` row / 1 unit: DC-38 −1 on-hand / 0 reserved. |

### Reconciliation exception

The single displayed exception is intentional seeded demo history, not a Phase 18 anomaly: `SC-AD73AFBB` remains `PENDING_PAYMENT` with one `REVIEW_REQUIRED` attempt for 1,590,000 VND, no provider transaction number, and no provider event. The report visibly labels it **Review required / Payment review / Excluded from net**; its net contribution is zero. `DemoDataBootstrap` creates this deterministic `review-0` record specifically to exercise the reconciliation-exception surface.

### Access, localization, and responsive evidence

| Check | Observed evidence |
| --- | --- |
| Manager access | `manager.demo` opened the branch-scoped report normally. |
| Cashier restriction | `cashier.demo` direct navigation to `/operations/reports` produced the controlled 403 workspace. |
| Customer restriction | Real SQL Server acceptance coverage rejects the report API for a customer with HTTP 403. |
| Guest restriction | An unauthenticated request to `/api/v1/operations/reports/scope` returned HTTP 401. |
| English UI | Browser inspection showed the English reporting headings, **Pos cash**, **Void**, and reconciliation labels. |
| Vietnamese UI | The human tester switched through the rendered VI control and reported all required Vietnamese headings and labels PASS. |
| Approx. 390 px viewport | The human tester reported no whole-page horizontal overflow and that reporting tables remained reachable. |

Targeted real-SQL-Server acceptance run `VerticalSlice7ReportingExternalIT` passed 5/5 integration tests (0 failures, 0 errors), in addition to the normal 20 unit tests. It covers snapshot-price reporting, POS classification, void netting, excluded review exceptions, branch/time boundaries, and customer report denial.

Scenario F is **PASS**. The report is consistent with the preserved Phase 18 Pickup, Delivery, cancellation/refund, and POS evidence; no report data was altered to obtain this result.
