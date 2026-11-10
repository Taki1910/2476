# P1 Search, Discovery & Product Experience Design

## Status

Approved on 2026-09-27.

## Job and outcome

Help storefront shoppers describe a shoe in ordinary Vietnamese or English, recover from an unsuccessful query, and select a truthful product color before choosing a purchasable size. The experience must feel like shopping for a shoe rather than operating catalog variants.

Success means common name, color, category/use, and conservative price queries produce explainable ranked results; an unsatisfied query produces clearly separated purchasable suggestions; product detail resolves Product → color → size → variant; and add-to-cart feedback is immediate but unobtrusive.

## Architecture

- Keep deterministic discovery in the backend storefront read layer.
- Normalize case, whitespace, Vietnamese accents, and `đ`; use a deliberately small VI/EN alias map grounded in existing catalog fields.
- Parse only explicit price forms such as `dưới 2 triệu`, `dưới 1.5tr`, and `<= 1500000`; never treat `size 41` or a SKU-shaped value as money.
- Rank exact product names, strong name tokens, structured color/category/use matches, then broader metadata matches. Weak tokens cannot accumulate past a strong name match.
- Preserve SKU lookup as an explicit fallback when the query is SKU-shaped, but do not teach shoppers to use SKU.
- Return exact `results` separately from purchasable `suggestions`; do not pad exact results with unrelated products.
- Derive color and size groups from existing published variants. Stored composite colors such as `White / Black` remain one option.
- Keep requested size distinct from a resolved, purchasable variant. Changing color clears stale variant resolution and never silently selects another color.

## Interface direction

- Search uses the promise “Describe the shoe you’re looking for” / “Mô tả đôi giày bạn đang tìm”, with examples limited to supported concepts.
- Remove the redundant header Search item without adding another global control.
- Product selection shows labeled color controls followed by the size set for that color.
- If a requested size is unavailable after a color change, show same-size available colors as explicit recovery actions.
- Unavailable sizes never enable Add to cart and expose clear unavailable semantics.
- Replace the persistent add-to-cart confirmation panel with a readable compact toast, optional View cart action, and a subtle cart-badge acknowledgment that respects reduced motion.
- Write concise VI and EN copy independently and keep all dynamic feedback screen-reader accessible.

## Scope and boundaries

- No database migration, new persistence entity, LLM, embedding service, or dependency.
- Existing pricing, inventory availability, reservation, checkout, payment, delivery, promotion, and fulfillment behavior remains authoritative and unchanged.
- Flat variants may remain in the API only as an isolated compatibility path for Fit Assistant; the customer-facing selection UI uses the new grouped projection.
- Use real media only. Improve framing, aspect ratio, ordering, and card composition; do not duplicate, recolor, or fabricate product imagery.
- When a product has one truthful image, show one image instead of a fake gallery.

## Material states

- Empty query: normal published catalog browse.
- Meaningful matches: ranked exact results only.
- No meaningful match: truthful no-match message followed by available suggestions and reset/edit controls.
- Loading, network error, empty catalog, and long localized text remain recoverable.
- Product detail: no color, color chosen, size chosen and available, requested size unavailable, stale selection cleared, and add success/error.
- Desktop, laptop, tablet, and approximately 390px mobile remain usable with keyboard, screen reader, and reduced motion.

## Known limitation

The current product media model has only primary and hero URLs and no color association. Demo products usually have one image and some reuse assets. P1 must not hide this limitation or create fake variation. Color-linked media requires real assets and a separately approved ADR-backed change.
