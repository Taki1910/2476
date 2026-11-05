# UX05 nested Vite harness

Development URL: `/test-browser/ux05/` on the existing frontend Vite server.
Uses the repository's existing Vite config; no config, dependency, script or
production route is added. The production entry does not import this directory.

## Isolation

- Installs fixture-only fetch before importing real App/OrderStatusView/API/session.
- Only exact same-origin GET `/api/v1/orders/a` and `/api/v1/orders/b`, without
  query/hash, are allowed and each consumes an explicitly queued response.
- Unknown/exhausted requests and ALL mutations fail closed, with no native fetch
  fallback. No login, payment, refund or cancellation transaction can execute.
- CSP `connect-src 'none'`, `worker-src 'none'`, `form-action 'none'` provides a
  second boundary. Vite HMR is intentionally blocked; reload after harness edits.
- Refuses to mount if this origin has a service worker registration/controller.
  It does not unregister or modify workers. Use a clean browser context if blocked.
- localStorage/sessionStorage are in-memory shadows before application imports;
  no real saved cart/session/preferences are read or written by this harness.
- All account/order values are synthetic. Route changes use memory history.
- Network log displays intercepted requests, not actual HTTP/server evidence.

## Procedure

Queue a response, then click the production **Refresh status** or **Try again**.
Each response is consumed once; exhausted queues intentionally fail.

1. A loaded: queue403/404, Refresh; check private details/dialog/actions disappear.
2. Queue success, Try again; check only the current fixture returns.
3. Queue delayed success, Refresh; observe loading/no private content/actions.
4. While A held, Show B; Release delayed responses; B must remain visible.
5. Queue network failure, Refresh; queue success, Try again.
6. Switch synthetic account: actual App generation key remounts order with403.
7. Queue401: actual API emits session-ended; App routes to the harness boundary
   placeholder. This does not validate the production login page or router guard.
   Reload the harness to reset this case (do not sign in).
8. Toggle pickup/delivery then queue success/Refresh; verify location label/value.
9. Repeat under desktop/mobile browser viewport and VI/EN. Keyboard Tab/Enter on
   real read controls only. Never click payment/cancel/refund controls as a test
   of business behavior; their visibility is the acceptance observation.

Current fixtures cover pending-payment order details. Additional canonical
paid/fulfillment/refund timeline scenarios remain to be supplied and observed;
do not declare complete UX05 acceptance from this harness smoke test.

## Verification (from frontend)

```
npm test -- test-browser/ux05/transport.test.ts --maxWorkers=1
node_modules/.bin/vue-tsc -p test-browser/ux05/tsconfig.json --noEmit
npm run build
```

2026-10-19: test-first initial run failed to collect because transport did not
exist (not a behavioral RED). Implemented transport:2/2 tests PASS. Explicit
assertions cover unknown/exhausted/cross-origin/mutation rejection, denied status,
network failure and releasing A after B. Harness typecheck and production build
PASS; production dist contains no harness markers.

Edge smoke observed with real components and synthetic fixture transport:
A→403 removes details/actions; retry success restores A; held A shows loading;
B remains after late A. Screenshots captured in conversation for403 and loading.
This is frontend-only evidence; no server authorization/SQL claim. Full visual,
mobile/keyboard,401/404,account/network scenario acceptance remains pending.
