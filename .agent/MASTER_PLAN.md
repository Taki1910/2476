# Master Plan

## Purpose

Build and preserve a smart multi-store shoe commerce platform whose catalog,
inventory, pricing, orders, payments, fulfillment, staff scope and reporting
share one authoritative modular-monolith core. The Vue SPA is a client of REST
contracts; SQL Server and versioned migrations preserve business invariants.

This file records durable scope. Short-term execution belongs in
`CURRENT_STATE.md` and `TASKS.yaml`; requirement-level history belongs in
`REQUIREMENTS.yaml` and `DECISIONS.md`.

## Initial approved scope

- Identity and server-enforced role/permission/branch/location scope.
- Catalog and ProductVariant identity without catalog-owned stock.
- Location-aware inventory, explicit reservation and immutable movements.
- Server-authoritative integer-VND pricing and immutable order evidence.
- Order-centred online commerce with idempotent payment handling.
- Pickup fulfillment, cancellation/void and limited exact-cash POS.
- Immutable audit and location-scoped reconciliation reporting.
- Separate Vue SPA with responsive, accessible customer and staff surfaces.

Sources: `AGENTS.md`, `docs/PROJECT_OVERVIEW.md`,
`docs/MVP_IMPLEMENTATION_BASELINE.md`, `docs/BUSINESS_RULES.md`.

## Added scope

- Atomic multi-item online commerce (`ADR-0026`).
- Delivery fulfillment (`ADR-0027`).
- Advisory A4-calibrated shoe fitting (`ADR-0028`).
- Automatic promotions, voucher families and immutable allocations
  (`ADR-0032`, `ADR-0035`).
- Versioned Storefront HOME merchandising and publication (`ADR-0033`).
- Controlled Product Evidence and Product presentation (`ADR-0037`–`0039`).
- Deterministic P5A merchandising recommendations.
- Optional P5B manager copy/intent assistance; P5A remains authoritative
  (`ADR-0042`).
- Progressive Fit Assistant quick-size path; A4 remains mandatory only for the
  photo-measurement path.
- Project Memory / Requirement Ledger so scope survives compaction and handoff.

## Milestones

| Milestone | Durable state | Primary evidence |
|---|---|---|
| Blueprint and MVP baseline | Completed | `docs/PROJECT_OVERVIEW.md`, `docs/MVP_IMPLEMENTATION_BASELINE.md` |
| Core transaction MVP | Completed | `docs/PHASE_18_FINAL_ACCEPTANCE.md` |
| Promotion/Voucher C3 | Completed | `docs/PHASE_C3_CERTIFICATION.md` |
| Storefront merchandising S1 | Completed | `docs/PHASE_S1_CERTIFICATION.md` |
| Product evidence/readiness | Completed | `docs/PRODUCT_EVIDENCE_EXECUTION_LEDGER.md`, `docs/FINAL_PRODUCT_READINESS_EXECUTION_LEDGER.md` |
| P5A deterministic merchandising | Completed locally | `.agent/HISTORY.md`, P5A plan/tests |
| P5B optional manager AI | Implemented; live provider smoke pending | `docs/FINAL_FREEZE.md` |
| Project Memory ledger | Completed 2026-10-07 | `.agent/REQUIREMENTS.yaml`, `.agent/ACCEPTANCE_MATRIX.md`, skill scenarios |

## Deferred or removed from active scope

- Production-grade return/exchange workflow, carrier integration and live
  delivery tracking.
- Electronic/split/offline POS, hardware integration and expanded POS.
- Partial/split payment capture and additional payment providers.
- Generic AI chat, autonomous ranking/actions/publication, AI-generated media,
  embeddings/vector search and persistent AI history.
- Redis, brokers, search clusters, microservices and Kubernetes without a
  separately confirmed requirement.
- Dark theme; the current product is intentionally light-only.

Deferred items are not active backlog merely because architecture can support
them. Their registry entry or a new explicit requirement must authorize work.

## Scope governance

1. Latest explicit user decision.
2. `REQUIREMENTS.yaml` active entry and supersession links.
3. `DECISIONS.md`.
4. This master plan.
5. Approved ADR/domain documents and acceptance evidence.
6. `CURRENT_STATE.md`, chat summary and model memory only as working context.

Never delete historical requirements. Use `superseded` or `cancelled`, retain
the old ID, and link the replacement when one exists.
