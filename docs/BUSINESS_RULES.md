# Business Rules — Blueprint v1.1.1

## Rule governance

Only `CONFIRMED` rules are globally mandatory requirements. The v1.1.1
operational invariants below are accepted architecture constraints for the
[approved MVP implementation baseline](MVP_IMPLEMENTATION_BASELINE.md) whenever
their capability is implemented. This acceptance does not promote deferred
features into MVP scope or resolve their `OPEN DECISION` policies.
`LEGACY-ONLY` rules are never ported silently.

## Confirmed invariants

| ID | Rule |
|---|---|
| BR-CAT-001 | Product owns catalog identity; ProductVariant owns sellable SKU attributes; neither owns inventory quantity. |
| BR-INV-001 | Inventory quantity is location-aware and reservation is explicit. |
| BR-INV-002 | `available = onHand - reserved`; reservation is never inferred from unpaid orders. |
| BR-ORD-001 | Order is the central commerce aggregate shared by POS and online workflows. |
| BR-ORD-002 | Confirmed order pricing and applied promotion evidence are immutable. |
| BR-PAY-001 | Financial commands/provider events are idempotent. |
| BR-PAY-002 | Refund references eligible captured value and never exceeds remaining refundable amount. |
| BR-PROMO-001 | Promotion/voucher evaluation is centralized and deterministic. |
| BR-SEC-001 | Authorization and branch/location scope are enforced server-side at the application boundary. |
| BR-SEC-002 | UserAccount, CustomerProfile and EmployeeProfile are distinct. |
| BR-SEC-003 | Reset tokens are hashed; permanent plaintext password compatibility is forbidden. |
| BR-AUD-001 | Business audit is immutable, structured and attributable. |
| BR-AI-001 | AI output is non-authoritative and cannot directly mutate critical business state. |

## Accepted v1.1.1 operational invariants

### Catalog and pricing

| ID | Rule |
|---|---|
| BR-CAT-101 | SKU is globally unique, immutable and never repurposed after history exists. |
| BR-CAT-102 | A sellable variant has one Product, canonical size/option combination and lifecycle state. |
| BR-CAT-103 | Active sellable option combination is unique within Product and size-system context; archive does not free a historical SKU for reuse. |
| BR-CAT-104 | In Vertical Slice 1, a `DRAFT` ProductVariant may publish only with a positive VND current price and positive on-hand balance at at least one enabled Location. |
| BR-PRICE-101 | One Order and all related Payment/Refund records use one currency. |
| BR-PRICE-102 | Client-supplied price/discount/tax totals are never authoritative. |
| BR-PRICE-103 | PriceQuote expires and is revalidated at Order placement; successful placement snapshots its evidence immutably. |
| BR-MON-101 | Initial currency is VND stored/calculated as exact integer đồng; binary floating point is forbidden. |
| BR-MON-102 | Percentage component totals use `HALF_UP` once to one đồng; allocated shares use largest remainder with stable OrderItem-ID tie-break. |
| BR-MON-103 | `itemGross = sum(baseUnitPrice * quantity)` and `discountedItemSubtotal = itemGross - itemDiscount`. |
| BR-MON-104 | `voucherBase = discountedItemSubtotal - orderDiscount`; `merchandiseNet = voucherBase - voucherDiscount`. |
| BR-MON-105 | `shippingNet = shippingFee - shippingDiscount`; initial `tax = 0`; `finalPayable = merchandiseNet + shippingNet + tax`. |
| BR-MON-106 | Every monetary component is non-negative, a discount cannot exceed its base, and all line/unit allocations sum exactly to the rounded Order component. |
| BR-MON-107 | A zero-payable Order uses the ordinary confirmation transaction without an external PaymentAttempt. |

### Scope and authorization

| ID | Rule |
|---|---|
| BR-SCOPE-101 | Every Order has one server-assigned responsible Branch. |
| BR-SCOPE-102 | Every inventory mutation identifies one authorized Location; Branch alone is not an inventory balance. |
| BR-SCOPE-103 | POS order scope is derived from the active Register/CashierShift, not request branch fields. |
| BR-SCOPE-104 | Staff access to customer data is use-case/minimum-field based; customer profiles are not globally branch-owned. |
| BR-SCOPE-105 | Disabled account, employee, assignment or revoked authorization version invalidates sensitive session authority. |

