# Project Overview — Blueprint v1.1.1

## Document status

- Blueprint version: `v1.1.1`
- Blueprint architecture status: `ACCEPTED FOR THE APPROVED MVP BASELINE`
- MVP status: `APPROVED MVP IMPLEMENTATION BASELINE`
- Governance source: root `AGENTS.md`
- Critical review inputs: `ARCHITECTURE_REVIEW_V1.md` and
  `ARCHITECTURE_REVIEW_V1_1.md`
- Legacy role: reference only; `QLCHGiay/` is not the new application
- Implementation status: Core MVP implemented through Flyway `V15`; see the
  root [README](../README.md) for current setup, verification, and demo limits.

Blueprint v1.1 resolved the six architecture-review blockers. Blueprint v1.1.1
then completed the targeted corrections and independent acceptance re-review.
The revised MVP planning package subsequently received final independent
approval on 2026-08-24. The official implementation-planning scope and gates
are recorded in [MVP_IMPLEMENTATION_BASELINE.md](MVP_IMPLEMENTATION_BASELINE.md).

## Status vocabulary

| Status | Meaning |
|---|---|
| `CONFIRMED` | Explicit current requirement or governance invariant |
| `PROPOSED` | Technically defended behavior that is not independently approved as a requirement |
| `OPEN DECISION` | Required input is missing; affected implementation is gated |
| `LEGACY-ONLY` | Legacy behavior; not a new requirement |
| `DEPRECATED` | Forbidden or unsuitable for the new system |
| `DEFERRED` | Intentionally outside the selected delivery slice |

## Product vision

Build a smart multi-store shoe commerce platform supporting in-store POS and
online customer commerce while preserving correct inventory, financial,
security, and historical records across branches, locations, and POS terminals.

## Accepted architecture boundaries for the MVP

- Modular monolith, REST-first backend, separate Vue SPA, SQL Server, and
  versioned migrations.
- Product/ProductVariant separation; Catalog owns no stock.
- Location-aware Inventory with explicit reservations and immutable movements.
- Order-centered commerce with independent Payment and Fulfillment state.
- Immutable confirmed pricing/promotion snapshots.
- Idempotent payment/refund/provider-event processing.
- UserAccount, CustomerProfile, and EmployeeProfile separation.
- Stable server-enforced permissions and branch/location scope.
- AI proposal/validation/approval boundary.
- No legacy invoice-centric model, inferred reservations, localized role/state
  logic, or permanent plaintext-password fallback.

These architecture boundaries are accepted for the approved MVP baseline.
Slice-specific business policies remain `OPEN DECISION`, and accepted support
for a deferred capability does not place that capability in MVP scope.

## Approved MVP capability classification

| Capability | MVP classification | Boundary |
|---|---|---|
| Registered Customer identity and scoped staff authorization | `CORE` | Standard server session, explicit permissions, ownership and Branch/Location scope |
| Branch, Location, Register, and CashierShift context | `CORE` | Required for inventory, limited cash POS, and staff scope |
| Catalog, ProductVariant, size/SKU, base price | `CORE` | Stable sellable identity; no catalog stock |
| Inventory, Reservation, and movement | `CORE` | Location-aware and concurrency tested |
| Online pickup Order and simulated full electronic capture | `CORE` | Asynchronous recovery, confirmation, pickup, cancellation, and Void |
| Limited cash POS | `CORE` | Cash only; shared Order/Inventory; immediate handover |
| Audit and basic reconciliation reporting | `CORE` | Immutable source facts and scoped reporting |
| Limited Voucher and password reset | `SHOULD / OPTIONAL` | Require separate admission and slice gates |
| Return/Refund product workflow and scheduling infrastructure | `DEFERRED` | Architecture support is retained; no core implementation commitment |
| Guest, delivery, production provider, advanced promotion/tax/reporting/AI | `DEFERRED` | Requires later approved scope |
| Electronic/split/offline POS and hardware integration | `DEFERRED` | Not part of limited POS |
| Redis, brokers, search, microservices, Kubernetes | `DEFERRED` | No MVP requirement |

## Approved implementation order

```text
Foundation 0
  -> Foundation 1
  -> Stage 2: Catalog + Location Inventory
  -> Stage 3: Base Pricing + Deterministic Quote
  -> Stage 4: Online Pickup Checkout + Async Payment
  -> Stage 5: Pickup Fulfillment + Cancellation/Void
  -> Stage 6: Limited Cash POS
  -> Stage 7: Reconciliation Reporting + Demo Hardening
```

