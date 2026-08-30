# ADR-0025: Authoritative Reporting and Reconciliation

- Status: `ACCEPTED`
- Accepted: 2026-08-28
- Scope: Vertical Slice 7 basic reporting and Core MVP reconciliation
- Decision class: reporting authority, financial inclusion, time, and scope
- Extends: ADR-0003, ADR-0004, ADR-0006, ADR-0022, ADR-0023, and ADR-0024

## Context

Online capture, successful Void allocations, POS CashTender, OrderItem snapshots,
InventoryBalance, Reservations, StockMovement, Branch, and Location already hold
the facts needed by the Core MVP reports. A second mutable reporting ledger would
duplicate authority and make reconciliation less trustworthy.

## Decision

Reporting is a read-only application module. Purpose-built SQL projections read
the existing transaction tables directly; no report total, cache, snapshot, or
report-owned business state is persisted.

Financial inclusion is:

```text
onlineGross = successful PaymentAttempt amount
posGross = accepted CashTender amount
grossSales = onlineGross + posGross
successfulVoids = SUCCEEDED ORDER_ITEM VoidAllocation amount
netSales = grossSales - successfulVoids
```

Product Sales uses the same financially recognized online/POS facts, grouped by
immutable OrderItem variant, SKU, and size evidence. Only successful ORDER_ITEM
VoidAllocations reduce net product sales. Current Core MVP has no shipping, tax,
voucher, or Refund financial component, so Net Sales must equal the sum of Net
Product Sales for the same scope and interval.

`REVIEW_REQUIRED` PaymentAttempt represents provider-confirmed money that could
not safely confirm the merchant Order. It is excluded from normal Gross/Net
Sales and exposed with its amount as a reconciliation exception. `UNKNOWN` and
`REVIEW_REQUIRED` Void operations remain exceptions with zero recognized
reversal. A definitively failed operation with RELEASED allocation is retry work
and also has zero recognized reversal. Only `SUCCEEDED` allocation evidence is
subtracted; successful retry is therefore counted once.

Sales intervals are Vietnam business dates in `Asia/Ho_Chi_Minh`. The server
converts requested dates to UTC instants using half-open `[from, to)` boundaries.
Every response carries a server `asOf`. Inventory is a current/as-of report from
InventoryBalance; `available` is derived as `onHand - reserved`. StockMovement
and contributing Reservation facts are explanatory evidence, not balance
authority.

Every report requires `REPORT_VIEW` and an active assignment to the requested
enabled Location. Historical sales attribution comes from immutable Order
responsible Branch and OrderItem Location evidence, never current staff,
Register, Product, or Price configuration.

Each compound report runs in a read-only SQL Server `REPEATABLE_READ`
transaction. The MVP dataset is bounded and queries are aggregate/projection
statements, so this provides an internally consistent response without
serializable commerce locking or entity-by-entity loading.

## Database consequence

No V16 migration is introduced. Existing constraints and access paths are
sufficient for the MVP-sized dataset. Query plans are kept set-based; indexes
may be added later only after measurement shows a concrete reporting access
path needs one.

## Rejected alternatives

- Mutable daily totals, reporting snapshots, or a reporting ledger.
- Revenue derived from Order status alone.
- Counting both Order and Payment/CashTender for one sale.
- Subtracting cancellation, UNKNOWN, ACTIVE, or RELEASED reversal state.
- Browser-defined timezone boundaries or financial arithmetic.
- Redis, OLAP, warehouse, Kafka, export, decorative charts, or report builders.
