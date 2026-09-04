# Lifecycles — Blueprint v1.1.1

## Status and conventions

State-machine boundaries used by the
[approved MVP implementation baseline](MVP_IMPLEMENTATION_BASELINE.md) are
accepted architecture. Lifecycles for optional/deferred capabilities remain
architecture support rather than active implementation scope, and their open
business policies remain `OPEN DECISION`. State identifiers are stable internal
values; localized labels never drive logic.

Every transition must check actor/scope, expected current state and version;
write audit where required; and return the recorded result on idempotent replay.
Terminal states reject mutation except explicitly documented administrative
reconciliation that appends history rather than rewriting it.

Operational state is mutable through guarded transitions. Order/price/product
snapshots and financial/inventory movement facts are immutable. UI summary
states such as “paid and delivered” are derived read-model values.

## ProductVariant — Vertical Slice 1

```text
DRAFT -> PUBLISHED
```

- Actor: account with `CATALOG_MANAGE`.
- `PUBLISHED` requires an existing positive VND current price and positive
  on-hand stock at at least one enabled Location.
- This slice has no archive, unpublish, price history, reservation, or stock
  movement transition; those require their own admitted lifecycle/use case.

Voucher transitions and locks below apply only if the optional Voucher slice is
admitted. Core MVP checkout uses deterministic base price without Voucher.

## 1. Cart

```text
ACTIVE -> CONVERTED
ACTIVE -> EXPIRED
ACTIVE -> ABANDONED (optional explicit cleanup)
```

- Actor: customer/session owner.
- Only `ACTIVE` Cart accepts item changes.
- Cart contains estimates only; it neither reserves inventory nor consumes a
  voucher.
- CheckoutAttempt snapshots cart input. Successful Order confirmation converts
  the Cart idempotently; failed checkout may leave it active until expiry.
- Terminal: `CONVERTED`, `EXPIRED`, `ABANDONED`.

## 2. CheckoutAttempt

```text
STARTED -> RESOURCES_HELD -> PAYMENT_PENDING -> SUCCEEDED
   |             |                 |
   +-----------> FAILED <----------+
                 |
                 +-> EXPIRED
Any non-terminal/callback conflict -> RECONCILIATION_REQUIRED
```

| Transition | Actor | Preconditions | Side effects |
|---|---|---|---|
| STARTED -> RESOURCES_HELD | Checkout use case | Valid scope, quote, cart, inventory, voucher | Create pending Order; reserve stock/voucher; snapshot price |
| RESOURCES_HELD -> PAYMENT_PENDING | Payment initiation | Order/resources active | Create/reuse PaymentAttempt; external call after commit |
| RESOURCES_HELD -> SUCCEEDED | POS cash use case | Active shift, tender accepted | Confirm Order, commit stock, redeem voucher, tender/audit atomically |
| PAYMENT_PENDING -> SUCCEEDED | Provider callback/use case | Successful capture and active resources | Record capture; confirm Order; commit stock; redeem voucher |
| Non-terminal -> FAILED | Use case/provider | Deterministic non-retryable failure | Release active holds; keep failure reason |
| Held/Pending -> EXPIRED | Scheduled/use case | Deadline passed, no successful capture, expected version | Release holds; expire pending Order |
| Any conflict -> RECONCILIATION_REQUIRED | System | Captured money with terminal/missing resources, conflicting provider facts | Keep facts; initiate governed void/refund/manual resolution |

Same idempotency key/fingerprint returns the same Attempt. Same key/different
fingerprint is invalid. Terminal: `SUCCEEDED`, `FAILED`, `EXPIRED`; reconciliation
is operationally non-terminal until resolved but cannot repeat resource effects.

## 3. Order commercial state

Current implemented online-payment slice:

```text
PENDING_PAYMENT -> PAID
PENDING_PAYMENT -> CANCELLED
```

`PAID` means trusted channel-specific settlement and an immutable commercial
commitment. Online VNPAY success commits its Reservation without physical issue;
limited POS exact-cash success directly issues on-hand stock and records
immediate handover. `PAID` alone does not imply delivery or refund eligibility.
The simple unpaid cancellation command is forbidden from `PAID`.

