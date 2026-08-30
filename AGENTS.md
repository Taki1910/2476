# AGENTS.md

# Smart Multi-Store Shoe Commerce Platform

## 0. Purpose

This repository contains the new generation of the shoe commerce platform.

The project is NOT a fork of the legacy application.

The legacy application is a source of:

- business knowledge;
- validated business rules;
- test cases;
- historical behavior;
- reusable domain concepts.

The new project is the source of truth.

When legacy behavior conflicts with an explicitly confirmed requirement or this project's architecture, the new project wins.

---

# 1. Core Development Principles

## 1.1 Business-first

Do not implement a feature merely because it is technically possible.

Every non-trivial feature must have:

1. business purpose;
2. actor;
3. preconditions;
4. state changes;
5. side effects;
6. authorization requirements;
7. failure behavior;
8. testable acceptance criteria.

If any of these are unclear, stop implementation and identify the ambiguity.

Do not silently turn assumptions into business rules.

---

## 1.2 Domain-first

Business logic belongs in the domain/application layer, not in:

- REST controllers;
- Vue components;
- database triggers unless explicitly justified;
- AI prompts;
- UI event handlers.

Controllers coordinate HTTP concerns.

Frontend coordinates presentation concerns.

AI proposes or assists.

Business/domain logic remains deterministic and testable.

---

## 1.3 No legacy architecture cloning

Do NOT copy legacy:

- controllers;
- Thymeleaf routes;
- entity relationships;
- invoice-centric architecture;
- `SanPham.tonKho`;
- `DonHang -> HoaDon` duplication;
- string-based roles;
- string-based status parsing;
- repository-heavy controllers;
- plaintext password compatibility;
- legacy session architecture.

Legacy code may be inspected for business intent only.

---

# 2. Source of Truth Hierarchy

When information conflicts, use this priority:

1. Explicit current project requirements.
2. Approved architecture decisions / ADRs.
3. `docs/BUSINESS_RULES.md`.
4. `docs/DOMAIN_MODEL.md`.
5. `docs/LIFECYCLES.md`.
6. Approved API contracts.
7. Tests representing confirmed requirements.
8. Legacy audit / legacy implementation.
9. Agent assumptions.

Lower-priority information MUST NOT silently override higher-priority information.

If two authoritative sources conflict:

STOP.

Report the conflict.

Do not choose arbitrarily.

---

# 3. Requirement Status

Important decisions must be classified as one of:

- `CONFIRMED`
- `PROPOSED`
- `OPEN DECISION`
- `LEGACY-ONLY`
- `DEPRECATED`

Never present `PROPOSED` behavior as confirmed business behavior.

Never port `LEGACY-ONLY` behavior without explicit approval.

---

# 4. Architecture

## 4.1 Default architecture

Use a modular monolith unless an ADR explicitly approves another architecture.

Preferred structure:

    Client Applications
          |
          v
       REST API
          |
          v
    Application Layer
          |
          v
      Domain Layer
          |
          v
    Infrastructure
          |
          v
       SQL Server

Do not introduce microservices by default.

---

## 4.2 Domain boundaries

The system should maintain clear ownership around:

- Identity & Access
- Customer
- Employee
- Branch / Location
- Catalog
- Pricing
- Promotion / Voucher
- Cart
- Order
- Payment
- Inventory
- Fulfillment / Shipment
- Return
- Refund
- Notification
- Audit
- Reporting
- AI / Recommendation
- Content / Page Configuration

These are domain boundaries, not necessarily separate deployable services.

---

# 5. Aggregate Ownership

## 5.1 Product

Product owns catalog identity.

ProductVariant owns sellable SKU-level attributes.

Inventory does NOT belong to Product or ProductVariant.

---

## 5.2 Inventory

Inventory owns:

- on-hand quantity;
- reserved quantity;
- available quantity;
- reservations;
- stock movements;
- transfers;
- receiving;
- adjustments;
- returns.

Never represent reservation as an aggregate query over unpaid orders.

Preferred invariant:

    available = onHand - reserved

All reserve/release/commit operations must have explicit transactional semantics.

---

## 5.3 Order

Order is the central commerce aggregate.

Do NOT recreate:

    DonHang -> HoaDon

as the primary model.

POS and online commerce share the Order core but may have different application workflows.

Examples:

- `POSCheckout`
- `OnlineCheckout`
- `CancelOrder`
- `RequestReturn`
- `RequestRefund`

---

## 5.4 Payment

Payment is a financial domain.

Do not assume:

