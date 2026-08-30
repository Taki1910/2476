# Blueprint v1.1 Changelog

> Revision status: **PROPOSED FOR REVIEW**
>
> Input: Blueprint v1 plus `ARCHITECTURE_REVIEW_V1.md` (`REJECT / REDESIGN`).
>
> Scope: documentation and architecture decisions only; no application code,
> schema migration, dependency, or infrastructure change.

## Revision objective

Blueprint v1 established useful domain boundaries but did not define enough
transaction, concurrency, accounting, scope, lifecycle, or ADR governance detail
to guide safe implementation. Version 1.1 resolves the six blocker classes while
leaving business policy choices visibly open.

## Blocker resolution map

| Blocker | Blueprint v1 position | Blueprint v1.1 decision | Main affected documents | Reason and consequence |
|---|---|---|---|---|
| Checkout consistency | Order, reservation, voucher, and payment existed but no complete atomic/recovery protocol | Atomic local placement; durable CheckoutAttempt/PaymentAttempt; provider outside transaction; idempotent callback; POS cash atomic; late capture reconciled | `ARCHITECTURE.md`, `DOMAIN_MODEL.md`, `BUSINESS_RULES.md`, `LIFECYCLES.md`, `ACTORS_AND_USE_CASES.md`, ADR-0010 | Prevent duplicate orders, oversell, consumed benefits, and ambiguous timeout outcomes; requires status recovery and reconciliation |
| Inventory concurrency | Locking/versioning named but not selected; expiry race unclear | Pessimistic balance/reservation locks, canonical lock order, conditional terminal transitions, operation uniqueness, immutable movements | `DOMAIN_MODEL.md`, `ARCHITECTURE.md`, `BUSINESS_RULES.md`, `LIFECYCLES.md`, ADR-0004 | Defines one enforceable stock invariant; throughput must be measured and race-tested |
| Financial/refund accounting | Idempotency and separate Refund named, but refundable arithmetic/out-of-order events incomplete | Append-only financial facts; pending refund amount reservation; Payment lock; provider-event uniqueness; contradiction/late-event reconciliation; cash belongs to shift | `DOMAIN_MODEL.md`, `ARCHITECTURE.md`, `BUSINESS_RULES.md`, `LIFECYCLES.md`, ADR-0005, ADR-0010 | Prevents over-refund and destructive rewriting; creates an operational reconciliation responsibility |
| Branch/location scope | Branch, location, warehouse, register, and order ownership were ambiguous | Branch is commercial owner; Location is physical stock node; every Order has a server-assigned responsible branch; explicit shared-location grants; register/shift ownership | `PROJECT_OVERVIEW.md`, `DOMAIN_MODEL.md`, `ACTORS_AND_USE_CASES.md`, `SECURITY_MODEL.md`, `FRONTEND_ARCHITECTURE.md`, ADR-0006, ADR-0011 | Removes generic store assumptions and closes client-selected scope leaks; online allocation policy remains open |
| Incomplete lifecycles | Several entities had concepts but no complete state/transition/failure model | Added Cart, CheckoutAttempt, Order, Reservation, Transfer, PaymentAttempt, pickup/delivery fulfillment, Return, Refund, voucher, account/profile, password-reset, shift, notification, AI/content lifecycles | `LIFECYCLES.md`, supported by domain/business/security docs | Makes terminal-state ownership and race behavior reviewable; exact timeouts/eligibility remain business decisions |
| ADR governance | All nine ADRs were marked Accepted before independent review | Lifecycle `PROPOSED -> UNDER REVIEW -> ACCEPTED`; all ADRs returned to Proposed; decision classes and independent-review gate added | `ADR/README.md`, ADR-0001 through ADR-0012 | Removes false authority; implementation planning must wait for review/required business confirmation |

## HIGH finding closure summary

