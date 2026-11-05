# Progress History

## Checkpoint — 2026-10-13 isolated sample-data experience

Agent: `/root`

- User approved a new local demo database. `shoe_commerce_experience_20261013`
  was absent, created empty and populated by the existing Flyway V1-V36 and
  `DemoDataBootstrap` through the `demo` profile. The preserved databases and
  the process already using port 8080 were not changed.
- Verified 18 products, 73 variants, 146 balances, five synthetic accounts,
  134 orders, zero failed migrations, zero invalid balances and zero duplicate
  SKUs. Customer catalog imagery and `manager.demo` login/fulfillment queue
  were observed in the real browser; a final reload again showed the manager
  session and 18 products.
- Backend runs on `127.0.0.1:8081` and Vite on `127.0.0.1:5173`, with both
  listeners verified loopback-only; one process-local proxy target lets the
  demo coexist with port 8080. An exact pre-edit Vite config and runtime
  details are saved in ignored `artifacts/demo-experience-20261013/`.
- Current-source backend package and frontend production build passed. No
  external payment or AI provider was called and no certified baseline or
  datasource was promoted.

## Checkpoint — 2026-10-13 autonomous frontend UX polish

Agent: `/root`

- Kept the intentionally dirty user-owned checkout and made exact pre-slice
  frontend copies under `artifacts/autonomous-ux-20261013-before/`.
- Replaced hard-to-discover scrolling staff links with a permission-gated,
  semantic Operations disclosure; the cart success toast now retranslates
  while visible when VI/EN changes.
- New and extended behavior tests were red before the implementation. Final
  frontend regression passed 330/330 across 29 files; typecheck and production
  build passed. Desktop and 390x844 browser checks passed after correcting
  a 1 px menu overflow; diff check passed.
- The synthetic loopback preview did not use a database or provider. No
  business-rule, API, migration, protected datasource or certified-baseline
  promotion was performed.

## Checkpoint — 2026-10-12 DB-RECOVERY-01 controlled rebuild

Agent: `/root`

- Preserved original `shoe_commerce` at V20 and created only the authorized
  `shoe_commerce_rebuild_20261012` target after an explicit absence check.
- Applied and validated Flyway V1-V35, then ran `DemoDataBootstrap` under the
  `demo` profile with the datasource explicitly bound to the rebuild database,
  demo providers active and merchandising AI disabled.
- Classified all seeded rows as SYNTHETIC DEMO DATA; no historical production
  transaction, payment confirmation or audit history was invented.
- Created a verified COPY_ONLY backup of the original V20 database and restored
  it to `shoe_commerce_upgrade_v20_20261012`; V21-V35 and Hibernate validation
  passed without repair, baseline or migration edits.
- Fresh and upgraded V35 schemas are semantically equivalent. `DBCC CHECKDB`,
  constraint, catalog, inventory, commerce, payment, fulfillment, IAM and
  idempotency checks found no data violations.
- Final rebuild verdict: `DB REBUILD PASSED — READY FOR PRE-AUD03`.
- Independent recertification verdict: `DB PREFLIGHT PASSED`. AUD-03 was not
  started; Inventory Transfer awaits a later explicit user instruction.

## Checkpoint — 2026-09-29 recovery bootstrap

Agent: Progress Keeper `/root`

Task: reconstruct persistent state after post-freeze work without prior `.agent`
records.

Completed:

- Confirmed branch and HEAD.
- Preserved the intentionally dirty working-tree boundary.
- Accepted the documented V29 freeze and explicit P1 completion ledger.
- Reconciled post-freeze plans with migrations V30-V33, current source/tests,
  file timestamps, Surefire reports, frontend build artifact, and Git status.

Changed:

- Created `.agent/CURRENT_STATE.md`.
- Created `.agent/ORCHESTRATOR.md`.
- Created `.agent/TASKS.yaml`.
- Created `.agent/HISTORY.md`.

Verification:

- Current Surefire: 83 discovered, 76 executed, 7 skipped, 0 failures/errors.
- Current Failsafe: no reports found.
- Current frontend build artifact exists; durable test/build receipt absent.
- Current SQL V30-V33 and post-P1 browser certification are not proven.
- `git diff --check` reported no whitespace errors.

Remaining:

- Reconcile approval/acceptance for barcode/POS work.
- Reconcile P2B and P3 plan completion against the current diff.
- Run missing SQL, current frontend, browser, and review gates.

Decisions:

