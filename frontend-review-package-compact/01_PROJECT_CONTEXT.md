# Project Context

## Stack

Vue `3.5.22`, TypeScript `5.9.3`, Vite `7.1.9`, Vue Router `4.6.3`, Vitest `3.2.4`, một SPA, global CSS trong `frontend/src/styles.css`, không có component library.

## Customer flow

`/` catalog/storefront/search → `/products/:id` product và variant → `/cart` cart, quote, promotion/voucher, pickup/delivery, checkout và payment start → `/payment/result` → `/orders` → `/orders/:id` order detail/fulfillment timeline.

Offers dùng `/promotions` và `/promotions/:familyId`; account dùng `/login`, `/register`, `/account/vouchers`. Checkout nằm trong `CartView.vue`, không có route checkout riêng.

## Staff flow

Các màn hình chính trong bộ compact: fulfillment queue/detail, POS, reports, People & Access, và storefront management. Source đầy đủ còn có shipping/promotion administration nhưng view đó không nằm trong bộ compact.

## Frontend-visible permissions

`CATALOG_BROWSE`, `ORDER_PLACE`, `FULFILL_ORDER`, `POS_SELL`, `REPORT_VIEW`, `SHIPPING_RATE_MANAGE`, `PROMOTION_MANAGE`, `STOREFRONT_MANAGE`, `STAFF_MANAGE_SCOPED`, `IDENTITY_MANAGE`.

Frontend dùng permission để điều hướng/hiển thị thuận tiện; server mới là authority.

## Review context

Sản phẩm là shoe-commerce, không phải generic SaaS dashboard. Review cần tách tiêu chí storefront/customer khỏi POS/admin data-dense UI; chú ý product photography, merchandising, price, variant, offer, trust, checkout clarity, responsive behavior 1440/1024/768/390, accessibility và VI/EN localization.

