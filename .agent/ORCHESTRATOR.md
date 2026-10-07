# Orchestrator Protocol

## Ownership

The main/orchestrator agent owns requirement classification, registry changes,
decisions, reconciliation and current project state. A sub-agent executes its
assigned scope, returns findings/evidence and may suggest requirement impact; it
does not edit `REQUIREMENTS.yaml`, `DECISIONS.md`, `MASTER_PLAN.md` or
`CURRENT_STATE.md` unless the handoff explicitly grants that file scope.

If multiple writers are explicitly authorized, each receives non-overlapping
requirement IDs/files. The orchestrator verifies evidence, integrates ledger
changes and reconciles the result.

## Required workflow

```text
user requirement
  -> classify
  -> add/update REQUIREMENTS and DECISIONS when needed
  -> update TASKS/current plan
  -> implement
  -> verify
  -> attach evidence
  -> mark completed
  -> update ACCEPTANCE_MATRIX
  -> refresh CURRENT_STATE and append HISTORY
```

Classify every durable instruction as one of:

- New requirement: allocate a never-reused ID.
- Clarification: keep the ID; update wording/notes and record a decision when material.
- Requirement change: mark old entry `superseded`, create a new ID and link both.
- Cancellation: mark `cancelled` and record why.
- Temporary instruction: keep it in task/session state only.

## Anti-drift rules

1. Never reconstruct full scope from model memory, chat summary,
   `CURRENT_STATE.md` or the active task.
2. Before audit, project review, progress/final report, freeze, acceptance test,
   whole-project replanning/backlog, “what is missing?”, “is the plan complete?”
   or scope documentation, read in this order:
   `MASTER_PLAN.md` -> `REQUIREMENTS.yaml` -> relevant `DECISIONS.md` ->
   `ACCEPTANCE_MATRIX.md` -> `CURRENT_STATE.md`/`TASKS.yaml`; read HISTORY only
   for provenance not already resolved.
3. Code existence is not completion. `completed` requires applicable evidence.
4. A MASTER_PLAN scope item missing from the registry is an inconsistency.
5. A completed requirement missing from ACCEPTANCE_MATRIX is an inconsistency.
6. A matrix row whose ID is absent from the registry is an inconsistency.
7. Never delete an obsolete requirement; use `superseded` or `cancelled`.
8. Cancelled and superseded requirements never enter active backlog.
9. Current explicit user instruction outranks the registry, but the orchestrator
   must persist the resulting requirement/decision change immediately.

## Session resume

Ordinary continuation reads:

1. `ORCHESTRATOR.md`
2. `CURRENT_STATE.md`
3. `MASTER_PLAN.md`
4. `REQUIREMENTS.yaml`
5. relevant `DECISIONS.md`
6. `TASKS.yaml`
7. `ACCEPTANCE_MATRIX.md` when verification matters
8. `HISTORY.md` only when trace-back is needed

Audit/report/final-review work instead starts with MASTER_PLAN, REQUIREMENTS,
DECISIONS and ACCEPTANCE_MATRIX before current state.

## Report completeness

Reports enumerate the registry, group by area, apply the requested scope, read
relevant decisions and check evidence. Before delivery record:

```text
requirements_count
reported_requirements_count
excluded_requirements: ID -> superseded | cancelled | out of report scope
```

No requirement may disappear silently.

## Reconciliation

Compare `MASTER_PLAN` vs `REQUIREMENTS` vs `TASKS` vs `ACCEPTANCE_MATRIX` vs
`CURRENT_STATE`, then check relevant decisions and observable repository/evidence.
Report exactly one verdict:

```text
CONSISTENT
```

or

```text
INCONSISTENCIES FOUND
- exact ID/file: recorded claim vs observed evidence
```

At minimum detect missing/orphan requirements, stale tasks, completed tasks with
incomplete requirements, completed requirements without evidence, unapplied
decisions, duplicates, wording conflicts, matrix orphans and cancelled work in
active backlog. Preserve both claims and provenance until evidence resolves the
mismatch; do not change business semantics just to make files agree.

## Current coordination boundary

- Working tree is intentionally dirty and user-owned.
- Do not stage, commit, switch, restore, stash, reset, clean, merge, rebase or push.
- `docs/FINAL_FREEZE.md` is the latest verified product baseline.
- HEAD `6962e65` is newer observable source, not automatically a certified baseline.
- Live OpenAI provider smoke remains pending and must not block deterministic P5A.
