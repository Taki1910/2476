# Approved MVP Implementation Baseline

> Status: **APPROVED MVP IMPLEMENTATION BASELINE**
>
> Approved: 2026-08-24
>
> Implementation status: preparation authorized; implementation not started

## Purpose and authority

This document is the implementation-planning index for the approved MVP. It
records delivery scope, implementation conventions, sequencing, and unresolved
entry gates. It does not replace the authoritative architecture, domain,
lifecycle, security, reporting, or ADR documents linked below.

Approval applies to the MVP baseline and its accepted architecture boundaries.
It does not convert every Blueprint proposal into a confirmed product
requirement, admit deferred capabilities into active scope, or resolve the
`OPEN DECISION` items listed in this document.

## Acceptance history

```text
Blueprint v1.1.1
    -> Independent Acceptance Review: REJECT / REVISE
    -> Correction
    -> Independent Acceptance Re-Review: ACCEPT WITH CONDITIONS
    -> MVP Baseline Planning
    -> Independent Baseline Approval: APPROVE WITH CHANGES
    -> Baseline Revision
    -> Final MVP Baseline Approval: APPROVE
    -> APPROVED MVP IMPLEMENTATION BASELINE
```

The accepted architecture is authoritative for MVP implementation preparation.
Slice-specific policy choices remain `OPEN DECISION` until their entry gates.

## MVP actors and channels

Implementation-facing actors are:

- **Registered Customer** — ownership-based access to profile, checkout, and
  Orders.
- **Cashier** — assigned Register/CashierShift and scoped POS activity.
- **Back Office Operations Staff** — an explicit combination of operational
  permissions; not an unrestricted role.
- **Administrator** — identity administration plus only separately granted
  operational permissions; no hidden super-admin bypass.
- **Payment Provider integration identity** — authenticated provider-event
  application only.

Store Manager, Inventory Staff, Merchandising Staff, and Fulfillment Staff
remain useful conceptual actors. The MVP may demonstrate their responsibilities
through one Operations role, but the underlying permission boundaries remain
distinct.

The MVP uses one SPA with three experience areas:

- Customer Web — core;
- POS — limited;
- Back Office — limited.

## Approved core scope

### Customer Web — core

- Registered Customer authentication.
- Catalog browsing and variant/size selection.
- Location-aware availability.
- Deterministic, server-authoritative base-price quote without Voucher.
- Online pickup checkout with explicit Inventory Reservation.
- Asynchronous simulated electronic single full capture.
- Payment and Order status recovery.
- Pickup preparation and handover.
- Confirmed pre-handover cancellation and financial Void.
- Basic customer Order, Payment, and Fulfillment visibility.

### POS — limited

- Cashier authentication and assigned Register/CashierShift context.
- The same authoritative base-pricing model.
- Cash-only sale using the shared Order and Inventory core.
- Immediate handover and receipt representation.

Electronic or split tender, offline operation, and device/hardware integration
are not part of the MVP POS scope.

### Back Office — limited

- Account, permission, and assignment administration.
- MVP Branch/Location setup.
- Catalog, ProductVariant, SKU, and base-price management.
- Inventory receive, adjust, and query.
- Pickup processing and scoped operational access.
- Basic reconciliation reporting.

Operational staff specializations do not require separate frontend applications.

## Optional and deferred scope

`SHOULD` / optional follow-on, but not core commitments:

- limited Voucher;
- password reset.

`DEFERRED`:

- automated scheduled-worker infrastructure;
- Return/Refund product workflow;
- guest checkout;
- delivery/carrier integration;
- electronic/split POS tender and offline POS;
- production payment provider;
- advanced promotion behavior and Voucher reissue/reversal;
- non-zero tax or legal e-invoice;
- advanced reporting/analytics and inventory valuation;
- AI;
- Redis, Kafka/brokers, search infrastructure, microservices, and Kubernetes.

Accepted Blueprint boundaries for a deferred capability mean only that the
architecture can support it later. They are not authorization to implement it.

