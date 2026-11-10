# UX upgrade — implementation and verification

Date: 2026-10-15. Requirement: REQ-UX-003. Status: implementation present;
visual acceptance remains in progress. This does not replace FINAL_FREEZE.

## Delivered changes

| Slice | Current implementation |
| --- | --- |
| UX-01 | Compare up to three products; two-column mobile pairing, differences filter, per-product loading/retry, keyboard close and focus return. Fixed catalog card overlap that intercepted compare clicks. |
| UX-02 | Gallery uses supplied alt text, honest image-error fallback, lazy thumbnails, selected-size summary and mobile purchase action sharing the existing add handler. |
| UX-03 | Quick-size result distinguishes usual size from reference starting point; explicit variant selection remains required. Late photo responses are discarded after closing/unmounting. |
| UX-04 | Failed cart lines link directly to the product's size selection; existing quote/idempotency recovery remains authoritative. |
| UX-05 | Order journey displays only recorded timestamps in chronological order, including cancellation. No invented ETA or future progress. |
| UX-06 | Native need/color/budget controls submit the existing discovery query. URL synchronization no longer misreads “under 2 million” as a budget of 2 VND. |
| UX-07 | Transfer history has status filtering and scoped counts; failed reads clear stale rows/actions. Existing POS shift/sale, fulfillment task queue and report exceptions are reused, without a duplicate dashboard. |

An additional App retry guard reloads an initially unmatched route after session
startup failure. Its live recovery still needs verification with a running backend.

## Automated evidence

- `npm.cmd --prefix frontend test -- --maxWorkers=1 --testTimeout=30000 --hookTimeout=30000`: **351/351**, 31 files, exit 0. Log: `artifacts/ux-upgrade-20261013/verified-serial-tests-20261015.log`.
- `npm.cmd --prefix frontend run build`: **PASS**, including `vue-tsc -b`. Log: `artifacts/ux-upgrade-20261013/verified-build-20261015.log`.
- Entry bundle: **274.41 kB / 88.48 kB gzip**; routes remain split. Earlier lazy-route baseline was 260.11 kB; comparison checkpoint 273.52 kB.
- `git diff --check`: PASS (line-ending notices only).
- Interaction coverage includes actual filter form events, URL/API propagation, natural-language budget reset, clear filters, transfer status filtering and clearing stale actions after a 403 refresh.
- Earlier full parallel runs timed out while importing route modules; no assertion was removed or test skipped. Route imports now run concurrently inside their test. Serial verification avoids machine contention; explicit timeouts apply to this command only.

## Browser evidence and remaining gates

2026-10-14 observations: comparison desktop/mobile pairing, max-three limit,
differences, Escape/focus return, VI/EN; 390/768/1440 widths. Subsequent 320px
PDP check had client/scroll width 305/305, explicit color/size selection and
mobile add-to-cart. Delivered order journey verified at 390px.
Screenshots: `artifacts/ux-upgrade-20261013/mobile-size-320.png` and
`artifacts/ux-upgrade-20261013/order-journey-390.png`.

2026-10-15: frontend starts at localhost:5173. Backend cannot connect to AN:1433:
Windows service `MSSQL$AN` is stopped. Attempting to start it failed because the
current Windows process cannot open the service. This is an OS permission
failure, not an automatic approval rejection. No DB configuration was changed.

Remaining: live discovery/staff checks, App retry recovery, full cross-page
320/390/768/1440 matrix, 200% zoom, reduced-motion runtime and slow/error network
acceptance. Restore SQL Server service through an authorized administrator,
then restart the demo backend against `shoe_commerce_experience_20261013`.
Do not substitute protected databases or fabricate browser acceptance.
Real-photo lookbook needs supplied assets; existing illustrations remain honest.
No conversion/speed improvement percentage is claimed without a measured baseline.

## Safety and recovery

No backend, public API, dependencies, commercial policies, or Git state mutations
were introduced by these UX slices. Worktree includes pre-existing user changes.
Before-copies are in `artifacts/ux-upgrade-20261013/ux01-before`,
`ux02-03-before`, `ux04-before`, `ux05-before`, `ux06-07-before`, plus
`App-before-session-recovery.vue`. Compare individual hunks before restoring;
never overwrite later/user changes with a wholesale checkout reset.

Root reconciled interrupted worker output and inspected contracts/callers.
Financial and inventory mutations retain their existing handlers and idempotency;
this report certifies frontend checks only, not a new SQL/business release.

### Live follow-up — 2026-10-15
SQL service subsequently observed RUNNING; the earlier service blocker is resolved.
Demo backend restarted against the same isolated database. Actual browser checks:
- Discovery: Running + Black + 2,000,000 VND submitted as `running black under 2000000`; backend interpretation correct. No exact matches kept separate from six suggestions. Clear filters restores catalog; VI labels verified.
- Catalog width checks: 320/390/768/1440 viewport, client/scroll widths respectively 305/305, 375/375, 753/753, 1425/1425 (no horizontal document overflow).
- operations.demo lands on scoped fulfillment queue, with nine actionable tasks and separate recovery records. Existing operations menu exposes authorized transfer/report/catalog pages.
- Transfer page loads stock rows and empty transfer history; at 320px document client/scroll both305. No stock/transfer mutations performed.
- Controlled outage of only the agent-started demo backend: transfer refresh displays connection error, clears stock options/rows and disables Create draft. Reload displays session-check recovery UI. Backend restarted afterward.
This adds live evidence without claiming the full visual matrix complete. Reduced-motion media rules inspected in source; runtime emulation and actual200% browser zoom remain unverified.
- App recovery PASS: after backend readiness, Retry returned to `/login?returnTo=/operations/inventory/transfers` rather than leaving a blank route. Signing in as customer.demo then displayed403 and no operations controls.
- Cart and PDP width checks at320/390/768/1440 all had scrollWidth equal clientWidth. Existing size40/39 cart lines survived sign-out/backend restart; no checkout performed. Product link preserved selected size40.
- Cancelled order SC-AD73AFBB displayed only Created/Cancelled timestamps, correct unpaid-cancellation explanation and no payment/refund action. Same four widths had no document overflow.
- Screenshot: `artifacts/ux-upgrade-20261013/product-live-20261015.png` (1440px). Backend now running on8081 (PID24292), frontend5173. No application source changes in this follow-up, so the previous351-test/build evidence remains applicable.
Remaining acceptance is narrower: real200% zoom, reduced-motion runtime, slow-network scenarios, and full staff/comparison visual inspection across all widths (width metrics alone are not full visual acceptance). Transfer history demo contains zero records; populated status behavior is covered by the interaction test, not claimed as live evidence.

