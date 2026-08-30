# Architecture Decision Records

## Purpose

ADRs record consequential architectural decisions and their evidence. They do
not convert legacy behavior or an unreviewed proposal into a requirement.

## Lifecycle

```text
PROPOSED -> UNDER REVIEW -> ACCEPTED -> SUPERSEDED
                  \------> REJECTED
```

- **PROPOSED**: drafted and internally checked, but not authoritative.
- **UNDER REVIEW**: being evaluated by an independent reviewer and required
  business owners.
- **ACCEPTED**: approved and authoritative for implementation planning.
- **REJECTED**: evaluated and explicitly not selected; retained for history.
- **SUPERSEDED**: replaced by a newer accepted ADR that links back to it.

Only an `ACCEPTED` ADR is an implementation decision. Blueprint v1.1.1 passed
independent acceptance re-review with conditions, and the revised MVP baseline
subsequently received final `APPROVE` on 2026-08-24. The ADRs in this directory
are therefore accepted architecture for the
[approved MVP implementation baseline](../MVP_IMPLEMENTATION_BASELINE.md), with
the scope qualifiers stated in each record.

Acceptance of architecture support does not admit optional/deferred features
into MVP implementation scope. Business policy choices still require the named
owner and remain `OPEN DECISION` until their slice-entry gates.

## Required metadata

Each ADR states:

- status and date;
- decision class: safe to freeze, requires business confirmation, or deferred;
- context and forces;
- decision and invariants;
- consequences and risks;
- rejected alternatives;
- unresolved decisions and supersession links when relevant.

## Change rules

1. Revise a `PROPOSED` ADR directly while preserving review traceability in the
   Blueprint changelog.
2. After acceptance, do not silently rewrite the decision. Create a superseding
   ADR and mark the old record `SUPERSEDED`.
3. Implementation discoveries that invalidate an accepted assumption require an
   ADR update before broadening scope or adding infrastructure.
4. `LEGACY-ONLY` behavior is evidence, not an accepted decision.

## Blueprint v1.1.1 acceptance result

The acceptance gate required that:

- it is consistent with the domain model, lifecycles, business rules, security,
  and API/frontend boundaries;
- all blocker-level contradictions are resolved;
- remaining open business choices are explicit and do not make the decision
  ambiguous;
- feasibility and failure/recovery behavior are credible;
- an independent reviewer records approval or required revisions.

That progression is recorded in the MVP baseline acceptance history. Future
changes to an accepted decision follow the supersession rules above.
