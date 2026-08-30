# ADR-0015: Bounded Idempotent Payment Initiation

- Status: `SUPERSEDED` by ADR-0022 for online electronic payment
- Accepted: 2026-08-25
- MVP scope: Vertical Slice 4 only
- Decision class: Explicit current slice requirement implementing ADR-0005

## Context

An owned pending Order needs a durable local financial intent before any future
provider call. Client retries and concurrent submissions must not create a
double-charge-capable state or alter inventory.

## Decision

One `Payment` belongs to one Order and owns `PaymentAttempt` children. This slice
implements only `PENDING -> CANCELLED`. The attempt snapshots the immutable Order
total and currency; no amount comes from the request or current catalog.

The exact case-sensitive idempotency scope is customer account plus opaque key.
The Order identity is the request fingerprint: same key and Order replays the
existing attempt, while the same key with another Order conflicts. A SQL Server
key-range lock claims the scoped key before the Order lock. Scoped uniqueness
backstops idempotency, and a filtered unique index allows at most one `PENDING`
attempt per Payment.

Order cancellation follows Order -> Payment/Attempt -> Reservation -> Balance,
cancels a pending local attempt, and releases the adopted reservation atomically.

## Consequences

- First creation returns HTTP 201; a valid replay returns HTTP 200.
- Another key for an Order with a pending attempt returns HTTP 409.
- `PAYMENT_INITIATE` is customer-facing; `PAYMENT_EVENT_APPLY` remains provider-only.
- Provider selection/calls/references, credentials, callbacks, result states,
  transactions, retries, capture, void, refund, reconciliation, and stock commit
  remain deferred.

## Rejected alternatives

- A generic idempotency framework or provider interface.
- Client-supplied amount or catalog-price recalculation.
- Application-only existence checks without locks and unique indexes.
- Leaving a pending PaymentAttempt after successful Order cancellation.