## Plan reconciliation review — 2026-10-15

Verdict: REQUEST CHANGES before declaring REQ-UX-003 accepted. Seven areas have
implementation or deliberate reuse, but there is no evidence for seven accepted
slices. This review inspected code/diffs, the canonical plan and saved test/build
logs; it did not rerun tests or reproduce the two following cases in a browser.

### Required findings

1. **P2 / UX01 — cached comparison cannot detect unpublication on reopening.**
   `ProductComparison.vue:25` returns when a product is already loaded;
   `show():90` only toggles the panel. After product A loads, closing the panel,
   changing its publication/price/availability on the server and opening it again
   leaves the old comparison presented without revalidation or a stale-data label.
   Contradicts plan Review Focus1. Revalidate the at-most-three selected IDs on an
   explicit reopen/refresh, keep per-item errors and add the success-then404 test.
   No polling is needed. This is a new feature gap, not a checkout authority bypass.

2. **P2 / UX05 — rejected refresh retains old order details and actions.**
   `OrderStatusView.vue:67` catches an order read error but never clears `order`;
   template `error && !order` at109 therefore does not replace an already loaded
   order after403. Receiver/address/status and eligible action buttons can remain.
   API request helper dispatches session-ended only for401, so403 does not provide
   that cleanup. This behavior predates the timeline change but is explicitly
   covered by the approved UX05 no-stale-data acceptance. Clear private order data
   on403/404 and add mounted success→403 coverage; distinguish temporary network
   failure from definitive loss of access. Do not claim cross-account backend
   access: server authorization is unchanged and still authoritative.

3. **P3 / tracking — plan/checklist no longer represents current evidence.**
   Plan line13 still says PLANNED/not implemented, and execution checkboxes remain
   blank. App-recovery/SQL-failure paragraphs in this report are superseded by the
   live follow-up, but still read as current in isolation. Reconcile each checkbox
   with dated evidence; mark partial/unverified items explicitly. Do not blanket
   check the seven slices or erase historical failures.

### Per-slice comparison

| Slice | New versus reused | Remaining before acceptance |
| --- | --- | --- |
| UX01 | New selection/panel/differences/mobile pair. Tests and prior browser evidence exist. | Fix cache revalidation; client interaction regression for retry/out-of-order responses (current guard test is pure, panel tests are SSR); complete visual gates. |
| UX02 | Existing gallery enhanced with alt/fallback/lazy thumbnails and shared-handler mobile bar. | Actual200% zoom/occlusion check; representative multi-image visual pass. Real-photo editorial work awaits assets and is explicitly conditional in plan. |
| UX03 | Existing quick/photo flow retained; new size labels and late-response invalidation. | Not a new recommendation algorithm or broad quick-flow redesign. Complete cross-step locale/photo edge-case visual acceptance. |
| UX04 | New line-level link. Existing price-change notice, quote recovery, explicit review and idempotency are reused. | Explicit error-code→copy/action matrix and per-slice acceptance receipt are not shown; verify keyboard/error recovery in browser. Do not count existing safeguards as newly implemented. |
| UX05 | New timestamp journey/next-action section, existing state mapping reused. | Rejected-refresh handling above and mounted fast-ID/authorization regression evidence. |
| UX06 | New native need/color/budget form, URL/API integration. | Browser back/forward and slow/failure cases for the controls, not just generic search; no claim that clearing unparseable natural-language budget is equivalent to preserving its semantics. |
| UX07 | New transfer filtering/counts/read-failure clearing. POS/fulfillment/report task layouts are reused, not redesigned. | Role/location-change while requests run; cashier/manager live acceptance; populated transfer history browser pass (current demo history empty). Reuse is allowed by plan. |

### Verification and scope

- Historical latest full run:351/351 tests,31 files; build includes typecheck.
  Command uses one worker and30s test/hook budgets. Default parallel run was not
  green due to import timeouts. Report both, not just an unqualified test claim.
- New comparison tests cover helper logic and SSR; these do not by themselves
  prove mounted event/focus/race behavior. Prior browser evidence covers some of it.
- Source/diff review shows no new dependency/backend/API/domain mutation in UX
  slices. Financial/stock/authorization decisions remain server-owned.
- Actual200% zoom, reduced-motion runtime, slow network and full visual matrix
  remain open. Equal scroll/client widths prove no document overflow only.
- Planned before/after task timing/backtracking/error-count measurement is absent.
  Do not invent a completion percentage or conversion improvement.