## Technology and persistence baseline

Backend:

- Java 25 LTS and Spring Boot 4.1.x;
- Boot-managed Spring Security;
- Maven with Maven Wrapper;
- modular monolith, packaged by business module;
- internal `api`, `application`, `domain`, and `infrastructure` organization
  where useful;
- Spring Data JPA for ordinary persistence;
- explicit SQL/locking repository logic for concurrency-critical paths;
- Jakarta Bean Validation and application-use-case transaction boundaries;
- external provider calls only outside database transactions.

Exact compatible patch and plugin versions are pinned immediately before
scaffolding; this baseline does not invent patch numbers.

Persistence:

- SQL Server with Flyway versioned migrations;
- `BIGINT IDENTITY` internal keys and application-generated UUID v4 public IDs;
- internal IDs are never exposed in public contracts;
- Java `Instant`, SQL Server `datetime2(6)`, and UTC-only persistence;
- optimistic version columns where stale writes matter;
- stable string lifecycle/status codes;
- exact scale-zero VND stored as `DECIMAL(19,0)`;
- append-only critical financial, inventory, and audit facts;
- no cascade deletion of authoritative business history;
- real SQL Server for database and concurrency verification.

## Security, permissions, and session baseline

The approved single-instance browser baseline is a standard server-side servlet
session with `Secure`, `HttpOnly`, and appropriate `SameSite` cookie policy,
CSRF protection for unsafe requests, session-ID rotation after authentication or
privilege change, and invalidation on logout.

`authVersion` invalidates stale authority. Account disable, password change, and
grant change increment authority version. Every sensitive request evaluates
current permission, ownership, Branch, Location, account, profile, and assignment
state server-side. Frontend route guards are never authorization.

Passwords use BCrypt with configurable cost and an encoded algorithm prefix.

**Spring Session JDBC is `DEFERRED`** until multi-instance deployment, durable
sessions, restart persistence, or remote-session management requires it.

Minimum permission vocabulary:

- `IDENTITY_MANAGE`
- `CATALOG_MANAGE`
- `PRICE_MANAGE`
- `INVENTORY_VIEW`
- `INVENTORY_ADJUST`
- `POS_SELL`
- `FULFILL_PICKUP`
- `ORDER_VIEW_SCOPED`
- `ORDER_CANCEL`
- `REPORT_VIEW`
- `PAYMENT_EVENT_APPLY`

Minimum policies:

- Customer: ownership-based access to own profile, checkout, and Orders.
- Cashier: `POS_SELL`, scoped Order visibility, and assigned Register/Shift.
- Operations: explicit combinations of catalog, pricing, inventory,
  fulfillment, cancellation, and reporting permissions.
- Administrator: `IDENTITY_MANAGE` plus separately granted operational
  permissions; no implicit business bypass.
- Provider: `PAYMENT_EVENT_APPLY` as integration identity only.

Mandatory audit writing is a system responsibility, not an actor permission.

## REST and money conventions

- API prefix: `/api/v1`.
- Public resource IDs: UUID.
- Transport DTOs, never JPA entities, define API contracts.
- Errors use RFC 9457 Problem Details with stable `code`, `correlationId`, and
  validation `errors[]` where applicable.
- `401` means unauthenticated.
- `403` means authenticated but unauthorized.
- `404` conceals existence where disclosure would violate ownership/security.
- `412` means a supplied `If-Match` precondition is false.
- `409` means a domain/lifecycle conflict or mismatched idempotency-key
  fingerprint. It is not interchangeable with `412`.
- Timestamps use RFC 3339 UTC; business dates use ISO `YYYY-MM-DD`.

MVP money JSON is:

```json
{
  "amount": 123000,
  "currency": "VND"
}
```

The API bound is:

```text
0 <= amount <= 9_007_199_254_740_991
```

Backend/domain calculations use exact scale-zero values. Binary floating-point
money arithmetic is forbidden. Exceeding the safe JSON integer bound requires
an explicit versioned contract change.

