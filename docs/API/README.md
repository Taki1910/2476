# API contract index

The REST controllers and their DTOs define the existing wire contracts; approved
ADRs and domain rules define behavior. JPA entities are not public contracts.
All paths use `/api/v1` unless an existing controller explicitly specifies otherwise.

- [Commerce, promotions and vouchers](COMMERCE.md)
- [Reporting and accepted additive changes](REPORTING.md)
- [Identity/session and login throttling](IDENTITY.md)
- [Complete controller inventory and operation contracts](OPERATIONS.md)
- Identity/session: `identity/AuthController.java`, `SecurityConfiguration.java`,
  Foundation1SecurityExternalIT; login throttle policy [ADR-0036](../ADR/0036-bounded-single-instance-login-throttle.md).
- Catalog/quotes: `catalog/StorefrontController.java`, `pricing/CartQuoteService.java`,
  VerticalSlice2CustomerCatalogExternalIT and VerticalSlice3CheckoutExternalIT.
- Orders: `order/CustomerOrderController.java`, CustomerOrderService,
  VerticalSlice3ExternalIT and VerticalSlice3CheckoutExternalIT.
- Payment and fulfillment: controller/DTOs under `payment/` and `fulfillment/`,
  VnPayPaymentExternalIT and VerticalSlice4–7 external suites.
- Staff/branch scope: controller/DTOs under `identity/` and `branch/`, ADR-0030,
  Foundation1SecurityExternalIT.
- Inventory and POS: controller/DTOs under `inventory/` and `pos/`,
  VerticalSlice2ExternalIT, InventoryTransferExternalIT and
  VerticalSlice6PosExternalIT.
- Shipping: `shipping/` controller/DTOs, ADR-0031 and ShippingC1ExternalIT.
- Storefront configuration: `merchandising/` controller/DTOs, ADR-0033 and StorefrontMerchandisingExternalIT.
- Fitting: `fitting/` controller/DTOs and FitAnalysisExternalIT.

Java source paths above are relative to `backend/src/main/java/com/shoecommerce/`;
test suites reside under `backend/src/test/java/com/shoecommerce/`. The operation
inventory covers all 23 controllers plus security-chain login/logout. Exact DTOs
are referenced directly to avoid duplicating conflicting schemas.

Common requirements: server DTO validation, authenticated application authorization
and branch/location scope where applicable, stable ProblemDetail code, monetary
integer-VND strings where the current DTO uses strings, and explicit existing
idempotency semantics. GET browse endpoints may be public; that does not authorize
customer-owned data or staff actions. SPA consumers live in `frontend/src/api.ts`.

## Transaction-contention problem contract

After a complete local rollback caused by SQL Server error `1205` or `1222`, an
idempotent mutation returns HTTP `503` with safe ProblemDetail fields:

```json
{
  "status": 503,
  "detail": "This request conflicted with another operation. Try the same action again.",
  "code": "CONCURRENCY_RETRY_REQUIRED",
  "retryable": true,
  "reuseSameIdempotencyKey": true
}
```

The client does not retry automatically. A user-initiated retry submits the
exact payload with the same idempotency key. It must not create a new operation
or key for that retry; changed payload requires a new command identity. The
server does not automatically replay the transaction, does not retry external
providers, and emits no `Retry-After` header without an approved wait policy.
Raw SQL, Hibernate and JDBC details are never part of this response. See
[ADR-0044](../ADR/0044-client-owned-contention-retry.md).

## Manual VNPAY reconciliation contract

`POST /operations/payment-attempts/{attemptId}/reconcile` accepts no request
body and requires `Idempotency-Key`, `PAYMENT_RECONCILE` and exact Location
scope. The server derives the original reference, timestamp and VND amount from
stored evidence, performs one signed QueryDr call outside database transactions,
requires mandatory numeric response status/transaction fields and the stored
provider transaction number to match when one is known, and
returns only the safe result fields indexed in [OPERATIONS](OPERATIONS.md).

The same actor/key/attempt replays the command; the same actor/key with another
attempt returns `IDEMPOTENCY_KEY_CONFLICT`. Signature or identity/amount mismatch
returns `PAYMENT_RECONCILIATION_RESULT_MISMATCH` without mutation. Provider
transport failure is distinct and is never automatically retried. When local
apply receives the ADR-0044 contention response, the UI may explicitly reuse
the same key and exact attempt; stored verified evidence is applied without a
second QueryDr call. A new deliberate provider query after provider failure uses
a new key.

Possible success outcomes are `RESOLVED_PAID`, `RESOLVED_FAILED`,
`STILL_UNRESOLVED` and `STILL_REVIEW_REQUIRED`. Reconciliation never creates a
new PaymentAttempt or reopens a cancelled/expired Order. Scheduled reconciliation
and live VNPAY certification are not implemented. See
[ADR-0045](../ADR/0045-manual-vnpay-querydr-reconciliation.md).