The broader approved future lifecycle below remains unimplemented and requires
a later slice to map or supersede the bounded `PAID` state explicitly.

```text
PENDING_PAYMENT -> CONFIRMED -> CANCELLATION_PENDING -> CANCELLED
       |               |                    |
       +-> EXPIRED     +--------------------+
       +-> CANCELLED
```

- `PENDING_PAYMENT`: immutable price snapshot exists; resources are held.
- `CONFIRMED`: required channel-specific tender/capture and stock/voucher commit
  succeeded. Price/items cannot be edited.
- `CANCELLATION_PENDING`: compensation is required because money or fulfillment
  may already exist.
- `CANCELLED`: commercial cancellation complete; required financial compensation
  succeeded or an authorized reconciliation resolution explicitly closed it.
- `EXPIRED`: payment deadline passed before confirmation and holds released.
- POS immediate handover may confirm and derive completion in one use case.
- Online fulfillment is separate; `COMPLETED` is a derived summary, not an Order
  mutation required for payment correctness.
- Invalid: terminal -> pending/confirmed; confirmed item/price edits; callback
  reopening cancelled/expired Order.

Unconfirmed cancellation/expiry releases active Reservation/Voucher holds and
does not restore on-hand. If policy allows confirmed cancellation before dispatch or
handover, the guarded transition to `CANCELLED` atomically cancels
the still-cancellable Fulfillment and creates one idempotent
`CANCELLATION_RESTORE` movement that leaves on-hand unchanged and decrements
reserved at the original allocation Location. The Reservation changes from
`COMMITTED` to `CANCELLED_RESTORED` while retaining `committedAt` as history. Financial
void/refund intent and its active amount reservation are recorded in that local
transaction; the provider is invoked only after commit. Failure leaves
cancellation pending/reconciliation and never repeats stock restoration.

Dispatch and cancellation both lock Order then Fulfillment. If dispatch wins,
direct cancellation cannot restore stock; return-to-sender or Return owns the
physical compensation. After handover/delivery, only the Return disposition flow
may restore stock.

## 4. Inventory Reservation

Current quote-checkout reservation lifecycle:

```text
ACTIVE -> ADOPTED -> COMMITTED -> CONSUMED
                              +-> CANCELLED_RESTORED
   |          +----> RELEASED
   |          +----> EXPIRED
   +---------------> RELEASED
```

`COMMITTED` is a non-expiring allocation to the paid Order. Payment
confirmation does not change `onHand` or `reserved`; physical issue is owned by
a later fulfillment transition. `ADOPTED -> EXPIRED` is terminal: after the
server-owned deadline, lazy cleanup releases `reserved`, leaves `onHand`
unchanged, and changes the owning unpaid Order to `CANCELLED` in the same Order
-> PaymentAttempt -> Reservation -> Balance transaction.

Broader inventory-command target lifecycle (not the quote-checkout payment
transition above):

```text
ACTIVE -> COMMITTED
ACTIVE -> RELEASED
ACTIVE -> EXPIRED
```

- Create: checkout transaction locks ordered balances, checks available and
  increments reserved once.
- Commit: locks Reservation/balance, requires `ACTIVE`, decrements reserved and
  on-hand, appends movement, then marks `COMMITTED`.
- Release/expire: locks same records, requires `ACTIVE`, decrements reserved and
  marks terminal without changing on-hand.
- Expiry requires deadline passed and no successful confirmation/capture fence.
- Duplicate operation returns terminal result without balance mutation.
- Order-coupled commit/release/expiry follows the global Order -> Payment if
  required -> Voucher -> Reservation -> Balance hierarchy, with Fulfillment
  acquired after Order and before Payment whenever cancellation/dispatch state is
  involved. Balance-only commands start at Balance and never acquire an earlier
  rank.
- Terminal: `CONSUMED`, `CANCELLED_RESTORED`, `RELEASED`, `EXPIRED`.

