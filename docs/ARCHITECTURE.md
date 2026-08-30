# Architecture — Blueprint v1.1.1

## Status and baseline

- Architecture status: `ACCEPTED FOR THE APPROVED MVP BASELINE`
- Implementation-planning status: `APPROVED MVP IMPLEMENTATION BASELINE`
- Modular monolith, REST-first backend, one Vue SPA and SQL Server are the
  approved MVP baseline.
- Detailed scope, sequencing, and remaining gates are indexed in
  [MVP_IMPLEMENTATION_BASELINE.md](MVP_IMPLEMENTATION_BASELINE.md).

## Governing decision records

The records below are accepted architecture decisions under
[ADR governance](ADR/README.md). Acceptance of a boundary does not admit a
deferred capability into MVP implementation scope.

| Boundary | ADR |
|---|---|
| Deployment shape | [ADR-0001](ADR/0001-modular-monolith.md) |
| REST/frontend split | [ADR-0002](ADR/0002-rest-first-vue-spa.md) |
| Order-centered commerce | [ADR-0003](ADR/0003-order-centered-commerce.md) |
| Location inventory/concurrency | [ADR-0004](ADR/0004-location-aware-inventory.md) |
| Payment/refund accounting | [ADR-0005](ADR/0005-idempotent-payment-refund.md) |
| Identity and scoped authorization | [ADR-0006](ADR/0006-identity-rbac-branch-scope.md) |
| Pricing/promotion/voucher | [ADR-0007](ADR/0007-central-pricing-promotion.md) |
| AI proposal boundary | [ADR-0008](ADR/0008-ai-proposal-boundary.md) |
| SQL Server/versioned migrations | [ADR-0009](ADR/0009-versioned-sql-server-persistence.md) |
| Checkout consistency/recovery | [ADR-0010](ADR/0010-checkout-consistency-and-recovery.md) |
| Branch/location ownership | [ADR-0011](ADR/0011-multi-branch-ownership-and-scope.md) |
| Audit/reliable delivery | [ADR-0012](ADR/0012-transactional-audit-and-sql-delivery.md) |
| Provider payment-result application | [ADR-0016](ADR/0016-idempotent-provider-payment-result.md) |
| Location-scoped pickup fulfillment creation | [ADR-0017](ADR/0017-location-scoped-pickup-fulfillment.md) |
| Location-scoped pickup picking start | [ADR-0018](ADR/0018-start-location-scoped-pickup-picking.md) |
| Customer catalog and versioned quotes | [ADR-0019](ADR/0019-customer-catalog-and-versioned-price-quotes.md) |
| Quote checkout and atomic reservation | [ADR-0020](ADR/0020-quote-checkout-atomic-reservation.md) |
| Independent checkout reservation expiry | [ADR-0021](ADR/0021-independent-checkout-reservation-expiry.md) |

```text
Customer Web | POS | Admin | future Mobile
                    |
                 REST API
                    |
           Application Use Cases
                    |
              Domain Modules
                    |
          Infrastructure Adapters
                    |
                SQL Server
```

## Dependency and ownership rules

```text
web -> application -> domain
infrastructure ------^  (implements ports)
```

- Controllers handle HTTP, DTO validation and mapping; one request invokes an
  application use case.
- Application use cases own transaction boundaries, authorization and cross-
  aggregate coordination.
- Aggregates own their transitions and invariants.
- Infrastructure owns provider/JPA/SQL mechanics, not business decisions.
- No controller or domain writes another domain through its repository.
- Query/read models may join domains but never become write authority.

## Proposed logical modules

```text
identity-access
customer
employee
branch-location-pos
catalog
pricing
promotion-voucher
cart-order-checkout
inventory
payment
fulfillment-return
refund
notification
audit
reporting
ai-recommendation
content
```

They are boundaries inside one deployment, not separate services. A shared
kernel is limited to proven primitives such as Money, stable IDs and Clock; it
must not become a cross-domain utility dump.

## Cross-domain transaction model

### Local decisions

When all affected records live in SQL Server and form one business decision,
the application use case uses one local database transaction. Examples:

- Order placement + Inventory reservation + Voucher reservation + immutable
  Order price snapshot.
- POS cash confirmation + Order confirmation + stock commit + voucher redemption
  + tender entry + audit/required notification intent.
- Payment callback record + legal Payment transition + stock/voucher commit +
  Order confirmation when all preconditions remain valid.
