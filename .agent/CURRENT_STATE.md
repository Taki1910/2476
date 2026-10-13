# Current State

## Project position

- Project Memory / Requirement Ledger upgrade: completed 2026-10-07.
- Latest verified product baseline: `docs/FINAL_FREEZE.md`.
- Verified baseline verdict: core system ready; optional P5B live-provider smoke pending.
- Current branch: `codex/remediation-audit-20260913`.
- Current HEAD observed during REC-001 recovery: `3170a896cd7cbe6f9575a4cbfeb1651cb1d8697f`.
- Working tree is intentionally dirty and user-owned; HEAD alone is not the baseline.

## Active task

`AUTONOMOUS-UX-20261013` is complete under existing `REQ-UX-001`: the
narrow-screen staff navigation is a named, keyboard-operable Operations menu,
and the cart success toast follows the active VI/EN locale. The user's
2026-10-13 request delegated implementation and design choices. No business
rule, API, datasource, provider or publication state changed in this slice.

### Routing and resource checkpoint (2026-10-13 09:21 +07)

- Observed source: branch `codex/remediation-audit-20260913`, HEAD
  `3170a896cd7cbe6f9575a4cbfeb1651cb1d8697f`, intentionally dirty
  user-owned working tree. Certified baseline remains `docs/FINAL_FREEZE.md`;
  V36 candidate remains unpromoted.
- Primary workflow: Hybrid Engineering Router `814B2F75`; support: Shoe
  Commerce Router `F0B91A40`, Session Continuity, Impeccable 4.1.1
  `9D124382`, UI/UX Pro Max `305A7527`, Superpowers Brainstorming
  `A32D2255` (user delegated design choice), TDD `64B03FCE`, Verification,
  Ponytail full `46A57E26`. Loaded: router routing/resources, hybrid
  routing/conflict policy, continuity/resources, Impeccable polish/craft floor,
  UI/UX quick reference, TDD and good-tests reference. No new skill or dependency.
- Resource source: Codex usage tool at this checkpoint reports 89% used in the
  5-hour window and 76% in the weekly window. Tool/verification token cost is
  UNKNOWN; mode CONSERVATIVE, one bounded inline slice, no workers, reserve for
  frontend test/typecheck/build and state handoff.
- Exclusions: Return/inspection, notifications, live AI/VNPAY calls, public
  callback, datasource promotion, protected database mutation, Git state
  changes and new domain semantics. Exact next action: preserve touched-file
  copies, write behavior tests, observe RED, make the smallest UI fix, then run
  relevant verification and update this record.

### Completion checkpoint (2026-10-13 12:51 +07)

- Exact pre-slice copies and rollback instructions:
  `artifacts/autonomous-ux-20261013-before/README.md` (ignored local artifact).
  No prior user-owned work was reset or staged.
- TDD: new Operations-menu permission/discovery test and VI/EN cart toast
  regression failed before the implementation and passed afterward.
- Full frontend: 29 files, 330/330 tests passed; Vue TypeScript check passed;
  production build passed (105 modules, large-chunk warning).
- Browser: desktop menu opened with permission-gated links; at 390x844 the
  one-column menu exposed all links. A 1 px menu overflow found visually was
  corrected; document scrollWidth = clientWidth = 375 afterward. The preview
  server used synthetic session data and did not connect to a database.
- `git diff --check` passed. This is post-freeze UI evidence, not promotion of
  the older certified baseline or of the V36 database candidate.

`POST-AUD03-GATE-CLOSURE` remains complete. Separate user approval was
previously required for V36 datasource promotion and for admission/execution
of the `REQ-PAY-003` sandbox campaign; this UI slice does neither.

### Gate-closure checkpoint (2026-10-13)

- Real Windows-host browser verification reached the explicitly configured V36
  candidate. Desktop 1440x900 and mobile 390x844 rendered successfully with no
  document-level horizontal overflow and no console warning/error. Inventory
  Transfer validation plus `DRAFT -> DISPATCHED -> RECEIVED`, lifecycle evidence,
  VI/EN, storefront discovery, PDP selection, empty cart and Operations navigation
  were visibly verified.
- Recommended visual follow-ups only: the mobile global navigation intentionally
  uses a contained horizontal scroller, and an already-rendered success toast
  retains its original language when the locale changes mid-session. Neither
  hides a critical action or changes business state.