- Execution did not fully follow the per-slice gate sequence: later slices were
  built while earlier visual gates remained open; final aggregate test pass does
  not retrospectively satisfy every slice's RED/browser/review receipt.
- Current report wording should be “implementation present in all seven areas,
  acceptance incomplete”; no release, merge or baseline promotion is approved.

Next order: fix/test UX05 rejection recovery and UX01 revalidation; reconcile
checkbox/evidence matrix; finish per-role/visual/network checks; then assess
acceptance. This review changed documentation only.

## UX05 correction — 2026-10-15 15:33 +07

Skill activation **PASS in this session**: the catalog exposes the existing
`C:/Users/asus/.codex/skills/reuse-first-research/SKILL.md`; its SHA-256 remains
`B89EE711B33989DFB6BA0F7B09CD324310B45F6DE3B7B58DA9174B66E7003522`, matching the
previous static review. Its local-first comparison gate was used before edits.
The `.agents/skills/reuse-first-research/SKILL.md` target is absent.
**SKILL INSTALLATION INCOMPLETE**: installer and both AGENTS snippets remain
missing. No skill reinstall, overwrite, snippet generation or AGENTS edit.

Research Decision **ADAPT**: reuse the existing `load` generation/ID/unmount
guards, `errorCopy`, API session-ended event and App keyed RouterView. Transfer
already clears stale reads after errors. A cache framework or shared new helper
would add no benefit for this local state. The earlier Research Decision and Vue
watcher reference remain applicable; no new dependency or external code copied.

Root cause: failed refresh changed only `error`; `order` stayed populated, so
`error && !order` never selected the unavailable view. The retained order also
kept its cancellation dialog and payment/cancellation actions. One production
line now clears `order` when any read begins, so only a successful current read
can restore private details and actions. All callers (mount, Refresh, ID watch,
post-cancel refresh and retry-void refresh) share this function. Existing command
identity is retained for same-key recovery; server and financial behavior unchanged.

| Read outcome | UI contract |
| --- | --- |
| 401 | API still dispatches session-ended; App clears session/routes to login. Component independently has no stale order. |
| 403 | Authorization error, no old private detail/dialog/actions; explicit read retry remains. |
| 404 | Record-unavailable error, no old detail/actions; explicit read retry remains. |
| Network/5xx | Existing temporary-service copy and Try again; read retry restores data on success. No automatic command/payment retry. |
| ID/account change or late response | Existing generation/ID/unmount and App RouterView key prevent old data becoming current. |

Mounted regression coverage uses the existing Vue client renderer in
`cart-flow.test.ts`, real setup/event handlers, and actual App shell for account
switching. It is not SSR and not browser acceptance. Added seven cases: 401/403/404
refresh; network failure then retry; fast ID switch with reversed response order;
account switch with visible data; account switch with a pending old response.
The RED run after repairing an incomplete fixture produced 4 expected failures
(401/403/404/network retained private address) and 2 passes (existing guards).
After the one-line fix: focused 6 files **176/176 PASS**; the additional visible
account-switch variant then passed in the full run: **31 files, 358/358 PASS**.
Typecheck and production build **PASS**, exit 0; `git diff --check` exit 0
(CRLF notices only). No test failure, warning or unhandled error in full-run log.

Evidence: `artifacts/ux05-20261015/` contains before copies/hashes, final hashes,
`red.log`, `green-focused.log`, `full-tests.log`, `typecheck.log`, `build.log`.
Commands: `npm.cmd --prefix frontend test -- --maxWorkers=1 --testTimeout=30000
--hookTimeout=30000`, `npm.cmd --prefix frontend run typecheck`, and
`npm.cmd --prefix frontend run build`. Test selection is recorded in focused log.
Review against exact before-copy confirms only one production line changed;
existing timeline changes belong to the earlier user-owned tree.

**BROWSER ACCEPTANCE PENDING**: actual browser attempt to
`http://127.0.0.1:5173/` returned `net::ERR_CONNECTION_REFUSED`; no 5173/8081
listener was listed. Windows Service Control Manager access was denied while
checking SQL service status, so SQL running/stopped is UNKNOWN in this session.
This was an OS access failure, not an automatic approval rejection. No service,
datasource or database was changed. No mock browser is substituted for demo
acceptance. Remaining: demo success→403/404, session/account switch, network retry,
VI/EN and keyboard/visual acceptance. No payment or order mutation is required.

Verdict: **UX05 defect fixed and automated regression verified; UX05 NOT YET
ACCEPTED**. REQ-UX-003 stays `in_progress`. UX01 and other outstanding slices
are unchanged. Recommended next target: close UX05 demo-browser acceptance when
the isolated demo is reachable, before advancing to another implementation.

## UX05 browser acceptance attempt — 2026-10-17

Verdict: **UX05 BROWSER ACCEPTANCE BLOCKED**. No UX05 acceptance or UX01
implementation is claimed. REQ-UX-003 remains in_progress.

Routing: existing Shoe Commerce Router + reuse-first-research (unchanged
`B89EE711...`) + browser verification; single agent, CONSERVATIVE, quota UNKNOWN.
Research Decision REUSE the existing demo and verified UX05 fix. Current HEAD
remains b60b69f with user-owned dirty changes; OrderStatusView SHA-256 remains
`20DB19707EE8517E8CD0D327D3EDEC01FFCAB4D4AAB98509B76F3F8A82ED3214`.
Skill installation remains incomplete; no skills or AGENTS modified.

Fresh observations (not inferred from old logs):