- Refund amount reservation + Refund creation.

Module ownership still applies inside the transaction: the coordinator calls
domain/application ports; it does not bypass invariants.

### External decisions

Payment/email/carrier/AI calls never remain inside a database transaction.
Before an external call, persist durable pending intent and commit. Callback or
retry processing uses provider event IDs, idempotency keys, legal transitions
and reconciliation state.

No distributed transaction is required.

## Canonical database lock hierarchy

All transactions may start at the first rank they need, but thereafter acquire
only increasing ranks. Within a rank, duplicate targets are normalized and rows
are acquired by the stated stable key. No flow may hold a higher-ranked business
lock and then request a lower-ranked one.

| Rank | Locked/fenced record | Canonical key and purpose |
|---:|---|---|
| 0 | Command identity: CheckoutAttempt/idempotency claim or ProviderEvent claim | Scoped operation/event key; claim before business effects so concurrent duplicates have one owner |
| 1 | Order | Order internal ID ascending; establishes commercial state fence |
| 2 | Fulfillment records, then Return records | Record type in that order, then ID ascending; serializes dispatch/handover, cancellation and returnable quantity without changing aggregate ownership |
| 3 | Payment root, then PaymentAttempt, VoidOperation/VoidAttempt/VoidAllocation, Refund and RefundAttempt children | Payment ID, then child type in the listed order and child ID ascending; serializes capture/void/refund capacity |
| 4 | PriceVersion, PromotionDefinition, limited PromotionUsage, Voucher, VoucherUsage and VoucherIssuance fences | Record type in the listed order, then stable ID ascending; prevents edit/expiry/usage TOCTOU |
| 5 | InventoryReservation | Reservation ID/business reference ascending; serializes terminality/history |
| 6 | InventoryBalance | `(locationId, productVariantId)` ascending after duplicate demand is summed |
| 7 | CashierShift | Shift ID ascending; revalidates open shift immediately before tender entry |

Append-only PaymentTransaction, StockMovement, tender, AuditEvent and
NotificationIntent rows are inserted after their owning locks are held; they are
effects, not earlier lock ranks.

Rules:

- Identify foreign keys before locking, but do not lock Fulfillment, Return or
  Payment and then request their Order. A callback resolves IDs first, locks
  Order, then every needed later rank.
- A transaction needing only Fulfillment/Return starts at rank 2; one needing
  only Payment/Refund starts at rank 3. A balance-only adjustment/transfer starts
  at rank 6. Skipped ranks need not be locked.
- New Order/Payment/Reservation rows are inserted at their logical rank inside
  the transaction; later ranks are not acquired first merely because the row is
  new.
- Price/promotion calculation may be previewed before the transaction, but the
  versions, time boundary and limited usage are re-read/fenced at rank 4 before
  the snapshot becomes authoritative.
- CashierShift may be read for preliminary authorization, but POS cash re-locks
  it at rank 7 and rechecks `OPEN`. Shift close never holds rank 7 and requests
  Order/Payment/Inventory locks.
- Physical Return inspection/restock and financial Refund remain separate
  transactions. Restock starts at rank 2 Fulfillment/Return and then rank 6
  balance; a Return-linked Refund locks rank 2 evidence before rank 3 Payment.
  They must not be combined into a reverse-order mega-transaction.

External payment/void/refund/provider calls never occur while database locks are
held. A local transaction persists/claims durable PaymentAttempt or RefundAttempt
and commits; the adapter call follows. Callback/poll processing opens a new
transaction and follows the hierarchy again.

### Lock hierarchy by critical flow