- Candidate audit: 18 storefront products with media, 146 scoped stock rows,
  `469 onHand - 11 reserved = 458 available` and zero invariant violations. It
  contains two completed synthetic Transfers (three lines/four units), including
  the new visual-gate transfer, plus the previously identified cancelled checkout
  smoke artifact. No HOME revision or public offer is published; fallback HOME
  remains usable, so this is missing demo configuration rather than a V36 defect.
- Promotion assessment: `V36 DEMO PROMOTION READY`. No datasource was changed.
  A pristine presentation candidate may instead be restored from the verified
  V35 backup and migrated to V36 under separate authorization.
- REQ-PAY-003 preparation is recorded in
  `docs/REQ-PAY-003-VNPAY-SANDBOX-CERTIFICATION-PLAN.md`. Official VNPAY 2.1.0
  PAY/IPN/Return/QueryDr/SIT material was reconciled; focused payment tests passed
  17/17 and Transfer frontend tests passed 2/2. Merchant credentials, approved
  public HTTPS IPN and live sandbox evidence remain absent. No provider call,
  tunnel, database promotion or feature implementation occurred.
- Requirement state is unchanged: AUD-03 DONE; REQ-INV-002 IN_PROGRESS;
  Physical-return movement NOT IMPLEMENTED and blocked by REQ-FUL-003;
  REQ-FUL-003 NOT ADMITTED; REQ-PAY-003 PLANNED / certification preparation.

## Active routing record (2026-10-13, gate closure)

- Objective / IDs: close remaining POST-AUD03 visual/demo-readiness evidence and
  prepare `REQ-PAY-003` for a separately approved VNPAY sandbox campaign. This is
  a temporary gate/preparation instruction, not a new requirement or admission.
- Observed baseline: branch `codex/remediation-audit-20260913`, HEAD
  `3170a896cd7cbe6f9575a4cbfeb1651cb1d8697f`, intentional user-owned dirty tree,
  continuous migration source V1-V36. Certified baseline remains
  `docs/FINAL_FREEZE.md`; V36 candidate remains unpromoted.
- Primary skill: Hybrid Engineering Router
  `814B2F750453FE68DAD9224C38E957A4D8EFBE59484F929EDE2A764BE71917BF`.
  Support: Shoe Commerce Router `F0B91A40E6F5374EB7C836FA0C263F1921A8B4D9A7B5386EB33DA78D0E4C1266`,
  Computer Use `8211E4B11A19A0DC4F0ACE91096C74C5B36037CA4CB2C8988F0F535BB209E951`,
  Source-Driven Development `719D4E54083C90DED62112FB41DF3DBC4619309118EE0E6AA4D846F92D8204AF`,
  Session Continuity `54DC62170C76AA0D90A7809FCD4B7C432A8D5807BF038EC199A7315EF721716E`,
  Security `287E2A2850F9D9FCEC68A0DBF2CF20EE587928BEADCEF4937AEB120754915CB6`,
  Ponytail full `46A57E26A2632E7FA40EAE6A3CF3011CCDC4D8DB19D8F8617907D6B5DEEF055E`.
- Reason: this phase crosses local browser execution, SQL/demo integrity, payment
  security and source-based certification planning but authorizes no new product
  behavior. Existing tooling and contracts must be reused; no dependency install.
- Exclusions: no promotion, protected-database mutation, new clean candidate,
  external VNPAY transaction, public tunnel, secret capture/logging, refund,
  Return/exchange, notification or other backlog implementation.
- Resource mode was CONSERVATIVE and single-agent; quota/telemetry UNKNOWN. This
  routing record is completed by the gate-closure checkpoint above.

### POST-AUD03 completion checkpoint (2026-10-13)

- Candidate `shoe_commerce_v36_demo_candidate_20261012_232455` was restored from
  the protected V35 demo COPY_ONLY/CHECKSUM backup and migrated to V36. Flyway
  36/36, Hibernate validation, DBCC CHECKDB, constraints and inventory invariants
  passed; pre/post migration V35 business counts/checksums matched.
- Controlled synthetic real-stack evidence includes one two-line completed
  Transfer, exact replay/conflict/scope checks, checkout create/cancel, and
  representative public and operations reads. The candidate is usable but is
  not a promoted or certified baseline.
