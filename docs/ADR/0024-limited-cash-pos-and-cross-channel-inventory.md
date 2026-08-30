# ADR-0024: Limited Cash POS and Cross-Channel Inventory

- Status: `ACCEPTED`
- Accepted: 2026-08-28
- Scope: Vertical Slice 6 limited cash POS
- Decision class: POS, cash settlement, and cross-channel inventory transaction semantics
- Extends: ADR-0003, ADR-0004, ADR-0005, ADR-0006, ADR-0019, and ADR-0023

## Context

The MVP requires one in-person cash sale to share Catalog, Pricing, Order,
Inventory, and Fulfillment authority with Customer Web. POS must compete with
online checkout for the same location-specific `InventoryBalance` without a
second stock model or a synthetic reservation.

## Decision

V12 supports one published ProductVariant, quantity one, and exact cash tender.
The server resolves the current effective VND price; no client amount, discount,
change calculation, split tender, or price override is accepted.

A `POSRegister` belongs to one enabled Location. Existing active employee
Location assignment is the Register authorization boundary; explicit
cashier-to-register assignment is deferred. `POS_SELL` authorizes Register,
Shift, lookup, sale, and receipt use cases.

A cashier and a Register may each have at most one `OPEN` `CashierShift`.
Filtered SQL Server unique indexes are the final invariant. Shift close is
idempotent. Expected cash is derived from successful immutable `CashTender`
rows; declared cash, variance, approval, and cash adjustment are deferred.

A successful POS command creates one shared `Order` in `PAID` state with
`channel=POS`, one immutable OrderItem price/version snapshot, one
`CashTender`, one immediately `HANDED_OVER` Fulfillment, one
`POS_CASH_SALE` StockMovement, and audit evidence in one local transaction.
It creates no `PaymentAttempt` and no `InventoryReservation`.

POS sale idempotency is scoped by `(CashierShift, Idempotency-Key)`. The
persisted sale row stores the variant fingerprint. Exact replay returns the
same receipt; a different variant returns `IDEMPOTENCY_KEY_CONFLICT`.

## Locking and atomicity

Shift open locks the cashier account and Register before checking/inserting the
OPEN Shift. Sale and close both take the Shift write lock first, so close-first
rejects sale and sale-first commits before close.

The POS sale sequence is:

```text
CashierShift
-> current effective PriceVersion read
-> InventoryBalance(variant, register.location) write lock
-> new Order / Tender / Fulfillment / Sale / Movement / Audit facts
```

The price row is immutable while effective and is not a competing mutable lock.
New merchant facts introduce no wait edge to pre-existing rows. Online checkout
and POS both converge on the same SQL Server `InventoryBalance` write lock.
Online reserves only when `onHand - reserved >= 1`; POS issues only when the
same expression is at least one. POS applies:

```text
onHand -= 1
reserved unchanged
```

No network call or JVM mutex participates. Any local failure rolls back every
fact and the balance mutation.

## Consequences

- POS and online cannot both win the final unit.
- Historical receipts do not change with later Catalog or Pricing changes.
- Register Location, Shift, cashier, tender, handover, and physical issue remain
  explicit immutable evidence.
- Cash settlement has no provider reference and does not fabricate electronic
  payment history.
- Multi-line baskets, non-cash tender, change calculation, variance,
  Return/Refund, Voucher, hardware, and reporting remain deferred.

## Rejected alternatives

- Separate POS Product, Price, Order, or Inventory models.
- A long-lived or synthetic POS InventoryReservation.
- Client-authored price or JavaScript-authoritative cash arithmetic.
- PaymentAttempt/provider rows for cash.
- Redis, a POS-only mutex, or retry-until-green concurrency tests.