| Flow | Locks/fences in order |
|---|---|
| Online placement | 0 CheckoutAttempt -> 1 new Order -> 4 price/promotion/voucher -> 5 new Reservation -> 6 balances |
| POS cash placement/confirmation | 0 CheckoutAttempt -> 1 new Order -> 3 new Payment -> 4 benefit/voucher -> 5 new Reservation -> 6 balances -> 7 Shift |
| Electronic payment initiation | 0 command -> 1 Order -> 3 Payment/PaymentAttempt; commit, then provider call |
| Payment confirmation/reconciliation | 0 ProviderEvent -> 1 Order -> 3 Payment -> 4 Voucher/usage -> 5 Reservation -> 6 balances |
| Unconfirmed cancellation or hold expiry | 0 command/job -> 1 Order -> 3 Payment if present -> 4 Voucher/usage -> 5 Reservation -> 6 balances |
| Confirmed pre-dispatch cancellation | 0 command -> 1 Order -> 2 Fulfillment -> 3 Payment/reversal intent -> 5 committed Reservation -> 6 balances/restoration movement |
| Pickup handover | 0 handover key -> 1 Order -> 2 Fulfillment -> 5 committed Reservation -> 6 Balance; append `PICKUP_HANDOVER` |
| Reservation commit/release | 1 owning Order when commercial state changes -> 3 Payment if confirmation requires it -> 4 Voucher if coupled -> 5 Reservation -> 6 balance |
| Inventory receive/adjust/transfer | 6 affected balances in canonical key order; no Order/Payment/Voucher locks |
| Voucher claim/expiry without Order mutation | 4 Voucher/Issuance/usage only |
| Refund create/retry/callback without Return evidence | 0 command/provider event -> 3 Payment -> Refund/RefundAttempt children; no Inventory lock |
| Refund requiring Return evidence | 0 command -> 2 Fulfillment/Return -> 3 Payment -> Refund/RefundAttempt children; no Inventory lock |
| Return create/inspection/restock | 0 command -> 2 Fulfillment/Return -> 6 balance when approved restock occurs |
| CashierShift close | 7 Shift only; it never requests an earlier rank |

### Lock race outcomes

| Scenario | Transaction order and terminal condition | Deadlock/retry behavior |
|---|---|---|
| Two customers purchase the final SKU | Each claims its operation, creates its own new Order, then competes at rank 6 after summing demand. First commit reserves; second reads zero available and rolls back placement. | No cycle; loser receives deterministic stock conflict using the same operation identity. |
| POS competes with online | Both converge on the same rank-6 balance. POS locks Shift only after balance; online never locks Shift. | No reverse Shift->Balance path; one reserve/commit wins. |
| Reservation expiry races confirmation | Both lock Order -> Payment if present -> Voucher -> Reservation -> Balance. Only one can transition Reservation from `ACTIVE`. | No documented cycle; loser returns/reconciles the recorded terminal state. |
| Cancellation races payment confirmation | Both lock the same Order first, then follow identical ranks. Confirmed capture blocks ordinary cancellation; cancellation winner makes a later capture reconciliation-only. | Same-order serialization; retry/query by original key. |
| Two redemptions race for final voucher use | Both lock the same rank-4 usage/issuance row before Reservation/Balance. One reserves/redeems; the other fails eligibility before payment. | No Inventory->Voucher reverse path; deterministic benefit conflict. |
| Confirmed cancellation races dispatch | Both lock Order then the same rank-2 Fulfillment. Cancellation winner marks Fulfillment cancelled and creates one rank-6 restoration movement; dispatch winner forbids direct restoration. | One conditional winner; retry uses the same operation key and cannot restore twice. |
| Return/refund races another refund | A Return-linked Refund locks rank-2 evidence then rank-3 Payment. Physical restock continues to rank 6 and never requests Payment afterward. | Concurrent amount requests recheck remaining reversible value; no Payment-after-Balance path. |

On a database deadlock/lock-timeout before any external call, the entire local
transaction rolls back. The application may perform a bounded retry with the
same idempotency key/fingerprint and backoff. If commit outcome is unknown, it
queries the durable operation; it never chooses a new key. Provider retries use
the existing attempt/provider idempotency identity.

## Deterministic checkout protocols

Core MVP pricing is base price without Voucher. Voucher locks, reservations,
redemption, and related transitions in the protocols below apply only if the
optional Voucher slice is separately admitted.

The implemented one-line customer checkout is the bounded protocol in
ADR-0020: an account-row command fence, immutable quote validation,
deterministic Location selection, one pessimistically locked Balance, and new
Reservation/Order/audit facts in one transaction. Existing-Order transitions
continue to follow the global hierarchy. Expired checkout holds are released in
a separate short Order -> Reservation -> Balance transaction before new work.
The quote deadline is checked only when checkout validates the quote; the new
Reservation receives its own server-time deadline from the configured checkout
Reservation TTL. Quote, checkout, and customer catalog availability paths first
select only relevant expired checkout holds (status/deadline plus variant or
visible product scope) and normalize each affected variant through that same
short transaction before deciding availability.

### Shared placement transaction

1. Resolve actor/customer/channel and server-owned branch/location scope; group
   requested quantity by `(location, variant)` and benefit identity.
2. Claim/resolve CheckoutAttempt at rank 0 by scoped idempotency key and request
   fingerprint.