- Fresh disposable V1->V36 SQL regression passed 68/68; frontend passed 329/329,
  typecheck and production build. Browser access to localhost/LAN was blocked by
  the environment, so desktop/mobile visual verification is explicitly NOT
  VERIFIED. Transfer rendered-component/browser coverage remains Recommended.
- Independent integration review found no Critical or Required issue across
  correctness, transactions/locks, security/API, frontend recovery and migration/
  shared-state behavior.
- Registry and matrix reconcile exactly at 46 IDs: 38 completed, 1 in_progress,
  4 planned, 2 superseded and 1 cancelled. `REQ-INV-002` remains in progress only
  for Physical Return, which is NOT IMPLEMENTED and remains blocked by
  `REQ-FUL-003` Return/inspection evidence.
- Protected `shoe_commerce` V20, rebuilt demo V35 and upgrade-copy V35 remained
  ONLINE and unmodified. Temporary processes, SQL principal and disposable SQL
  database were removed; the verified backup and candidate were preserved.
- Recommended next target is `REQ-PAY-003` live VNPAY sandbox certification,
  subject to separate admission and credentials. `REQ-NOT-001` ranks second;
  `REQ-FUL-003` is postponed pending policy decisions. No status was changed.

## Continuation routing record (2026-10-13)

- Objective / IDs: continue `POST-AUD03-INTEGRATION`; complete the pending
  independent review, full-ledger reconciliation and ranked proposal using only
  existing requirement/task IDs. No new product requirement is introduced.
- Observed baseline: branch `codex/remediation-audit-20260913`, HEAD
  `3170a896cd7cbe6f9575a4cbfeb1651cb1d8697f`; the intentional user-owned
  REC-001/AUD-01/AUD-02/AUD-03 dirty worktree is unchanged in scope.
- Historical evidence at that checkpoint: isolated V35->V36 candidate migration,
  real HTTP/SQL smoke, fresh V1->V36 migration, focused SQL 68/68, frontend
  329/329, typecheck and build PASS. Visual browser access was environment-
  blocked then; the later gate-closure checkpoint above supersedes that gap.
- Primary skill remains Hybrid Engineering Router; supporting skills are Shoe
  Commerce Router, Session Continuity, Code Review and Quality, Source-Driven
  Development, Systematic Debugging and Ponytail full. Skill content is unchanged
  from the recorded hashes; no reload beyond the mandatory router was needed.
- Exclusions / resource mode: no feature implementation, candidate promotion,
  physical Return, live provider effects, Git state mutation or protected-DB
  mutation. CONSERVATIVE single-agent continuation; quota estimate UNKNOWN.
- Next exact action: independently inspect the AUD-03/shared-state diff, then
  reconcile registry/matrix/task status and research/rank at most three existing
  next-milestone IDs.

## Active routing record (2026-10-12, post-AUD-03)

- Objective: prepare an isolated V36 demo candidate from the protected V35 demo,
  run controlled real-stack/integrity verification, review AUD-03 integration,
  reconcile the full backlog and rank up to three existing next-milestone IDs.
- Baseline observed: branch `codex/remediation-audit-20260913`, HEAD
  `3170a896cd7cbe6f9575a4cbfeb1651cb1d8697f`; worktree remains intentionally
  dirty with user-owned REC-001/AUD-01/AUD-02/AUD-03 changes.
- Database boundary: preserve `shoe_commerce`, `shoe_commerce_rebuild_20261012`,
  `shoe_commerce_upgrade_v20_20261012`, certification databases and all existing
  backup artifacts. Only a new collision-free candidate may be created/restored;
  no promotion, in-place migration, clean, repair, baseline or reseed.
- Primary workflow: Hybrid Engineering Router
  `814B2F750453FE68DAD9224C38E957A4D8EFBE59484F929EDE2A764BE71917BF`;
  support: Shoe Commerce Router `F0B91A40E6F5374EB7C836FA0C263F1921A8B4D9A7B5386EB33DA78D0E4C1266`,
  Session Continuity `54DC62170C76AA0D90A7809FCD4B7C432A8D5807BF038EC199A7315EF721716E`,
  Code Review and Quality `8F3CABCA581BBF7CB5F0ADD3F7454E7A4523F9D4353A6A4A217E6FA515309612`,
  Source-Driven Development `719D4E54083C90DED62112FB41DF3DBC4619309118EE0E6AA4D846F92D8204AF`,
  Ponytail full.