| Check | Evidence / conclusion |
| --- | --- |
| Browser localhost:5173 | `net::ERR_CONNECTION_REFUSED` before startup. |
| Browser 127.0.0.1:8081/api/v1/auth/me | `net::ERR_CONNECTION_REFUSED`; backend HTTP unavailable from browser. This does not prove SQL is stopped. |
| Windows SQL service query | `Get-Service MSSQL$AN` reports service not found in the visible environment. SQL host state remains UNKNOWN; no service operation attempted. |
| Listener query | `Get-NetTCPConnection` fails with CIM access unavailable. The earlier phrase “no listener listed” must not be read as proof that no listener exists. |
| Authorized frontend startup | Process-local DEMO_API_TARGET=http://127.0.0.1:8081; `npm.cmd --prefix frontend run dev -- --host 127.0.0.1 --port 5173 --strictPort` reports Vite ready. Browser then reports `net::ERR_CONNECTION_TIMED_OUT` to 127.0.0.1:5173. Successful process startup did not establish browser reachability. |
| Backend restart safety | Source inspection shows DemoDataBootstrap.run invokes seeds and synchronizeReserved. Restarting with `demo` may write data; not performed under the current no-data-change constraint. Historical datasource was shoe_commerce_experience_20261013 on AN:1433, but current datasource cannot be verified without a reachable backend. |

The frontend process created by this attempt was interrupted with Ctrl+C after
the browser timeout; no pre-existing process was stopped. No elevation, Windows
permission change, database command, datasource rewrite, or production code edit.

All requested live UX05 scenarios remain UNVERIFIED in this attempt: successful
order read followed by 403/404, account switch, rapid IDs/out-of-order responses,
network timeout/retry, loading/error/empty, VI/EN/keyboard, private-data/action
clearing, and slow-network flicker/context retention. The 358-test receipt from
2026-10-15 remains historical automated evidence; no tests rerun or substituted
for browser acceptance. No flicker defect has been demonstrated, so no UI change.

Recovery prerequisite: make the existing host demo frontend/backend accessible
to the in-app browser, verify its datasource is the existing isolated demo, and
confirm the startup path does not reseed/migrate/alter data. An authorized host
operator can establish service/runtime status outside the restricted CIM view;
this attempt requests no new privilege. Once reachable, resume the above live
scenarios and save screenshots/receipt before accepting UX05. Only after that
gate: NEXT TARGET REQ-UX-003 / UX01 — Product Comparison Cache Revalidation,
beginning with its REUSE/ADAPT/BUILD gate.

## UX05 safe browser recovery receipt — 2026-10-17

**Verdict: UX05 BROWSER ACCEPTANCE BLOCKED. REQ-UX-003 remains IN_PROGRESS.**
This receipt supersedes environment conclusions in the earlier attempt, without
changing historical certificates or the 358/358 automated-test receipt.

### Routing and research

Existing shoe-commerce-skill-router, reuse-first-research, systematic debugging,
computer-use and verification workflow; conservative single-agent execution.
Skill activation PASS for the incumbent instruction workflow; complete package
status remains SKILL INSTALLATION INCOMPLETE (installer and AGENTS snippets
missing; no installation or overwrite). Research decision REUSE existing Vite,
CUA browser and source evidence; no new tooling/dependency or architecture.

### Fresh environment evidence

| Boundary | Observation | Conclusion |
| --- | --- | --- |
| Vite process | `npm.cmd --prefix frontend run dev -- --host 127.0.0.1 --port 5173 --strictPort`; Vite 7.3.6 ready in 946 ms, loopback URL printed; exec session 10908 | Startup succeeded in execution context. Does not establish host/browser reachability. |
| Shell to frontend | Read-only Invoke-WebRequest for both 127.0.0.1:5173 and localhost:5173, while Vite ready: `An attempt was made to access a socket in a way forbidden by its access permissions` | Socket permission boundary before HTTP; no HTTP application status obtained. |
| Browser to frontend | Fresh IAB navigation to both addresses, tabs 4/5: `net::ERR_CONNECTION_TIMED_OUT` | Browser cannot reach the started frontend. No application UI rendered. |
| Process/port ownership | Get-Process node/java/sqlservr and netstat filtered for 5173/8081/1433 returned no visible rows, even during Vite ready | Restricted visibility cannot establish actual host ownership/listeners. Only newly created foreground session ownership is known. |
| Shell to backend | Read-only 127.0.0.1:8081/api/v1/auth/me: same forbidden-socket error | Backend readiness UNKNOWN, not an application authorization failure. Earlier browser refusal remains historical. |
| SQL discovery | Registry `HKLM:\SOFTWARE\Microsoft\Microsoft SQL Server\Instance Names\SQL`: AN = MSSQL16.AN | Registered instance found; absence must not be inferred. |
| Windows service status | Get-Service: `Cannot open Service Control Manager on computer '.'. This operation might require other privileges.` | Service running state UNKNOWN; permission error is distinct from connection refused. No elevation requested. |

The exact observable failures are shell socket permission denial and browser
loopback timeout despite Vite readiness. A separate namespace/sandbox is a
possible explanation, NOT proven by these tools. No firewall/root-cause claim.
Vite config proxies /api to process-local DEMO_API_TARGET (set to loopback8081
for this attempt), otherwise localhost8080; frontend binding is the CLI loopback
argument. No config file changed. Session 10908 was stopped with Ctrl+C (exit1);
no pre-existing process was stopped.

### Backend safety source inspection (not runtime verification)

- DemoDataBootstrap is a demo-profile transactional CommandLineRunner. It seeds
  accounts, locations, shipping, catalog, presentation, promotion, fitting,
  online/POS scenarios and sales history, then synchronizes reserved inventory.