- Do not treat application code presence as verified completion.
- Do not repeat the accepted V29 freeze or P1 implementation.
- Do not assign replacement implementation before reconciliation.

Handoff:

- First handoff should go to an orchestrator/requirements owner for
  `P2B-BARCODE-POS`; exact first action is recorded in CURRENT_STATE.md.

## Checkpoint — 2026-10-01 P0-P4 acceptance and P5A resume

Agent: Progress Keeper `/root`

- User accepted `P0-P4 LOCAL RECOVERY GATE PASSED — SQL CERTIFICATION PENDING`.
- Fresh evidence: backend 79/79, frontend 227/227, TypeScript/build PASS,
  `git diff --check` PASS, staged files 0, V30-V33 preserved, no V34.
- Previous P5A pause was superseded; preserved plan and RED test are now active.
- P5A execution starts at clone-from-published; SQL certification remains pending.

## Checkpoint — 2026-10-01 P5A local completion

Agent: `/root`

- Implemented the approved P5A deterministic merchandising slice without a migration,
  dependency, persistent recommendation state, or Git state change.
- Added fenced clone-from-published, protected candidates, deterministic bestseller and
  Product-promotion evidence, readiness/placement evidence, manager Apply-to-draft UX,
  safe selectors, and removed internal metrics from public Hero.
- Verified backend 82/82 and frontend 235/235; typecheck/build and diff check passed.
- SQL external suite discovered 231 tests and skipped all because the SQL Server
  datasource is unavailable; real-browser QA was therefore not represented as passed.
- Final local status: `P5A IMPLEMENTED — P0–P5A LOCAL GATE PASSED, SQL CERTIFICATION PENDING`.

## Checkpoint — 2026-10-01 Final Human UX / Visual Polish

Agent: `/root`

- User accepted P5A and authorized a frontend-presentation-only final UX/visual slice.
- Removed shopper-facing SKU/variant language from cart and customer count summaries;
  improved natural VI/EN copy for search, payment, saved offers, merchandising, and POS.
- Preserved Stockroom Proof Sheet, APIs, business rules, permissions, migrations, and dependencies.
- Verified focused frontend 116/116, full frontend 236/236, typecheck/build PASS,
  backend unit regression 82/82, migrations V1-V33 with no V34, and staged files 0.
- Browser presentation shell passed 390/768/1024/1440 overflow/light checks, but
  backend `:8080` was unavailable; no real-stack certification was claimed.
- Final status: `FINAL HUMAN UX/VISUAL POLISH PASSED — SQL INTEGRATION CERTIFICATION PENDING`.

## Checkpoint — 2026-10-07 Project Memory / Requirement Ledger

Agent: `/root`

- Reviewed the existing `.agent` continuity set, current authoritative product
  baseline, architecture/business sources, ADR supersessions and acceptance ledgers.
- Added MASTER_PLAN, stable REQUIREMENTS registry and ACCEPTANCE_MATRIX without
  modifying application code, schema or migrations.
- Backfilled durable behavior/capability requirements; preserved the historical
  single-item checkout and promotion tie-break as superseded entries.
- Recorded progressive fit clarification: A4 remains required for photo
  measurement, while the primary guidance flow also supports usual-size input.
- Reconciled stale P0-P5A task evidence against `docs/FINAL_FREEZE.md`; preserved
  the newer HEAD-versus-freeze evidence gap instead of inventing certification.
- Added anti-drift, report-completeness, resume, sub-agent ownership and
  reconciliation rules to ORCHESTRATOR.
- Extended the global `session-continuity` skill and its behavioral contract with
  Project Memory scenarios PM-A through PM-J.
- No Git state-changing operation or business-code change was performed.

## Checkpoint — 2026-10-08 REC-001 Inventory Receive recovery

Agent: `/root`

- Rehydrated the inventory model, permissions, Location scope, idempotency,
  audit and SQL constraint patterns before implementation.
- Added a distinct location-scoped direct Receive use case: positive quantity
  increases `onHand`, leaves `reserved` unchanged, preserves derived available,
  writes one immutable `RECEIVE` movement and transactional audit event.
- Added actor-scoped idempotency replay/conflict behavior, pessimistic balance
  protection, V34 movement constraints and exact replay snapshots.
- Added the minimal existing-manager-table workflow with stable retry identity;
  no redesign, new dependency, Transfer or Return implementation was added.