- Loaded references: Hybrid routing/conflict policy; Session Continuity workflows
  and resource/worker continuity; complete canonical `.agent` ledger; relevant
  architecture/domain/security/ADR evidence is the next read step.
- Exclusions: no next-feature implementation, physical Return, partial receipt,
  refund/exchange expansion, database promotion, modification of V1-V36, Git
  state change, live payment effects, fake historical evidence or full project
  certification.
- Resource mode: CONSERVATIVE. Account telemetry observed 2026-10-12: 33% used
  in the 5-hour window and 52% used in the weekly window; remaining task/tool
  cost is uncertain, so work stays single-agent and checkpointed with verification
  reserve. No delegation is authorized by this routing record.

## Active routing record (2026-10-12)

- Objective / requirement: implement the approved multi-line Inventory Transfer
  lifecycle `DRAFT -> DISPATCHED -> RECEIVED`, including location authorization,
  idempotency, deterministic locking, provenance and the required SQL/frontend
  acceptance evidence.
- Baseline: re-observed branch `codex/remediation-audit-20260913`, HEAD
  `3170a896cd7cbe6f9575a4cbfeb1651cb1d8697f`, V1-V35 migration source and the
  intentional user-owned dirty REC-001/AUD-01/AUD-02 worktree.
- Database boundary: preserve `shoe_commerce`, `shoe_commerce_rebuild_20261012`,
  `shoe_commerce_upgrade_v20_20261012` and existing backup artifacts; destructive
  migration/concurrency verification may target only a separately named,
  explicitly configured disposable database created for this task.
- Primary skill: Superpowers Executing Plans `F38E8F2D`; support: Shoe Commerce
  Router `F0B91A40`, Hybrid Engineering Router `814B2F75`, TDD `64B03FCE`,
  API/interface `5DAFD0C4`, Security `287E2A28`, Session Continuity `54DC6217`,
  Verification `2BEFE7FC`, Ponytail full.
- Exclusions: no physical return, REQ-FUL-003, refund/exchange expansion,
  partial receipt, post-dispatch reversal, server automatic retry, new retry
  framework, full project certification or next backlog item.
- Resource mode: CONSERVATIVE; quota/telemetry UNKNOWN; one inline worker and no
  new implementation delegation; the user-authorized independent reviewer is
  retained for one bounded re-review. Estimated next slice: focused frontend and
  SQL test edits plus verification/re-review; tool and token cost UNKNOWN, with
  mandatory verification reserve. Outcome: both Required findings were closed;
  the 68-test SQL batch, full frontend gates and independent re-review passed.
  No next backlog item is selected.

### AUD-03 completion checkpoint (2026-10-12)

- Implemented multi-line Transfer `DRAFT -> DISPATCHED -> RECEIVED` with
  `INVENTORY_TRANSFER`, exact source/destination Location scope, actor/key/action/
  payload idempotency, canonical Location-then-Variant locks and immutable
  `TRANSFER_OUT`/`TRANSFER_IN` provenance.
- Focused domain/controller tests: 7/7 PASS. Disposable SQL Server acceptance:
  68/68 PASS across Transfer, AUD-01 contention, REC-001 inventory,
  checkout/reservation and POS suites; no failures, errors or skips.
- V36 fresh V1->V36 and existing V35->V36 migration test: PASS. Disposable DB
  `DBCC CHECKDB`, table-specific constraint checks, migration history and manual
  inventory/transfer/provenance invariants: PASS / zero violations.
- Frontend: 28/28 files and 329/329 tests PASS; focused Transfer 2/2, typecheck
  and production build PASS. `git diff --check` PASS.
- Security/API/five-axis code review and independent remediation re-review: PASS
  after bounding the scoped history read to the newest 100 transfers and adding
  frontend lifecycle evidence plus SQL negative/rollback proof; no remaining
  Critical or Required findings.
- Protected databases remain ONLINE and unchanged at V20/V35/V35. V36 was used
  only on the three explicitly disposable AUD-03 databases; the temporary SQL
  login was removed.
- Full backend `mvn test` was additionally attempted but is not an AUD-03 gate;
  Java 25 Mockito self-attach broke the Surefire pipe before suite execution.
  Required focused and SQL suites all completed independently and passed.

## Prior database recovery routing record (2026-10-12)

- Objective / requirement: DB-RECOVERY-01 controlled fresh V1-V35 database and
  synthetic demo rebuild protecting REQ-INV-002 / AUD-03.