- Base application.yml enables Flyway, baseline-on-migrate false, migration
  directory contains V1-V36; pending migrations can write on startup.
- Hibernate ddl-auto is validate. No explicit spring.sql.init setting found;
  resource inventory has no schema.sql/data.sql. This is not proof that runtime
  overrides or external initialization cannot exist. Datasource is environment
  supplied; current effective datasource was not verified.
- Source searches for runners, lifecycle/event callbacks, scheduling, timers,
  executors and initialization interfaces found the demo runner and standard
  security/clock beans; no other matching scheduled/background startup hook.
  Negative static search does not certify all dependency/runtime side effects.
- Beyond startup, StorefrontCatalogService list/detail calls
  normalizeExpiredCheckoutHolds. CheckoutHoldExpiryService can expire pending
  payments, release promotions/reservations and cancel orders transactionally.
  Therefore even catalog navigation is not guaranteed database read-only.

No proven read-only backend startup configuration is offered. Disabling Flyway
and demo seeding alone does not prove safe real-stack navigation. Backend was
not launched, no database query/mutation, credentials, service, permissions or
datasource changes were performed.

### Acceptance gates

| Gate | Status |
| --- | --- |
| Frontend HTTP connectivity | BLOCKED from shell; forbidden socket, no HTTP result |
| Browser connectivity | BLOCKED; localhost and 127.0.0.1 time out while Vite ready |
| Mocked browser UX05 acceptance | NOT RUN; frontend inaccessible |
| Real backend readiness | UNKNOWN; runtime unavailable to verify, startup safety not established |
| Real-stack acceptance | PENDING |
| Automated regression | Historical 358/358 only, not rerun or counted as browser evidence |

All requested browser scenarios remain unverified: success then403/404, account
switch, rapid IDs, out-of-order responses, network failure/retry, loading/error/
empty states, VI/EN, keyboard, private details/actions and slow-network flicker.
No new defect demonstrated; no application/test source change. Existing tests
are mounted in-memory tests, not live browser evidence. No mock result is claimed
as backend authorization or SQL evidence.

Recovery: an authorized host operator must establish visibility of the existing
loopback listener and browser route using ordinary existing permissions; obtain
same-host HTTP status and confirm which execution context owns Vite. Do not widen
binding, tunnel, change firewall or elevate as a workaround in this task. Once
reachable, resume controlled frontend API fixtures before real-stack testing.
Real-stack work additionally needs a separately established safe runtime/data
boundary accounting for request-triggered expiry writes. UX01 stays deferred
until this acceptance sequence is resolved or explicitly changed by the user.

Safety: no commit/push/reset/release promotion; dirty user work retained.
OrderStatusView SHA256 remains
`20DB19707EE8517E8CD0D327D3EDEC01FFCAB4D4AAB98509B76F3F8A82ED3214`.

## UX05 Windows-host handoff — 2026-10-17

Status: CASE D — host execution unavailable through a confirmed trusted tool.
UX05 BROWSER ACCEPTANCE BLOCKED; host HTTP/browser UNTESTED, real stack PENDING.

Routing: incumbent router + reuse-first-research (REUSE existing Vite/config),
systematic debugging and sneaker-store-user-flow-qa human-execution handoff.
Single agent, conservative, quota UNKNOWN. Observed dirty baseline retained;
historical freeze unchanged. No application source or security policy changes.

New evidence: frontend/package.json dev script is only `vite`, with no predev or
postdev hook. vite.config.ts loads Vue plugin and proxies /api to DEMO_API_TARGET,
otherwise localhost8080. No backend launch in these scripts. Repo filename search
excluding node_modules/.git/target found no Playwright/Cypress/e2e harness; package
scripts provide Vitest, typecheck and build. No dependency installed.

Execution tool runs PowerShell under the declared workspace-write sandbox with
restricted network. CUA provides an in-app browser; its actual network namespace
and relation to the normal Windows host are UNKNOWN. Available app terminal tool
reads output only; opening a terminal does not establish an outside-sandbox
execution mechanism. Native computer control is disabled. No confirmed trusted
host command channel is available here. Prior forbidden-socket error is observed
inside sandbox; whether Windows-host policy also contributes is UNKNOWN. No new
socket experiment or Vite process was run in this phase.

### Minimal host handoff (normal PowerShell, not administrator)

Window 1, keep running:

```powershell
Set-Location -LiteralPath 'C:\Users\asus\Downloads\SU26-SD21132-SOFF3022-main\DA1'
$env:DEMO_API_TARGET='http://127.0.0.1:0'
npm.cmd --prefix frontend run dev -- --host 127.0.0.1 --port 5173 --strictPort
```

DEMO_API_TARGET is process-local and inherited by Vite; port0 is deliberately
unusable as a backend destination. This prevents the existing proxy default from
reaching a real service at8080. Close this dedicated PowerShell window afterward
to discard its environment. No .env/config edit. This is a connectivity probe,
not API mocking or UX05 acceptance. API proxy failures are expected.

Window 2 on the SAME Windows machine:

```powershell
netstat -ano -p tcp | findstr ":5173"
curl.exe --noproxy "*" --max-time 10 -I http://127.0.0.1:5173/
```