### Cross-domain locking

| ID | Rule |
|---|---|
| BR-LOCK-101 | Command/idempotency or ProviderEvent identity is claimed before business effects. |
| BR-LOCK-102 | A transaction may start at any required rank but then acquires only increasing ranks: CashierShift -> Order -> Fulfillment/Return -> Payment/Refund -> Price/Promotion/Voucher usage -> Reservation -> InventoryBalance. POS sale/close fence the Shift first; rows created by the sale are not pre-existing lock targets. |
| BR-LOCK-103 | Within a rank, rows use stable ascending keys; duplicate inventory demand is summed by `(location, variant)` before locks. |
| BR-LOCK-104 | No provider/email/carrier/AI call occurs while database locks are held. |
| BR-LOCK-105 | Deadlock/lock-timeout rolls back the whole local transaction; bounded retry reuses the original idempotency key/fingerprint. Unknown commit outcome is queried, never retried with a new key. |
| BR-LOCK-106 | Physical Return restock and financial Refund are separate commands; a flow holding InventoryBalance never requests Payment. |

### Inventory

| ID | Rule |
|---|---|
| BR-INV-101 | `onHand >= 0`, `reserved >= 0`, and `reserved <= onHand`; backorder is disabled for v1.1. |
| BR-INV-102 | Reserve sums requested quantity per `(variant, location)`, follows the global hierarchy, locks each balance in canonical order and succeeds only if aggregate available quantity is sufficient. |
| BR-INV-103 | Commit/release/expire is a terminal conditional Reservation transition and changes a balance at most once. |
| BR-INV-104 | Quantity-changing operations create exactly one immutable StockMovement per business operation line. |
| BR-INV-105 | Reservation expiry cannot win against a commit that already holds the same reservation/balance locks. |
| BR-INV-106 | First-release allocation uses one Location per OrderItem; split allocation is deferred. |
| BR-INV-107 | Transfer dispatch removes source on-hand; receipt adds destination on-hand; in-transit stock is unavailable. |

### Checkout and order

| ID | Rule |
|---|---|
| BR-CHK-101 | CheckoutAttempt is uniquely identified by actor/client idempotency scope and request fingerprint. |
| BR-CHK-102 | Reusing a key with the same fingerprint returns the recorded outcome; a different fingerprint is rejected. |
| BR-CHK-103 | Cart and quote do not reserve stock or consume vouchers. Resource holds begin only in Order placement. |
| BR-CHK-104 | Online Order placement atomically revalidates quote/voucher, creates pending Order, reserves stock and reserves voucher usage. |
| BR-CHK-105 | Online order becomes financially binding/confirmed only after successful capture/tender confirmation and atomic stock commit/voucher redemption. |
| BR-CHK-106 | If captured payment arrives after Order cancellation/expiry or resource release, Order is not reopened; reconciliation initiates idempotent void/refund. |
| BR-CHK-107 | Limited POS cash confirmation atomically records one `PAID` POS Order, exact CashTender, immediate handover, `onHand -= 1`, one StockMovement and audit evidence; it creates no Reservation or PaymentAttempt. |
| BR-CHK-108 | Placement/confirmation/cancellation/expiry acquire every required Shift, Order, Fulfillment/Return, Payment, Benefit/Voucher, Reservation and Balance lock only in BR-LOCK-102 order and revalidate protected facts under those fences. |
| BR-CAN-101 | Unconfirmed cancellation/expiry releases active stock/Voucher holds exactly once and does not create an on-hand restoration movement. |
| BR-CAN-102 | Under ADR-0022 stock semantics, an allowed confirmed pre-handover cancellation atomically fences Order and Fulfillment, leaves `onHand` unchanged, decrements `reserved`, transitions the historical Reservation `COMMITTED -> CANCELLED_RESTORED`, and appends one immutable `CANCELLATION_RESTORE` per Order. |
| BR-CAN-103 | Dispatch and confirmed cancellation lock Order then Fulfillment; exactly one wins. A dispatch winner requires return-to-sender/Return compensation and forbids direct cancellation stock restoration. |
| BR-CAN-104 | Each cancellation restoration has a unique Order/OrderItem operation key; retry returns the recorded result and financial-provider failure cannot repeat the stock movement. |
| BR-CAN-105 | When confirmed cancellation has captured exposure, the local cancellation transaction records the selected active void/refund reservation under Payment before any external call; success permits final cancellation and failure remains explicit reconciliation without another stock restoration. |