| Review finding | v1.1 disposition |
|---|---|
| H-01 quote/voucher TOCTOU | Quote is advisory; placement locks and atomically revalidates/reserves price benefit and stock |
| H-02 stale employee session | Request-time account/assignment/version checks plus `authVersion` revocation; shift remains separate |
| H-03 cash/shift accounting | One-open-shift rule, immutable tender entries, expected/counted/variance model, governed close/reconciliation |
| H-04 return/fulfillment race | Versioned quantity facts; locked eligibility; dispatch/cancel has one winner and compensation path |
| H-05 follow/marketing ownership | Optional engagement boundary with audience snapshot, consent/unfollow suppression, issuance/send dedupe |
| H-06 notification reliability | Transactional intent, delivery lifecycle, leased claims, retries, dedupe, and visible terminal failure |
| H-07 audit integrity/privacy | Required audit is atomic, append-only, non-cascading, actor-attributed, redacted, and permission protected |
| H-08 reporting semantics | Explicit branch/location/date measures, gross/capture/refund/net facts, `asOf`/freshness and reconciliation source |
| H-09 variant/price identity | Immutable unique SKU, canonical size context, option uniqueness, archive-not-repurpose, scoped effective Price |
| H-10 multi-instance workers | Conditional aggregate transitions or leased SQL claims with operation uniqueness and retry metadata |
| H-11 capstone breadth | Essential/demonstration/optional/deferred classification and dependency-ordered release gates |

## Major architecture changes

### Transaction and recovery model

- Local invariants sharing SQL Server use one short database transaction.
- External providers are never invoked inside that transaction.
- Durable pending intent precedes external calls; callbacks/polling/reconciliation
  apply outcomes idempotently.
- SQL-backed after-commit work is sufficient initially for notifications and
  integrations; no broker/Redis is approved without evidence.

### Domain ownership

- CheckoutAttempt coordinates; Order, Inventory, Promotion/Voucher, Payment,
  Fulfillment, Return, Refund, Notification, and Audit keep separate ownership.
- Branch owns commercial responsibility; Location owns physical stock.
- PriceQuote is advisory. The placed Order owns immutable price/product/promotion
  evidence.
- Physical return/restock and financial refund are separate lifecycles.

### Promotion, campaign, and voucher model

- PromotionDefinition, Campaign, Voucher, Issuance, Redemption, quote evidence,
  and order snapshots are distinct.
- Evaluation is deterministic and centralized across POS and online, with stable
  priority/tie-breaking; no universal lowest-price-wins assumption.
- Limited benefits are reserved at placement and redeemed at confirmation.
- Product-follow marketing respects consent, audience snapshot, unsubscribe, and
  deduplication. AI may draft but cannot activate or grant discounts.

### Security, REST, and frontend boundaries

- UserAccount credentials/sessions are separated from employee/customer profiles.
- Scoped RBAC combines permission, ownership, branch/location grants, and policy.
- Proposed first-party browser baseline is revocable HttpOnly session cookies,
  CSRF, and `authVersion`; JWT/Redis are not silently selected.
- Forgot-password request/complete transitions now define token hashing, atomic
  single use, rate limiting, session revocation, audit, and notification.
- API contracts will live under `docs/API/` and must describe idempotency,
  scopes, stable errors, unknown/pending states, and recovery before endpoints.
- Customer, POS, and Admin remain distinct frontend experiences; none owns
  business truth.

### AI boundary

- AI remains optional and non-authoritative.
- Image foot analysis and virtual try-on are deferred pending privacy, evaluation,
  and feasibility evidence.
- No model/provider/vector store/GPU/broker is selected during Blueprint.

## ADR changes

