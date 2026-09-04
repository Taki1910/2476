# ADR-0027: Pickup and delivery fulfillment

- Status: `ACCEPTED` for Phase 16
- Date: 2026-09-01
- Extends: ADR-0017, ADR-0018, ADR-0023 and ADR-0026
- Supersedes: pickup-only intent creation after payment and pickup-only naming in
  the active fulfillment product contract

## Context

Phase 15B has one Order, one common inventory Location, N immutable OrderItems,
N explicit Reservations and one pickup Fulfillment fence. It already serializes
handover and paid cancellation in the required Order -> Fulfillment -> Payment
-> Reservations -> InventoryBalances order. Adding a separate delivery
aggregate would duplicate that lifecycle owner and make cancellation, stock and
staff queues disagree.

## Decision

Evolve the existing one-to-one row into the single generalized Order
Fulfillment aggregate. The physical `pickup_fulfillment` table and legacy pickup
HTTP aliases remain temporarily for append-only migration and regression
compatibility; they are implementation names, not a second source of truth.

Every new cart checkout chooses exactly one type:

- `PICKUP`: the customer selects one public Location returned by the server for
  the quoted cart. Checkout revalidates that the Location and Branch are enabled,
  that every line is available there and that the UUID was eligible for this
  cart. A client cannot submit an arbitrary internal database identifier.
- `DELIVERY`: checkout snapshots receiver name, receiver phone, complete address
  and optional note. The server selects the first common enabled stock Location
  by the existing deterministic lock/order rule. Phase 16 delivery fee is the
  confirmed integer-VND snapshot `0`; no client-authored fee or total is accepted.

The fulfillment intent is inserted in the same transaction as the pending Order
and its N Reservations. It is not actionable or visible in the employee queue
until the Order is `PAID`. Payment continues to commit every Reservation without
issuing physical stock.

## Lifecycles

`PICKUP` uses the existing persisted states:

```text
PENDING -> PICKING -> PREPARED -> HANDED_OVER
   |          |           |
   +----------+-----------+-> CANCELLED
```

`PICKING` is explicit acceptance/start preparation. `PREPARED` means ready for
pickup. Handover consumes all committed Reservations and appends one immutable
`PICKUP_HANDOVER` movement per OrderItem.

`DELIVERY` reuses the shared preparation states and adds dispatch states:

```text
PENDING -> PICKING -> PREPARED -> OUT_FOR_DELIVERY -> DELIVERED
   |          |           |
   +----------+-----------+-> CANCELLED
```

For delivery, `PREPARED` means ready for dispatch. The atomic transition to
`OUT_FOR_DELIVERY` is the physical stock-issue point: it consumes every committed
Reservation and appends one immutable `DELIVERY_DISPATCH` movement per OrderItem.
`DELIVERED` changes only fulfillment evidence. Direct cancellation after
dispatch is forbidden and requires a future return-to-sender/Return workflow.

All displayed timeline events come from persisted timestamps: creation,
acceptance, ready, dispatch, delivery, handover and cancellation. Type-specific
database constraints reject impossible states or snapshot combinations.

## Cancellation, money and concurrency

Paid cancellation remains a full-order operation. Before pickup handover or
delivery dispatch it atomically:

1. locks the actor command fence, Order and Fulfillment;
2. reserves the exact full captured amount and one immutable ORDER_ITEM void
   allocation per line;
3. changes the Order and Fulfillment to `CANCELLED`;
4. changes every Reservation `COMMITTED -> CANCELLED_RESTORED`, decrements
   `reserved` and leaves `onHand` unchanged;
5. appends one `CANCELLATION_RESTORE` movement per reservation.

Handover/dispatch and cancellation use the same Order then Fulfillment lock
order, so exactly one wins. Accept/ready/dispatch/delivered transitions lock the
same rows and permit exactly one valid state change. Provider calls remain
outside the local transaction and retries reuse the original idempotency key.

## Authorization and public contracts

Generalized employee commands require `FULFILL_ORDER` and an active exact
Location assignment. The Operations role receives it; customer and Cashier do
not. Customer order reads remain owner-scoped. Queue/detail SQL joins the current
assignment and never trusts frontend filtering. Disabled Branches or Locations
cannot be selected at checkout or used to start work.

The cart quote response carries currently eligible pickup Locations as public
UUID/code/name facts. Checkout includes a typed fulfillment request; the type,
selected Location or delivery snapshot are part of the idempotency fingerprint.
Order responses and employee queue/detail expose the immutable fulfillment type,
safe delivery snapshot, authoritative state and timestamps.

## Migration and compatibility

Flyway V19 appends type, delivery snapshot, dispatch/delivery evidence and their
constraints/indexes; all existing rows backfill as `PICKUP`. It also adds
`FULFILL_ORDER` and extends stock movement constraints for
`DELIVERY_DISPATCH`. V1-V18 are unchanged. Fresh V1-V19 and populated V18->V19
upgrade both remain mandatory. Hibernate continues with `ddl-auto=validate`.

Legacy paid Orders without a pre-created intent may still use the existing
server-side creation fallback. New cart Orders always persist intent at checkout.
Split-location fulfillment, carrier integration, delivery tracking, partial
delivery/cancellation and returns remain outside Phase 16.