## 5. Inventory Transfer

```text
DRAFT -> REQUESTED -> APPROVED -> IN_TRANSIT -> RECEIVED
                    -> REJECTED
REQUESTED/APPROVED -> CANCELLED
IN_TRANSIT -> RECONCILIATION_REQUIRED (loss/damage/quantity discrepancy)
```

- Request/approval actors require source/destination scope and approval policy.
- Dispatch locks source balance and removes on-hand once; source movement is
  idempotent. In-transit stock is unavailable.
- Receipt locks destination and adds accepted quantity once.
- Sale versus dispatch is serialized by the source balance lock.
- Terminal: `RECEIVED`, `REJECTED`, `CANCELLED`; reconciliation resolves through
  adjustments with audit rather than editing movements.

## 6. PaymentAttempt and financial transactions

Current VNPAY electronic-attempt lifecycle:

```text
PENDING -> SUCCEEDED
        -> FAILED
        -> CANCELLED
        -> EXPIRED
        -> REVIEW_REQUIRED
```

The merchant transaction reference is unique. The VNPAY provider transaction
reference is unique when supplied. Exact compatible IPN replay is a no-op;
conflicting facts do not overwrite successful history. `FAILED` keeps Order
`PENDING_PAYMENT` and Reservation `ADOPTED`, so a new idempotency key may create
a retry while the hold remains eligible. A late verified success becomes
`REVIEW_REQUIRED` and cannot reopen the Order or inventory hold.

- Initiation derives amount/currency from the immutable Order and creates a
  provider deadline no later than the Reservation deadline.
- Browser Return verifies context only for a fixed SPA redirect; it never
  confirms payment.
- Verified IPN success locks Order -> Payment -> Attempt -> Reservation and
  atomically marks Attempt `SUCCEEDED`, Order `PAID`, Reservation `COMMITTED`.
- Verified failure changes only the Attempt. Invalid signature, merchant,
  reference, currency or amount changes nothing.
- If expiry wins, Order becomes `CANCELLED`, Reservation `EXPIRED` and the
  pending attempt `EXPIRED`; a later success becomes `REVIEW_REQUIRED` without
  stock resurrection.
- Terminal for customer retry: `SUCCEEDED`, `FAILED`, `CANCELLED`, `EXPIRED`;
  `REVIEW_REQUIRED` is operational reconciliation work.

```text
CREATED -> PENDING -> SUCCEEDED
                   -> FAILED
                   -> CANCELLED
                   -> EXPIRED
                   -> UNKNOWN/RECONCILIATION_REQUIRED
```

- Initiation requires scoped idempotency key, fingerprint, Order/currency/amount
  consistency and eligible Order state.
- Provider call occurs after durable `CREATED/PENDING` commit.
- Unique ProviderEvent may cause one legal transition. Duplicate returns prior
  processing outcome.
- Out-of-order event is stored. If it confirms an already-known compatible
  fact, it is idempotent; if contradictory, mark reconciliation.
- HTTP timeout leaves `PENDING/UNKNOWN`; client retries/query by operation ID.
- Append-only PaymentTransaction records successful/failed provider facts.
- A successful financial `VOID` consumes the same captured exposure as Refund.
  Before an external void call, its target amount is fenced as
  `activeVoidReserved` under Payment. Unknown retains the fence; definitive
  failure releases it once; success converts it to `successfulVoided` and stores
  immutable component allocations.
- Each active reversal attempt has positive, immutable component allocations
  summing to the attempt reserved amount. Allocation lifecycle is only
  `ACTIVE -> SUCCEEDED` or `ACTIVE -> RELEASED`.
- Pending/unknown provider state leaves every allocation `ACTIVE`. Under the
  Payment lock, success converts all attempt allocations and its aggregate
  reservation atomically; definitive failure releases all attempt allocations
  and that attempt's aggregate reservation exactly once. Partial conversion or
  release is forbidden.