| ADR | Change | Current status |
|---|---|---|
| ADR-0001 Modular Monolith | Governance metadata corrected | Proposed |
| ADR-0002 REST-first Vue SPA | Governance metadata corrected; tooling remains open | Proposed |
| ADR-0003 Order-centered commerce | Governance metadata corrected | Proposed |
| ADR-0004 Location-aware inventory | Added explicit invariants, lock order, reservation races, movements, and transfer semantics | Proposed |
| ADR-0005 Payment/refund | Added refundable arithmetic, pending reservation, locking, event ordering, reconciliation, and cash semantics | Proposed |
| ADR-0006 Identity/scoped RBAC | Added branch/location/register/shift scope, session baseline, `authVersion`, and reset atomicity | Proposed |
| ADR-0007 Promotion/voucher | Added campaign/issuance/evidence model, deterministic pipeline, placement reservation, and delivery constraints | Proposed |
| ADR-0008 AI proposal boundary | Governance metadata corrected; provider and advanced capabilities remain deferred | Proposed |
| ADR-0009 SQL Server migrations | Governance metadata corrected; no migration tool selected | Proposed |
| ADR-0010 Checkout consistency and recovery | New blocker-resolution protocol | Proposed |
| ADR-0011 Multi-branch ownership and scope | New ownership/scope vocabulary and invariants | Proposed |
| ADR-0012 Transactional audit and SQL delivery | New atomic audit, durable notification, claim/retry, dedupe, and no-broker baseline | Proposed |

## Requirements status discipline

- `CONFIRMED`: supplied by authoritative project governance or explicitly stated
  target capability; still needs testable acceptance criteria.
- `PROPOSED`: architecture selected for review, not yet implementation authority.
- `OPEN DECISION`: a business/architecture owner must choose before the dependent
  implementation slice.
- `LEGACY-ONLY`: observed legacy behavior with no automatic requirement status.
- `DEFERRED`: intentionally outside the current implementation horizon.

No legacy behavior was promoted silently. `LEGACY_REUSE.md` remains the audit
baseline; reuse decisions still require target-domain contracts and tests.

## Remaining open decisions

### Business-critical before commerce implementation

- Guest checkout and customer account/history merge.
- Online responsible-branch and stock-allocation policy; pickup selection rules.
- Payment deadline/retry, supported tenders, split/partial payment, late capture,
  and cash drawer/shift variance policy.
- Tax, currency, rounding, legal invoice/receipt, and order-number requirements.
- Promotion types, stacking/exclusivity, usage limits, voucher TTL/reversal, and
  campaign approval/communication consent.
- Cancellation, return eligibility/inspection/restock, refund approval and
  customer communication.
- Transfer approval, reservation TTL, damaged/quarantine, and physical count
  variance. Backorder is disabled for the current v1.1 baseline; only a future
  reconsideration remains possible through an explicit later decision.

### Security and delivery before affected slices

- Final role/permission/grant catalogue, cross-branch access, maker-checker, and
  support-data masking.
- Session store/duration, MFA/step-up, password policy, reset channel/TTL/rate
  thresholds, secrets and key management.
- REST versioning/public ID format, error catalogue, pagination limits, CORS, and
  API compatibility workflow.
- Browser/device/POS hardware support, offline/degraded operation, SEO/SSR,
  accessibility target, and deployment split.

### Optional/deferred

- Notification channels/templates/preferences and marketing frequency policy.
- AI provider, accuracy targets, consent/retention, supported measurements and
  size systems, evaluation/fallback, and provider exit strategy.
- Message broker, Redis, microservices, search engine, vector database, GPU
  service, and split-location line allocation remain evidence-triggered only.

## Decisions considered safe to freeze after independent review

- Modular monolith with clear domain/application/infrastructure boundaries.
- SQL Server as initial transactional source of truth with versioned migrations
  before schema implementation.
- REST transport DTOs and separate Customer/POS/Admin frontend experiences.
- Order-centered commerce with immutable confirmed snapshots; invoice/receipt as
  downstream legal/document concerns.
- Location-owned inventory with explicit reservations/movements and no stock on
  ProductVariant.
- Separate Payment, Return, and Refund ownership.
- Stable scoped authorization at server use cases and separated account/profiles.
- One deterministic pricing/promotion authority.
- AI proposal-only boundary and non-AI fallback.
- No speculative broker, Redis, microservices, or AI infrastructure.

Checkout, lock, scope, and accounting protocols are credible proposed defaults,
but still need independent architecture/database/security review before they are
frozen.

## New or residual risks

