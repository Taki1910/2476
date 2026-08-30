# ADR-0014: Adopt a Customer Reservation into a Pending Order

- Status: `ACCEPTED`
- Accepted: 2026-08-25
- MVP scope: Vertical Slice 3 only
- Decision class: Explicit current slice requirement
- Supersedes: ADR-0013 reservation lifecycle boundary only

## Context

Vertical Slice 2 created customer-owned inventory holds without introducing the
Order aggregate. This slice must turn exactly one hold into durable commercial
intent without consuming physical inventory or building payment.

## Decision

One customer-owned `ACTIVE` reservation creates one `PENDING_PAYMENT` Order and
one immutable OrderItem. The reservation becomes `ADOPTED`; this is deliberately
distinct from inventory `COMMITTED`, which remains the future physical stock
consumption transition.

The reservation row serializes create/create and create/release races. A unique
Order reservation reference backstops one adoption. Order cancellation locks
Order, Reservation, then InventoryBalance, transitions `ADOPTED -> RELEASED`,
and decrements reserved once without changing on-hand.

The OrderItem snapshots positive integer-VND unit price and quantity. Total is
derived from those immutable facts and current catalog price never recalculates
the Order.

## Consequences

- Exactly one reservation and one item are supported per Order in this slice.
- Customer ownership and `ORDER_PLACE` authorize create, read, and cancellation.
- Cart, CheckoutAttempt, payment, confirmation, fulfillment, expiry, promotion,
  tax, shipping, and multi-item orchestration remain outside this slice.

## Rejected alternatives

- Leaving an adopted reservation independently releasable.
- Treating adoption as physical inventory commit.
- Reading current catalog price when displaying historical Order value.
- Application-only duplicate checks without a reservation lock and uniqueness.