- Provider outcomes correlate to the exact attempt generation. Stale outcomes
  cannot mutate newer attempts, and reports use only `SUCCEEDED` allocations.
- Terminal attempts: `SUCCEEDED`, `FAILED`, `CANCELLED`, `EXPIRED`; UNKNOWN needs
  reconciliation and cannot authorize duplicate blind charge.

The broader target financial lifecycle below remains a blueprint for future
providers and reversal work; it does not replace the implemented VNPAY states
above.

The MVP uses one direct full VNPAY payment. Partial and split capture are
`DEFERRED`; later support requires immutable successful-capture component
allocations before reversal capacity is derived.

## 7. Fulfillment / Shipment

### Online pickup

```text
PENDING -> PICKING -> PREPARED -> HANDED_OVER
       -> CANCELLED (before handover when eligible)
```

Creation and picking retain their earlier semantics. Vertical Slice 5 (v11)
adds `PREPARED`, idempotent `HANDED_OVER`, and terminal `CANCELLED`. Preparation
requires the paid Order's `COMMITTED` Reservation and changes no stock. Handover
atomically applies `onHand -= quantity`, `reserved -= quantity`, changes the
Reservation to `CONSUMED`, and appends `PICKUP_HANDOVER`. Confirmed cancellation
may win from `PENDING`, `PICKING`, or `PREPARED`; it leaves `onHand` unchanged,
decrements `reserved`, changes the Reservation to `CANCELLED_RESTORED`, and
appends `CANCELLATION_RESTORE`.

### Delivery

```text
PENDING -> PICKING -> PREPARED -> OUT_FOR_DELIVERY -> DELIVERED
PENDING/PICKING/PREPARED -> CANCELLED (guarded)
```

- Checkout creates the delivery intent and immutable receiver/address snapshot;
  employee transitions become actionable only after successful payment.
- Dispatch consumes every committed Reservation and writes exactly one
  `DELIVERY_DISPATCH` movement per OrderItem. Delivered records evidence only.
- Cancellation racing dispatch locks/version-checks the Fulfillment aggregate;
  the losing command gets conflict and cannot bypass the future Return policy.
- Dispatch follows Order -> Fulfillment lock order. An allowed confirmed
  cancellation uses the same fences; only its winning pre-dispatch path may
  issue `CANCELLATION_RESTORE`.
- Dispatch and delivery commands use actor-scoped idempotency keys.
- Terminal: `DELIVERED`, `CANCELLED`.
- POS immediate handover uses Order/stock/tender transaction and does not create
  an unnecessary Shipment.

## 8. Return

```text
REQUESTED -> APPROVED -> IN_TRANSIT/EXPECTED -> RECEIVED -> INSPECTED -> CLOSED
          -> REJECTED
APPROVED/EXPECTED -> CANCELLED (before receipt when policy permits)
```

- Request actor: customer/support; approval actor/policy is open.
- Eligibility atomically checks delivered/handed-over quantity, return window,
  prior accepted quantities and expected Fulfillment version.
- Receipt and inspection may be partial by ReturnItem.
- Inspection records restock/quarantine/damaged/supplier-return disposition.
- Only approved restock disposition issues idempotent Inventory return movement.
- Return does not itself move money.
- Terminal: `REJECTED`, `CANCELLED`, `CLOSED`.

## 9. Refund

```text
REQUESTED -> APPROVED -> PROCESSING -> SUCCEEDED
          -> REJECTED       |       -> FAILED_RETRYABLE -> PROCESSING
                            +------> RECONCILIATION_REQUIRED
REQUESTED/APPROVED -> CANCELLED

RefundAttempt:
CREATED -> PROVIDER_PENDING -> SUCCEEDED
                            -> DEFINITIVELY_FAILED
                            -> UNKNOWN -> SUCCEEDED/DEFINITIVELY_FAILED
```

- Request may reference Return or approved refund-without-return reason.
- Creation locks Payment and atomically reserves remaining refundable amount.
- Duplicate idempotency key returns existing Refund.
- Another concurrent Refund sees reduced remaining refundable amount.
- Provider timeout/unknown result keeps that reservation and goes to
  reconciliation; no retry attempt is allowed while the outcome is ambiguous.
