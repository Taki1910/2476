# External Review Scope

Review the current frontend as a commerce product, keeping customer storefront quality distinct from the utilitarian needs of POS and admin surfaces.

## Review areas

1. Product identity and visual direction.
2. Homepage/storefront merchandising and banner effectiveness.
3. Visual hierarchy and information architecture.
4. Navigation and route discoverability.
5. Product discovery, catalog, and search UX.
6. Product detail, size/color selection, and fit-assistant guidance.
7. Cart, quote, voucher, promotion, and checkout UX.
8. Pickup, delivery, payment, order history, detail, and fulfillment tracking.
9. POS, fulfillment, reporting, People & Access, shipping, promotion, and storefront-management usability.
10. Responsive behavior at approximately 1440, 1024, 768, and 390 px.
11. Accessibility: semantics, keyboard/focus, contrast, labels, errors, disabled states, dialogs, touch targets, and announcements.
12. Loading, empty, validation-error, server-error, no-results, unavailable, and processing states.
13. VI/EN localization: longer labels, terminology, hardcoded strings, and layout resilience.
14. Shared-component and design-token consistency.
15. Frontend architecture that affects UX: route guards, local state, browser persistence, typed API boundary, and recovery states.
16. Generic or AI-generated visual patterns, if present.

## Classification rules

For every finding, distinguish among:

- Confirmed defect: directly demonstrated by source or an included capture.
- Likely UX issue: evidence suggests user friction, but further validation is useful.
- Visual/design weakness: quality or hierarchy issue without necessarily being a functional defect.
- Implementation risk: code/state/API boundary may produce future UX or reliability problems.
- Optional improvement: beneficial but not required for the current behavior.
- Feature request: missing capability, not automatically a bug.

Do not infer a missing feature is a defect. Do not claim WCAG compliance, backend correctness, or business-rule violations from frontend appearance alone. Do not propose backend rewrites when a frontend observation can stand on its own.

