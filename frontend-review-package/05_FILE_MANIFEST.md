# File Manifest

The package is intentionally organized for review rather than for backend deployment.

| Path | Category | Why included | Relevant review areas |
|---|---|---|---|
| `frontend/index.html` | App shell | Browser entry document, language metadata, title, viewport, and theme color. | Shell, metadata, responsive baseline. |
| `frontend/package.json`, `frontend/pnpm-lock.yaml`, `frontend/pnpm-workspace.yaml` | Tooling | Exact frontend scripts, versions, and dependency lock. | Stack and reproducibility. |
| `frontend/vite.config.ts`, `frontend/tsconfig.json` | Tooling | Build, proxy, module, and type-check configuration. | API boundary and architecture. |
| `frontend/src/main.ts` | Routing/app entry | Router definitions, route guards, permissions, redirects, and SPA mount. | Navigation, authorization UX, architecture. |
| `frontend/src/App.vue` | Shared shell | Header, navigation, locale switcher, session handling, cart badge, logout, and global states. | IA, shell, accessibility, responsive behavior. |
| `frontend/src/styles.css` | Design system implementation | Global CSS, variables, typography, spacing, controls, tables, states, breakpoints, and responsive rules. | Visual identity, consistency, contrast, responsive UX. |
| `frontend/src/i18n.ts` | Localization | English/Vietnamese messages, locale detection, persistence, and document language. | Translation quality and layout resilience. |
| `frontend/src/api.ts` | Frontend API boundary | Typed DTOs, endpoint client, CSRF/session behavior, error mapping, and request semantics. | State handling, API integration, recovery UX. |
| `frontend/src/session.ts` | Session/auth state | Account loading, sign-in/out, session expiry, cross-tab changes, and permission helpers. | Auth flow and protected UX. |
| `frontend/src/cart.ts`, `frontend/src/checkout.ts` | Commerce state | Browser cart ownership, quantity limits, quote recovery, idempotent checkout state, and persistence. | Cart, checkout, logout, resilience. |
| `frontend/src/merchandising.ts` | Storefront state/types | Homepage section types, draft normalization/validation, ordering, media, and publication guards. | Merchandising and admin UX. |
| `frontend/src/format.ts`, `frontend/src/product-media.ts`, `frontend/src/voucher.ts` | Shared helpers | Currency/date/error/media/voucher presentation helpers used by views. | Consistency and localized copy. |
| `frontend/src/views/*.vue` | Customer and staff views | All meaningful route-level screens currently in the frontend. | End-to-end flows, responsive and accessibility review. |
| `frontend/src/components/*.vue` | Shared commerce components | Homepage rendering, commerce item lists, storefront preview, and fit assistant. | Reuse, content hierarchy, component quality. |
| `frontend/public/products/*` | Static product assets | Product photography used by catalog, product, cart, and storefront previews. | Merchandising, image treatment, visual identity. |
| `frontend/src/*.test.ts` | Frontend tests | Current API, state, flow, rendering, localization, promotion, voucher, fit, and storefront tests. | Intentional behavior and regression context. |
| `api-context/docs/FRONTEND_ARCHITECTURE.md` | Architecture context | Accepted frontend boundaries and customer/POS/admin UX rules. | Architecture affecting UX. |
| `api-context/docs/ACTORS_AND_USE_CASES.md` | Product context | Actor and use-case expectations relevant to screen responsibilities. | Flow and permission interpretation. |
| `api-context/docs/PROMOTION_ENGINE.md` | Commerce contract context | Promotion/voucher concepts needed to interpret the checkout UI. | Offers and pricing presentation. |
| `api-context/docs/SECURITY_MODEL.md` | Security context | Session, permission, ownership, and branch-scope expectations. | Auth/accessibility of actions, authorization UX. |
| `api-context/adr/0026-*.md`, `0027-*.md`, `0029-*.md`, `0030-*.md`, `0031-*.md`, `0032-*.md`, `0033-*.md` | Selected decisions | Only ADRs directly explaining frontend-visible checkout, fulfillment, cart, staff scope, shipping, promotion, and storefront behavior. | State/recovery and intended behavior. |
| `design-system/MASTER.md` | Design intent | Project-level visual direction and design-system reference. | Product identity and consistency. |
| `screenshots/product-ui-audit/*` | Existing screenshots | Broad visual evidence across routes, states, widths, and languages. | UI/UX, responsive, accessibility review. |
| `screenshots/phase15b/*`, `screenshots/phase16/*`, `screenshots/refinement/*` | Existing screenshots | Selected flow and refinement evidence not duplicated from the broad audit set. | Customer, fulfillment, POS, reporting, and bilingual review. |

## Explicit exclusions

Excluded are `backend/`, `QLCHGiay/`, database/migration material, Git history, `node_modules/`, `dist/`, build caches, test output, logs, jars/binaries, prior audit reports, temporary scripts, environment files, and machine/provider secrets.