- Success converts active refund reservation to successful refunded amount.
- A provider outcome is `DEFINITIVELY_FAILED` only when its contract guarantees
  that reference cannot later succeed. Under the Payment lock it appends the
  failed fact and releases only that attempt's reservation exactly once.
- `FAILED_RETRYABLE -> PROCESSING` locks Payment, recomputes remaining capacity,
  reacquires the amount and creates a new immutable RefundAttempt sequence under
  the same logical Refund. It never edits the old failed attempt.
- An old attempt event is correlated by attempt/provider reference and cannot
  release or complete a newer reservation. A contradictory event enters
  reconciliation and blocks further refunds.
- Rejection/cancellation before provider work releases the active reservation.
- Two requests or retries serialize on Payment, maintaining
  `successfulVoided + successfulRefunded + activeVoidReserved +
  activeRefundReserved <= successfulCaptured`.
- Terminal Refund states: `SUCCEEDED`, `REJECTED`, `CANCELLED`. A failed retryable
  Refund is operationally non-terminal until retried, cancelled by policy, or
  closed through reconciliation.

## 10. Promotion, Voucher Issuance and Redemption

```text
PromotionDefinition: DRAFT -> ACTIVE -> PAUSED -> ENDED -> ARCHIVED
Campaign:             DRAFT -> SCHEDULED -> ACTIVE -> COMPLETED/CANCELLED
VoucherIssuance:      ISSUED -> RESERVED -> REDEEMED
                             -> EXPIRED/REVOKED
                      RESERVED -> ISSUED (release while valid)
                      RESERVED -> EXPIRED (release after validity end)
                      RESERVED -> REVOKED (release after effective revocation)
                      REDEEMED -> REVERSED (only approved policy)
```

- Activation/schedule validates rules, scope and time window.
- Issuance uses unique campaign/customer/definition key.
- Checkout reserves one eligible issuance atomically under usage limit.
- Confirmation redeems it once; checkout failure/expiry releases it.
- Expiry/revocation racing reservation/redeem uses the same issuance/usage lock
  and expected state.
- Customer-specific issuance never changes owner.
- Terminal promotion/campaign states do not change historical Order evidence.
- A hold created while eligible is honored until its hold deadline. Natural
  definition/issuance expiry or administrative revocation prevents new holds but
  does not rewrite that active lease. Confirmation before the lease deadline may
  redeem it. On release/expiry, the same issuance/usage lock and authoritative
  server Clock choose exactly one result: `REVOKED` if an effective revocation
  exists, otherwise `EXPIRED` if validity ended, otherwise `ISSUED`. Replay
  returns the recorded result without changing usage again.

## 11. Customer and employee account lifecycles

### UserAccount

```text
PENDING_VERIFICATION -> ACTIVE -> LOCKED -> ACTIVE
                              -> DISABLED
```

- Verification requirement differs by customer/employee policy.
- Lock is security-temporary; Disabled is administrative terminal until an
  authorized re-enable policy.
- Disable, password reset, grant/assignment changes increment authorization
  version and revoke applicable sessions.

### CustomerProfile

```text
ACTIVE -> ARCHIVED
```

Archive does not delete Order history. Account and profile state are separate.

### EmployeeProfile / BranchAssignment

```text
Employee: ACTIVE -> SUSPENDED -> ACTIVE
                 -> TERMINATED
Assignment: ACTIVE -> EXPIRED/REVOKED
```

Sensitive employee commands require active account, employee and assignment plus
permission/scope. Losing any one denies the next request and invalidates active
authorization version.

## 12. Forgot-password activity

```text
ResetToken: ISSUED -> CONSUMED
                   -> EXPIRED
                   -> REVOKED
```

1. Request returns a generic response and is rate-limited by normalized account
   indicator and source risk signals.
2. If eligible, generate a cryptographically random token; store only unique
   hash, purpose, account, issue/expiry time and state.