Voucher, Return/Refund, and scheduled-worker infrastructure are outside this
core order. Optional A Voucher, Optional B Scheduled automation, and Optional C
Return + Refund require separate admission decisions.

## Resolved architecture-review blockers

1. Checkout now has a durable CheckoutAttempt, idempotent request fingerprint,
   deterministic POS/online protocols, resource-hold deadlines, and explicit
   reconciliation behavior.
2. Inventory now defines balance locking, conditional reserve/release/commit,
   expiry fencing, immutable movement keys, and transfer ordering.
3. Payment/Refund now defines attempts, append-only provider transactions,
   captured/refundable/refunded invariants, atomic refund reservation, and
   out-of-order callback handling.
4. Branch scope now distinguishes commercial ownership, inventory location,
   register, fulfillment location, and customer access; scope is derived
   server-side.
5. Lifecycles now separate commercial, payment, fulfillment, reservation,
   return, refund, voucher, account, and cashier-shift state.
6. ADRs use a consistent lifecycle and were accepted only after independent
   Blueprint and final MVP baseline review.

## Blueprint v1.1.1 targeted corrections

1. All cross-domain transactions now follow one global business-lock hierarchy:
   Order, Fulfillment/Return, Payment, limited Benefit/Voucher, Reservation,
   InventoryBalance, then CashierShift when applicable. Durable operation
   identity is claimed first; external providers are called only after commit.
2. Active refund amount is retained for unknown provider outcomes, released
   exactly once after a contractually definitive failure, and reacquired under
   the Payment lock before a retry attempt.
3. The initial monetary model is frozen to VND integer đồng, deterministic
   `HALF_UP` rounding/allocation, tax amount zero, a minimal stacking matrix,
   immutable adjustment allocations, and snapshot-based refunds.
4. `REPORTING_GLOSSARY.md` defines metric equations, immutable source facts,
   state inclusion, branch attribution and time basis. Inventory monetary value
   is explicitly deferred until acquisition cost is modeled.
5. Successful financial void and Refund now consume one captured capacity under
   Payment; pending/unknown voids fence overlapping reversal and successful
   voids carry immutable component allocations.
6. Allowed confirmed pre-dispatch cancellation now serializes with Fulfillment,
   restores committed stock through one compensating movement and preserves the
   committed Reservation as history.
7. Reset-token multiplicity, current backorder-disabled status and held-Voucher
   release after expiry/revocation now have one cross-document meaning.

## Remaining implementation gates

The authoritative gate list is maintained in
[MVP_IMPLEMENTATION_BASELINE.md](MVP_IMPLEMENTATION_BASELINE.md#remaining-entry-gates).
Before scaffolding, only exact compatible version pins, Maven coordinates and
names, root package, SQL Server development/test and CI access, and launch
conventions remain open. All other listed decisions block only their named
Foundation or product slice.

## Blueprint v1.1.1 consistency result

- Domain ownership cycles are removed by application coordination through
  durable operation IDs and aggregate-owned transitions.
- `Order=CANCELLED` with `Payment=CAPTURED` is permitted only as a temporary
  reconciliation case that requires idempotent void/refund; it never silently
  reopens the order.
- Reservation expiry cannot release a reservation being committed because both
  operations lock the same reservation/balance records and condition on the
  expected active state.
- Voucher reservation is released when checkout fails/expires; redemption
  occurs only at successful order confirmation.
- Disabled accounts/assignments invalidate effective authorization through an
  incremented authorization version and server-side session checks.
- AI cannot create authoritative discount, inventory, payment, refund,
  authorization, or executable-page state.
- Clients receive only authoritative server-computable data; exact availability
  remains a time-bound promise, not a client calculation.
- Every critical transaction uses the same upward lock order, including
  cancellation/dispatch and Return/restock; no documented flow acquires a
  higher-ranked business lock and then requests a lower-ranked one.
- Failed/unknown/successful RefundAttempt outcomes have distinct effects on
  active refund reservations, so retries cannot silently create over-refund.
- Successful voids and refunds consume the same captured financial capacity;
  pending/unknown voids fence overlapping capacity under the Payment lock.
- Allowed confirmed pre-dispatch cancellation restores committed stock through
  one immutable compensating movement and never rewrites Reservation history.
- Order, Payment, successful Void, Refund, Promotion and Report amounts use the
  same snapshotted allocation and reporting equations.

## Blueprint and MVP approval result

The independent acceptance re-review accepted the corrected architecture with
conditions. The final revised planning package closed those conditions and
received `APPROVE`. The repository is ready for Foundation 0 pre-scaffold
decisions; no application implementation or scaffolding is authorized by this
document update alone.
