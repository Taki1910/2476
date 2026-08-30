# ADR-0020: Quote Checkout, Atomic Reservation, and Order Evidence

- Status: `SUPERSEDED` by ADR-0021; its non-deadline decisions are reaffirmed there
- Accepted: 2026-08-26
- Scope: Vertical Slice 3 customer checkout
- Decision class: Inventory concurrency, checkout idempotency, and reservation expiry
- Extends: ADR-0019

## Context

A customer PriceQuote is immutable price evidence, not a stock hold. Checkout
must turn one owned, unexpired quote into one pending Order and one authoritative
location reservation without overselling, duplicate creation, or partial state.

## Decision

- Support one quoted variant with quantity one. Payment, Voucher, Cart,
  multi-line allocation, and fulfillment remain out of scope.
- Serialize checkout commands for one customer on the existing account row.
  Under that fence, `(owner, Idempotency-Key)` returns the same Order for the
  same quote and rejects a different quote. Database uniqueness is the final
  backstop. This is the bounded rank-0 command fence; a separate CheckoutAttempt
  table is deferred until checkout needs pending/unknown provider recovery.
- Read PriceQuote without a write lock because it is immutable. Recheck owner,
  server time, and current variant publication during checkout. One quote may
  create at most one Order, enforced by a filtered unique index.
- Consider enabled Location balances in internal Location-ID order. For each,
  take a SQL Server pessimistic write lock and recheck
  `onHand - reserved >= 1`; the first successful row is authoritative. No
  customer-visible quantity or Location choice is introduced.
- In one application transaction, validate the quote and sellability, reserve
  the locked balance, append an `ADOPTED` InventoryReservation, append the
  `PENDING_PAYMENT` Order/OrderItem snapshots, and append reservation/order
  audit events. Any failure rolls back every effect.
- The current schema requires the new Reservation fact before the new Order's
  foreign key. This bounded create path never acquires a pre-existing Order
  after a Balance lock, so it introduces no reverse wait edge. Every transition
  of an existing Order still follows Order -> Reservation -> Balance.
- The original decision tied a checkout hold to its source quote's `expiresAt`.
  ADR-0021 supersedes only that deadline rule; the atomic creation, lock order,
  and lazy transition design in this record remain accepted.

## Consequences

- SQL Server row locking and the balance constraints prevent overselling across
  application instances; frontend button state is not part of correctness.
- Reservation expiry uses server time and lazy cleanup. No scheduler, broker,
  cache, or distributed lock is required.
- Per-customer checkout commands are serialized. Replace the account-row fence
  with a dedicated command-claim record only if measured customer checkout
  throughput or provider recovery requires it.
- The Order snapshots quote/version identity, SKU, canonical size, VND unit
  price, quantity, and total. Current Catalog/Pricing changes cannot rewrite it.

## Rejected alternatives

- In-memory synchronization, Redis, distributed locks, or stale availability.
- Decrementing `onHand` before payment success.
- Client-authored amount, currency, total, Location, or order identity.
- A background scheduler solely for this slice.
- Reusing one quote for multiple successful Orders.
