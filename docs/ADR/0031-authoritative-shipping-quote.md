# ADR-0031: Authoritative shipping quote

Status: Accepted for Phase C1

## Decision

Delivery `CartQuote` selects the lowest qualifying internal `Location.id`, preserving the certified inventory rule. It snapshots that Location and Branch, stable province/district codes, the immutable shipping-rule revision, zone, merchandise amount, shipping fee and final total. Checkout reserves at that exact Location; loss of availability is a conflict and never triggers a silent substitute.

Published rule bodies are immutable. Normal replacement creates a new revision. `RETIRED` excludes new quotes while an unexpired quote remains valid; `INVALIDATED` rejects referenced quotes. Publication uses a serializable SQL Server range lock and rejects equal-scope, equal-priority overlapping periods.

Province/district codes come from the project’s offline GSO-2024 administrative-code snapshot. C1 imports only Hà Nội (Ba Đình, Cầu Giấy, Đông Anh), Hồ Chí Minh City (District 1, Thủ Đức), and Đà Nẵng (Hải Châu). Labels are presentation data.

`commerce_order.total_amount` is final payable and equals `merchandise_amount + shipping_fee_amount`. Order is the monetary authority. The former fulfillment delivery-fee column is removed; fulfillment APIs read the Order fee.

Location rule administration requires `SHIPPING_RATE_MANAGE` plus exact active Location access. Global rules require both `SHIPPING_RATE_MANAGE` and `IDENTITY_MANAGE`. Branch-wide interactive administration is deferred because current scope access does not prove authority over every Location in a Branch.

Promotions, configurable fulfillment priority, geocoding and ward-based pricing remain outside C1.
