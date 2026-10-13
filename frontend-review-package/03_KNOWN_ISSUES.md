# Known Concerns to Verify

These are review prompts, not confirmed bugs. Verify each against the copied source and screenshots before classifying it.

## Customer and commerce flows

- Cart after logout: browser cart persistence and account switching may still be confusing on a shared device even though the code separates guest and account-owned envelopes.
- Logout navigation: verify the destination and preserved context when signing out from a product or variant page.
- Search reset: verify whether clearing the query immediately restores the normal catalog or leaves stale no-results content.
- Promotion and voucher comprehension: check whether automatic offers, saved claims, code entry, rejection reasons, and savings are understandable at the moment of purchase.
- Checkout clarity: check whether quote expiry, pickup/delivery choice, payment processing, retry, and recovery states are easy to understand without duplicate-action risk.

## Storefront and merchandising

- Homepage customization: verify whether the current section model supports enough merchandising flexibility for a real shoe storefront.
- Storefront-management UX: check scale, information hierarchy, editing burden, preview usefulness, bilingual authoring, and publication status.
- Save concurrency/editing state: verify whether content remains editable while a save is in flight and whether conflict/unsaved-edit behavior is clear.
- Banner effectiveness: check whether banners communicate product value, promotion, category, campaign, urgency, and brand personality—not only visual cleanliness.

## Visual quality and inclusion

- Text/background contrast: inspect all surfaces, especially muted labels, status treatments, pink/red panels, tables, and mobile layouts.
- Generic AI appearance: look for excessive identical rounded cards, containerization, border radius, gradients, decorative icons, badges, shadows, uniform spacing, repetitive hero patterns, or generic marketing copy.
- Responsive and bilingual resilience: verify long English/Vietnamese labels, tables, dialogs, navigation, touch targets, and horizontal overflow at all supplied widths.

