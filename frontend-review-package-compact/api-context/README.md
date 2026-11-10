# Frontend API Context

The canonical frontend client and TypeScript contracts are in `../frontend/src/api.ts`. This folder contains only supporting context; it is not a second API implementation.

## Transport boundary

- Requests use same-origin `/api/v1` paths.
- The browser sends the server session cookie with `credentials: include`.
- CSRF metadata is requested through `/auth/csrf` before state-changing calls.
- Critical checkout, payment, POS, and fulfillment actions may send `Idempotency-Key` headers.
- Error responses are represented as `ApiError` with HTTP status, stable code, detail, and optional variant ID.
- The frontend renders server-owned prices, availability, promotion outcomes, payment states, fulfillment states, and permissions. It is not the source of truth for them.

## Endpoint groups used by the frontend

The client currently covers:

- Auth/session: `/auth/me`, `/auth/login`, `/auth/register`, `/auth/logout`, `/auth/csrf`.
- Storefront/catalog: `/storefront/products`, `/storefront/products/:id`, `/storefront/hero`, `/storefront/homepage`, `/storefront/price-quotes`, `/storefront/cart-quotes`, `/storefront/products/:id/fit-analysis`.
- Public offers and account vouchers: `/storefront/promotions`, `/storefront/promotions/:familyId`, `/me/voucher-claims`, and claim operations.
- Customer commerce: `/orders/cart-checkout`, `/orders/checkout`, `/orders`, `/orders/:id`, cancellation, payment-attempt lookup, payment initiation, and void retry.
- Delivery references: `/reference/provinces`, `/reference/provinces/:code/districts`.
- Fulfillment: `/operations/fulfillments`, task detail, fulfillment creation, accept, prepare/ready, handover, dispatch, and delivered actions.
- POS: registers, current shift, SKU/variant lookup, sale creation, and receipt lookup.
- Reporting: report scope, net sales, product sales, inventory, and reconciliation.
- Staff access: staff records, locations, capabilities, capability assignments, location assignments, and account enable/disable.
- Operations administration: shipping-rate revisions, promotion rules, and storefront draft/publication endpoints.

## Contract files

- `../frontend/src/api.ts`: DTOs and transport calls.
- `../frontend/src/merchandising.ts`: storefront configuration types and deterministic draft validation.
- `docs/FRONTEND_ARCHITECTURE.md`: accepted frontend boundaries and UX rules.
- `adr/`: selected decisions explaining browser-visible state and recovery semantics.

No backend implementation or provider credentials are included. If a reviewer needs a backend detail, treat the typed frontend contract and the copied architecture documents as the intended review boundary.

