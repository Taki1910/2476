# Core MVP demo

Use a fresh disposable SQL Server database; never edit schema or reporting rows manually. Start the backend with `SPRING_PROFILES_ACTIVE=demo`. Flyway applies V1–V20 and the isolated demo bootstrap creates the baseline once; a second startup recognizes the same stable codes/SKUs without duplication.

## Start from a fresh database

Prerequisites: Java 25, an empty database already created on a reachable SQL Server instance, and Node.js/pnpm. Database creation is infrastructure setup; no business-data SQL is required. Supply your own local datasource credentials through environment variables, never committed files.

From the repository root, start the backend in one PowerShell terminal:

```powershell
$env:JAVA_HOME = '<path-to-jdk-25>'
$env:SPRING_DATASOURCE_URL = 'jdbc:sqlserver://localhost:<sql-port>;databaseName=<empty-demo-database>;encrypt=true;trustServerCertificate=true'
$env:SPRING_DATASOURCE_USERNAME = '<local-sql-user>'
$env:SPRING_DATASOURCE_PASSWORD = '<local-sql-password>'
$env:SPRING_PROFILES_ACTIVE = 'demo'
cd backend
.\mvnw.cmd spring-boot:run
```

In a second terminal, from the repository root:

```powershell
cd frontend
pnpm install
pnpm dev
```

Open `http://localhost:5173/`; the development server forwards API traffic to the backend on port 8080. Wait for backend startup to finish. Check the startup log for the explicit `demo` profile and successful Flyway/Hibernate validation. Do not reuse a legacy database or an older running backend when checking the current seed.

Keep the payment-result URL on the same browser origin as the storefront. The
default local origin is `http://localhost:5173`; when using `127.0.0.1`, start
the backend with `VNPAY_FRONTEND_RESULT_URL=http://127.0.0.1:5173/payment/result`.
This preserves the local session through the deterministic demo-payment redirect.

## Accounts and flow

Demo profile only — every account uses the disposable password `DemoPass!2026`:

- Customer: `customer.demo`
- Second customer: `customer.second`
- Fulfillment: `operations.demo`
- Cashier: `cashier.demo`
- Reporting manager: `manager.demo`

The bootstrap creates Demo Branch A with sales-floor and stockroom locations, three registers, 18 shoe stories / 73 published size variants, merchandising metadata, current and historical prices, and a healthy/low/zero stock mix. It also creates deterministic historical evidence: paid, failed, cancelled, voided, and `REVIEW_REQUIRED` online orders; pickup states plus delivery examples in `OUT_FOR_DELIVERY` and `DELIVERED`; closed shifts with baseline POS sales; and an intentional 7/30-day sales story. `GET /api/v1/storefront/hero` exposes data-driven merchandising candidates without fixing a product in the UI. New transactions can still be run through the steps below.

1. Use the seeded inventory at `DEMO-FLOOR`; no manual stock setup is necessary. For example, `DEMO-CC-39` starts with available stock. If a previously used demo database has exhausted it, select another available variant or create a new disposable database, not manual business-data SQL.
2. Browse/search a product, build a cart, sign in, check the server quote, then choose Pickup and an eligible Location or Delivery with receiver/address details before creating one order.
3. In the demo profile, complete the isolated deterministic payment route opened after payment initiation. It validates application orchestration only, not VNPAY certification.
4. As fulfillment staff, accept and ready the paid request, then hand over Pickup or dispatch and complete Delivery. Alternatively cancel before physical issue and show its void/reconciliation state.
5. As cashier, open a shift, look up an in-stock SKU, receive exact cash, confirm the sale, and show the immutable receipt.
6. As a manager, open Reporting for the same location/time range and compare online gross, POS gross, successful voids, net sales, product sales, inventory, and reconciliation exceptions.

POS lookup requires the complete variant SKU, not a product name or partial SKU. Each confirmation sells exactly one pair. Expected cash is the accepted cash within the cashier's shift; online sales do not increase it. Actual counted cash and a cash difference are not collected by this workflow.

The reporting date range defaults to the current business day in `Asia/Ho_Chi_Minh`; use an earlier start date to include historical demo POS sales. In Reconciliation, expand **Details and next action** for the seeded `REVIEW_REQUIRED` payment: the provider-confirmed amount stays visible with zero net effect. The report is read-only and exposes no provider-specific reason or resolution operation. Do not invent a resolution or hide this evidence.

Product pictures are illustrative, not guaranteed exact-color/variant photographs. The chosen SKU, size/color and server quote are authoritative. Use My Orders to find the same human-readable order reference, inspect every purchased line, track its state, or resume an eligible pending payment after refresh.

## Phase 17 — fitting demo

Use a fresh demo database and open any profiled product such as **Court
Classic**. On Product Detail:

1. Select **Find my size** and read the short A4 capture guide.
2. Choose `docs/demo-assets/fit/valid-a4-foot.png`.
3. Confirm the preview, then analyze it. The controlled result is approximately
   250.6 mm length and 97.8 mm width, with a deterministic Court Classic
   recommendation of EU 40. This is an idealized synthetic fixture result, not
   a claim of camera or clinical precision.
4. Select **EU 40**, verify that the existing size/color row is selected, and
   press **Add to cart** yourself. Fitting never adds a product automatically.
5. Choose another size manually to verify that the advisory result does not
   lock the normal variant controls.

For the failure path, choose
`docs/demo-assets/fit/invalid-no-reference.png`; the page must show a retake
message for the missing A4 reference and no size recommendation. The clipped
and blurred fixtures exercise their corresponding retake reasons.

The A4 reference is mandatory: an image with no complete reference must not
produce millimetres or a size. The recommendation is advisory; it never changes
to an in-stock size automatically, never switches colour automatically, and
never adds an item to the cart. See `docs/AI_FITTING_EVALUATION.md` for the
controlled-fixture limits and current real-camera validation gap.

For deterministic concurrency and reconciliation proof, set `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`, and `SPRING_DATASOURCE_PASSWORD` for a fresh isolated SQL Server database, then run the verified Windows acceptance command:

`.\mvnw.cmd -Pacceptance clean verify`

The profile fails when the datasource URL is absent; the suite covers final-unit online races, payment versus expiry, handover versus cancellation, POS versus online final-unit, and sale versus shift-close races. Ordinary `.\mvnw.cmd test` remains the fast unit-test command.

## Payment honesty

There is no production payment-success bypass. The demo completion and Void adapters exist only in the explicit `demo` profile; default/local profiles retain the real VNPAY boundary. Real sandbox payment and full reversal are **not run** unless the operator supplies merchant credentials and callback infrastructure.

## SQL Server acceptance recovery

Verify that SQL Server is running and that the configured datasource host, port,
and fresh disposable database are reachable. Run Java/Maven under the account
authorized for the configured authentication mode.

For Windows integrated authentication, configure the matching JDBC native library
path through `JAVA_TOOL_OPTIONS` when required by the selected driver. Keep the
datasource host, port, database, and authentication settings in the documented
environment variables; do not hard-code a workstation-specific connection.

Create a new database using the authorized account, then run
`.\mvnw.cmd -Pacceptance clean verify`. Keep the SQL-backed Failsafe results separate
from the default fast Surefire unit tests and from optional container suites. Use
only reports produced by the current run; do not sum old XML files.