### POS register and cash

| ID | Rule |
|---|---|
| BR-CASH-101 | Exactly zero or one `OPEN` CashierShift exists per Register and per Cashier; SQL Server filtered unique indexes are the final invariant. |
| BR-CASH-102 | A limited POS sale is quantity one, current server-priced VND, exact cash, and immutable under `(Shift, Idempotency-Key)` plus variant fingerprint. |
| BR-CASH-103 | For the limited slice, `expectedCash = sum(successful CashTender amount)`; there is no opening float, change, counted cash, paid-in/out, refund, or variance. |
| BR-CASH-104 | Shift close is idempotent and serializes with sale on the same Shift lock: close-first rejects the sale; sale-first contributes to expected cash before close. |

### Payment and refund

| ID | Rule |
|---|---|
| BR-PAY-101 | Provider event ID and provider transaction reference are unique in their provider scope. |
| BR-PAY-102 | Successful captures cannot exceed the Order payable amount under the selected payment policy. |
| BR-PAY-103 | Unknown/out-of-order/conflicting provider results are preserved and reconciled; they do not overwrite terminal history. |
| BR-PAY-104 | A successful financial void reverses previously successful captured exposure; authorization-only cancellation is a distinct fact and does not reduce captured exposure. |
| BR-PAY-105 | Void and Refund use the same captured capacity under the Payment lock. Pending/unknown void amount remains actively fenced; success converts it to `successfulVoided`; definitive failure releases it exactly once. |
| BR-PAY-106 | For every paid component `c`, `successfulVoid(c) + successfulRefund(c) + activeVoid(c) + activeRefund(c) <= capturedComponentCapacity(c)`. The MVP sets capacity from the immutable Order paid component only after full capture/cash settlement; partial/split capture is `DEFERRED`. |
| BR-PAY-107 | Every active reversal attempt has positive immutable component allocations whose sum equals its reserved amount. Under the Payment lock all allocations atomically become `SUCCEEDED` on success or `RELEASED` on definitive failure; pending/unknown leaves all `ACTIVE`, and partial conversion/release is forbidden. |
| BR-PAY-108 | Provider outcomes correlate to the exact attempt generation; stale events cannot mutate newer attempts. Reports consume only `SUCCEEDED` allocations, and callbacks/reports never reconstruct them. |
| BR-PAY-109 | A verified VNPAY success before hold expiry atomically changes Attempt to `SUCCEEDED`, Order to `PAID`, and Reservation to `COMMITTED`; payment does not issue physical stock, so on-hand and reserved remain unchanged. |
| BR-PAY-110 | A verified VNPAY failure changes only Attempt to `FAILED`; while the Order and Reservation remain eligible, a new idempotency key may create a new attempt and at most one attempt is `PENDING`. |
| BR-PAY-111 | A verified success after cancellation/expiry is recorded as `REVIEW_REQUIRED`; it cannot reopen Order or recreate/re-reserve inventory, and automated void/refund remains deferred. |
| BR-PAY-112 | Browser Return is presentation-only. Only a verified VNPAY IPN with matching merchant, merchant reference, exact amount/currency and provider transaction facts may establish payment state. |
| BR-REF-101 | `successfulVoided + successfulRefunded + activeVoidReserved + activeRefundReserved <= successfulCaptured`; unknown void/refund outcomes remain active. |
| BR-REF-102 | Refund amount reservation and state creation, retry re-reservation, success conversion and definitive-failure release occur atomically under the Payment lock. |
| BR-REF-103 | Same refund idempotency key returns the same result; different concurrent refunds compete for remaining refundable amount. |
| BR-REF-104 | Refund may reference Return or an authorized refund-without-return reason; refund never directly restocks. |
| BR-REF-105 | Provider timeout/ambiguous result never releases refund capacity or permits a new attempt; it enters reconciliation. |
| BR-REF-106 | A contractually definitive failed RefundAttempt is immutable and releases only its own reservation exactly once. Retry creates a new attempt after capacity is reacquired under Payment lock. |
| BR-REF-107 | Provider events correlate to one attempt generation; an old failure cannot release a newer attempt reservation. |
| BR-REF-108 | Refund maximum uses remaining snapshotted line/shipping allocations after successful Void/Refund allocations and eligible captured value after active/successful void exposure; current promotion definitions are never recalculated. |
| BR-REF-109 | A partial item return does not refund shipping. A full pre-fulfillment cancellation may refund snapshotted `shippingNet`; fulfilled-order shipping is non-refundable in the minimum policy. |
| BR-REF-110 | Voucher/promotion discounts have no cash value beyond the customer's allocated paid amount; no refund automatically reissues a benefit. |
| BR-REF-111 | `remainingRefundable = successfulCaptured - successfulVoided - activeVoidReserved - successfulRefunded - activeRefundReserved`; successful Void and Refund allocations cannot consume the same Order component twice. |

