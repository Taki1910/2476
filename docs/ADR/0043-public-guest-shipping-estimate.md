# ADR-0043: Public Guest Shipping Estimate

- Status: `PROPOSED`
- Proposed: 2026-10-04
- Decision class: `OPEN DECISION` — public API and security boundary
- Related: ADR-0031 authoritative shipping quote; ADR-0040 address resolution

## Context

A guest can build a browser cart, but the current cart quote is not a safe
guest-estimate endpoint. `POST /api/v1/storefront/cart-quotes` is authenticated,
creates an account-owned `CartQuote`, stores promotion evidence, and is later
consumed by checkout. Its application service requires an authenticated actor.
The current security configuration also does not expose the complete geographic
reference flow required by the cart.

Making that endpoint public, inventing a client-side fee table, or creating a
synthetic account would weaken ownership and financial boundaries. Guest
checkout remains a separate open decision; this proposal covers estimation
only.

## Proposed decision

If admitted, add a public, non-persistent shipping-estimate use case with a
dedicated contract. It accepts normalized cart demand plus the minimum enabled
destination codes required by the existing policy. The server reuses current
catalog price lookup, delivery-origin resolution, address validation, and
`ShippingService` rule selection.

The response contains presentation-only estimates:

- merchandise subtotal;
- estimated shipping;
- estimated total;
- calculation instant or expiry guidance.

It does not create a `CartQuote`, Order, reservation, voucher claim, promotion
usage, checkout attempt, or authenticated state. It grants no checkout right
and is never accepted as checkout evidence. After authentication, checkout must
request the existing authoritative account-owned quote and may return different
availability, promotion, shipping, and total results.

Only the required geographic reference reads and the dedicated estimate route
would become public. Inputs remain server-validated, bounded by existing cart
limits, covered by the common error contract, and subject to an explicit abuse
control decision before acceptance.

## Acceptance gates

Before changing code, confirm:

1. whether public estimates include automatic public promotions or only
   merchandise plus shipping;
2. the public request-rate/abuse policy;
3. which province, district, and ward reference reads are public;
4. that integration tests prove no durable quote/order/inventory/voucher effect;
5. that authenticated checkout always recalculates through the existing quote
   authority.

## Rejected shortcuts

- Publishing the existing account-owned cart-quote endpoint.
- Duplicating shipping rules or totals in Vue.
- Persisting anonymous quotes under a shared/synthetic account.
- Treating a guest estimate as locked price, stock, promotion, or checkout
  authority.

## Consequence

The current remediation batch documents the gap but does not implement it.
This ADR must be reviewed and moved to `ACCEPTED` before the public API/security
boundary is changed.
