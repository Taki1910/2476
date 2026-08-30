# ADR-0017: Location-Scoped Pickup Fulfillment Creation

- Status: `ACCEPTED`
- Accepted: 2026-08-25
- MVP scope: Vertical Slice 6 only
- Decision class: Explicit current slice requirement implementing the approved pickup lifecycle
- Extends: ADR-0011 branch/location scope and ADR-0016 paid Order boundary

## Context

A paid online Order needs a durable operational handoff without treating payment
as fulfillment or mutating already-consumed inventory. Creation must not permit
a client or broadly assigned employee to redirect stock responsibility.

## Decision

One `PickupFulfillment` may belong to one Order. It records a public UUID, the
Order, responsible Branch, exact Location, `PENDING` status, version, and creation
time. `PENDING` is the approved initial state; readiness is a later transition.

The command accepts only the Order public UUID. Under the existing pessimistic
Order lock it derives Branch and Location from immutable Order facts, verifies
the `PAID` Order, one successful PaymentAttempt, consumed reservation at that
Location, and coherent enabled Branch/Location. It requires persisted
`FULFILL_PICKUP` and an active assignment to that exact Location. Branch-only,
wrong same-Branch Location, cross-Branch, customer, provider, and ungranted
administrator authority are denied.

Creation and `PICKUP_FULFILLMENT_CREATED` audit share one transaction. A unique
Order constraint and the Order lock make sequential/concurrent duplicates a
stable conflict. The existing `(location_id, branch_id)` key is reused as a
composite foreign key. Order remains `PAID`; PaymentAttempt remains `SUCCEEDED`;
Reservation remains `CONSUMED`; InventoryBalance is unchanged.

## Consequences

- Disabled Branch/Location scope cannot create fulfillment; relocation is deferred.
- No client Location input can redirect the persisted fulfillment.
- Payment and Reservation are read only after the Order fence because their
  required states are terminal in the current bounded model.
- Shipping, picking/packing, ready-for-pickup, handover, cancellation
  compensation, return, refund, notification, and partial/multi-location
  fulfillment remain deferred.

## Rejected alternatives

- Starting in `READY_FOR_PICKUP` without preparation.
- Branch-only authorization or role-name checks.
- Client-selected fulfillment Location.
- Mutating inventory when creating the operational handoff.
- A generic shipment/workflow framework for this single transition.