3. Insert/lock the Order at rank 1, then fence effective price/promotion versions
   and limited Voucher/usage records at rank 4.
4. Insert/lock Reservation records at rank 5, then lock all balances at rank 6
   in canonical `(location, variant)` order.
5. Under those fences, revalidate time, price, promotion/voucher ownership and
   limits, aggregate stock demand and hold deadline.
6. Atomically persist the pending Order monetary snapshots, active Reservation,
   reserved Voucher usage and CheckoutAttempt `RESOURCES_HELD` state.
7. Return the same outcome on replay; a changed fingerprint is a conflict.

Price/promotion become authoritative for that Order at step 5 until its payment
deadline. Stock and voucher are held, not consumed.

### Online electronic payment

The currently implemented Vertical Slice 5 boundary uses the narrower
`PENDING_PAYMENT -> PAID`, `PENDING -> SUCCEEDED|FAILED`, and
`ADOPTED -> CONSUMED` lifecycle from ADR-0016. It has no provider call,
Voucher, retry, PaymentTransaction expansion, or reconciliation workflow.

1. After placement commit, create/reuse PaymentAttempt and call the provider.
2. While pending, Order remains `PENDING_PAYMENT`; resource holds remain active
   until the approved deadline/grace rule.
3. On an authenticated provider event, claim the unique event, resolve IDs
   without business locks, then lock Order -> Payment -> Voucher/usage ->
   Reservation -> balances.
4. If resources are active and Order eligible, record capture, commit stock,
   redeem voucher and confirm Order atomically.
5. In the current bounded slice, if Order/resources are terminal, persist a
   rejected provider-event receipt, keep commercial state terminal, and return
   conflict. Captured-money reconciliation and void/refund remain deferred.
6. The current `FAILURE` result leaves resources held but does not allow another
   attempt; retry/deadline policy remains deferred.

The order becomes financially binding at successful capture plus atomic Order
confirmation. A client timeout does not change this; replay/query returns the
durable outcome.

### Current pickup-fulfillment creation

Vertical Slice 6 creates one `PENDING` PickupFulfillment for an eligible `PAID`
Order. The command locks Order first, derives responsible Branch and exact
Location from the Order/reservation facts, requires `FULFILL_PICKUP` plus an
active assignment to that enabled Location, verifies successful payment and
consumed reservation, and inserts fulfillment plus audit atomically. A unique
Order constraint backstops duplicate creation. It does not mutate Order,
Reservation, or Inventory and does not implement preparation or handover.

Vertical Slice 7 adds only `PENDING -> PICKING`. It locks the PickupFulfillment
row at rank 2, rechecks current exact-Location authority and terminal commercial
eligibility, sets `pickingStartedAt` once, and appends audit in the same
transaction. It does not acquire lower-rank business locks or mutate Order,
Payment, Reservation, or Inventory.

### POS

- Cash: quote revalidation, Order creation/confirmation, stock commit, voucher
  redemption and shift tender entry occur in one database transaction.
- Electronic: use the online provider protocol but derive Branch/Location from
  Register/CashierShift and perform immediate handover only after confirmation.
- A duplicate request returns the original sale/attempt; it never creates a
  second charge or stock movement.

### Cancellation and inventory compensation

The business policy still decides whether cancellation is allowed. Once an
allowed command begins, architecture fixes its effects:

1. Unconfirmed cancellation/expiry locks active holds and releases Reservation/
   Voucher once; on-hand was never committed, so no restoration movement is
   created.
2. Confirmed pre-dispatch cancellation claims its operation, locks Order then
   Fulfillment, then any needed Payment, committed Reservation and balances in
   rank order. It conditionally moves Order to `CANCELLATION_PENDING`, cancels
   the still-cancellable Fulfillment, increases on-hand at each original
   allocation Location and appends one immutable `CANCELLATION_RESTORE` movement
   per OrderItem plus required audit in the same local transaction. Any local
   failure rolls the whole cancellation/restoration decision back. When money
   was captured, the same Payment fence creates the durable selected void/refund
   intent and its active reversal reservation before commit.
3. The `COMMITTED` Reservation remains immutable history. Unique
   `(CANCELLATION_RESTORE, orderId, orderItemId)` identity makes replay return the
   prior result without restoring twice.
4. Dispatch/handover locks Order then Fulfillment in the same order. If it wins,
   confirmed cancellation cannot restore directly and must use return-to-sender
   or Return. Post-handover stock changes only through approved Return
   disposition.
