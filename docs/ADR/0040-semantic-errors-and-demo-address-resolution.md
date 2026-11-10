# ADR-0040: Semantic API errors and demo delivery-address resolution

- Status: `ACCEPTED`
- Date: 2026-09-27
- Extends: ADR-0020, ADR-0022, ADR-0027 and ADR-0031

## Context

Checkout currently quotes delivery from province and district only. The final
fulfillment row stores receiver data and one free-form address, so it cannot
retain an independently readable ward or the labels that the customer selected.
Invalid locations, malformed receiver fields and provider failures can also
collapse into generic HTTP 400 handling. In particular, missing VNPAY
configuration is an operational failure but is presented as customer input.

This slice must improve conversion safety without changing shipping prices,
inventory ownership, promotion evaluation, payment idempotency or the rule that
only a verified provider result confirms payment.

## Decision

### Stable error contract

Every REST error remains RFC 9457 `ProblemDetail` and includes a stable `code`.
Boundary-validation errors use `VALIDATION_FAILED` and may include a
`fieldErrors` object whose keys are request-property paths and whose values are
machine-readable error identifiers. Business errors keep their existing codes.

The conversion flow additionally defines:

- `ADDRESS_PROVINCE_UNKNOWN`, `ADDRESS_DISTRICT_UNKNOWN` and
  `ADDRESS_WARD_UNKNOWN` — malformed or unknown location selection (`400`);
- `SHIPPING_DESTINATION_UNSUPPORTED` — a valid resolved location outside the
  published shipping rules (`409`);
- `PAYMENT_PROVIDER_UNAVAILABLE` — payment-session creation cannot currently be
  provided (`503`);
- `HTTP_500`/network failure — safe generic retry guidance.

Details never contain credentials, configuration values, stack traces or Java
exception names. Correlation IDs remain available for support and server logs.

### Address resolver

Checkout depends on an `AddressResolver` application boundary, not arrays in a
controller or Vue component. The first implementation reads enabled provinces,
districts and wards from SQL tables and returns normalized codes, labels and a
canonical address. A future external provider can replace this implementation
without changing checkout or shipping-rule evaluation.

The local dataset is intentionally small and tagged `DEMO-2026`. Resolution
proves only that the submitted administrative hierarchy is present in that
dataset. It does not geocode or prove that a street/house address physically
exists.

### Quote, checkout and persistence

Delivery quote input adds `destinationWardCode`. The resolver validates the
province/district/ward hierarchy before the existing district-based shipping
rule is evaluated. The quote and Order snapshot the ward code so checkout can
reject a changed address hierarchy.

Checkout delivery input adds `wardCode`. It resolves the complete address and
persists receiver name, phone, province/district/ward codes and labels, detailed
address line, canonical address and optional note on the fulfillment snapshot.
Existing `delivery_address` remains the canonical display address for backward
API compatibility.

Flyway V30 appends the ward reference table and snapshot columns. Historical
delivery rows remain valid; old migrations are not changed.

### Shipping and payment compatibility

Shipping rule selection continues to use province and district only. No fee,
zone, origin-location or inventory behavior changes.

VNPAY configuration absence is raised as `PAYMENT_PROVIDER_UNAVAILABLE` before
any PaymentAttempt is persisted. The surrounding transaction therefore retains
the existing retry and idempotency guarantees. No successful provider result is
fabricated.

## Consequences

- A valid but unsupported address is distinguishable from an unknown ward.
- The customer can switch to pickup without losing the cart or entered address.
- Existing delivery clients must add a ward before creating new delivery quotes;
  pickup contracts are unchanged.
- Only the bundled demo hierarchy is selectable until an approved provider and
  production dataset are introduced.