### Fulfillment and return

| ID | Rule |
|---|---|
| BR-FUL-101 | Handover/delivery/cancellation quantity transitions are versioned and cannot make fulfilled or remaining quantities inconsistent. |
| BR-FUL-102 | Cancellation racing dispatch/handover has one conditional winner; if fulfillment wins, compensation follows return-to-sender/return policy. |
| BR-FUL-103 | Atomic cart checkout creates exactly one `PENDING` Pickup or Delivery intent at the common reserved Location. Pickup requires an eligible selected Location; Delivery snapshots validated receiver name, phone, address and optional note. |
| BR-FUL-104 | Accepting preparation locks the authoritative Fulfillment and permits exactly one `PENDING -> PICKING` transition, sets `pickingStartedAt` once, requires current `FULFILL_ORDER` plus exact active Location scope, and changes no commercial or inventory fact. |
| BR-FUL-105 | Pickup preparation permits `PENDING/PICKING -> PREPARED` without stock mutation. Idempotent handover requires `PREPARED`, locks Order then Fulfillment then Reservation and Balance, applies `onHand -= quantity` and `reserved -= quantity`, changes the Reservation to `CONSUMED`, and appends one `PICKUP_HANDOVER`. |
| BR-FUL-106 | Delivery preparation permits `PENDING/PICKING -> PREPARED`; idempotent dispatch requires `PREPARED`, consumes every committed Order reservation exactly once, and appends one `DELIVERY_DISPATCH` movement per OrderItem. |
| BR-FUL-107 | `OUT_FOR_DELIVERY -> DELIVERED` records actor, time and idempotency evidence and does not mutate inventory a second time. |
| BR-FUL-108 | Confirmed cancellation may win only before pickup handover or delivery dispatch. The winner restores every committed reservation as one whole-order decision; after physical issue the future Return workflow is required. |
| BR-RET-101 | Return creation locks/version-checks authoritative Fulfillment/return quantity facts and cannot exceed delivered/handed-over minus already accepted return quantity. |
| BR-RET-102 | Only approved inspection disposition creates an idempotent Inventory return movement. |

### Promotion, voucher and engagement

| ID | Rule |
|---|---|
| BR-PROMO-101 | Promotion evaluation order, priority, exclusivity, tie-break and rounding are deterministic and versioned. |
| BR-PROMO-102 | First supported stacking permits at most one item promotion per line, one automatic order promotion, one Voucher and one shipping benefit; these layers stack in that order. |
| BR-PROMO-103 | Candidates within one layer are mutually exclusive and resolve by priority descending then stable definition ID ascending; lowest-price-wins is not implicit. |
| BR-VCH-101 | Voucher Definition, Voucher Issuance and Voucher Redemption are separate identities. |
| BR-VCH-102 | Voucher issuance is deduplicated per campaign/customer/definition according to policy. |
| BR-VCH-103 | Limited voucher reservation/redemption is atomic and concurrency safe. |
| BR-VCH-104 | Voucher is reserved at Order placement and redeemed at successful confirmation; failure/expiry releases it. |
| BR-VCH-105 | Voucher discount is allocated to eligible OrderItems at placement; a refund returns only paid allocated value and never automatically restores/reissues the Voucher. |
| BR-VCH-106 | Releasing a `RESERVED` issuance under the Voucher/usage lock returns it to `ISSUED` only if still valid; otherwise it becomes `REVOKED` when an effective revocation exists or `EXPIRED` when its validity has ended. Identical release replay has no second usage effect. |
| BR-MKT-101 | Marketing consent/unfollow suppresses unsent delivery; targeting never bypasses voucher/promotion eligibility. |