- one payment per order;
- cash-only payment;
- synchronous payment;
- one attempt;
- no retries.

The architecture must be able to represent:

- payment attempts;
- transaction state;
- provider reference;
- idempotency;
- webhook/provider events;
- retry/failure;
- capture;
- refund;
- partial refund.

Financial operations must be idempotent.

---

# 6. Inventory Rules

Inventory operations must be explicit.

Valid concepts include:

- reserve;
- release;
- commit;
- receive;
- adjust;
- transfer;
- return.

Inventory state must be location-aware.

At minimum distinguish:

- branch sales floor;
- warehouse.

Never put stock columns on catalog lookup/master entities.

Never calculate available stock by scanning unpaid invoices/orders.

Any concurrency-sensitive stock operation requires explicit transaction and locking/versioning strategy.

---

# 7. Order / Payment / Refund State

States must be modeled explicitly.

Do NOT use:

- `contains("cancel")`;
- `contains("complete")`;
- localized display strings;
- arbitrary free-text state.

Use enums/state identifiers and explicit transitions.

Every state machine must define:

- allowed transitions;
- forbidden transitions;
- actor;
- preconditions;
- side effects;
- failure behavior.

Example:

    PENDING_PAYMENT
        -> PAID
        -> CANCELLED

    PAID
        -> PROCESSING
        -> REFUND_REQUESTED

The exact states must be defined in `docs/LIFECYCLES.md`.

Do not invent final state names if the lifecycle is not yet approved.

---

# 8. Promotion / Voucher Architecture

Promotion is a first-class domain.

It may include:

- product discount;
- order discount;
- percentage discount;
- fixed discount;
- threshold discount;
- free shipping;
- shipping discount;
- voucher;
- customer-specific voucher;
- campaign;
- targeted promotion.

Promotion eligibility may depend on:

- product;
- category;
- order subtotal;
- customer;
- customer segment;
- branch;
- channel;
- time window;
- usage limits;
- previous usage;
- campaign.

Do not implement promotion calculations separately in controllers.

Use a centralized pricing/promotion pipeline.

Preferred conceptual flow:

    Cart / Order
        |
        v
    Pricing Engine
        |
        +-- Product Promotions
        +-- Order Promotions
        +-- Voucher
        +-- Shipping Promotions
        +-- Customer Eligibility
        +-- Campaign Rules
        |
        v
      PriceQuote
        |
        v
      Checkout

Historical order pricing must be snapshotted.

Changing a promotion later must NOT alter an already-confirmed order.

---

# 9. Voucher Distribution

Do not treat voucher as only a database code.

Voucher may be distributed through:

- homepage;
- product page;
- checkout;
- email;
- notification;
- customer targeting;
- campaign.

Distinguish:

    Voucher Definition
    Voucher Issuance
    Voucher Redemption

A customer-specific voucher must have explicit ownership/eligibility.

Marketing/event-driven behavior must not directly bypass promotion validation.

---

# 10. Security

Security is cross-cutting and business-critical.

Required concepts include:

- authentication;
- authorization;
- role;
- permission;
- branch/location scope;
- account lifecycle;
- password policy;
- forgot password;
- reset token;
- session/token revocation;
- rate limiting;
- security audit.

Never use localized role text as the authorization mechanism.

Never store password-reset tokens in plaintext if a hashed token can be used.

Never restore permanent plaintext-password fallback from legacy.

Never allow frontend-only authorization.

Every sensitive use case must be authorized server-side.

Authorization must be enforced at the application/domain boundary, not only at HTTP route level.

---

# 11. Customer / Employee / Account Separation

Do not merge these concepts:

    UserAccount
    CustomerProfile
    EmployeeProfile

An account represents authentication identity.

A customer profile represents commerce/customer information.

An employee profile represents workforce information.

Branch assignment and permissions must be explicit.

---

# 12. REST API

The backend is REST-first.

Controllers must NOT contain business orchestration that belongs in application services.

Preferred flow:

    HTTP Request
        |
        v
    REST Controller
        |
        v
    Request DTO Validation
        |
        v
    Application Use Case
        |
        v
    Domain Rules
        |
        v
    Repository / Infrastructure
        |
        v
    Response DTO

Do not expose JPA entities directly as API contracts.

Do not allow frontend DTO requirements to silently redefine domain behavior.

API errors must use a consistent error contract.

Validation must exist server-side even if frontend validation exists.

---

# 13. Frontend

Frontend is a separate SPA consuming REST APIs.

Preferred framework:

    Vue

