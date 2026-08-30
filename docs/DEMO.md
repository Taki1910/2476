# Core MVP demo

Use a fresh disposable SQL Server database; never edit schema or reporting rows manually. Start the backend with `SPRING_PROFILES_ACTIVE=demo`. Flyway applies V1–V15 and the isolated demo bootstrap creates the baseline once; a second startup recognizes the same stable codes/SKUs without duplication.

Demo profile only — every account uses the disposable password `DemoPass!2026`:

- Customer: `customer.demo`
- Fulfillment: `operations.demo`
- Cashier: `cashier.demo`
- Reporting manager: `manager.demo`

The bootstrap creates Demo Branch A / Demo Sales Floor, register `DEMO-01`, and published Court Classic variants `DEMO-CC-BLK-40` and `DEMO-CC-BLK-41` for 125000 VND with on-hand 3 each. It creates no transactional outcomes.

1. Receive/set inventory at a permitted location.
2. Sign in as a customer, browse a product, select its variant, request a quote, then check out.
3. In the demo profile, complete the isolated deterministic payment route opened after payment initiation. It validates application orchestration only, not VNPAY certification.
4. As fulfillment staff, prepare then hand over the paid pickup. Alternatively, use the supported confirmed-cancellation flow and show its void/reconciliation state.
5. As cashier, open a shift, look up an in-stock SKU, receive exact cash, confirm the sale, and show the immutable receipt.
6. As a manager, open Reporting for the same location/time range and compare online gross, POS gross, successful voids, net sales, product sales, inventory, and reconciliation exceptions.

For deterministic concurrency and reconciliation proof, run `mvn verify`; the SQL Server integration suite covers final-unit online races, payment versus expiry, handover versus cancellation, POS versus online final-unit, and sale versus shift-close races.

## Payment honesty

There is no production payment-success bypass. The demo completion and Void adapters exist only in the explicit `demo` profile; default/local profiles retain the real VNPAY boundary. Real sandbox payment and full reversal are **not run** unless the operator supplies merchant credentials and callback infrastructure.