- Verified backend focused 4/4, SQL migration plus inventory integration 9/9
  with no skips, full frontend 313/313, TypeScript/build, UI detector and diff check.
- Corrected REQ-INV-002 from false completion to `in_progress`; registered the
  completed REC-001 as REQ-INV-003. REC-002 remains transfer plus
  physical-return movements and was not started.

## Checkpoint — 2026-10-09 AUD-01 Transaction Contention Contract

Agent: `/root`

- Added exact SQL Server error-number classification for 1205/1222 across
  throwable and `SQLException#getNextException()` chains; broad SQLState and
  Spring lock exceptions remain non-retryable.
- Added a safe HTTP 503 ProblemDetail contract and caller-owned explicit retry
  using the exact payload and same idempotency key, with no server/provider retry.
- Verified real SQL Server deadlock and lock timeout rollback, replay, mismatch
  conflict and unrelated database failure behavior.
- Added strict frontend machine-field parsing, safe VI/EN copy and stable retry
  identities for checkout/payment, receipt/adjustment, cancellation/void,
  handover/dispatch/deliver and existing POS behavior.
- Added ADR-0044 and reconciled Architecture, BR-LOCK-105, API README and the
  historical ADR-0026/0032 statements without rewriting history.
- Verified backend 106/106, focused SQL 89/89, migration 1/1, frontend focused
  116/116 and full 321/321, typecheck/build and diff checks. The independent
  review's three P2/P3 findings were remediated and the re-review found no
  actionable findings. No V35, dependency, authorization expansion, raw-detail
  response or AUD-02/AUD-03/REC-002 work.

## Checkpoint — 2026-10-11 AUD-02 VNPAY Manual QueryDr Reconciliation

Agent: `/root`

- Added an authorized, exact-Location-scoped manual QueryDr workflow for existing
  `PENDING`/`REVIEW_REQUIRED` VNPAY attempts without creating another attempt.
- Added official signed request construction and signature-first response checks,
  including exact identity/amount/type matching and mandatory bounded numeric
  response status/provider transaction fields.
- Added durable actor/key/attempt idempotency and saved verified evidence around
  one provider call outside database transactions; same-key local-contention retry
  applies saved evidence without a second QueryDr.
- Routed definitive outcomes through existing payment-result invariants; invalid,
  mismatched, unavailable and unresolved outcomes cannot bypass Order/Reservation
  rules or expose raw provider details.
- Added V35, PAYMENT_RECONCILE authorization, operations API/UI, VI/EN copy and
  ADR-0045 with architecture, lifecycle, business, security and API reconciliation.
- Verified backend unit 123/123, clean SQL acceptance 50/50 with fresh/upgrade
  migration and real contention, frontend 327/327, typecheck/build and diff check.
  Independent re-review found no remaining Critical, Important or Minor issues.
- Scheduled reconciliation worker and live VNPAY certification remain
  unimplemented. AUD-03/REC-002 was not started.

## Checkpoint — 2026-10-12 AUD-03 / REC-002 Inventory Transfer

Agent: `/root`

- Resumed only after DB-RECOVERY-01 and independent PRE-AUD03 returned the
  authorized database gate PASS; rechecked the prescribed branch/HEAD and
  preserved all existing dirty work and protected databases.
- Added multi-line `DRAFT -> DISPATCHED -> RECEIVED` Transfer with dedicated
  permission, both exact Location scopes, durable actor/key/action/payload
  idempotency and deterministic Location-then-Variant balance locking.
- Draft changes no stock. Full dispatch subtracts only source available on-hand
  and records `TRANSFER_OUT`; full receipt adds destination on-hand and records
  `TRANSFER_IN`. Reserved is unchanged and in-transit provenance is durable.
- Added V36, REST commands/list/options, minimal Vue operations workflow,
  explicit same-key client retry and safe VI/EN copy. Added ADR-0046 and
  reconciled lifecycle, domain, architecture, business, security and API docs.
- Verified focused backend 7/7; disposable SQL Transfer/AUD-01/REC-001/
  checkout/POS regression 68/68; fresh V1->V36 and V35->V36 migration; DBCC,
  constraints and zero invariant/provenance violations; frontend 329/329,
  typecheck/build; diff and five-axis review.
- Closed the independent review's two Required findings by rendering dispatched/
  received quantities and lifecycle timestamps with exact VI/EN in-transit copy,
  and adding unauthorized/invalid/disabled source plus multi-line dispatch and
  receive rollback SQL evidence. Independent re-review found no remaining
  Critical or Required findings.