## Idempotency baseline

Client operation identity is mandatory for:

- Online Order placement;
- Payment initiation;
- Void initiation;
- confirmed cancellation;
- POS cash sale;
- Inventory receipt;
- Inventory adjustment;
- pickup handover.

Refund initiation and stock transfer require it if those later capabilities are
implemented. Provider callbacks use authenticated provider-event identity and
the exact attempt generation, not a browser `Idempotency-Key`.

The same key and fingerprint replays the recorded result. Reusing a key with a
different fingerprint returns `409 Conflict`.

GET, ordinary reads, version-protected catalog administration, and harmless
optimistic-concurrency-protected administration do not require idempotency
storage.

## RR-01 component reversal capacity

For each immutable paid component `c`:

```text
successfulVoid(c)
+ successfulRefund(c)
+ activeVoid(c)
+ activeRefund(c)
<= capturedComponentCapacity(c)

remainingComponentCapacity(c)
= capturedComponentCapacity(c)
- successfulVoid(c)
- successfulRefund(c)
- activeVoid(c)
- activeRefund(c)
```

Aggregate capacity remains:

```text
successfulVoided
+ successfulRefunded
+ activeVoidReserved
+ activeRefundReserved
<= successfulCaptured
```

For every active reversal attempt:

```text
sum(component allocations) = attempt reserved amount
allocation.amount > 0
```

The MVP supports a single full capture or full cash settlement. Therefore:

```text
capturedComponentCapacity(c)
= immutable Order paid component amount(c)
```

only after successful full capture or cash settlement. Zero-payable Orders have
no reversal capacity. Partial and split capture are `DEFERRED`; a future partial
capture must first persist immutable successful-capture component allocations.

Reversal-allocation lifecycle:

```text
ACTIVE -> SUCCEEDED
ACTIVE -> RELEASED
```

Provider `PENDING`/`UNKNOWN` is attempt state; allocations remain `ACTIVE` during
ambiguity. Component identity and allocation amount are immutable.

Under the Payment lock, one transaction converts every allocation for a
successful attempt from `ACTIVE` to `SUCCEEDED`, converts the attempt's active
aggregate reservation to the successful reversal amount, and appends the
successful financial transaction. Partial conversion is forbidden.

A contractually definitive failure converts every allocation for that attempt
from `ACTIVE` to `RELEASED` and releases only that attempt's aggregate reservation
exactly once in one transaction. Partial release is forbidden. Expected-state
failure rolls back the entire outcome transaction or enters reconciliation.

Provider events correlate to the exact attempt generation. Stale events cannot
mutate newer attempts. Reporting consumes only `SUCCEEDED` allocations;
callbacks and reports never reconstruct allocations. Physical schema design is
an `OPEN DECISION` before the online checkout/payment slice.

## Frontend baseline

- Vue 3, TypeScript, Vite, and Vue Router.
- One SPA with Customer, POS, and Back Office areas.
- Cookie-session and CSRF integration.
- Typed API boundary and feature-specific transport DTOs.
- Local/feature state by default.
- No state/query library or component library is frozen yet.
- The SPA is never authoritative for pricing, availability, inventory,
  payment/refund lifecycle, authorization, or business transitions.

Frontend scaffolding begins with the first frontend-relevant product slice, not
during Foundation 0 merely for completeness.

## Foundation and delivery sequence

Foundation 0 establishes Java/Spring/Maven, modular structure, SQL Server,
Flyway, the real-SQL integration harness, ID/time/money/status conventions,
Problem Details, correlation, `Clock`, audit boundaries, and module dependency
rules. It is not a product vertical slice.

Foundation 1 establishes UserAccount authentication, servlet session, CSRF,
BCrypt, `authVersion`, permissions, Branch/Location, assignments, ownership, and
scope policies. It is enabling architecture, not a product vertical slice.

Official core sequence:

