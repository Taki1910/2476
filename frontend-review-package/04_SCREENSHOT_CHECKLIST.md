# Screenshot Checklist

Existing screenshots are included in `screenshots/`. They are evidence for review, not a replacement for interactive testing.

## Included capture sets

- `screenshots/product-ui-audit/`: broad customer and staff matrix with 1440, 1024, 768, and 390 px variants where available, including store, product, quote/checkout, payment, orders, fulfillment, POS, reports, auth, forbidden, register, and unavailable states.
- `screenshots/phase15b/`: selected customer catalog, search, product, cart, order-list, and order-detail captures in English and Vietnamese across the four target widths.
- `screenshots/phase16/`: pickup/delivery checkout and fulfillment timeline captures, including mobile and confirmation/cancellation states.
- `screenshots/refinement/`: representative storefront, checkout, forbidden, POS, pickup, and reporting captures.

## Optional manual captures for uncovered or changing routes

If interactive access is available, capture these at 1440, 1024, 768, and 390 px in both languages where practical:

- `/promotions` and `/promotions/:familyId`
- `/account/vouchers`
- `/operations/people`
- `/operations/shipping`
- `/operations/promotions`
- `/operations/storefront`

For each route, capture at least the default, loading, empty, validation-error, server-error, disabled-action, and long-content states where the implementation supports them. Also capture mobile navigation, keyboard focus, modal/dialog, no-results search, unavailable product, stale quote, payment processing/unknown, and save-conflict states when reproducible.

No new browser captures were fabricated during packaging. Some existing staff screenshots visibly contain non-secret demo identifiers; no passwords, tokens, or credential values were intentionally exported.