unless an approved ADR explicitly selects another framework.

Customer, POS, and Admin experiences may use separate application areas/components, but business logic must not be duplicated between them.

Frontend must NOT become the source of truth for:

- pricing;
- promotion;
- stock;
- payment;
- authorization;
- refund eligibility;
- order state.

---

# 14. UI / UX Agent Rules

Ponytail and UI UX Pro Max may be used for UI/UX work.

Frontend agents may:

- design pages;
- create components;
- improve layout;
- improve accessibility;
- improve responsive behavior;
- create design tokens;
- improve information hierarchy;
- implement approved API contracts.

Frontend agents may NOT:

- change business rules;
- change domain states;
- weaken authorization;
- bypass API validation;
- alter financial logic;
- alter inventory semantics;
- invent undocumented API behavior.

If UI requires an API that does not exist:

STOP.

Request or propose an API contract.

Do not create arbitrary endpoints merely to satisfy the UI.

---

# 15. AI Architecture

AI is an assistant/subsystem, not the source of truth for transactional business rules.

## Customer AI

Possible capabilities:

- foot/size analysis;
- size recommendation;
- product recommendation;
- personalization;
- promotion recommendation;
- future virtual try-on.

## Admin AI

Possible capabilities:

- banner generation;
- marketing content;
- homepage configuration;
- page configuration;
- campaign content;
- product merchandising suggestions.

AI-generated output must be treated as:

    Proposal / Draft

until validated.

Preferred flow:

    User Prompt
        |
        v
        AI
        |
        v
    Structured Output
        |
        v
    Validation
        |
        v
    Business Rules
        |
        v
    Preview / Approval
        |
        v
      Publish

AI must NOT directly:

- modify inventory;
- approve refunds;
- alter financial transactions;
- modify authorization;
- grant arbitrary discounts;
- publish critical business changes without required approval.

Never allow free-form AI-generated SQL or arbitrary production mutations.

---

# 16. AI Page Configuration

For dynamic pages, prefer structured configuration over generated HTML.

Preferred:

    AI
      |
      v
    Page Configuration
      |
      v
    Validation
      |
      v
    Admin Preview
      |
      v
    Publish
      |
      v
    Vue Renderer

Do not let AI directly write arbitrary HTML/CSS/JavaScript into production.

---

# 17. Multi-Branch Rules

All branch-sensitive data must have explicit ownership/scope.

Examples:

- inventory;
- register;
- cashier shift;
- employee assignment;
- reports;
- orders where applicable;
- promotions where applicable.

Never assume that an authenticated employee can access all branches.

Never rely on frontend filtering to enforce branch scope.

Branch authorization must be enforced server-side.

---

# 18. Audit

Audit is cross-domain.

Audit events should support:

- actor;
- action;
- entity type;
- entity ID;
- before;
- after;
- timestamp;
- request/correlation ID;
- source.

Audit records must be treated as immutable business history.

Do not implement audit as a mutable text field on individual business entities unless explicitly justified.

---

# 19. Notifications

Notification architecture must distinguish:

- recipient;
- channel;
- preference;
- template;
- delivery state;
- deduplication;
- source/domain event.

Potential channels:

- in-app;
- email;
- push.

Do not broadcast all notifications to all active accounts.

---

# 20. Database Rules

Database must enforce critical invariants where appropriate.

Examples:

- non-negative quantity;
- valid monetary values;
- unique SKU;
- unique relevant catalog combinations;
- unique provider references;
- idempotency constraints;
- valid foreign keys.

Do not rely exclusively on Java validation for invariants that must survive concurrent access.

Prefer versioned migration tooling for the new project.

Do NOT use legacy `schema.sql` as the new migration mechanism.

Do not expose sequential internal IDs as public identifiers unless explicitly justified.

---

# 21. Transactions and Concurrency

Transaction boundaries belong around application use cases.

Do not place large transaction orchestration in REST controllers.

For concurrency-sensitive operations:

- identify shared state;
- identify invariant;
- select optimistic/pessimistic strategy;
- define retry/failure behavior;
- test concurrent behavior.

Especially critical:

- inventory;
- payment;
- voucher redemption;
- order confirmation;
- refund;
- register/shift operations.

A transaction that "usually works" is not sufficient.

---

# 22. Testing Requirements

Every significant business feature should have tests at appropriate levels.

At minimum consider:

### Unit tests

For:

- business rules;
- pricing;
- promotion;
- state transitions;
- authorization policies.

### Integration tests

For:

- database constraints;
- transaction behavior;
- inventory;
- payment;
- API contracts.

### Concurrency tests

Required for critical operations such as:

- stock reservation;
- checkout;
- payment;
- voucher redemption.

### End-to-end tests

Use selectively for critical customer/POS flows.

Do not rely exclusively on Mockito tests for database-heavy business behavior.

---

# 23. Model Selection Policy

Use the GPT-5.6 model family according to task complexity.

## Tier 1 — Sol

Preferred model:

    GPT-5.6 Sol

Use for:

- architecture;
- domain modeling;
- business-rule design;
- lifecycle/state-machine design;
- security architecture;
- payment/refund architecture;
- inventory concurrency;
- promotion engine design;
- major refactors;
- architecture review;
- security review;
- difficult cross-module debugging;
- complex integration;
- final pre-release architecture audit;
- orchestration decisions.

Default reasoning:

    high

Use:

    xhigh

when the task is particularly difficult, cross-cutting, or failure-sensitive.

Use:

    max

only for exceptional quality-first tasks where additional reasoning is justified.

Do NOT waste Sol on repetitive CRUD or mechanical changes.

---

## Tier 2 — Terra

Preferred model:

    GPT-5.6 Terra

Use for:

- normal backend implementation;
- REST controllers;
- DTOs;
- services;
- repositories;
- ordinary database work;
- standard validation;
- unit/integration tests;
- normal debugging;
- Vue page implementation;
- API integration;
- routine refactoring;
- documentation requiring moderate reasoning.

Default reasoning:

    medium

Use:

    high

when implementation crosses multiple modules or requires meaningful debugging.

Use:

    xhigh

only when the task is genuinely difficult and Sol is unavailable or unnecessary.

---

## Tier 3 — Luna

Preferred model:

    GPT-5.6 Luna

Use for:

- boilerplate;
- repetitive implementation;
- formatting;
- mechanical refactors;
- simple component generation;
- simple documentation;
- changelog updates;
- straightforward test generation;
- repetitive code transformations;
- high-volume low-risk tasks.

Default reasoning:

    low

Use:

    none

for purely mechanical tasks.

Do not assign Luna to:

- architecture decisions;
- security design;
- financial logic;
- inventory concurrency;
- promotion engine design;
- state-machine design;
- major refactors.

---

# 24. Model Escalation Policy

Agents should escalate when:

- requirements conflict;
- architecture is ambiguous;
- multiple domain boundaries are affected;
- a financial invariant is involved;
- concurrency is involved;
- security is involved;
- database migration is risky;
- an implementation would require changing an ADR;
- a business rule is unclear;
- tests repeatedly fail for a non-obvious reason.

Escalation path:

    Luna
      ↓
    Terra
      ↓
    Sol

Do not blindly retry the same reasoning with the same model.

When escalating, provide:

- task;
- current hypothesis;
- files inspected;
- changes made;
- failing tests;
- unresolved uncertainty;
- decision required.

---

# 25. Multi-Agent Governance

Sub-agents are allowed.

Preferred structure:

                    Orchestrator
                         |
        +----------------+----------------+
        |                |                |
      Domain           Backend          Frontend
        |                |                |
    Security           Database           UX
        |
        QA / Testing

The orchestrator owns:

- task decomposition;
- dependency ordering;
- agent assignment;
- integration;
- conflict detection;
- final verification.

Sub-agents own only their assigned scope.

---

# 26. Agent Boundaries

## Domain Agent

May modify:

- domain model;
- business rules;
- use-case design;
- lifecycle documentation;
- relevant domain code.

Must not silently change:

- frontend contracts;
- security policy;
- infrastructure;
- unrelated modules.

---

## Backend Agent

May modify:

- REST API;
- application services;
- repositories;
- backend tests;
- backend infrastructure.

Must respect approved domain rules.

---

## Database Agent

May modify:

- migrations;
- indexes;
- constraints;
- database configuration.

Must not alter domain semantics without an approved decision.

---

## Security Agent

May modify:

- authentication;
- authorization;
- security configuration;
- account security lifecycle;
- security tests.

Security changes require careful review.

---

## Frontend Agent

May modify:

- Vue pages;
- components;
- state management;
- API integration;
- styling;
- accessibility.

Must not change backend business behavior.

---

## UX Agent

May modify:

- visual design;
- information architecture;
- design tokens;
- component UX;
- responsive behavior.

May use Ponytail and UI UX Pro Max.

Must not modify business/domain semantics.

---

## QA Agent

May:

- add tests;
- inspect regressions;
- validate acceptance criteria;
- identify missing coverage;
- challenge assumptions.

QA must not weaken tests merely to make implementation pass.

---

# 27. Agent Handoff Protocol

When handing work between agents, provide:

```text
Task
Scope
Relevant files
Requirements
Current decisions
Expected outcome
Tests to run
Known risks
Open questions
````
Do not hand off only:
```
"Finish this feature."
```
Before modifying code:
1.  Inspect relevant files. 
2.  Inspect applicable documentation. 
3.  Identify architectural boundary. 
4.  Identify business rules. 
5.  Determine tests. 
6.  Implement the smallest coherent change. 
Do not perform unrelated refactors during feature work.
Do not "clean up everything" unless explicitly requested.
API changes require consideration of:
-  request DTO; 
-  response DTO; 
-  validation; 
-  authorization; 
-  error contract; 
-  pagination/filtering; 
-  idempotency; 
-  backward compatibility where applicable; 
-  frontend consumers; 
-  tests. 
Do not change an API contract casually.
If a breaking change is necessary, document it.
Do not add a dependency because it is convenient.
Before adding a major dependency, determine:
-  what problem it solves; 
-  why existing dependencies are insufficient; 
-  operational cost; 
-  security implications; 
-  testing implications; 
-  whether the project actually requires it. 
Particularly scrutinize:
-  Redis; 
-  Kafka/RabbitMQ; 
-  Elasticsearch/OpenSearch; 
-  WebSocket infrastructure; 
-  object storage; 
-  external AI providers. 
Technology is justified by requirements, not by fashion.
Do not introduce microservices, Kafka, service discovery, distributed transactions, or similar infrastructure without a concrete requirement.
The default is:
```
Modular Monolith
```
The architecture must still maintain boundaries that could support future extraction if justified.
Important architectural decisions must be documented.
Use:
```
docs/ARCHITECTURE.md
docs/DOMAIN_MODEL.md
docs/BUSINESS_RULES.md
docs/LIFECYCLES.md
docs/SECURITY_MODEL.md
docs/PROMOTION_ENGINE.md
docs/AI_STRATEGY.md
docs/FRONTEND_ARCHITECTURE.md
docs/ACTORS_AND_USE_CASES.md
docs/LEGACY_REUSE.md
```
Architecture decisions belong in:
```
docs/ADR/
```
Do not duplicate conflicting specifications across documents.
Create/update an ADR when a decision:
-  changes domain boundaries; 
-  introduces infrastructure; 
-  changes transaction semantics; 
-  changes security model; 
-  changes payment behavior; 
-  changes inventory behavior; 
-  changes public API architecture; 
-  introduces an AI execution boundary; 
-  introduces a major external provider. 
Do not create ADRs for trivial implementation details.
A task is not complete merely because the code compiles.
For non-trivial features, verify:
-  requirement implemented; 
-  business rules respected; 
-  authorization checked; 
-  validation implemented; 
-  transaction boundary reviewed; 
-  failure behavior considered; 
-  tests added/updated; 
-  relevant integration tests pass; 
-  API contract verified; 
-  documentation updated if architecture changed; 
-  no unrelated changes introduced. 
For financial/inventory/security features, additional review is mandatory.
Never violate these invariants without an explicit approved decision.
```
available = onHand - reserved
```
Reservation must be explicit.
Confirmed order pricing is immutable historical data.
Financial operations must be idempotent.
Refund must correspond to an eligible financial transaction.
Promotion validity must be evaluated by deterministic business rules.
Frontend visibility is not authorization.
AI output is not automatically authoritative business state.
Business audit history must not be casually mutable/deleted.
Branch-restricted data must be enforced server-side.
If uncertain:
DO NOT guess.
Use this order:
1.  Search project documentation. 
2.  Inspect relevant code/tests. 
3.  Check ADRs. 
4.  Identify whether the behavior is legacy-only. 
5.  Determine whether the issue is architectural or implementation-level. 
6.  Ask for clarification if the decision affects business semantics. 
A wrong architectural assumption is more expensive than a short clarification.
The project should evolve according to:
```
Requirement
    ↓
Business Rule
    ↓
Domain Model
    ↓
Use Case
    ↓
API Contract
    ↓
Implementation
    ↓
Test
    ↓
Verification
```
Never reverse this chain merely because an implementation is convenient.
The goal is not to produce the maximum amount of code.
The goal is to produce a coherent, testable, secure, maintainable business system whose architecture can be explained and defended academically and technically.