5. Any external financial void/refund uses durable Payment intent committed with
   cancellation state, then runs outside locks. Provider failure leaves explicit
   pending/reconciliation work and never replays the stock restoration.

## Authoritative initial monetary model

The first supported commerce model is single-currency VND. All persisted money
uses exact integer đồng (`DECIMAL(19,0)` or an equivalent exact integer/decimal
representation); application calculations use decimal/integer arithmetic, never
binary floating point. Percentage results use `HALF_UP` to one đồng at the
defined component total.

```text
itemGross             = sum(baseUnitPrice * quantity)
itemDiscount          = sum(rounded selected item-promotion discounts)
discountedItemSubtotal= itemGross - itemDiscount
orderDiscount         = rounded selected automatic order promotion
voucherBase           = discountedItemSubtotal - orderDiscount
voucherDiscount       = rounded selected voucher benefit, capped at voucherBase
merchandiseNet        = voucherBase - voucherDiscount
shippingNet           = shippingFee - shippingDiscount
tax                   = 0 for the initial model
finalPayable           = merchandiseNet + shippingNet + tax
```

Every component is non-negative; each discount is capped by its component base,
and `finalPayable >= 0`. A zero-payable Order confirms without an external
PaymentAttempt but still uses the normal stock/voucher confirmation transaction.
Tax calculation and legal e-invoice behavior are deferred until an explicit
requirement and ADR extend this monetary model; an initial Order snapshots
`tax = 0`.

Item discounts are calculated from each line gross and rounded once per line.
Order and voucher discounts are calculated/rounded once at Order level, then
allocated proportionally to eligible lines using largest remainder: floor exact
shares to đồng, distribute remaining đồng by descending fractional remainder,
then stable OrderItem ID. The same method allocates a line amount across units
when partial-quantity refunds require it. Allocations always sum exactly to the
rounded component total.

The first supported stacking matrix is: at most one item-level automatic
promotion per line, one automatic order promotion, one Voucher, and one shipping
benefit. Different layers stack in that sequence; candidates within one layer
are mutually exclusive and resolved by priority descending then stable definition
ID ascending. Advanced merchant-configurable stacking remains deferred.

The immutable placement snapshot stores all bases, selected rule/version and
scope evidence, rounded component totals, per-line/per-unit allocations,
shipping/tax components, calculation-policy version and final payable. Refunds
use these allocations and never recalculate current promotions.

## Inventory concurrency strategy

- Pessimistic row locks are `PROPOSED` for InventoryBalance/Reservation quantity
  commands because they are short, contention-sensitive and simple to explain.
- Normalize/sum duplicate requested lines first; lock all balance rows at rank 6
  in canonical key order after any required Order/Payment/Voucher/Reservation
  ranks.
- Commit/release/expire condition on Reservation state/version and share the
  same locks; exactly one terminal transition changes the balance.
- Optimistic versioning is suitable for ordinary catalog/admin edits where a
  stale update should return conflict.
- Unique operation/movement keys make command retries idempotent.
- Transfer dispatch locks the source; receipt locks the destination. A sale and
  transfer racing at source are serialized by the same balance lock.

### Required inventory race outcomes

| Scenario | Deterministic outcome |
|---|---|
| One unit; Customer X and Y place simultaneously | Both target the same balance lock. The first transaction that acquires it and commits reserves the unit; the second then reads `available = 0` and fails placement with no Order/resource hold side effects. |
| POS and online compete | Channel gives no hidden priority. Both use the same balance lock/invariant; configured allocation priority may be added only as an explicit business policy. |
| Reservation expiry races payment confirmation | Both lock the same Reservation/balance and conditionally leave `ACTIVE`. Confirmation wins and commits, or expiry wins and releases; a later capture after expiry enters reconciliation. |
| Cancellation races payment | Both lock Order/resources and require the expected state. Exactly one terminal path wins; confirmed payment blocks ordinary cancellation, while late capture after cancellation is recorded and voided/refunded. |
| Confirmed cancellation races dispatch | Both lock Order then Fulfillment. Cancellation winner restores committed stock exactly once; dispatch winner requires return-to-sender/Return and cannot use direct restoration. |
| Approved return restock races another stock movement | The return movement is issued only after receipt/inspection and locks the same balance. It serializes with sale/adjustment/transfer and cannot be applied twice. |
| Transfer dispatch races sale | Both lock the source balance. The winner changes availability; the loser revalidates and either proceeds with the remainder or fails without negative stock. |