- Baseline: observed branch `codex/remediation-audit-20260913`, HEAD
  `3170a896cd7cbe6f9575a4cbfeb1651cb1d8697f` plus user-owned dirty REC-001,
  AUD-01 and AUD-02 work. Certified baseline remains `docs/FINAL_FREEZE.md`.
- Outcome: created only `shoe_commerce_rebuild_20261012` after absence proof,
  applied V1-V35, ran the authorized synthetic demo bootstrap with external
  integrations disabled, verified it, and separately verified V20->V35 on
  `shoe_commerce_upgrade_v20_20261012` restored from a COPY_ONLY backup.
- Primary skill: Superpowers Executing Plans `F38E8F2D`; orchestration and
  support: Shoe Commerce Router `F0B91A40`, Hybrid Engineering Router
  `814B2F75`, Systematic Debugging `808FC571`, Session Continuity `54DC6217`,
  Verification Before Completion `2BEFE7FC`, Ponytail full `46A57E26`.
- Exclusions: no modification/drop/overwrite of any existing database; no
  migration edit, clean, repair, baseline or manual history edit; no live VNPAY,
  OpenAI or notification call; no full certification or AUD-03 implementation.
- Resource mode: CONSERVATIVE; quota/telemetry UNKNOWN; one inline worker, no
  delegation. Database creation and synthetic seeding are explicitly authorized
  only for the named rebuild target; upgrade verification must use a separate copy.

### DB-RECOVERY-01 completion checkpoint (2026-10-12)

- Original `shoe_commerce` remains ONLINE at Flyway V20 with the same creation
  date and 16 permission rows; no schema or data mutation was applied to it.
- Rebuild target is ONLINE at V35: 35/35 successful migrations, zero failed or
  duplicate versions, and Flyway source validation passed.
- Authorized `DemoDataBootstrap` produced SYNTHETIC DEMO DATA: 18 products, 73
  variants, 146 balances, 5 accounts, 134 orders and 117 stock movements.
- `DBCC CHECKDB` passed. All constraints are enabled/trusted; table-by-table
  checks found zero violations. SQL Server's aggregate `DBCC CHECKCONSTRAINTS`
  reports a collation-combination error for `catalog_product_variant`, but all
  nine checks/FK/unique constraints on that table pass individually and the
  equivalent manual predicate check returns zero violations.
- Isolated V20 copy applied V21-V35 successfully. Fresh and upgraded schemas
  match semantically across tables, columns, checks, FKs, defaults and index
  definitions; only two SQL Server-generated PK names differ.
- Rebuild verdict: `DB REBUILD PASSED — READY FOR PRE-AUD03`.
- Independent rerun verdict: `DB PREFLIGHT PASSED`; database gate is open, but
  AUD-03 was not started.

### PRE-AUD03 checkpoint (2026-10-12)

- SQL Server `AN\AN` 16.0.1200.5 Developer Edition; candidate target
  `shoe_commerce`, ONLINE/FULL, created 2026-09-05. No active application session
  or process-level datasource/profile configuration was observed.
- Repository migrations are continuous V1-V35. Database history is successful
  V1-V20 only; all applied checksums match source, with V21-V35 pending.
- Target has 34/52 expected business tables; 18 are missing. Core business tables
  contain zero rows; only base role/permission reference rows remain.
- `DBCC CHECKDB` and `DBCC CHECKCONSTRAINTS` exited 0; read-only orphan,
  duplicate, constraint-trust and inventory invariant checks returned zero
  violations, but empty data cannot establish business/demo readiness.
- No `.bak`/`.bacpac` or matching `msdb` backup history was found. Migrations,
  demo bootstrap, isolated acceptance databases and a two-row historical
  inventory snapshot are rebuild/reference evidence, not verified recovery of
  the deleted database or historical transactions.

## Completed routing record (2026-10-11)

- Objective / requirement: REQ-PAY-004 / AUD-02 only — authorized manual QueryDr
  reconciliation of existing ambiguous VNPAY payment attempts, with verified
  provider evidence, legal idempotent local transition, audit and operations UI.
- Baseline: observed HEAD `3170a896cd7cbe6f9575a4cbfeb1651cb1d8697f` plus
  user-owned dirty REC-001/AUD-01 changes; the certified baseline remains
  `docs/FINAL_FREEZE.md` and is not promoted by this task.
