# ADR-0003: Make Order the Commerce Aggregate

- Status: `ACCEPTED`
- Accepted: 2026-08-24
- MVP scope: Core architecture baseline
- Date: 2026-08-23
- Decision class: Safe to freeze after independent review
- Decision source: repository governance plus Blueprint v1.1.1 AR-02 correction

## Context

Legacy duplicates mutable commerce data across `DonHang` and `HoaDon` and uses
invoice as the center of checkout, payment, and stock changes.

## Decision

Use Order/OrderItem as the shared commerce core for POS and online workflows.
Store confirmed pricing/product/promotion snapshots on the order. Treat legal
invoice/receipt artifacts as downstream documents whose exact policy remains an
open business decision.

## Consequences

- POS and online share invariants but use different application workflows.
- Payment, Inventory, Fulfillment, Return, and Refund remain separate owners.
- Confirmed order pricing is immutable.
- An allowed confirmed pre-dispatch cancellation is coordinated from Order but
  preserves ownership: Fulfillment fences dispatch and Inventory records one
  compensating restoration movement; committed Reservation history is not
  rewritten.

## Rejected alternatives

- Recreating `DonHang -> HoaDon`.
- Separate unrelated POS-order and online-order data models.
