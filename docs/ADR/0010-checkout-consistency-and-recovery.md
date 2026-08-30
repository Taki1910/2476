# ADR-0010: Checkout Consistency, Idempotency, and Recovery

- Status: `ACCEPTED`
- Accepted: 2026-08-24
- MVP scope: Core checkout without Voucher; Voucher protocol optional
- Date: 2026-08-23
- Decision class: Safe protocol; timeout and allocation policies require confirmation
- Decision source: Blueprint v1.1.1 H-01 plus AR-02 correction

## Context

Checkout crosses Order, Inventory, Promotion/Voucher, Payment, POS shift, Audit,
and Notification. A single database transaction cannot include an external
payment provider, while splitting every local write into independent steps would
allow duplicate orders, overselling, consumed vouchers, or paid-cancelled orders.

## Decision

Use a durable `CheckoutAttempt` application-process record with an idempotency key
and request fingerprint. It coordinates domain owners but does not replace their
aggregates.

Core MVP uses deterministic base price without Voucher. Voucher fences and
redemption below apply only if the optional Voucher slice is separately admitted.

### Global lock hierarchy

Every cross-domain local transaction follows the same increasing order, skipping
ranks it does not need:

```text
0 operation/provider-event identity
1 Order
2 Fulfillment records, then Return records
3 Payment, then PaymentAttempt, Refund and RefundAttempt children
4 PriceVersion, PromotionDefinition, PromotionUsage, Voucher, VoucherUsage,
  VoucherIssuance
5 InventoryReservation
6 InventoryBalance by (locationId, productVariantId)
7 CashierShift
```

Targets within a rank are normalized and sorted by stable ID. Holding a later
rank and then requesting an earlier one is forbidden. External provider calls
occur only after the local transaction commits. Deadlock/lock-timeout rolls back
the whole transaction and performs a bounded replay with the original key and
fingerprint; an unknown commit is queried by that key rather than retried under a
new identity.

### Atomic placement transaction

For both POS and online:

1. Resolve customer/channel/responsible branch/allocation location, authorize,
   and normalize/sum demand by `(location, variant)` and benefit identity.
2. Claim the CheckoutAttempt, then acquire/fence required Order,
   Price/Promotion/Voucher, Reservation and Balance records in global rank order.
3. Revalidate versions/time/usage after rank-4 fences and stock after rank-6
   locks.
4. Atomically persist a `PENDING_PAYMENT` Order with immutable `VND_V1`
   item/price/allocation evidence,
   active stock reservation, benefit reservation, and CheckoutAttempt state.

An identical idempotent retry returns the recorded outcome. Reusing a key with a
different fingerprint is rejected. Preview quote/cart state is advisory only.

### Electronic payment protocol

1. In a short database transaction, create a durable PaymentAttempt and mark the
   checkout payment-pending.
2. Call the provider after commit.
3. Apply authenticated callback/poll results in a new transaction using unique
   provider-event keys and conditional state transitions.
4. On verified success, lock affected records and atomically confirm financial
   facts and Order, commit inventory reservation, redeem voucher, and queue
   after-commit audit/notification work.
5. On definitive failure, keep the order eligible for retry or cancel according
   to policy. Unknown outcomes remain pending/reconciliation, never guessed.

### POS cash protocol

For the initial simple cash sale, financial success, Order confirmation,
inventory commit, voucher redemption, cash-shift entry, and durable side-effect
queue share one local database transaction. Receipt printing occurs after commit
and cannot repeat the sale.

### Expiry, cancellation, and late events

Unconfirmed cancellation/expiry locks Order, reservation and benefit hold and
releases active holds exactly once; it creates no on-hand restoration because
stock was not committed. A callback and expiry race therefore has one winner.

If policy permits confirmed cancellation before dispatch/handover, the command
locks Order -> Fulfillment -> Payment when needed -> committed Reservation ->
InventoryBalance. It atomically cancels the still-cancellable Fulfillment,
increments on-hand at the original allocation Location and inserts one immutable
`CANCELLATION_RESTORE` movement per OrderItem. The Reservation stays `COMMITTED`.
Dispatch uses Order -> Fulfillment too, so only cancellation or dispatch wins;
the dispatch winner requires return-to-sender/Return and cannot restore directly.

Financial void/refund intent is durable before any provider call. A verified
capture after cancellation/expiry never silently reopens the Order; record the
financial truth, flag reconciliation, and follow the governed reversal path.
When confirmed cancellation has captured exposure, its Payment fence records the
selected active void/refund reservation before commit. Provider failure cannot
replay the uniquely keyed inventory restoration.

## Consequences

- Local invariants use one SQL Server transaction; external calls use durable
  state plus idempotent callbacks and reconciliation.
- Clients require status-query/recovery UX after timeouts.
- CheckoutAttempt, PaymentAttempt, provider events, and business operation keys
  require database uniqueness and retention policy.
- Payment deadline, retry window, order-number timing, allocation, and late-capture
  customer communication remain open business decisions.

## Risks and mitigations

- Cross-module lock deadlocks: use the frozen hierarchy, stable row ordering,
  short transactions and same-key bounded replay.
- Provider accepts payment but callback is lost: polling/reconciliation uses the
  durable attempt/provider reference.
- Side-effect loss after commit: write SQL-backed after-commit work in the same
  transaction; a broker is not required initially.
- Duplicate browser submit: command idempotency and fingerprinting, not disabled
  buttons alone, prevent duplication.

## Rejected alternatives

- One transaction that waits on the payment provider.
- Create Order only after provider success without durable local intent.
- Compensating all local placement writes across separate commits.
- Treating callback arrival order as truth or reopening cancelled orders.
- Introducing distributed transactions or a message broker for the first release.
