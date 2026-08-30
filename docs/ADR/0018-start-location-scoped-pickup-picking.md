# ADR-0018: Start Location-Scoped Pickup Picking

- Status: `ACCEPTED`
- Accepted: 2026-08-25
- MVP scope: Vertical Slice 7 only
- Decision class: Explicit current slice fulfillment-lifecycle and transaction decision
- Extends: ADR-0017 location-scoped pickup fulfillment

## Context

A pending pickup fulfillment needs one operational transition showing that staff
at its physical Location began preparation. This must remain distinct from
commercial confirmation, inventory consumption, readiness, and handover.

## Decision

Add exactly `PickupFulfillment PENDING -> PICKING` and immutable
`pickingStartedAt`. The task-oriented command accepts only the fulfillment public
UUID. `FULFILL_PICKUP` plus a current active assignment to its exact enabled
Location is required. The staff account is captured by transactional audit only;
there is no picker assignment state.

The transaction takes a pessimistic lock on the PickupFulfillment row at rank 2,
then revalidates `PENDING`, Branch/Location coherence, `PAID` Order, one
`SUCCEEDED` PaymentAttempt, and the `CONSUMED` reservation. Those commercial
states are terminal in the current bounded model and are read without acquiring
lower-rank business locks. The winner sets `PICKING` and `pickingStartedAt` and
appends `PICKUP_PICKING_STARTED`. A repeated or concurrent losing command returns
conflict and produces no mutation or audit.

A SQL lifecycle check permits only `PENDING` with a null timestamp or `PICKING`
with a non-null timestamp. Audit failure rolls back the transition. Order,
PaymentAttempt, Reservation, and InventoryBalance remain unchanged.

## Consequences

- Disabled Branch/Location scope cannot start new handling; relocation is deferred.
- The existing optimistic version remains a stale-write backstop behind the row lock.
- No client status assignment or idempotency-key framework is introduced.
- `READY_FOR_PICKUP`, handover, customer verification, notifications, picking
  lists, workforce assignment, cancellation, returns/refunds, and inventory
  movement remain deferred.

## Rejected alternatives

- A generic state-machine/workflow framework.
- A mutable status endpoint.
- A new permission for one transition.
- Storing picker ownership without a workforce-assignment requirement.
- Reallocating or decrementing inventory when picking begins.
