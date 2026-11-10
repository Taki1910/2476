# Reporting API contract

Authority: [ADR-0034](../ADR/0034-paid-component-reversal-and-reporting.md).
Existing controller: `backend/src/main/java/com/shoecommerce/reporting/ReportingController.java`;
DTOs and application authorization: `ReportingService.java` in that directory.
Consumer: `frontend/src/views/ReportsView.vue` via `frontend/src/api.ts`.

All paths below are GET under `/api/v1/operations/reports`. Reads have no
idempotency key or mutation; authentication and server-side location/report
authorization remain required. Existing 401/403 and ProblemDetail errors remain.

| Path | Request | Response |
|---|---|---|
| `/scope` | None | ScopeReport, permitted locations |
| `/net-sales` | fromDate, toDate, locationId | NetSalesReport |
| `/product-sales` | fromDate, toDate, locationId | ProductSalesReport |
| `/inventory` | locationId, optional sku | InventoryReport |
| `/reconciliation` | fromDate, toDate, locationId | ReconciliationReport |

Dates retain the current Vietnam business-day range semantics; monetary fields
are integer-VND strings. Existing onlineGross/posGross/grossSales fields retain
their settled-receipt meaning. Existing product net's gross-price bug is corrected
to immutable paid merchandise. Do not include shipping in SKU revenue.

Implemented additive fields (SQL and frontend regression verified): merchandiseGross,
itemDiscount, orderDiscount, voucherDiscount, merchandiseNetBeforeReversal,
shippingGross, shippingDiscount, shippingNetBeforeReversal, merchandiseVoids,
shippingVoids, unallocatedLegacyVoids. Product rows expose relevant merchandise
breakdowns only. netSales recognizes captures minus successful reversals in the
selected period. Legacy unallocated reversal is explicit; it is not assigned to
SKU by the old proportional algorithm. UNKNOWN/failed requests are not successful
reversal revenue; reconciliation displays their existing operational states.

Successful reconciliation categories are `VOID` for SNAPSHOT_V2 merchandise,
`VOID_SHIPPING` for SNAPSHOT_V2 shipping and `VOID_LEGACY` for historical allocations.
ProductSalesReport carries `unallocatedLegacyVoids` at report level. Product rows
never absorb those amounts; therefore their sum may differ from total cashflow.
An empty result retains zero totals and empty rows. Invalid dates/location filters
are rejected by the service; authorization is checked before scoped data is read.

Acceptance: M1–M10 in the financial plan, VerticalSlice7ReportingExternalIT,
frontend report rendering, and actual cancellation/reporting flow. This contract's
approval is separate from implementation verification; see CURRENT_BASELINE.