- Original `shoe_commerce` V20, rebuilt demo V35 and upgrade-copy V35 remained
  ONLINE and unchanged. Physical-return movement was not implemented and stays
  blocked by REQ-FUL-003; REQ-INV-002 therefore remains `in_progress`.

## Checkpoint — 2026-10-13 POST-AUD03 Integration and Milestone Proposal

Agent: `/root`

- Created the collision-free V36 demo candidate
  `shoe_commerce_v36_demo_candidate_20261012_232455` from a verified V35
  COPY_ONLY/CHECKSUM backup. V36 migration, Flyway 36/36, Hibernate validation,
  DBCC CHECKDB, constraints and pre/post migration data preservation passed.
- Ran controlled real-stack Transfer, authorization, idempotency, checkout and
  representative public/operations smoke. The candidate now contains clearly
  identified synthetic smoke evidence and was not promoted.
- Re-ran the exact SQL suites on a fresh disposable V1->V36 database: 68/68 PASS.
  Frontend regression passed 329/329 with typecheck/build; `git diff --check`
  passed. At this historical checkpoint local visual browser access was blocked;
  the later gate-closure checkpoint records the independent browser PASS.
- Independent five-axis review found no Critical or Required integration issue.
  Rendered-component/browser coverage for Transfer remains a Recommended test
  improvement, not a correctness failure demonstrated by current evidence.
- Reconciled all 46 registry IDs against the acceptance matrix: 38 completed,
  1 in progress, 4 planned, 2 superseded and 1 cancelled, with no orphan ID.
  Physical-return movement remains NOT IMPLEMENTED and blocked by REQ-FUL-003.
- Researched and ranked existing future scope without admitting it: REQ-PAY-003
  live VNPAY sandbox certification first, REQ-NOT-001 second, and REQ-FUL-003
  postponed pending return/inspection/refund policy decisions.
- Protected V20/V35/V35 databases and user-owned Git changes were preserved.
  Temporary services, SQL principal and disposable test database were removed;
  the backup and V36 candidate remain for a separate promotion decision.

## Checkpoint — 2026-10-13 POST-AUD03 Gate Closure and REQ-PAY-003 Preparation

Agent: `/root`

- Reverified branch `codex/remediation-audit-20260913`, HEAD
  `3170a896cd7cbe6f9575a4cbfeb1651cb1d8697f`, continuous V1-V36 migration source,
  protected database identities and the intentional user-owned dirty worktree.
- Ran real browser verification against the explicit V36 candidate at desktop
  1440x900 and mobile 390x844. Transfer validation and full lifecycle, VI/EN,
  storefront, PDP, cart and Operations navigation rendered with no browser
  console warning/error or document-level horizontal overflow.
- Classified two non-blocking UX observations: contained horizontal mobile nav
  scrolling and a success toast that keeps its original locale after a mid-page
  language switch. No critical action or information was hidden.
- Added one clearly identified synthetic visual-gate Transfer and completed it;
  the candidate now has two received Transfers, three lines and four units.
  Inventory remains 469 on-hand, 11 reserved and 458 available across 146 rows
  with zero invariant violations. Five demo accounts remained accessible.
- Confirmed 18 products with media, no published HOME revision and zero public
  offers. The deterministic fallback storefront renders; absent curated content
  is demo configuration, not an application regression.
- Returned `V36 DEMO PROMOTION READY` without changing any datasource. Recommended
  a separate pristine V36 candidate only if presentation history must exclude
  the preserved synthetic acceptance artifacts.
- Reconciled current official VNPAY 2.1.0 PAY/IPN/Return/QueryDr/SIT material and
  wrote `docs/REQ-PAY-003-VNPAY-SANDBOX-CERTIFICATION-PLAN.md`. Focused payment
  tests passed 17/17 and Inventory Transfer frontend tests passed 2/2. No VNPAY
  call, tunnel, secret disclosure or new payment behavior occurred.
- REQ-PAY-003 remains planned until merchant credentials, an approved public
  HTTPS IPN and redacted live sandbox evidence exist. All AUD-03/REQ-INV-002/
  REQ-FUL-003 status and exclusions remain unchanged.

## 2026-10-13 — autonomous route-loading/build improvement

