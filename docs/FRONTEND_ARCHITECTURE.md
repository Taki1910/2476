# Frontend Architecture — Blueprint v1.1.1

> Architecture status: **ACCEPTED FOR THE APPROVED MVP BASELINE**
>
> MVP scope and gates: [MVP_IMPLEMENTATION_BASELINE.md](MVP_IMPLEMENTATION_BASELINE.md)

## Baseline

- `CONFIRMED`: A separate frontend consumes versioned REST contracts.
- `CONFIRMED`: Frontend is never authoritative for price, promotion, voucher,
  stock, payment, refund, authorization, scope, or lifecycle transitions.
- Customer Web, POS, and Admin/Back Office are distinct experience areas in one
  MVP SPA; they may share design and transport primitives without sharing
  business workflows.
- The approved stack is Vue 3, TypeScript, Vite, and Vue Router.
- Browser authentication uses the standard server-side servlet session through
  a Secure HttpOnly cookie with CSRF protection; the frontend does not persist
  long-lived credentials.
- Use a typed API boundary, feature-specific transport DTOs, and local/feature
  state by default. No state/query library or component library is frozen yet.

## Experience boundaries

| Area | Primary actors | Responsibilities | Must not inherit blindly |
|---|---|---|---|
| Customer Web | Registered Customer | Browse, cart quote, Pickup/Delivery choice, payment recovery, fulfillment/cancellation timeline | Guest, Return/Refund, Voucher, AI, staff controls |
| POS | Cashier | Assigned Register/Shift, authoritative quote, cash sale, immediate handover, receipt/recovery | Electronic/split tender, offline/device integration, online flow |
| Admin/Back Office | Operations/Administrator | Identity grants, Branch/Location, catalog/base price, inventory, pickup/delivery queue, cancellation, basic reporting | Implicit broad permissions or separate apps per staff specialty |

Future mobile/native clients may consume the same use cases only through an
explicit API/authentication contract; no mobile framework is selected.

## Proposed structure

```text
App shell and guarded routes
  -> Experience area (Customer | POS | Admin)
     -> Feature pages and feature-local state
        -> Typed/versioned API client boundary
           -> Shared accessible UI primitives and design tokens
```

- Share layout primitives, accessibility, formatting, localization, error
  mapping, and API transport where behavior truly matches.
- Keep feature workflows separate; do not build a universal order screen for
  customer, cashier, and manager.
- Do not mirror persistence entities or implement domain services in the SPA.
- Contract documents live under `docs/API/` when endpoint planning begins. An
  endpoint is not implementation-approved until its use case and contract are
  reviewed.

## Checkout and recovery UX

- Generate one idempotency key per user-confirmed placement/payment action and
  reuse it only for an identical retry.
- Treat preview quotes and displayed availability as expiring information.
- On timeout, do not create a new order or payment immediately: query checkout,
  order, and payment status by safe public identifier/idempotency context.
- Display `processing` for unknown/pending provider outcomes and provide a safe
  refresh/recovery path; never show failure solely because the browser lost the
  response.
- A late or contradictory provider result is displayed as under reconciliation,
  not converted to a client-side success.
- Preserve form/cart context only where it cannot cause duplicate placement.

## POS UX rules

- Show and enforce active branch, sales-floor location, register, and cashier
  shift context before selling.
- Optimize keyboard/scanner input, focus, item/tender clarity, accessibility,
  receipt reprint, and recovery after network/print failure.
- Cash confirmation is complete only after the server commits the Order,
  Inventory, payment, and Shift entry. Voucher joins that transaction only if
  the optional slice is admitted. Printing failure does not repeat the sale.
- Electronic tender uses the same pending/processing/reconciliation UX as online
  payment if admitted later; it is `DEFERRED` from limited MVP POS.
- Offline selling and hardware/device integration are `DEFERRED`; do not imply
  them from local UI state.

## Customer UX rules

- Present responsive catalog/product/variant selection with canonical size
  information and accessible non-AI alternatives.
- Explain pickup/delivery, branch/pickup location, payment, cancellation, return,
  and refund status separately.
- Do not expose exact stock when policy only promises availability.
- Promotion presentation must show eligible benefit evidence and rejection reason
  from the server without promising preview results through placement.
- Product-follow and marketing controls respect consent, preference, and
  unsubscribe status.

## Admin UX rules

- Keep active organization/branch/location scope visible and require deliberate
  scope change.
- Separate catalog, promotion, inventory, finance/refund, identity, audit, and
  reporting capabilities according to permission.
- Confirm irreversible/financial actions and display server-calculated impact;
  hidden buttons are not authorization.