3. New issuance revokes older active tokens for the same account/purpose.
4. Verification and consume lock the token/account and recheck expiry/state.
5. Password hash change, token consume, authorization-version increment,
   session revocation, security audit and required notification intent are one
   transaction.
6. Replay returns invalid/used without changing credentials.

Recovery channel, TTL, password policy and verification policy remain open
implementation gates.

## 13. CashierShift

```text
OPEN -> CLOSED
```

- Open requires `POS_SELL`, an active exact-Location assignment, an enabled
  Register/Location, and no open Shift for either cashier or Register.
- Only `OPEN` Shift accepts POS tender entries.
- Sale and close serialize on the Shift write lock. Close-first makes a new sale
  invalid; sale-first commits its CashTender before close calculates expected cash.
- Close is idempotent and records `expectedCash = sum(successful CashTender)`.
- Terminal: `CLOSED`.

Opening float, counted cash, variance/reconciliation, adjustments, refund and
manager close are deferred beyond the confirmed limited cash POS slice.

## 14. Notification delivery

```text
PENDING -> CLAIMED -> SENT
                  -> RETRY_WAIT -> CLAIMED
                  -> FAILED
PENDING -> SUPPRESSED (optional marketing only)
```

Unique dedupe key prevents duplicate intent/delivery. Claims expire safely and
are reacquired by conditional update. Mandatory security/transaction messages
ignore marketing opt-out but still follow channel availability policy.

## 15. Content / AI publication

```text
DRAFT_PROPOSAL -> VALIDATED -> PREVIEWED -> APPROVED -> PUBLISHED -> ARCHIVED
                               -> REJECTED
```

AI can create/revise Draft only. Validation rejects disallowed component types,
invalid scope, discount/business mutations and unavailable references.
Published configuration keeps version/provenance. Renderer hides/falls back for
later-unavailable products without executing arbitrary HTML/JS.

## Derived read-model states

Customer/POS/Admin UIs may derive labels such as `AWAITING_PAYMENT`, `READY_FOR_PICKUP`,
`PARTIALLY_REFUNDED` or `COMPLETED` from authoritative aggregates. Derived labels
cannot be submitted as transition commands or persisted as competing truth.
# Price quote validity

```text
VALID (quotedAt <= now < expiresAt)
  -> EXPIRED (now >= expiresAt)
```

- Actor: authenticated account with `CATALOG_BROWSE`.
- Preconditions: published variant, effective base-price version, and current customer-safe availability.
- Side effects: append one immutable PriceQuote; Inventory and Order state are unchanged.
- Expiry is time-derived rather than a mutable status update.
- An expired quote remains historical evidence and cannot become valid again.

# Quote checkout Order

Phase 15B applies these same approved states to the complete multi-item order;
it introduces no partial-order or per-line payment lifecycle. See ADR-0026.

```text
PENDING_PAYMENT -> PAID       (implemented by the later payment-result slice)
PENDING_PAYMENT -> CANCELLED  (owner cancellation or checkout-hold expiry)
```

- Actor: authenticated owner with `ORDER_PLACE` and `CHECKOUT_RESERVE`.
- Preconditions: owned unexpired unused quote, published variants, and every
  requested quantity available at one enabled deterministic Location.
- Success: one `ADOPTED` Reservation per line and one immutable unpaid Order
  are created atomically; no Payment is initiated. Single-line PriceQuote
  checkout remains supported alongside CartQuote checkout.
- Quote validity is checked with server time before reservation creation. The
  hold then receives a fresh, independently configured Reservation TTL from its
  server creation instant. Lazy expiry makes the Reservation `EXPIRED`, releases
  reserved stock for every line, and cancels only the still `PENDING_PAYMENT`
  Order. Verified payment commits every hold together; pickup handover and
  confirmed cancellation respectively consume or restore every committed hold.
- Customer catalog/quote/checkout availability paths select only relevant
  expired adopted checkout holds and normalize them per variant before reading
  or reserving stock. Repeated evaluation ignores terminal holds.