Capture the full Vite output, LISTENING row (expected127.0.0.1:5173) and PID,
and curl status or exact error. If startup/listener/HTTP fails, stop here and
return that evidence; no alternate ports/security changes. A ready message
alone does not pass connectivity. If successful, use a fresh InPrivate/Incognito
window in the host Edge/Chrome and navigate to http://127.0.0.1:5173/ . Capture
screenshot including address and rendered page/error, plus console errors.
Confirm both PowerShell windows and browser ran on the same Windows machine.
Do not log in or perform commerce operations. Stop Vite with Ctrl+C after capture.

Fresh private browser context avoids an existing origin's service worker/cache;
this preliminary page-load check is not the controlled API test harness. Before
UX05 A-F execution, a verified browser runner must intercept all backend requests,
fail closed for unknown requests and disable service workers. No real backend
must be reachable. Do not claim this prerequisite is implemented by port0 alone.

Requested return evidence: Vite output; listener/PID; curl output; browser
screenshot/console errors; same-host confirmation. If host works and Codex browser
still fails, classify Codex execution/browser boundary and reuse an available
host runner. If host fails, classify UX05 HOST CONNECTIVITY BLOCKED at that exact
step. Until evidence arrives the host is UNTESTED, not failed.

Mocked UX05 A-F, screenshots of order states, mobile/desktop, VI/EN, keyboard and
flicker remain NOT RUN. No new independently reproduced application defect.
358/358 remains historical. No database/backend start, Git mutation, privilege,
firewall, tunnel or wildcard exposure. REQ-UX-003 IN_PROGRESS; UX01 deferred.

## UX05 nested fixture harness — 2026-10-19

User approved Phase B with existing Vite config. Added only frontend/test-browser/ux05:
index.html, main.ts, fixtures.ts, transport.ts, transport.test.ts, tsconfig.json,
README.md. No production source/config/script/dependency edits in this phase.
Strict transport permits only scripted same-origin order A/B GETs; no network
fallback. CSP blocks native connections/workers/forms; worker registrations stop
bootstrap; browser storage is shadowed in memory. Real App/OrderStatusView/API
are reused with memory routes and synthetic account state, not real login.

Test-first initial failure: missing transport module (collection RED, not a
behavioral defect). Transport tests2/2 GREEN. Full frontend365/365 across32 files
PASS, harness typecheck PASS, production build PASS. No harness markers in dist.
OrderStatusView hash remains20DB19707EE8517E8CD0D327D3EDEC01FFCAB4D4AAB98509B76F3F8A82ED3214.

Edge live fixture smoke: A rendered with location/name/address and pending-payment
next action; queued403 removed details/actions and showed denial/retry; retry
success restored A; deferred refresh showed loading with old details/actions
removed; switching B then releasing old A retained B. Screenshots in conversation.
These observations supersede the earlier blanket browser-connectivity blocker.
They prove only fixture frontend behavior. Full UX05 acceptance remains pending:
401/404/account/network cases in browser, broader canonical timeline/state mapping,
mobile/keyboard and flicker evaluation are not certified by this smoke run.
No database, backend transaction, Git commit/reset/push or requirement promotion.

## UX05 browser checklist — 2026-10-19 (not accepted)

Routing: incumbent shoe-commerce router + verification-before-completion;
REUSE current harness, no source edits or new tooling. Edge extension browser,
nested /test-browser/ux05/, synthetic transport only, locale vi-VN.

Desktop requested viewport1440x900 was confirmed through DOM innerWidth/Height.
Document clientWidth/scrollWidth both1425 (vertical scrollbar); no horizontal
overflow observed. Full-page screenshots recorded in this conversation for
success, loading,403,keyboard retry recovery,late B and pickup.

| Scenario | Observed result |
| --- | --- |
| DELIVERY pending-payment success | PASS for available fixture: FIXTURE-A, Fixture location A · LOC-A, synthetic receiver/address, payment-needed next action, one created timestamp2026-10-01T10:00:00Z. Multi-event chronological ordering NOT PROVEN: fixture has one timestamp only. |
| Deferred refresh | PASS: role=status with loading text, no old private text, no action buttons, no alert. Held across tool calls before release; release restored A. |
|403 then keyboard retry | PASS: private details/actions absent, error/retry present. Queued success; Tab from main back-link moved focus to Retry (DOM activeElement text verified), Enter restored A. |
| A pending → B → late A | PASS: B reference/address/SKU/location present; late A did not replace B, including follow-up DOM inspection and screenshot. |
| Account switch while B pending | PASS: SYNTHETIC OTHER with403; releasing old response left denial/retry and no old details/actions. |
| PICKUP pending-payment success | PASS for available fixture: pickup label, Fixture location B · LOC-B, no delivery address, payment-needed copy; one created timestamp only. |

Mobile requested iframe390x844 cannot be certified by current harness:
DOM iframeCount=0, and harness CSP frame-src is none. No iframe was added.
Supplemental observation used browser viewport override390x844, explicitly NOT
an iframe test. Pickup screenshot showed document clientWidth=scrollWidth375
(vertical scrollbar); order section width343, no measured horizontal overflow.

Stopped mobile delayed scenario on concrete failure: after Queue delayed success,
production Refresh, then Release delayed responses, the UI remained role=status.
A subsequent full-page screenshot and DOM snapshot still showed loading. No
second release/retry was attempted. Console error/warn observed output was[].
The preceding attempted delivery toggle was not confirmed by the success DOM
(which still showed pickup), so do not label that run DELIVERY success.

Classification: harness/browser-control integration failure, exact root cause
UNKNOWN; production defect NOT demonstrated. Intercepted request log contains
completed fetches only, so it cannot establish whether release reached the
fixture callback or which held request is pending. No source repair attempted.
Screenshots and DOM are preserved in the conversation tool outputs, not asserted
as exported PNG files. Mobile late-A,403 keyboard recovery and requested iframe
layout acceptance remain UNTESTED after this failure.

