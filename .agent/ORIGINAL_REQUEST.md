# Original Request Sources

- Approved P5A design source: `C:\Users\asus\.codex\attachments\a2d087ae-ba79-4e28-94d6-46ee23c5d07d\Văn bản đã dán.txt`
- Resume/acceptance amendment: `C:\Users\asus\.codex\attachments\0d07bc32-6146-420b-abe9-347433ce3b3d\Văn bản đã dán.txt`
- Implementation plan: `docs/superpowers/plans/2026-09-30-p5a-merchandising-recommendations.md`
- Existing RED contract: `backend/src/test/java/com/shoecommerce/merchandising/StorefrontMerchandisingExternalIT.java`

The later amendment accepts the recovered P0-P4 local baseline, keeps SQL
certification pending, and authorizes resuming P5A. It does not change the
approved P5A functional scope or exclusions.

## Final Human UX / Visual Polish Amendment

- P5A acceptance source: `C:\Users\asus\.codex\attachments\a2872611-0394-4280-ade9-41a76d87c542\Văn bản đã dán.txt`
- The user then directly authorized implementation of the approved final polish design.
- Scope: frontend presentation only; preserve Stockroom Proof Sheet; prioritize Shopper,
  Manager, POS; improve natural VI/EN copy; check 390px/tablet/laptop/desktop; use
  native accessibility semantics first.
- Browser work without backend/SQL is mock/presentation QA only.
- Do not add dependencies/abstractions, change backend/business rules/permissions/schema/
  migrations, or implement V34/deferred features.
- Gate order: focused tests, full frontend, typecheck/build, backend unit regression,
  migration inventory, `git diff --check`, staged count.

## Project Memory / Requirement Ledger Amendment

- Source: `C:\Users\asus\.codex\attachments\32ad0d46-d982-4fc1-823d-21fab8b3c237\Văn bản đã dán.txt`
- Date: 2026-10-07.
- Coverage checked against source: yes; durable scope is represented by
  `.agent/MASTER_PLAN.md`, `.agent/REQUIREMENTS.yaml`,
  `.agent/ACCEPTANCE_MATRIX.md`, `.agent/DECISIONS.md` and
  `.agent/ORCHESTRATOR.md`.
- Goal: preserve original, added, changed, superseded, cancelled, completed and
  incomplete requirements with stable IDs and completion evidence across context
  compaction, new sessions and agent handoffs.
- Required rule: model memory, chat summaries and `CURRENT_STATE.md` are caches,
  not complete requirement history.
- Required workflow: classify -> registry/decision -> task -> implementation ->
  verification/evidence -> completion -> acceptance matrix -> state/history.
- Required audits: reconcile master plan, registry, tasks, matrix and current
  state; report explicit counts and explain every excluded requirement.
- Required skill update: extend the global `session-continuity` skill while
  preserving the distinction between active-session context and whole-project
  requirement memory.
- Explicit exclusion: no unrelated application, database, migration, formatting
  or architecture changes.
