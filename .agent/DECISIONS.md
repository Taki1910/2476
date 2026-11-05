# Decisions

## DEC-0001 — P0-P4 recovery accepted; P5A resumed

Date: 2026-10-01
Status: SUPERSEDED BY DEC-0004 for current baseline reporting

- Previous state: P5A paused while P0-P4 recovery was verified.
- Replacement: P0-P4 local recovery gate is accepted; P5A implementation is authorized.
- Source: user amendment at `.codex/attachments/0d07bc32-6146-420b-abe9-347433ce3b3d/Văn bản đã dán.txt`.
- Effect: do not reopen P0-P4 without a concrete regression; execute the preserved P5A plan and RED test.
- SQL certification remains pending and cannot be represented as passed.

Affected requirements: REQ-MERCH-002.

Supersedes / superseded by: superseded for current evidence by DEC-0004; retained
as the state that governed the 2026-10-01 execution.

## DEC-0002 — No Git state changes during P5A

Date: 2026-10-01
Status: ACTIVE

- The user expressly prohibits stage, commit, switch, restore, stash, reset,
  clean, merge, rebase, and push.
- Source files and tests may be edited in the existing dirty checkout.

Affected requirements: all changes in this working tree.

Supersedes / superseded by: none.

## DEC-0003 — P5A accepted; Final Human UX / Visual Polish authorized

Date: 2026-10-01
Status: ACTIVE

- The slice is frontend presentation only: no backend, business rules, permissions,
  schema, migration, dependency, or new abstraction.
- Stockroom Proof Sheet remains authoritative; priority is Shopper, Manager, then POS.
- Browser work without backend/SQL is presentation QA, never real-stack certification.
- Requirements needing V34 or business changes are deferred.
- The approved terminal status is `FINAL HUMAN UX/VISUAL POLISH PASSED — SQL INTEGRATION CERTIFICATION PENDING`.

Affected requirements: REQ-MERCH-002, REQ-UX-001.

Supersedes / superseded by: the old SQL-pending evidence was later superseded
for the current baseline by DEC-0004.

## DEC-0004 — Current baseline follows FINAL_FREEZE, not stale session state

Date: 2026-10-07
Status: ACTIVE

Context: `.agent/CURRENT_STATE.md`, `ORCHESTRATOR.md` and `TASKS.yaml` stopped at
the P5A/final-polish checkpoint, while `docs/CURRENT_BASELINE.md` delegates to
`docs/FINAL_FREEZE.md`, which records later P5B implementation and SQL regression.
The repository HEAD is newer again (`6962e65`), so HEAD alone is not acceptance
evidence.

Decision: use `docs/FINAL_FREEZE.md` as the latest verified product baseline;
record P5B as locally implemented and the live OpenAI smoke as planned/pending.
Treat commit `6962e65` changes as observable current source, not a new certified
baseline, until fresh full verification is recorded.

Reason: this preserves the explicit authority statement in CURRENT_BASELINE and
does not promote code presence or a later commit to verified completion.

Affected requirements: REQ-MERCH-002, REQ-AI-002, REQ-AI-003, REQ-UX-001.

Supersedes / superseded by: supersedes only the obsolete current-state claim
that SQL certification was still wholly pending.

## DEC-0005 — Multi-item checkout supersedes the historical single-item limit

Date: 2026-08-31
Status: ACTIVE

Context: early Vertical Slice 3 limited online checkout to one item and one
reservation. Phase 15B required one atomic cart order.

Decision: preserve the old constraint as REQ-CHECKOUT-001 `superseded`; use
REQ-CHECKOUT-002 and ADR-0026 for current online checkout. Limited POS remains
single-line and quantity one.

Reason: rewriting the old requirement would erase provenance and could wrongly
change POS semantics.

Affected requirements: REQ-CHECKOUT-001, REQ-CHECKOUT-002, REQ-POS-001.

Supersedes / superseded by: ADR-0026 supersedes the earlier online restriction.

## DEC-0006 — Promotion tie-break follows implemented revision ordering

Date: 2026-09-09
Status: ACTIVE

Context: historical BR-PROMO-103 used a conceptual definition-ID tie-break;
implemented C2/C3 defines priority descending then revision public ID ascending.

Decision: REQ-PROMO-003 remains `superseded`; REQ-PROMO-004 is current.

Reason: ADR-0032/0035 and BR-PROMO-110 are the accepted implementation contract.

Affected requirements: REQ-PROMO-003, REQ-PROMO-004.

Supersedes / superseded by: BR-PROMO-110 supersedes BR-PROMO-103 for C2/C3.

## DEC-0007 — A4 is optional for the primary fit flow, mandatory for photo measurement

Date: 2026-10-04
Status: ACTIVE

Context: the original fitting slice required a visible A4 reference to derive
real-world image measurements. The later Progressive Fit Assistant added a
usual-size path that does not measure an image.

Decision: keep A4 calibration and failure rules for REQ-FIT-001. REQ-FIT-002
offers an A4-free usual-size starting point and makes the photo path optional.
Neither path applies a size without shopper action.

Reason: this reduces friction without inventing uncalibrated measurement or
changing catalog/inventory/cart business behavior.

Affected requirements: REQ-FIT-001, REQ-FIT-002.

Supersedes / superseded by: clarification of scope, not deletion of the photo rule.

## DEC-0008 — Project files own requirement memory

Date: 2026-10-07
Status: ACTIVE

Context: session continuity answered what work was active but could lose older
requirements, exclusions, changes and acceptance evidence after compaction.