Controlled desktop observations showed no stale private data in loading/error
and B stayed stable after late A: no observable flicker in controlled delay.
This does not exclude sub-frame flicker or certify mobile flicker.

Verdict: UX05 NOT PASSED. Desktop subset has evidence; pending multi-event
ordering, exact iframe mobile setup and failed mobile release diagnosis.
Viewport override reset. No backend/database/financial action or production source
change. Historical365/365,build/typecheck results were not rerun in this browser
phase and are not fresh verification. Requirement status remains IN_PROGRESS.
Next: diagnose the harness/control release failure and propose minimal harness
correction; no immediate fix or acceptance promotion authorized by this checklist.

## UX05 Release diagnostics — 2026-10-20

Added test-only telemetry in transport.ts/main.ts and a transport.test.ts assertion.
Records INSTANCE/timeOrigin, START/HOLD/SETTLE request IDs, ACTIVATE/ACK handler,
released IDs, pending count at ACK, selected fixture/route/account/generation.
HOLD pending=2 in this initial trace denotes pending ID2, not two controllers;
ACK pending is the count. No production or transport response semantics changed.

Three fresh instrumented trials: mobile390x844 click, desktop1440x900 click,
desktop pending then resize390x844 and Enter. All restored FIXTURE-A;0/3 failures.
Each began with clean reload/A, confirmed loading, fresh DOM then one Release.
Condition wait for status hidden, no fixed sleep. All traces show START#1/SETTLE#1,
START#2/HOLD#2, ACTIVATE Release, RELEASE ids=2 pending=0, SETTLE#2 200,
ACK Release pending=0. No subsequent START. Third trial retained instance
3d938c1d-ade6-4a20-b24c-5d67d9f3b977 and generation0 across resize; browser reported
390x844. The resize event log did not appear, so no claim that the MCP override
emits a native resize event. No evidence of reset/new fetch in this trial.

Mobile390x844 is valid mobile viewport; iframe is not required by latest user
instruction. Root cause of historical failure remains UNKNOWN: no failing minimal
reproduction and no basis to select A/B/C/D/E. Current traces demonstrate correct
handler→controller→promise→UI progression, not an explanation of the old failure.
No behavior fix or acceptance promotion. Viewport override reset.

## UX05 browser recovery receipt — 2026-10-20

This checkpoint supersedes the old “mocked browser NOT RUN / host browser UNTESTED” status, not historical certificates. REQ-UX-003 remains IN_PROGRESS; UX01 remains deferred.

### Fresh runtime evidence

Edge tab 1655049516, http://127.0.0.1:5173/test-browser/ux05/, strict in-memory transport and CSP connect-src none. No real authentication, backend request or database operation. Actual DOM viewports: mobile 390×844 and desktop 1440×900; iframe is not required by the latest instruction.

| Criterion | Runtime evidence | Result |
|---|---|---|
| Delayed read clears old order/actions | Mobile VI main contains only back link and role=status loading while request #2 held; no error/private fields/actions. Release via Enter restores A. Desktop equivalent captured in preceding receipt. | Behavior PASS |
| A→B, late A | Mobile instance 00a83f3f-443b-468a-a2c2-fbe7598c6fb0: A #2 HOLD, B #3 SETTLE200, RELEASE ids=2, A #2 SETTLE200. Main remains FIXTURE-B, address/receiver/LOC-B, no A; observed again in following call. | PASS |
| 403 + keyboard retry | Same mobile instance: #4 403 removes entire order/actions; Tab from main back link focuses “Thử lại”, Enter produces B #5 200. Denied and recovery screenshots emitted in conversation. | PASS |
| 404 | Mobile B #7 404: only unavailable message “Không còn tìm thấy dữ liệu này…” and retry; no order/actions. | PASS |
| Network error + retry | Mobile B #8 NETWORK_ERROR: understandable temporary-service message and retry. Explicit retry #9 200 restores B. This is a synthetic network rejection, not evidence of an actual TCP timeout. | PASS for recovery contract |
| Account switch while pending | Mobile old-owner B #10 held; switch produces new-owner #11 403; release #10 200 leaves alert and no private order/actions. generation0→1, same harness instance. | PASS |
| 401 session boundary | Desktop EN queued401 invokes real App session handling; main becomes harness no-authentication/navigation boundary; no private order/actions. Does not test production login screen. | PASS for clearing |
| Delivery timeline/location/next action | Mobile VI and desktop EN prepared fixture: Created17:00 → Paid17:05 → Accepted17:10 → Ready17:20; location A/B and matching LOC-A/B; ready-for-dispatch copy, no payment action. No invented delivery timestamp. | DOM PASS |
| Pickup timeline/location/next action | Desktop EN ready-for-pickup: four same ordered timestamps, Fixture location A · LOC-A, collect at this location with order reference, no delivery address/payment button; cancel-before-pickup action. | DOM PASS |
| Horizontal overflow | Mobile after log-wrap: clientWidth=scrollWidth=375 (also390 when scrollbar hidden by capture); desktop1440=1440. | DOM PASS |
| Visual clipping/occlusion | Full-page MCP screenshots intermittently time out or stitch duplicated sections/sticky header across content; viewport screenshot also disagreed with observed scrollY. Clean prepared screenshots still show displaced header. Cannot establish from these images whether this is capture-only or product occlusion. | PENDING_VISUAL_EVIDENCE |
| Flicker | No stale private data observed in controlled delayed loading/error; B persists after A settles. No claim about all sub-frame flicker. | Controlled DOM behavior PASS; visual limitation above |