### Security, audit and notification

| ID | Rule |
|---|---|
| BR-SEC-101 | Password reset consume, password change, token terminal state and required session revocation are one transaction. |
| BR-SEC-102 | Issuing a new reset token revokes earlier active reset tokens for the same account/purpose. |
| BR-SEC-103 | Sensitive commands re-evaluate current account/assignment/authorization version. |
| BR-AUD-101 | Audit payload is redacted and cannot contain credentials, reset tokens, provider secrets or raw sensitive AI input. |
| BR-AUD-102 | A required AuditEvent is inserted in the protected command transaction; if it cannot persist, the privileged command fails. |
| BR-AUD-103 | Audit records are append-only for the application role, never cascade-deleted, and identify human/service/integration/system actor plus correlation and resource scope. |
| BR-NOT-101 | Mandatory security/transaction notifications are not suppressed by marketing preferences. |
| BR-NOT-102 | Notification intent/delivery has a unique dedupe key and idempotent retry behavior. |
| BR-NOT-103 | A worker claim is conditional and leased; attempt count, next-attempt time and terminal failure remain operationally visible. |

### Reporting

| ID | Rule |
|---|---|
| BR-RPT-101 | `REPORTING_GLOSSARY.md` is authoritative for metric names, equations, source facts, included/excluded states, branch attribution and time basis. |
| BR-RPT-102 | Financial metrics use immutable Order snapshots plus successful Payment/Refund facts; UI state, mutable text and cached dashboard totals are never sources of truth. |
| BR-RPT-103 | Financial event instants are stored in UTC; reporting days use `Asia/Ho_Chi_Minh` and half-open `[from, to)` intervals. |
| BR-RPT-104 | Sales and refunds attribute to Order responsible Branch; stock/transfer metrics retain source/destination Location and never become sales. |
| BR-RPT-105 | Inventory monetary value is not reported until an acquisition-cost/valuation model is confirmed; quantity and movement metrics remain valid. |

## Layer enforcement matrix

| Invariant | Domain | Application transaction | Database | API | Required test |
|---|---|---|---|---|---|
| Global lock hierarchy | ordered resource policy | acquire increasing ranks only | row locks/rollback | same-key retry/unknown query | cross-flow deadlock races |
| Available stock/reservation terminality | Guard transitions | Lock/conditional update | checks, version, unique operation | conflict code | concurrent integration |
| Checkout idempotency | fingerprint policy | return prior outcome | unique key/scope | required key on retryable commands | replay/mismatch |
| Voucher usage limit | eligibility | atomic reserve/redeem | unique issuance/redemption and counter guard | never trust eligibility | concurrent redemption |
| Order monetary equation/allocation | Money/promotion rules | calculate, round, allocate and snapshot | exact VND/non-negative fields | never trust totals | golden arithmetic/allocation |
| Capture/void/refund amounts | Money/state rules | Payment lock; unknown holds; definitive failure releases | valid money, unique refs/attempt/allocation | idempotency key | concurrent void/refund/timeout/failure retry |
| Branch/location scope | authorization policy | derive/verify scope | ownership FKs where possible | never trust arbitrary scope | cross-branch negative |
| Confirmed price immutability | Order invariant | confirmation | no mutable confirmed-line path | no edit contract | integration |
| Reset token single use | token lifecycle | atomic consume | unique hash/status/version | generic response | replay/race |
| Audit/notification dedupe | policy | transactional intent when required | append-only/unique dedupe | no client-authored audit | failure/retry |
| Report metric reconciliation | glossary equations | projection/query only | immutable facts/indexes | expose `asOf`/dimensions | capture/refund/cancel fixtures |

## Legacy-only rules requiring explicit disposition

