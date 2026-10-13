# Frontend Review Package

This package is a read-only export of the current frontend for independent UI/UX and frontend review.

Source project: `DA1`
Captured from the working tree: 2026-09-12

## What is included

- `frontend/`: Vue application source, tests, product assets, package metadata, Vite/TypeScript configuration, and lockfile.
- `api-context/`: frontend-facing contracts and only the architecture/ADR material needed to understand frontend behavior.
- `design-system/`: the project’s design direction reference. The implemented tokens and styles remain in `frontend/src/styles.css`.
- `screenshots/`: existing safe screenshots selected from the project’s capture artifacts. No screenshots were fabricated.
- `tests/`: a pointer to the Vitest tests retained in `frontend/src` so the copied app remains runnable.

## Reviewer orientation

The frontend is a single Vue 3 SPA with customer, POS, and admin/back-office experience areas. Checkout is implemented inside `CartView.vue`; there is no separate checkout route. The browser talks to the backend through the typed client in `frontend/src/api.ts` using `/api/v1` and same-origin session cookies.

The package intentionally does not contain the backend, database, migrations, build output, dependency directories, logs, prior audit reports, or Git history.

## Optional local run

From `frontend/`:

```text
pnpm install --frozen-lockfile
pnpm dev
```

The Vite proxy expects the backend at `http://localhost:8080`. No dependency installation or application runtime changes were performed while preparing this package.

## Upload recommendation

Upload the entire `frontend-review-package/` directory when possible. If the external AI needs a smaller set, prioritize `00_README.md` through `05_FILE_MANIFEST.md`, `frontend/src/`, `frontend/package.json`, `frontend/pnpm-lock.yaml`, `frontend/public/products/`, `api-context/`, `design-system/`, and `screenshots/`.

