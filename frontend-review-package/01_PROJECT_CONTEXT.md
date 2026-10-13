# Project Context

## Product

This is a shoe commerce platform with a customer storefront and staff workflows for fulfillment, point-of-sale selling, reporting, access management, shipping rules, promotions, vouchers, and storefront merchandising.

## Detected frontend stack

- Vue `3.5.22`
- TypeScript `5.9.3`
- Vite `7.1.9`
- Vue Router `4.6.3`
- Vitest `3.2.4`
- Archivo variable font via `@fontsource-variable/archivo`
- One SPA under `frontend/`
- Global styling in `frontend/src/styles.css`; no component library is configured

## Customer routes and journey

The actual customer-facing flow is:

`/` (`CatalogView`, including storefront/home highlights and search) → `/products/:id` (`ProductView`, variant selection and optional fit assistant) → `/cart` (`CartView`, quote, voucher/promotion selection, pickup or delivery choice, checkout, and payment start) → `/payment/result` → `/orders` → `/orders/:id` (`OrderStatusView`, fulfillment timeline and cancellation where eligible).

Additional customer/account routes are `/promotions`, `/promotions/:familyId`, `/account/vouchers`, `/login`, `/register`, and `/forbidden`.

Confirmed frontend capabilities include products and variants, server-provided availability, VND price quotes, multi-line cart state, automatic promotions, code/claimable vouchers, pickup, delivery address fields, VNPAY payment initiation/result handling, order history/detail, fulfillment status, and an advisory shoe-fit analysis flow.

## Staff routes and journeys

- `/operations/fulfillments` → `/operations/fulfillments/:id`: location-scoped fulfillment queue and task detail for accept, prepare/ready, handover, dispatch, delivery, and cancellation-related states.
- `/operations/pos`: register and cashier-shift context, SKU lookup, authoritative price/stock check, cash sale, immediate handover, and receipt/recovery display.
- `/operations/reports`: scoped net sales, product sales, inventory, and reconciliation views.
- `/operations/people`: staff access, capability, assignment, and account enable/disable management.
- `/operations/shipping`: shipping-rate revision management.
- `/operations/promotions`: promotion rule management and lifecycle actions.
- `/operations/storefront`: bilingual homepage section editing, ordering, preview, draft save, conflict handling, and publication.

The legacy `QLCHGiay/` application is not part of this package or the new frontend architecture.

## Roles and permissions visible in frontend code

The API `Account` shape carries `roles` and `permissions`, but the frontend does not define role names as its authorization mechanism. Route and navigation visibility use these permission codes:

`CATALOG_BROWSE`, `ORDER_PLACE`, `FULFILL_ORDER`, `POS_SELL`, `REPORT_VIEW`, `SHIPPING_RATE_MANAGE`, `PROMOTION_MANAGE`, `STOREFRONT_MANAGE`, `STAFF_MANAGE_SCOPED`, and `IDENTITY_MANAGE`.

Protected routes are a convenience layer; the server remains authoritative. The package includes the relevant security and scoped-access documents in `api-context/docs/`.

## Localization and responsive target

The UI supports English (`en`) and Vietnamese (`vi-VN`). Locale selection persists in browser storage and updates the document language. Existing capture artifacts target approximately 1440, 1024, 768, and 390 px widths; the reviewer should compare both languages where screenshots exist.

## Review goal

An external AI should independently evaluate the current frontend’s product identity, merchandising, flows, accessibility, responsive behavior, localization, component consistency, state handling, and API integration boundaries. This package is context for review, not a request to redesign or modify the application.

