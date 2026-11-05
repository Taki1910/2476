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
- Historical core-plan recovery: direct location-scoped Inventory Receive
  (`REQ-INV-003` / REC-001) and multi-line Inventory Transfer
  (`DRAFT -> DISPATCHED -> RECEIVED`, ADR-0046 / AUD-03) are completed.
  Physical-return movement remains tracked under incomplete `REQ-INV-002` and
  blocked by future-scope REQ-FUL-003 evidence.
- Cross-domain transaction-contention contract (`REQ-CONC-001` / AUD-01): exact
  SQL Server 1205/1222 rollback mapping and caller-owned same-key retry under
  ADR-0044.
- Manual VNPAY QueryDr reconciliation (`REQ-PAY-004` / AUD-02) is completed for
  existing ambiguous attempts under ADR-0045. Scheduled reconciliation and live
  VNPAY certification remain outside this slice.

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
| REC-001 Inventory Receive recovery | Completed 2026-10-08 | V34; `FlywayV34UpgradeExternalIT`; `VerticalSlice2ExternalIT`; manager UI tests |
| AUD-01 Transaction Contention Contract | Completed 2026-10-09 | ADR-0044; `ApiExceptionHandlerTest`; `TransactionContentionExternalIT`; frontend retry tests |
| AUD-02 VNPAY Manual QueryDr Reconciliation | Completed 2026-10-11 | ADR-0045; V35; provider/service/SQL/frontend acceptance and independent review |
| AUD-03 Inventory Transfer | Completed 2026-10-12 | ADR-0046; V36; domain/controller, SQL concurrency, migration, frontend and integrity acceptance |
| POST-AUD03 V36 visual gate | Completed 2026-10-13 | Real desktop/mobile browser verification; `V36 DEMO PROMOTION READY`; datasource unchanged |
| REQ-PAY-003 certification preparation | Prepared, not admitted | `docs/REQ-PAY-003-VNPAY-SANDBOX-CERTIFICATION-PLAN.md`; live sandbox/IPN evidence pending |

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

## Planned UX upgrade — 2026-10-13

REQ-UX-003 adds the user-accepted seven-part experience direction: product
comparison, PDP/lookbook, existing size-guidance refinement, cart recovery,
order journey, intent discovery and role-based staff task surfaces. Status is
planned, not implemented; DEC-0013 records its boundary. Canonical plan:
`docs/superpowers/plans/2026-10-13-shopping-experience-upgrade.md`.

2026-10-15 execution supersedes the planning-only status above: implementation present,351 frontend tests/build PASS, live discovery/recovery/role-boundary and responsive checks added. Remaining visual acceptance is tracked in docs/UX_UPGRADE_VERIFICATION_20261015.md; REQ-UX-003 remains in_progress.

2026-10-20 incremental reconciliation: current source retains UX01 cache revalidation, Guest Cart cross-tab/stale-login remediation, SEARCH-01 bounded typo suggestions and SEARCH-02 frontend autocomplete. User reports technical PASS for each; local source/regression cases corroborate implementation, while exact recent RED/GREEN and benchmark receipts were not located. UX01 and SEARCH-02 browser/endpoint acceptance, UX05 visual acceptance, and wider performance acceptance remain pending as applicable. Guest Cart backend race is INCONCLUSIVE, not a confirmed defect. This does not admit new scope or change the certified/release baseline; see CURRENT_STATE.md.