- Outcome: manual QueryDr provider contract, durable command/API, SQL migration,
  operations UI, documentation and independent financial-code review completed.
- Active skills: Shoe Commerce Skill Router; Hybrid Engineering Router;
  Source-Driven Development; Superpowers writing-plans, executing-plans,
  test-driven-development and verification-before-completion; API/interface,
  security-and-hardening and session-continuity; Ponytail full.
- Exclusions: no scheduled worker, automatic polling/provider retry, new payment
  attempt, new provider, live certification, refund/return workflow, generic
  payment framework, AUD-03 or REC-002.
- Resource mode: CONSERVATIVE; one inline implementer plus independent reviewer,
  quota/telemetry UNKNOWN.
- Tool note: the bundled `sdd-workspace` helper could not start under either
  WindowsApps Bash or Git Bash in this sandbox. The equivalent standard ignored
  workspace and ledger were created with PowerShell; product files were untouched.

### Completion checkpoint (2026-10-11)

- Observed baseline remains the same dirty REC-001/AUD-01 checkout; certified
  baseline is unchanged.
- AUD-02 provider, command/API, V35 migration, operations UI and ADR-0045 are
  implemented under TDD. Manual reconciliation creates no PaymentAttempt, performs
  no automatic provider retry and reuses saved verified evidence after local
  contention.
- Backend unit 123/123, clean SQL acceptance 50/50, frontend 327/327, typecheck,
  build and diff checks pass. Fresh V1 -> V35 and V34 -> V35 upgrade paths pass.
- Security/API review and independent re-review pass with no Critical, Important
  or Minor findings. Scheduled reconciliation and live certification remain
  unimplemented; AUD-03/REC-002 was not started.

## Open gates

- REQ-AI-003: optional release gate; live OpenAI provider smoke has no configured key/evidence and does not block graduation core.
- REQ-PAY-003, REQ-FUL-003 and REQ-NOT-001 are future scope, not active graduation backlog.
- REQ-INV-002 remains `in_progress`: Transfer is implemented; physical-return
  movement is not implemented and requires separate REQ-FUL-003 admission.

## Reconciliation state

`NEEDS_RECONCILIATION` / `INCONSISTENCIES FOUND`:

- `docs/CURRENT_BASELINE.md` and `docs/FINAL_FREEZE.md` certify HEAD `2302354`,
  while the observed repository HEAD is `3170a89`; the later source is not bound
  to fresh full acceptance evidence.
- Original approval artifacts for BR-CAT-110 / BR-CASH-105 were not found in the
  continuity sources. REQ-CAT-003 and REQ-POS-002 are backfilled from confirmed
  business rules plus current implementation evidence and remain provenance-review items.

Registry/matrix structure itself is consistent. These gaps are preserved rather
than resolved by assumption.

## REC-001 scoped evidence

- V34 fresh installation and V33 -> V34 upgrade: PASS.
- Inventory SQL integration/concurrency: 8/8 PASS; combined migration run 9/9 PASS.
- Backend domain/controller focused tests: 4/4 PASS.
- Frontend regression: 313/313 PASS; TypeScript and production build PASS.
- Impeccable detector: no findings; `git diff --check`: no whitespace errors.

## AUD-01 scoped evidence

- Backend unit: 106/106 PASS.
- Focused SQL Server acceptance: 89/89 PASS, including real deadlock 1205,
  lock-timeout 1222, rollback, replay/conflict and non-contention behavior.
- V34 fresh installation and V33 -> V34 upgrade: 1/1 PASS; no V35 migration.
- Frontend focused: 116/116 PASS; full regression: 321/321 PASS.
- TypeScript typecheck and production build: PASS; diff checks: PASS.
- Security/API review: no raw database detail, broad false-positive mapping,
  automatic retry, provider retry, authorization change, dependency or schema change.
- Independent review: three P2/P3 findings remediated and re-reviewed; no
  actionable findings remain. Unit and real SQL tests assert no `Retry-After`.

## Next exact action

The post-AUD-03 preflight and V36 candidate gate closure are already complete.
For future work, first resolve the documented admission and external-evidence
gates for REQ-FUL-003/REQ-INV-002, REQ-NOT-001, REQ-PAY-003 and REQ-AI-003;
preserve the older certified product baseline and protected SQL databases.
