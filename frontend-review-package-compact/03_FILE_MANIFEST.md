# Compact File Manifest

Tổng cộng: **49 file**.

## Review guidance and context — 6 files

- `00_README.md` — mục đích và thứ tự đọc.
- `01_PROJECT_CONTEXT.md` — stack, flow, permission và review context.
- `02_REVIEW_SCOPE.md` — phạm vi và cách phân loại finding.
- `03_FILE_MANIFEST.md` — manifest này.
- `api-context/README.md` — API/frontend boundary.
- `design-system/MASTER.md` — design intent.

## Frontend configuration — 4 files

- `frontend/index.html` — viewport, metadata, app entry.
- `frontend/package.json` — framework, dependency và scripts.
- `frontend/vite.config.ts` — Vue plugin và `/api` proxy.
- `frontend/tsconfig.json` — TypeScript strictness/module settings.

## App shell and frontend logic — 16 files

- `frontend/src/main.ts` — routes, guards, permissions, redirects.
- `frontend/src/App.vue` — shell, navigation, locale switcher, session/logout states.
- `frontend/src/styles.css` — design tokens, layout, controls, states, breakpoints.
- `frontend/src/api.ts` — typed DTOs, API calls, CSRF/session/error handling.
- `frontend/src/session.ts` — authentication/session/permission state.
- `frontend/src/cart.ts` — cart ownership, limits and persistence.
- `frontend/src/checkout.ts` — quote/checkout recovery and idempotency state.
- `frontend/src/i18n.ts` — VI/EN messages and locale persistence.
- `frontend/src/merchandising.ts` — storefront section model and validation.
- `frontend/src/format.ts` — currency/date/error presentation helpers.
- `frontend/src/product-media.ts` — product image mapping and alt text.
- `frontend/src/voucher.ts` — voucher error/presentation helpers.
- `frontend/src/components/CommerceItems.vue` — shared commerce item rendering.
- `frontend/src/components/FitAssistant.vue` — product fit-assistant flow.
- `frontend/src/components/StorefrontHomepage.vue` — customer homepage rendering.
- `frontend/src/components/StorefrontSectionPreview.vue` — admin draft preview.

## Key views — 14 files

- Customer: `CatalogView.vue`, `ProductView.vue`, `CartView.vue`, `OffersView.vue`, `OrdersView.vue`, `OrderStatusView.vue`, `PaymentResultView.vue`, `LoginView.vue`.
- Staff/admin: `PickupQueueView.vue`, `PickupDetailView.vue`, `PosView.vue`, `ReportsView.vue`, `PeopleAccessView.vue`, `StorefrontManagementView.vue`.

## Representative tests — 4 files

- `frontend/src/cart-flow.test.ts` — cart/checkout flow.
- `frontend/src/commerce-render.test.ts` — commerce rendering.
- `frontend/src/storefront-management.test.ts` — storefront editor behavior.
- `frontend/src/i18n.test.ts` — localization behavior.

## Visual evidence — 5 files

- `screenshots/store-en-1440.png` — storefront desktop.
- `screenshots/store-en-390.png` — storefront mobile.
- `screenshots/product-en-1440.png` — product detail desktop.
- `screenshots/cart-en-390.png` — cart mobile.
- `screenshots/pos-en-1440.png` — POS desktop.

## Intentionally omitted

Full package, backend, legacy app, database, generated output, dependencies, lockfile, product asset directory, secondary admin views, duplicated screenshots, logs, credentials, and unrelated tests remain outside this compact review set.