Screenshots for loading,403,recovery,late-B,mobile/desktop prepared were emitted in this conversation. They are not durable PNG artifacts. Do not use corrupted full-page captures as proof of no clipping. Console output [] was observed only; it is not proof of absence of all errors.

### Release root cause boundary

New failure captured on instance b8e3b406-97b0-46c6-8708-e3e8e9fea6ae at390×844: fresh semantic click returned but Release was merely active/focused; diagnostics still ended at HOLD #2 and RESIZE pending=1. There was no ACTIVATE/RELEASE/SETTLE. Visible=true/enabled=true. Fresh Enter on the same button immediately recorded ACTIVATE → RELEASE ids=2 → SETTLE #2 200 → ACK pending=0 and restored A. This supports hypothesis A (activation did not reach handler) for this occurrence; it does not establish the extension's internal cause or retroactively prove the earlier uninstrumented failure. No evidence for B–E in this occurrence. Earlier three clean instrumented reproductions passed0/3 failures; this later click failure is reported separately, not hidden in that denominator. No production fix made.

### Changes and fresh verification

Test-only main.ts adds Queue prepared order (existing Order enum/timestamps), and index.html wraps diagnostics pre text after observed harness overflow. No new dependency/config/production route. Reused existing harness/fixture transport. No change to production source this turn.

- npm.cmd --prefix frontend test -- --maxWorkers=1: 32 files,366/366 PASS,34.18s. cart-flow95,commerce-render26,api32,session3,session-route7,transport3. This is a fresh GREEN run; no new RED claim.
- npm.cmd --prefix frontend run build: exit0; vue-tsc -b and Vite110 modules PASS.
- frontend/node_modules/.bin/vue-tsc.cmd -p test-browser/ux05/tsconfig.json --noEmit: exit0.
- git diff --check: no whitespace errors (existing LF/CRLF warnings).
- dist scan for FIXTURE-A/SYNTHETIC ONLY/Fixture diagnostics/Queue prepared order: no matches.
- OrderStatusView.vue SHA256 remains20DB19707EE8517E8CD0D327D3EDEC01FFCAB4D4AAB98509B76F3F8A82ED3214.
- Working tree remains dirty with pre-existing changes; no commit/push/reset/checkout/release promotion; no backend/service/database changes.

### Decision

UX05 isolated browser BEHAVIOR gates above passed. Full UX05 acceptance is NOT promoted: final visual no-clipping/occlusion evidence remains pending, specifically prepared timeline/location/actions at both target viewports. Need a reliable viewport screenshot correlated with DOM scroll/geometry, not more session-shell tests or request interception. Real-stack backend acceptance remains PENDING and is not implied. No new user approval is required for continuing the already-authorized isolated visual verification; production edits require a demonstrated defect first.

## UX05 acceptance continuation — 2026-10-20 (latest)

User-directed closure of the OLD Release investigation:
**NON_REPRODUCIBLE WITH TEST-ONLY TELEMETRY; ROOT CAUSE UNKNOWN; NO PRODUCT DEFECT ESTABLISHED.**
This is the current disposition of the old incident. Prior observations remain historical evidence; no Release behavior changed and telemetry is retained. No Release scenario was executed in this continuation, so no anomaly recurrence/investigation reopening occurred.

Remaining requested acceptance (mobile A→B late A; mobile403 keyboard retry restoring A; prepared DELIVERY/PICKUP timeline and unobscured layout/screenshots) could not be freshly executed. Existing prepared test-only fixture already supplies four recorded timestamps (created10:00Z,paid10:05Z,accepted10:10Z,ready10:20Z), READY_FOR_DISPATCH/READY_FOR_PICKUP and no future delivered/handed-over timestamp. No fixture/source change needed this continuation.

Exact current tool/environment evidence:
- Existing Edge binding: `Browser is not available: 3`.
- Browser inventory: only IAB id2 and MCP Apps id1; no Edge/Chrome currently exposed.
- IAB harness navigation before startup: `net::ERR_CONNECTION_REFUSED`.
- Existing dev script inspected: vite only. Started this turn's frontend process with process-local DEMO_API_TARGET=http://127.0.0.1:0, host127.0.0.1,port5173,strictPort. Vite7.3.6 reported ready1360ms, session29733.
- IAB navigation after ready: `net::ERR_CONNECTION_TIMED_OUT`.
- Shell curl --noproxy * --max-time10 to the same harness URL: curl(7), failed connection after0ms. Get-NetTCPConnection query returned no listener in this observation context. Does not establish physical host/network namespace relationship or application failure.
- Sent Ctrl+C to the process created in this continuation; no existing user process was targeted.

Routing: existing shoe-commerce-skill-router, reuse existing isolated harness (REUSE), verification-before-completion before any promotion; single agent, quota UNKNOWN. Observed baseline from previous checkpoint366/366 remains historical for this continuation. Fresh final test/build gate is not claimed: prerequisite fresh browser gates did not pass. No repeated old Release reproduction, source/Release/transport edits, dependencies, backend, database, credentials, firewall, services or Git mutations.

Current verdict: UX05 PENDING_TOOL_RETRY (browser environment access); NOT PASSED. REQ-UX-003 remains IN_PROGRESS; UX01 deferred. Next: once a localhost-capable browser channel is available, run the remaining requested gates once, preserve trace before any reload if Release anomaly occurs, then run fresh final verification. No manual PowerShell request or additional approval is needed for already-authorized work.
