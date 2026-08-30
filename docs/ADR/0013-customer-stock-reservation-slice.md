# ADR-0013: Customer-Owned Stock Reservation Slice

- Status: `ACCEPTED`
- Accepted: 2026-08-25
- MVP scope: Vertical Slice 2 only
- Decision class: Explicit current slice requirement

## Context

The accepted inventory model requires explicit reservations, stored reserved
quantity, and database concurrency control. The broader checkout design creates
reservations with an Order/CheckoutAttempt, but Vertical Slice 2 explicitly
requires a usable reservation boundary without building those aggregates.

## Decision

For this slice, an authenticated customer may own a reservation directly by
account public UUID. A positive integer quantity moves one enabled Location's
balance from available to reserved under a pessimistic SQL Server row lock.

```text
available = onHand - reserved
0 <= reserved <= onHand
```

The bounded lifecycle is `ACTIVE -> RELEASED`. Release takes the reservation
lock before the balance lock and is idempotent. Physical stock adjustment locks
the same balance and cannot set `onHand` below `reserved`.

No timeout is invented because reservation TTL remains an open business policy.
Until a later accepted slice defines expiry, the owner must release the hold.

## Consequences

- This slice deliberately permits a pre-Order customer hold; it does not create
  Cart, CheckoutAttempt, Order, Payment, commit, or expiry semantics.
- A future checkout slice must explicitly attach, replace, or supersede this
  ownership model rather than silently treating it as an Order reservation.
- Real SQL Server concurrent last-unit evidence is required.

## Rejected alternatives

- Decrementing physical `onHand` for a temporary hold.
- Computing reserved stock by scanning carts or orders.
- Application-only timing checks, distributed locks, or a scheduler framework.