```text
Foundation 0
-> Foundation 1
-> Stage 2 — Catalog + Location Inventory
-> Stage 3 — Base Pricing + Deterministic Quote
-> Stage 4 — Online Pickup Checkout + Async Payment
-> Stage 5 — Pickup Fulfillment + Cancellation/Void
-> Stage 6 — Limited Cash POS
-> Stage 7 — Reconciliation Reporting + Demo Hardening
```

Voucher, Return/Refund, and scheduled infrastructure are not in the core
sequence. Optional ordering, if separately admitted, is:

```text
Optional A — Voucher
Optional B — Scheduled automation
Optional C — Return + Refund
```

These are not implementation commitments.

## Planned product vertical slices

1. **Publish stocked shoe variant:** Back Office -> SKU -> Location stock
   receipt -> sellable availability.
2. **Browse and authoritative quote:** Customer -> catalog -> variant/size ->
   availability -> PriceQuote.
3. **Online pickup purchase:** Quote -> checkout -> Reservation -> Order
   snapshot -> asynchronous payment -> callback/status recovery -> confirmation.
4. **Handover or cancellation:** confirmed Order -> prepare -> handover; or
   allowed cancellation -> Fulfillment fence -> `CANCELLATION_RESTORE` ->
   financial Void.
5. **Cash POS sale:** Cashier session -> Shift -> authoritative quote -> cash
   sale -> shared Order/Inventory -> immediate handover.

Foundation 0 and Foundation 1 are not vertical slices.

## Testing baseline

- Unit tests cover deterministic domain rules, pricing, state transitions, and
  authorization policies.
- Integration tests use real SQL Server for constraints, persistence,
  transactions, API contracts, and lock behavior.
- Mandatory concurrency tests cover stock reservation/checkout, payment event
  application, Void allocation/capacity, idempotent replay, and competing
  retry-sensitive commands in active scope.
- Selective end-to-end tests cover the five critical product slices.
- H2 and mock-only tests are not evidence for SQL Server locking semantics.
- Deferred Voucher and Return/Refund races become mandatory only if those slices
  are admitted.

## Remaining entry gates

All items below remain `OPEN DECISION` and block only the named entry point.

### Before scaffolding

- exact compatible Java/Spring Boot/Maven plugin/JDBC/Flyway versions;
- Maven coordinates, application name, and root Java package;
- SQL Server development/test environment and CI access;
- initial repository/backend/frontend launch conventions.

### Before Foundation 1 / Identity

- idle and absolute session timeouts;
- concurrent-session policy;
- exact role bundles and assignment rules.

### Before Catalog/Inventory

- canonical shoe-size representation and SKU constraints;
- adjustment reasons, thresholds, and authorization.

### Before Pricing

- quote lifetime, price-effective-time rules, and final quote API details.

### Before Online Checkout

- responsible Branch/Location allocation;
- Reservation TTL and payment grace;
- simulated-provider contract and callback authentication/reconciliation;
- RR-01 physical persistence design.

### Before Cancellation

- cancellation eligibility, authorized actors, and precise window;
- pickup/handover evidence boundary and simulated Void behavior.

### Before POS

- Register rules, Shift open/close, variance policy, cash-adjustment permission,
  and receipt requirements.

### Before Reporting

- report fields, business-timezone/reporting boundary, reconciliation views, and
  demo dataset.

## Authoritative references

- [Project overview](PROJECT_OVERVIEW.md)
- [Architecture](ARCHITECTURE.md)
- [Domain model](DOMAIN_MODEL.md)
- [Business rules](BUSINESS_RULES.md)
- [Lifecycles](LIFECYCLES.md)
- [Actors and use cases](ACTORS_AND_USE_CASES.md)
- [Security model](SECURITY_MODEL.md)
- [Frontend architecture](FRONTEND_ARCHITECTURE.md)
- [Reporting glossary](REPORTING_GLOSSARY.md)
- [ADR governance](ADR/README.md)
- [Blueprint v1.1.1 correction changelog](BLUEPRINT_V1_1_1_CHANGELOG.md)

