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
  VerticalSlice2ExternalIT and VerticalSlice6PosExternalIT.
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