## Financial concurrency strategy

- Online VNPAY uses unique merchant transaction references and verified IPN as
  the only payment-confirmation boundary; browser Return is presentation-only.
- Correlation IDs are resolved by short scalar reads before the mutation
  transaction, which then locks Order -> Payment -> Attempt -> Reservation and
  Balance only when expiry must release reserved stock.
- Successful payment commits the Reservation to the paid Order without changing
  on-hand or reserved; physical stock issue belongs to Fulfillment.
- Payment and hold expiry use the same ordered fences. The winner is final;
  verified success after expiry is recorded for review and cannot restore stock.
- Persist provider events once using a unique provider/event key.
- PaymentAttempt has a scoped idempotency key and request fingerprint.
- Payment/void/Refund amount-changing commands lock the Payment aggregate/
  financial summary before checking remaining reversible/refundable amount.
- A successful financial void reverses previously successful captured exposure.
  Authorization-only cancellation is a distinct fact and does not change
  `successfulCaptured`.
- Void and Refund consume the same captured capacity. Before an external void,
  `activeVoidReserved` fences its target amount under Payment; unknown retains
  it, definitive failure releases it once, and success converts it to
  `successfulVoided`.
- Pending refund reservations count against remaining refundable value.
- Out-of-order/conflicting terminal provider events become reconciliation work,
  never destructive updates.

### Required financial retry/race outcomes

| Scenario | Deterministic outcome |
|---|---|
| Duplicate payment command | Same key/fingerprint returns the original PaymentAttempt; mismatched reuse is rejected and cannot charge. |
| Provider succeeds but HTTP/client times out | Durable attempt/provider reference is queried or reconciled; the client does not create a new attempt blindly. |
| Duplicate webhook | Unique provider-event identity makes the duplicate a no-op returning the recorded result. |
| Webhooks arrive out of order or conflict | Preserve both verified facts; do not reverse a terminal record by arrival order; mark reconciliation where facts conflict. |
| Capture arrives after Order cancellation/expiry | Record captured money, keep Order terminal, and initiate the approved idempotent void/refund path. |
| Void and Refund race | Payment lock serializes both. A pending/unknown void fences its target amount, so Refund cannot reserve the same captured exposure. |
| Partial refund | Reserve only the requested amount under the Payment lock and reduce remaining refundable value while pending. |
| Duplicate refund command | Same idempotency key returns the same Refund; it cannot reserve the amount twice. |
| Refund after Return | Return evidence may satisfy eligibility, but the Refund still checks financial amount/permission independently. |
| Refund without physical Return | Allowed only with an explicit reason/permission policy; it does not create stock. |
| Two refunds race | Payment lock serializes the remaining-refundable check; at most the amount satisfying the financial invariant is reserved. |

### Pending financial reversal amounts and provider attempts

`activeRefundReserved` (the v1.1 `pendingRefundReserved`) is the sum of refund
amount reservations that still protect provider work in `REQUESTED`, `APPROVED`,
`PROCESSING`, or `UNKNOWN/RECONCILIATION_REQUIRED`. `activeVoidReserved` protects
the same capacity for pending/unknown financial void attempts. Both increase only
while Payment is locked.

```text
successfulVoided + successfulRefunded
  + activeVoidReserved + activeRefundReserved <= successfulCaptured
remainingRefundable = successfulCaptured
                    - successfulVoided
                    - activeVoidReserved
                    - successfulRefunded
                    - activeRefundReserved
```

RR-01 also protects each immutable paid component `c`:

```text
successfulVoid(c)
+ successfulRefund(c)
+ activeVoid(c)
+ activeRefund(c)
<= capturedComponentCapacity(c)

remainingComponentCapacity(c)
= capturedComponentCapacity(c)
- successfulVoid(c)
- successfulRefund(c)
- activeVoid(c)
- activeRefund(c)

sum(component allocations for one active attempt) = attempt reserved amount
allocation.amount > 0
```

The MVP supports one full capture or full cash settlement. Only after that
success, `capturedComponentCapacity(c)` equals the immutable Order paid component
amount for `c`. Partial and split capture are `DEFERRED`; future support must
persist immutable successful-capture component allocations before deriving
reversal capacity.