REQ-UX-001: 24 route pages now load on demand; fixed pre-existing Vite config
TS2591 using loadEnv. Frontend 333/333, typecheck/build and diff check passed.
Entry JS 532.02 -> 260.11 kB, gzip 152.22 -> 84.10 kB. Rollback/evidence in
artifacts/autonomous-completion-20261013. Whole-project/provider completion
is not claimed; certified baseline unchanged.

## 2026-10-13 — Seven-part UX upgrade plan

User accepted direction and requested planning. Added REQ-UX-003 (planned),
DEC-0013 and one canonical brief/implementation plan covering UX-01..07.
No application code, tests, DB or provider changed; no implementation claim.

- 2026-10-15: Reconciled UX01..07 implementation; 351 frontend tests, production build/typecheck and diff check pass. Partial browser evidence retained; stopped MSSQL$AN service and OS service-start permission block remaining live acceptance. See docs/UX_UPGRADE_VERIFICATION_20261015.md. REQ-UX-003 stays in_progress, FINAL_FREEZE unchanged.

## 2026-10-15 — Research skill and feature audit

Later UX05-only implementation: reused current research skill (activation PASS, full installation still incomplete), reproduced 4 stale-data failures with mounted tests, cleared order at read start, and verified 358/358 frontend tests plus typecheck/build. Seven new mounted cases cover denied reads, network retry, ID/response ordering and App account isolation. Demo browser connection refused; acceptance pending, no complete-program or release claim. Evidence: docs/UX_UPGRADE_VERIFICATION_20261015.md and artifacts/ux05-20261015.

Inspected existing Markdown-only `.codex/skills/reuse-first-research`, router overlap, dirty source and all 47 registry/matrix IDs. Requested installer and AGENTS snippets were absent, so no install or AGENTS edit occurred. Wrote `docs/REUSE_RESEARCH_FEATURE_RECONCILIATION_20261015.md` with feature inventory, Research Decisions, evidence limits and UX05 as the single next implementation target. No application, database or Git state changes; no new tests/browser run. Existing UX01/UX05 findings and guest registration merge gap remain unaccepted.

## 2026-10-17 — UX05 host-side recovery handoff

Inspected frontend dev lifecycle/config and tool execution capabilities. No confirmed outside-sandbox host execution channel; host HTTP/browser remain UNTESTED. No repeated socket attempts. Prepared normal host PowerShell startup/listener/curl and private-browser evidence handoff in UX_UPGRADE_VERIFICATION_20261015.md, using process-local dead backend proxy destination to avoid real service access. Mocked tests NOT RUN, real-stack PENDING; no new defect, source/backend/database/security/Git mutations. UX05 browser acceptance blocked, REQ-UX-003 in progress, UX01 deferred pending host evidence.

## 2026-10-20 — UX05 isolated browser checkpoint
Fresh366/366 frontend tests and build/typechecks PASS. Mobile delayed/late-response/account/403 keyboard retry/404/network and desktop EN pickup/delivery timeline/401 clearing observed with strict fixtures. Release failure localized to absent activation; Enter released exact held request. Added only prepared fixture control and diagnostics wrapping. Full acceptance remains pending reliable no-clipping screenshots because MCP captures were inconsistent. No production/backend/DB/Git mutations; UX01 deferred. Detailed receipt in docs/UX_UPGRADE_VERIFICATION_20261015.md.

## 2026-10-20 — Close old Release investigation; browser channel unavailable
Closed old investigation with user-specified NON_REPRODUCIBLE WITH TEST-ONLY TELEMETRY; ROOT CAUSE UNKNOWN; NO PRODUCT DEFECT ESTABLISHED. No Release re-run/edit. Edge no longer exposed; IAB localhost refused then timed out after safe Vite startup. Fresh browser gates pending tool recovery; no PASS promotion or source/database changes. Details in latest UX05 receipt.

## 2026-10-20 — Incremental milestone memory correction

Documentation-only inspection found UX01 cache revalidation, Guest Cart cross-tab/stale-login remediation, SEARCH-01 typo suggestions and SEARCH-02 frontend autocomplete still present in current source with relevant regression cases. The user reports technical PASS for all four and two Guest Cart RED→GREEN regressions; exact recent execution/benchmark receipts were not found locally, so the ledger records that provenance rather than inventing commands or timing. UX01 implementation is no longer an eligible next target. Guest Cart backend race remains INCONCLUSIVE; UX05 is PENDING_TOOL_RETRY; browser/endpoint/performance acceptance remains pending. No production source, tests, API, database, migration, Git state, or release certification changed. No eligible active unimplemented task selected.