Decision: `MASTER_PLAN.md`, `REQUIREMENTS.yaml`, `DECISIONS.md` and
`ACCEPTANCE_MATRIX.md` are the durable requirement-memory set. `CURRENT_STATE`
stays short. Requirements are classified as new, clarification, change,
cancellation or temporary instruction before implementation.

Reason: model memory, summaries and current-task files are incomplete caches.

Affected requirements: REQ-GOV-003.

Supersedes / superseded by: none.

## DEC-0009 — Dark theme remains outside current product scope

Date: 2026-09-25
Status: ACTIVE

Context: the product has an approved light palette but no approved dark palette.

Decision: retain explicit light-only rendering and mark REQ-UX-002 `cancelled`.

Reason: speculative pseudo-dark styling would be inconsistent and unaudited.

Affected requirements: REQ-UX-002.

Supersedes / superseded by: none; a later explicit design requirement may create
a new requirement ID.

## DEC-0010 — Separate future scope and optional provider gates from graduation core

Date: 2026-10-07
Status: ACTIVE

Context: four planned requirements remained easy to misread as active graduation
backlog even though the approved baseline and freeze audit classified them as
deferred deployment/future capabilities or an optional provider verification.

Decision: classify REQ-PAY-003, REQ-FUL-003 and REQ-NOT-001 as `future_scope`,
not graduation blockers, and require explicit new admission before implementation.
Classify REQ-AI-003 as an `optional_release_gate`, not a graduation-core blocker;
it activates only with deployment credentials and explicit authorization for an
external provider call. Keep all four implementation statuses `planned`.

Reason: scope disposition and implementation completion are different facts.
Future work must not be marked completed or silently reopened to close core scope.

Affected requirements: REQ-PAY-003, REQ-FUL-003, REQ-NOT-001, REQ-AI-003.

Supersedes / superseded by: none.

## DEC-0011 — VNPAY ambiguity is resolved by operator-owned manual QueryDr first

Date: 2026-10-09
Status: ACTIVE

Context: the current payment model preserves ambiguous VNPAY outcomes for review
but has no admitted workflow that resolves an existing payment attempt from the
provider's authoritative query response.

Decision: an authorized operator may manually query VNPAY QueryDr for an existing
eligible ambiguous attempt, verify the signed response against canonical persisted
payment evidence, and apply one legal idempotent local transition. The provider call
stays outside the local apply transaction. No new payment attempt is created, no
automatic polling/retry loop is added, and webhook/IPN is not treated as the only
recovery path.

Reason: financial ambiguity must be resolvable without guessing provider state or
duplicating a payment operation, while preserving AUD-01 caller-owned retry policy.

Affected requirements: REQ-PAY-001, REQ-PAY-004, REQ-CONC-001.

Supersedes / superseded by: none. Scheduled reconciliation and live-provider
certification remain separate future work.

Implementation record (2026-10-11): ADR-0045 and V35 implement this decision
with `PAYMENT_RECONCILE` plus exact Location scope, durable actor/key/attempt
identity and verified evidence reuse after local contention. Scheduled
reconciliation and live-provider certification remain unimplemented.

## DEC-0012 — Inventory Transfer is admitted; physical Return remains blocked

Date: 2026-10-12
Status: ACTIVE

Context: REQ-INV-002 still lacks explicit stock Transfer and physical-return
movements. Return inventory admission has no accepted receipt/inspection evidence
because REQ-FUL-003 remains future scope.

Decision: implement Inventory Transfer with lifecycle `DRAFT -> DISPATCHED ->
RECEIVED`. Draft changes no stock; dispatch removes only source available stock
and creates durable in-transit provenance; all-or-nothing receipt adds the full
dispatched quantity at destination. Do not implement generic Return/restock,
partial receipt, post-dispatch reversal or adjustment-based compensation.

Reason: Transfer has complete operational semantics and can be made auditable and
concurrency-safe now. Physical Return cannot safely admit stock before approved
Return/inspection evidence exists.

Affected requirements: REQ-INV-002, REQ-FUL-003, REQ-CONC-001.

Supersedes / superseded by: clarifies the admitted REC-002 slice; none.

Implementation record (2026-10-12): ADR-0046 and V36 implement multi-line
Transfer with `INVENTORY_TRANSFER`, both exact Location scopes, deterministic
Location-then-Variant locking, durable in-transit line evidence and immutable
`TRANSFER_OUT`/`TRANSFER_IN` movements. Create, dispatch and receive use
actor/key/action/payload idempotency and ADR-0044 explicit same-key retry.
Physical Return remains unimplemented and blocked by REQ-FUL-003.

## DEC-0013 — Seven-part UX upgrade direction accepted; planning requested

Date: 2026-10-13
Status: ACTIVE

The user accepted the seven proposed UX directions and requested a plan, then
asked to continue that planning. Track the additive program as REQ-UX-003
planned; preserve the completed historical REQ-UX-001 and cancelled dark theme.
Canonical brief, ordered tasks, proposed implementation defaults, dependencies
and acceptance are in docs/superpowers/plans/2026-10-13-shopping-experience-upgrade.md.
Order: comparison, PDP/lookbook, size guidance, cart recovery, order journey,
intent discovery, staff task surfaces. Existing gallery/quick-size are reused.
Agreement to UX direction does not invent new size rules, product claims,
stock thresholds, financial states or external provider authorization.
This turn creates the plan only; no application implementation is claimed.

### DEC-0013 execution admission

User now explicitly authorizes implementing the plan and using economical
subagents. Implementation defaults in the plan are admitted within its stated
API/domain constraints. One GPT-6 Luna implementer at a time is selected from
the available tool models; root integrates/reviews. Previous no-delegation
plan default is superseded by this explicit authorization. No Git mutations.