Each attempt allocation is immutable in component and amount and follows only
`ACTIVE -> SUCCEEDED` or `ACTIVE -> RELEASED`. Pending/unknown provider state
leaves allocations `ACTIVE`. Under the Payment lock, success converts all
attempt allocations and the aggregate reservation atomically and appends the
successful financial fact. Contractually definitive failure releases all
attempt allocations and that attempt's aggregate reservation exactly once.
Partial conversion/release is forbidden; expected-state failure rolls back the
outcome transaction or enters reconciliation. Reports consume only `SUCCEEDED`
allocations and never reconstruct them.

- A void target is limited to remaining captured exposure after prior successful
  voids, successful refunds and active reversal reservations. Void and Refund
  never reserve or consume the same amount twice.
- Provider void timeout/unknown keeps `activeVoidReserved`; no overlapping void
  or Refund starts until reconciliation. Success atomically decreases active
  void reservation, increases `successfulVoided`, and writes immutable
  `VoidAllocation` rows. Definitive failure releases the active void reservation
  exactly once. Retry uses the original logical operation and a provider-safe
  attempt identity under the same Payment lock.
- Provider timeout/unknown outcome keeps the amount reserved. No new attempt is
  allowed until that provider reference is reconciled.
- Provider success atomically appends the successful financial fact, decreases
  active reservation and increases successful refunded amount by the same value.
- A contractually definitive provider failure atomically appends an immutable
  failed RefundAttempt fact, marks the attempt terminal and releases that
  attempt's active reservation exactly once. “Definitive” is valid only when the
  selected provider contract guarantees no later success for that reference;
  otherwise the result is unknown and remains reserved.
- A retry never mutates the failed RefundAttempt. It locks Payment, recomputes
  remaining refundable value, re-reserves the amount if still available and
  creates a new RefundAttempt with a new provider idempotency reference under the
  same logical Refund.
- Provider events are correlated to their attempt generation. A duplicate/late
  failure for an older attempt cannot release a newer attempt's reservation.
- Rejection/cancellation before provider work releases the active reservation;
  physical Return/restock remains independent.

If a provider contradicts a state it contractually declared final, preserve the
event and mark financial reconciliation; do not start another refund or silently
rewrite prior facts.

## API contract ownership

Approved API contracts will live under `docs/API/` and/or an approved OpenAPI
definition when implementation planning begins. Transport DTOs implement those
contracts; persistence entities do not.

Every contract defines:

- authentication, permission and resource/branch/location scope;
- request/response DTOs and validation;
- stable error code, safe message, field errors and correlation ID;
- pagination, allowlisted filtering/sorting for collections;
- optimistic conflict behavior where relevant;
- idempotency key scope, request fingerprint mismatch, retention and replay
  response for retryable commands;
- backward-compatibility/versioning decision and frontend consumers.

The approved MVP API prefix is `/api/v1`; public resource identifiers are UUIDs
and persistence IDs are never public. RFC 9457 Problem Details includes stable
`code`, `correlationId`, and validation `errors[]` where applicable. Status
semantics are: `401` unauthenticated, `403` unauthorized, security-concealing
`404`, `412` for a false supplied `If-Match`, and `409` for domain/lifecycle
conflict or mismatched idempotency-key fingerprint. `409` and `412` are not
interchangeable. Timestamps use RFC 3339 UTC and business dates ISO
`YYYY-MM-DD`.

Client operation identity is mandatory only for Online Order placement, payment
and Void initiation, confirmed cancellation, POS cash sale, Inventory receipt
and adjustment, and pickup handover. Refund initiation and stock transfer join
that list only if implemented. Provider callbacks use authenticated event
identity plus exact attempt generation, not a browser idempotency key. GET,
ordinary reads, and adequately version-protected administration do not require
idempotency storage.

## Sessions and authorization

The approved single-instance MVP uses the standard server-side servlet session
with `Secure`, `HttpOnly`, appropriately `SameSite` cookies and CSRF protection.
Authentication/privilege change rotates the session ID; logout invalidates the
current session. Session authority includes `authVersion`, while sensitive
requests validate current account/profile/assignment, permission, ownership,
Branch and Location state. Account disable, password change, and grant change
increment authority version.

Spring Session JDBC is `DEFERRED` until multi-instance deployment, durable
sessions, restart persistence, or remote-session management requires it. Redis
is not required. Future mobile bearer authentication requires a later ADR.

## Reliable side effects without a broker

