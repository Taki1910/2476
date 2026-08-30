# ADR-0002: REST-First Backend and Vue SPA

- Status: `ACCEPTED`
- Accepted: 2026-08-24
- MVP scope: Core architecture baseline
- Date: 2026-08-23
- Decision class: Safe baseline; delivery/tooling details remain open
- Decision source: repository governance plus Blueprint v1.1 analysis

## Context

The new system needs customer, POS, and administrative clients. Legacy
Thymeleaf routes couple presentation, repositories, transactions, and business
logic.

## Decision

Expose REST APIs through transport DTOs and application use cases. Use a
separate Vue SPA baseline with distinct Customer, POS, and Admin experience
areas. Do not expose persistence entities as contracts.

## Consequences

- API contracts, errors, authorization, pagination, and idempotency are designed
  explicitly.
- Frontend validation improves UX but never replaces server rules.
- MVP tooling is Vue 3, TypeScript, Vite, and Vue Router in one SPA with
  Customer, POS, and Back Office areas. State/query and component libraries
  remain open; independent deployments are deferred.

## Rejected alternatives

- Porting Thymeleaf templates/controllers.
- Letting each frontend duplicate pricing, stock, or lifecycle logic.
