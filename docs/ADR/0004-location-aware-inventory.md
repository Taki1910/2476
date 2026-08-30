# ADR-0004: Explicit Location-Aware Inventory

- Status: `ACCEPTED`
- Accepted: 2026-08-24
- MVP scope: Core architecture baseline
- Date: 2026-08-23
- Decision class: Safe ownership model; policy details require business confirmation
- Decision source: Blueprint v1.1.1 H-01 plus AR-02/AR-05 correction

## Context

Multi-branch commerce cannot keep stock on a catalog variant or calculate
reservation by scanning unpaid orders. Multiple POS and online clients can race
to sell, release, transfer, or refund the same units. The system needs one
authoritative invariant and deterministic concurrency behavior.

## Decision

Inventory owns:

- `InventoryBalance(ProductVariant, Location)` with `onHand` and `reserved`;
- `InventoryReservation` and its lines, linked to checkout/order purpose;
- append-only `StockMovement` with a unique business operation key;
- `StockTransfer` and its dispatch/receipt lifecycle.

Enforce:

```text
onHand >= 0
reserved >= 0
reserved <= onHand
available = onHand - reserved
```

`available` is derived, not separately mutable. The first release allocates one
location per order line.

### Concurrency protocol

1. Claim the idempotency/business operation identity before business effects.
2. Normalize duplicate demand by `(locationId, variantId)` and sum each strictly
   positive requested quantity before validation or locking.
3. Follow the global hierarchy in `ARCHITECTURE.md`: Order -> Fulfillment/Return
   -> Payment -> Price/Promotion/Voucher -> InventoryReservation ->
   InventoryBalance -> CashierShift, skipping unneeded ranks and never acquiring
   an earlier rank after a later one.
4. At the InventoryBalance rank, acquire every affected balance in canonical
   `(locationId, variantId)` order and revalidate quantity and invariants while
   protected.
5. Balance/reservation state and immutable movements commit in the same local
   database transaction. A deadlock/timeout rolls back the whole transaction and
   replays with the original operation key/fingerprint.
6. Duplicate operation keys return the prior outcome; a key reused for different
   input is rejected.

Reservation `commit`, `release`, and `expire` compete through the same locks and
a conditional transition from `ACTIVE`, so exactly one terminal action wins.

For an allowed confirmed cancellation before dispatch/handover, Order and
Fulfillment are locked before the committed Reservation and InventoryBalance.
The transaction cancels the still-cancellable Fulfillment, increases on-hand at
the original Location, and appends one immutable `CANCELLATION_RESTORE` movement
per OrderItem. The Reservation stays `COMMITTED`; unique movement identity makes
retry idempotent. A dispatch winner forbids this direct restoration and requires
return-to-sender/Return compensation.

### Transfers

At dispatch, source on-hand decreases and goods become in transit. At receipt,
destination on-hand increases. In-transit goods are unavailable at both
locations. Approval/cancellation policy remains open, but no transfer may make a
balance negative or silently edit movement history.

## Consequences

- Catalog has no stock quantities or repository authority over inventory.
- Inventory-changing use cases require database constraints plus race-condition
  integration tests; UI validation is insufficient.
- Reconciliation compares current balances with immutable movement history and
  flags discrepancies rather than rewriting history invisibly.
- Reservation TTL/renewal, allocation priority, transfer approvals,
  damaged/quarantine stock, and physical-count variance rules remain open.
- Backorder is disabled for the current Blueprint v1.1 baseline. Enabling it in
  a future version requires an explicit business decision and superseding ADR;
  it is not an open choice for current implementation.

## Risks and mitigations

- Pessimistic locking can reduce throughput: keep transactions short, use
  canonical ordering, index lock keys, and measure before considering a more
  complex strategy.
- Scheduled expiry can race with callbacks: conditional terminal transitions
  ensure one winner.
- Shared warehouse access can leak scope: grants/routes are explicit and checked
  before locks/mutations.

## Rejected alternatives

- `ProductVariant.tonKho` or one balance per variant.
- Computing reserved quantity from unpaid orders.
- Optimistic retry without a defined conflict/idempotency protocol.
- Redis/cache as stock source of truth.
- Split-location order lines in the first release.