| Risk | Why it remains | Required control |
|---|---|---|
| Lock contention/deadlock | Pessimistic locking trades ambiguity for coordination | Canonical order, short transactions, indexes, race/load tests, monitoring |
| Reconciliation workload | Provider truth can arrive late or conflict | Durable evidence, queues/jobs, operational owner, alerts and runbook |
| Policy ambiguity | Architecture cannot invent tax, tender, allocation, return, or promotion policy | Named business owners and decision deadlines before dependent slices |
| Scope leakage | Shared locations and support/finance roles cross branch boundaries | Explicit grants, server resolution, negative access tests, audit |
| Promotion complexity | Rich rules can become an untestable rule language | Confirm a small first-release matrix and version deterministic evaluation |
| Reporting disagreement | Sales, payment, fulfillment, return, and refund dates differ | Define measures/dimensions/timezone and reconcile to financial events |
| Session recovery abuse | Reset/session behavior is security-sensitive | Atomic token use, rate limits, `authVersion`, notifications, security tests/review |
| AI privacy/quality | Foot data and generated content create non-transactional risk | Keep optional/deferred until consent, retention, evaluation, and provider ADR exist |

## Feasibility classification

| Class | Capability groups | Assessment |
|---|---|---|
| `ESSENTIAL` | Identity/scoped RBAC, branch/location/register, catalog/variants/pricing, inventory, shared order placement, POS/online core, payment, fulfillment, return/refund, audit | Feasible in a modular monolith/SQL Server if implemented in dependency order and blocker protocols are tested |
| `DEMONSTRATION VALUE` | Deterministic promotion/voucher slice, pickup/delivery lifecycle, reports with explicit semantics, transactional notifications, content publication | Feasible after essential owners; use a narrow rule/channel set first |
| `OPTIONAL ADVANCED` | Product follow campaigns, recommendation, AI content drafts, declared-measurement size recommendation | Feasible only after stable base data/contracts and measurable acceptance criteria |
| `DEFERRED` | Virtual try-on, image foot analysis without privacy/evaluation evidence, offline POS, split-line allocation, microservices, broker, Redis, vector/GPU infrastructure | Not required for Blueprint v1.1 implementation readiness; revisit on confirmed need |

## Cross-document consistency check

The v1.1 revision checked the following dependency chains:

```text
Identity/RBAC/Scope -> Branch/Location/Register/Shift
                    -> Catalog/Pricing -> Inventory
                    -> CheckoutAttempt/Order -> Payment
                    -> Fulfillment/Return -> Refund
                    -> Notification/Audit/Reporting

Promotion/Voucher -> PriceQuote -> Order snapshots
AI/Content -> validated proposal -> ordinary authorized use case
```

Resolved contradictions:

- price authority is placement revalidation/snapshot, not a preview quote;
- cart does not reserve stock; placed pending order does;
- provider calls are outside local transactions; callbacks are not trusted by
  arrival order;
- order cancellation, inventory release, and late capture have one conditional
  winner plus reconciliation;
- Branch and Location no longer mean the same thing;
- authentication session and cashier shift no longer mean the same thing;
- refund and return/restock no longer share one terminal flag;
- ADR status no longer implies approval before review.

Missing requirements are listed as open decisions rather than filled from legacy.
No circular aggregate ownership remains: CheckoutAttempt coordinates through
application use cases, while each domain owns its state. Cross-domain side
effects use committed records instead of synchronous circular service calls.

Potentially dangerous assumptions still requiring review are pessimistic-lock
throughput, one-location-per-line allocation, server-managed browser sessions,
SQL-backed side-effect delivery, and the initial promotion rule subset. These are
explicit proposals with upgrade triggers, not hidden guarantees.

## Review requirement

Blueprint v1.1 has completed its author-side consistency pass. It is not accepted
and does not authorize feature implementation, database migration, REST
controllers, dependency installation, or infrastructure provisioning. An
independent architecture review—and business-owner confirmation for the open
policies above—is still required.

## BLUEPRINT V1.1 STATUS

**PROPOSED FOR REVIEW**