Required AuditEvent rows are written in the originating transaction. If a later
notification or scheduled-automation slice is admitted, NotificationIntent and
SQL-backed worker claims use atomic conditional update/lock, attempt count,
next-attempt time, and unique dedupe keys. Kafka/RabbitMQ is not justified.

Automated scheduled-worker infrastructure is `DEFERRED` from core MVP. Expiry
and reconciliation rules remain deterministic through injected `Clock`, explicit
conditional commands, and tests. If automation is later admitted, work either
claims a durable row with owner/lease-expiry/attempt-count/next-attempt fields or
invokes the same idempotent aggregate command.

## Audit transaction and privacy boundary

Required AuditEvent insertion occurs in the same database transaction as the
privileged mutation and its failure aborts that mutation. Audit rows identify
human/service/integration/system actors and correlation/resource/scope while
using redacted structured payloads. The application database role has no
update/delete path for audit rows and foreign keys do not cascade-delete them.
Retention/archive is an `OPEN DECISION`, not ordinary application cleanup.

## Database invariants

The approved persistence baseline is SQL Server with Flyway migrations,
`BIGINT IDENTITY` internal keys, application-generated UUID v4 public IDs,
Java `Instant` mapped to `datetime2(6)`, UTC-only persistence, stable string
status codes, optimistic version columns where stale writes matter, and exact
scale-zero VND in `DECIMAL(19,0)`. Critical financial, inventory, and audit facts
are append-only and authoritative history has no cascade-delete path. Real SQL
Server is required for concurrency verification.

Versioned migrations must define at least:

- non-negative/consistent balance and monetary checks;
- unique SKU/public IDs/provider references/provider events/idempotency keys;
- unique active reservation/movement business references;
- unique voucher issuance/redemption/delivery dedupe keys;
- valid foreign keys and terminal-state update paths;
- filtered uniqueness for active CashierShift if cash/register scope is enabled;
- indexes for branch/location authorization, reservation expiry, provider-event
  processing, notification claims and report facts.

Application locking remains necessary for cross-row sums such as refundable
amount and usage limits.

## Reporting semantics

`REPORTING_GLOSSARY.md` is the authoritative metric contract. Reports use
immutable Order monetary/allocation snapshots, PaymentTransaction facts,
successful Void/Refund allocations, Fulfillment/Return facts and StockMovement/
InventoryBalance facts—not UI state, mutable text or cached totals.

- Monetary event instants are stored in UTC; reporting calendar boundaries use
  `Asia/Ho_Chi_Minh` and half-open intervals `[from, to)`.
- POS and online sales/refunds belong to the immutable Order responsible Branch;
  fulfillment and stock remain Location dimensions. Transfers are inventory
  movement, never sales.
- Gross/discount/captured/voided/refunded/net measures remain separate. Void and
  Refund reduce net metrics on their successful event time and do not rewrite
  the original capture period. Product net value subtracts their immutable item
  allocations.
- Cancelled/expired unpaid Orders have zero paid/net sales; their snapshotted
  payable value may appear only in a separately named cancelled-demand metric.
- Reports expose `asOf`/generated time and projection freshness and reconcile to
  immutable source facts.
- Inventory monetary value is not reported because no acquisition-cost/valuation
  domain exists. On-hand/reserved/available and movements remain reportable.
- SQL queries/projections are sufficient; no analytics service or search cluster
  is required.

## Technology and deployment constraints

Approved MVP baseline: Java 25 LTS, Spring Boot 4.1.x, Boot-managed Spring
Security, Maven with Maven Wrapper, modular monolith, SQL Server, Flyway, REST,
Vue 3/TypeScript/Vite/Vue Router, database transactions/locks/constraints and
application-use-case transaction boundaries. Package by business module, using
internal `api/application/domain/infrastructure` organization where useful.
Use Spring Data JPA for ordinary persistence and explicit SQL/locking repository
logic for concurrency-critical paths. Provider calls occur after local commit.
Exact compatible patch/plugin versions remain a pre-scaffold selection.

Not approved: Redis, brokers, Elasticsearch/OpenSearch, WebSocket infrastructure,
object storage provider, microservices, Kubernetes, distributed transactions,
event sourcing, vector database or dedicated AI infrastructure. Add only after
a concrete requirement and ADR.

## ADR index and lifecycle

ADR governance is defined in `ADR/README.md`. Blueprint architecture decisions
accepted for this baseline remain distinct from `OPEN DECISION` business policy
and from MVP scope. Deferred features are not implementation commitments.