| Rule | Status | v1.1 treatment |
|---|---|---|
| Customer must be at least 15 | `LEGACY-ONLY` | Do not port without business/legal confirmation |
| Employee must be at least 18 | `LEGACY-ONLY` | Confirm with project/HR requirement |
| Vietnamese mobile prefixes only | `LEGACY-ONLY` | Replace with approved contact policy |
| Price divisible by VND 1,000 | `LEGACY-ONLY` | Do not port; `VND_V1` rounds only fractional đồng as specified |
| Active product requires image | `LEGACY-ONLY` | Optional publication rule only |
| Promotion no-overlap / lowest price wins | `LEGACY-ONLY` | Replaced by explicit priority/stacking policy |
| Unpaid invoice reserves stock | `DEPRECATED` | Explicit Reservation only |
| One cash payment per invoice | `DEPRECATED` | Payment attempts/transactions/tenders |
| Localized states/roles and plaintext fallback | `DEPRECATED` | Stable identifiers and secure credentials |

## Open business decisions

- Legal tax calculation and receipt/e-invoice rules beyond the initial
  VND-integer, `tax = 0` monetary model.
- Reservation/payment-grace values for channels beyond implemented customer
  quote checkout, and the retry window. Customer quote checkout currently uses
  one server-configured Reservation TTL with a 15-minute default.
- Declared/counted cash, opening float, change, variance approval, cash
  adjustment, refund, multi-line sale, and non-cash tender beyond the confirmed
  exact-cash POS slice.
- Responsible Branch/Location allocation, pickup evidence, Reservation TTL, and
  payment grace for the selected online-pickup flow.
- Return window, eligibility, fees and refund approval thresholds.
- Promotion types/merchant-specific stacking beyond the frozen first matrix,
  advanced caps and any explicit voucher reissue/reversal policy.
- Role/permission matrix and maker-checker thresholds.
- Customer verification, consent and data-retention rules.
- Required shoe size systems and barcode/scanner behavior.
- Acquisition cost and inventory valuation method; monetary Inventory Value
  reporting remains deferred until this is confirmed.
# Customer catalog and base-price quote — Vertical Slice 2

- `CONFIRMED`: A registered customer requires `CATALOG_BROWSE` to browse storefront products, inspect published variants, and request a base-price quote.
- `CONFIRMED`: Storefront visibility requires a published ProductVariant and an effective base-price version.
- `CONFIRMED`: Customer availability is derived from enabled-location balances using `available = onHand - reserved`; only `AVAILABLE` or `UNAVAILABLE` is exposed.
- `CONFIRMED`: Browse, detail, and quote do not consume `onHand` or create a
  Reservation/Order. They may lazily normalize an overdue checkout hold,
  releasing `reserved` and moving its Reservation/Order to terminal states.
- `CONFIRMED`: Base-price changes close the current effective version and append a new version; historical versions and quote evidence are retained.
- `CONFIRMED`: A quote snapshots exact integer VND amount, source price-version ID, owner, quote time, and a 15-minute expiry.
- `CONFIRMED`: Quote creation for an unknown/unpublished variant is hidden as not found; an unavailable published variant is rejected and does not create a normal quote.
- `CONFIRMED`: A quote does not reserve stock and is not an Order, Payment, Promotion, Voucher, or checkout guarantee.

# Quote checkout, reservation and Order — Vertical Slice 3

- `CONFIRMED`: Checkout accepts only an owned persisted PriceQuote and a scoped opaque idempotency key; amount, currency, total, Location, and Order identity are server-owned.
- `CONFIRMED`: One PriceQuote may create at most one successful Order. Same customer/key/quote replays that Order; the same customer/key with another quote is a conflict.
- `CONFIRMED`: Checkout rechecks quote expiry, variant publication, enabled Location, and available stock using server time and current database state.
- `CONFIRMED`: Quantity is fixed at one. The first enabled Location with stock in stable internal Location-ID order is selected and locked; customer APIs expose neither Location choice nor exact quantity.
- `CONFIRMED`: Quote validation, `reserved += 1`, Reservation/Order snapshots, and required audit evidence share one SQL Server transaction and roll back together.
- `CONFIRMED`: Checkout leaves `onHand` unchanged, creates an `ADOPTED` Reservation and a `PENDING_PAYMENT` Order, and preserves `available = onHand - reserved`.
- `CONFIRMED`: A successful checkout creates a fresh Reservation deadline from
  its server creation instant plus the configured Reservation TTL; it never
  reuses the quote deadline. Relevant catalog, quote, or checkout reads lazily
  expire an overdue unpaid hold, release `reserved`, and cancel that unpaid
  Order using server time.