- Display data freshness and distinguish sales branch, stock location, payment
  date, fulfillment date, and refund date in reports.

## API and error contract

Every consumed endpoint must specify:

- method/path, request/response schemas, stable public IDs, and version policy;
- authentication, ownership, permission, and branch/location scope;
- validation and stable machine-readable error codes;
- pagination/filter/sort limits and timezone/currency semantics;
- idempotency and request fingerprint where relevant;
- expected pending/unknown/conflict states and recovery/status query;
- optimistic/pessimistic conflict behavior and compatibility expectations.

The frontend maps stable error/state codes to localized actionable UX. It never
parses message text or enum display labels to decide behavior.

The approved API starts at `/api/v1`, uses UUID public IDs and RFC 9457 Problem
Details. A false supplied `If-Match` is `412`; a domain/lifecycle conflict or
mismatched idempotency fingerprint is `409`. The frontend must not merge these
recovery paths. Money is `{ "amount": 123000, "currency": "VND" }` within the
safe JSON integer bound, and timestamps are RFC 3339 UTC.

## Security and accessibility

- Protected routing is convenience; every API remains authoritative.
- CSRF tokens follow the selected session design; sensitive values are not placed
  in URLs, logs, analytics, or browser storage.
- Render actions using returned capabilities but safely handle denial and stale
  grants/session revocation.
- Establish keyboard, focus, contrast, semantic labeling, error announcement,
  responsive, and reduced-motion baselines before choosing a component library.

## Open decisions

- Query/state strategy, component library, tests, localization, workspace
  layout, and independent deployments beyond the approved one-SPA baseline.
- Browser/device support, POS scanner/printer/cash-drawer integration, and
  degraded/offline mode.
- SEO/server rendering for customer catalog and analytics/privacy policy.
- Branding/design tokens and target accessibility conformance level.
- Exact REST versioning and generated-client workflow.

No dependency or infrastructure is installed merely to prepare for these
decisions.

Frontend scaffolding begins with the first frontend-relevant product vertical
slice, not during backend Foundation 0 merely for completeness.

## Implemented customer storefront slice

The first Vue surface uses Vue 3, TypeScript, Vite, and Vue Router in `frontend/`.
It consumes authenticated `/api/v1/storefront/products`, product-detail, and
price-quote contracts with same-origin session cookies and CSRF on quote
creation. The storefront also exposes a public `/api/v1/storefront/hero`
read model with merchandising flags and 7/30-day sales signals so a future
carousel can choose Top Seller, Trending, New Arrival, and Featured Collection
from data. It renders server-provided availability text and exact VND quote
amounts; it does not calculate or cache an authoritative price beyond the
quote's returned expiry.

Phase 15B product detail adds variants to a browser-persisted multi-line cart.
The cart requests one server CartQuote, displays each confirmed subtotal and
the full total, then submits only `quoteId`, normalized variant/quantity demands
and an opaque `Idempotency-Key` to `/orders/cart-checkout` (ADR-0026).
The command identity is persisted per account before the HTTP request; uncertain
retries, navigation and refresh retain the same payload/key. Server-confirmed
rejections allow editing/requoting. Display metadata and added-price estimates
are never submitted as authority. Controls are disabled while in flight and
server codes drive line-specific stock/price/session recovery copy.
Confirmation repeats every immutable item and explicitly states `PENDING_PAYMENT`;
payment requires a separate action and remains resumable from My Orders/detail.
Before checkout, quote timing uses the CartQuote
`expiresAt`. After checkout, hold messaging uses only the Order response's
server-authoritative `reservationExpiresAt`; the browser never derives a hold
deadline from the quote or its own clock.

Phase 16 extends the same durable checkout command with a required fulfillment
choice. Pickup submits a server-advertised common Location ID; Delivery submits
validated receiver/address fields. Customer order detail renders the persisted
snapshot and actual timestamps. The location-scoped employee queue exposes
Accept, Ready, Handover or Dispatch, then Delivered; confirmation dialogs guard
physical terminal actions and UI visibility never replaces `FULFILL_ORDER`.

Phase 17 adds `FitAssistant.vue` to Product Detail as a mobile-first advisory
flow: prepare, photo, check, analyze and recommendation. It uses the standard
PNG/JPEG file input with camera capture support, keeps the preview in browser
memory, and sends one multipart request with the existing CSRF token. Success
selects only the matching size/color variant; it never adds to cart or silently
changes color. Retake, unsupported-profile, unavailable-stock and retry states
remain explicit in English and Vietnamese, with live status/error announcements
and keyboard-accessible controls.
