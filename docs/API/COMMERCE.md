# Commerce contracts — current source index

Authority: ADR-0031 shipping, ADR-0032 automatic promotions,
[ADR-0035](../ADR/0035-voucher-family-claim-usage.md) voucher ownership/usage,
[ADR-0034](../ADR/0034-paid-component-reversal-and-reporting.md) paid money, and
[ADR-0040](../ADR/0040-semantic-errors-and-demo-address-resolution.md) semantic
errors and delivery-address resolution.
DTO definitions in the listed controllers/services remain the exact wire schema;
this index does not authorize a new endpoint or a client-side pricing calculation.

| Method / path | Contract source | Actor and behavior | Consumer / verification |
|---|---|---|---|
| GET `/api/v1/storefront/promotions` | StorefrontPromotionController; VoucherService.Page/PublicOffer | Public, page >= 0, size 1–100; published/discoverable only | OffersView; PromotionC3DiscoveryClaimExternalIT |
| GET `/api/v1/storefront/promotions/{familyId}` | VoucherService.PublicOffer | Public; safe unavailable 404; includes `code` only for a published, publicly discoverable CODE family linked by a storefront placement | Offer detail/banner; same suite |
| GET `/api/v1/me/voucher-claims` | VoucherClaimController; Page/SavedClaim | CUSTOMER; account-owned, paginated | My Vouchers; discovery/claim suite |
| PUT `/api/v1/me/voucher-claims/{familyId}` | VoucherService.ClaimView | CUSTOMER; atomic natural-key replay; no usage on save | Save action; discovery/claim concurrency tests |
| GET/POST `/api/v1/operations/promotions` | PromotionController; RuleView/Draft | PROMOTION_MANAGE; server validation; create draft | Promotion administration; PromotionC3AdministrationExternalIT |
| PUT `/api/v1/operations/promotions/{id}` | PromotionService.Draft/RuleView | PROMOTION_MANAGE; editable draft only | Same |
| POST `/api/v1/operations/promotions/{id}/publish` | PublishCommand/RuleView | PROMOTION_MANAGE; expected published revision fence | Same; concurrent publication tests |
| POST `/api/v1/operations/promotions/{id}/retire` or `/invalidate` | RuleView | PROMOTION_MANAGE; explicit transition, different accepted-quote semantics | Same; stale quote tests |
| PUT `/api/v1/operations/promotions/voucher-claims/{claimId}/revoke` | ClaimView | PROMOTION_MANAGE; terminal replay, audit | Same |
| GET `/api/v1/reference/provinces/{provinceCode}/districts/{districtCode}/wards` | ShippingController; AddressResolver.Reference | Public; enabled `DEMO-2026` wards in the selected hierarchy | CartView; ShippingC1ExternalIT |

The promotion controllers and services above are in
`backend/src/main/java/com/shoecommerce/promotion/`. Authentication/CSRF and the
common ProblemDetail contract apply to mutations. Ownership and permissions are
also checked at the application boundary. No idempotency header is invented for
these natural-key/transition operations.

Quote/checkout's existing DTO authorities are StorefrontController.CartQuoteRequest,
CartQuoteService and CustomerOrderController.CartCheckoutRequest / CustomerOrderService.
Quote determines money and availability; checkout revalidates immutable quote
evidence under existing locks. A selected voucher is NONE, CODE or CLAIM, never an
arbitrary client discount. A stale selection fails without substitution or partial
Order/stock/usage effects. Checkout preserves account/idempotency-key fingerprinting.

New delivery quotes require `destinationProvinceCode`, `destinationDistrictCode`
and `destinationWardCode`. Delivery checkout requires receiver name and phone,
`provinceCode`, `districtCode`, `wardCode`, `addressLine` and an optional note.
Shipping fees remain district-based; the ward is hierarchy validation and an
immutable order/fulfillment snapshot.

Errors use ProblemDetail with a stable `code`. Validation failures may also return
`fieldErrors`, for example `{ "receiverPhone": "INVALID" }`. Address failures use
`ADDRESS_PROVINCE_UNKNOWN`, `ADDRESS_DISTRICT_UNKNOWN`, `ADDRESS_WARD_UNKNOWN` or
`SHIPPING_DESTINATION_UNSUPPORTED`; unavailable payment initiation uses
`PAYMENT_PROVIDER_UNAVAILABLE` with HTTP 503 and no provider configuration detail.

Zero-total quote now uses `ZERO_PAYABLE_NOT_SUPPORTED`; client uses localized
recovery instructions. The cap error `CART_AMOUNT_LIMIT` retains its distinct
meaning. Financial void/reporting additions are implemented with targeted SQL
regressions; consult CURRENT_BASELINE for the separate full acceptance/browser gates.
