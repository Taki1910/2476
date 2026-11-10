# Current baseline

The authoritative project status is [FINAL_FREEZE.md](FINAL_FREEZE.md).

- Verdict: `FINAL PROJECT READINESS: PASSED FOR CORE SYSTEM — AI PROVIDER LIVE VERIFICATION PENDING`.
- P5B status: `P5B IMPLEMENTED — CORE/SQL REGRESSION PASSED, LIVE AI PROVIDER SMOKE PENDING`.
- Branch: `codex/remediation-audit-20260913`.
- HEAD: `2302354b6dc01d58239d98924ec5ce7afca50cdd`.
- Source of truth: this intentionally dirty working tree; HEAD alone does not
  reproduce the accepted application.
- Schema: SQL Server with Flyway V1 through V33; no V34.
- Verification: backend unit 99/99, SQL Server integration 232/232, frontend
  286/286 across 23 files, typecheck passed, production build passed with 98
  modules, and real-stack browser smoke passed at 1440×900 and 390×844.
- OpenAI live gate: not run because `OPENAI_API_KEY` is not configured in the
  local environment. Live provider verification remains a deployment
  prerequisite; it must not be inferred from local contract tests.

P5B is optional. When disabled or unconfigured, deterministic P5A
merchandising, Storefront management, manual editing, Save Draft and Publish
remain functional.

The architecture map is [ADR/README.md](ADR/README.md), the demo runbook is
[DEMO.md](DEMO.md), and the freeze-preparation evidence and remaining boundary
are recorded in [FINAL_FREEZE.md](FINAL_FREEZE.md).

Earlier audits, phase certificates and execution ledgers remain historical
evidence. Their older HEADs, migration inventories, test counts and verdicts do
not describe the current baseline.
