# Blueprint v1.1.1 Targeted Correction Changelog

> Blueprint architecture status: **ACCEPTED FOR THE APPROVED MVP BASELINE**
>
> MVP status: **APPROVED MVP IMPLEMENTATION BASELINE**

## 1. Purpose

Blueprint v1.1.1 is a minimal correction of Blueprint v1.1. It resolves exactly
the four `HIGH` findings from the independent review without redesigning domain
ownership, reopening resolved blockers, or starting implementation.

The subsequent acceptance review returned `REJECT / REVISE`. This same v1.1.1
candidate now includes the bounded correction pass for AR-01 through AR-06:
shared void/refund capacity, confirmed-cancellation stock compensation and
Fulfillment/Return lock ranking, Product Sales void allocation, reset-token
multiplicity wording, backorder status, and held-Voucher release state. No new
version number was introduced. The corrected candidate later passed independent
re-review with conditions, and the revised MVP baseline received final approval.

Statements explicitly labeled `PROPOSED` remain proposals unless the approved
MVP baseline selects them. `LEGACY-ONLY` behavior remains evidence only and was
not promoted into the new system's requirements.

## 2. Source review

Authoritative correction source:

- [Architecture Review v1.1](ARCHITECTURE_REVIEW_V1_1.md), verdict `APPROVE WITH
  CHANGES`, with zero blockers and four remaining high risks.

Baseline and governance sources:

- repository `AGENTS.md`;
- [Blueprint v1.1 Changelog](BLUEPRINT_V1_1_CHANGELOG.md);
- the existing Blueprint v1.1 domain, architecture, lifecycle, security,
  frontend, AI and ADR documents.

No unrelated `MEDIUM`, `LOW`, or open business-policy item was silently closed.

## 3. Change 1 — Lock hierarchy

### Decision

One canonical database lock/fence order now governs every cross-domain local
transaction, with unused ranks skipped:

```text
0 operation or provider-event identity
1 Order
2 Fulfillment records, then Return records
3 Payment, then PaymentAttempt, Refund and RefundAttempt children
4 PriceVersion, PromotionDefinition, PromotionUsage, Voucher, VoucherUsage,
  VoucherIssuance
5 InventoryReservation
6 InventoryBalance by (locationId, productVariantId)
7 CashierShift
```

A transaction may begin at the first rank it needs and then acquire only
increasing ranks. Targets within a rank are normalized and stably sorted.
Checkout sums duplicate demand by `(location, variant)` and benefit identity
before locking. Price/time/usage and stock are revalidated only after their
authoritative rows or versions are fenced.

External provider calls never run while database locks are held. The local
attempt commits first; provider interaction and callback/reconciliation run in
separate transactions. Deadlock/timeout rolls the entire local transaction back
and permits only bounded replay using the original idempotency key/fingerprint.
Unknown commit outcome is queried by that identity.

The critical-flow matrix and race outcomes cover online checkout, POS cash,
payment initiation/confirmation/reconciliation, unconfirmed expiry, confirmed
cancellation versus dispatch, reservation commit/release, voucher-only work,
inventory operations, refund, return/restock and shift close. No documented flow
acquires a lower rank after a higher rank.

### Invariant

```text
within one transaction: nextLockRank >= currentLockRank
within one rank: unique targets, stable key order
```

For inventory demand, each normalized quantity is strictly positive and checked
under the canonical InventoryBalance lock.

### Remaining ambiguity

Concrete SQL locking syntax, indexes, timeout values and retry count are
implementation-planning details. Allocation priority and reservation TTL remain
`OPEN DECISION`; neither changes the canonical order.

## 4. Change 2 — Pending refund failure

### Decision

`activeRefundReserved` is the protected sum of Refund amounts with provider work
that is requested, approved, processing, unknown or reconciliation-required.
Refund creation or retry increases it only under the Payment lock.

- Success atomically appends the successful financial fact, decreases the
  attempt's active reservation and increases successful refunded value by the
  same amount.
- Timeout/unknown retains the reservation and forbids a blind retry.
- A provider failure releases capacity only when the provider contract guarantees
  that the attempt reference can never later succeed. The definitive failed
  RefundAttempt remains immutable and releases only its own reservation exactly
  once.
- Retry retains the logical Refund and client operation identity, reacquires
  capacity under Payment lock and creates a new immutable RefundAttempt/provider
  reference. An older attempt event cannot release or complete a newer attempt.
- Contradictory late evidence is preserved and enters reconciliation; it never
  rewrites prior financial facts.

Physical Return/restock stays separate from financial Refund.

A successful financial void is an immutable reversal of previously successful
captured exposure. Authorization-only cancellation is a distinct fact. Void and
Refund share the same captured capacity under Payment. Pending/unknown void
amount remains `activeVoidReserved`; success converts it to `successfulVoided`,
definitive failure releases it once, and immutable `VoidAllocation` rows
reconcile financial reports.

### Invariant

```text
successfulVoided + successfulRefunded
  + activeVoidReserved + activeRefundReserved <= successfulCaptured
remainingRefundable = successfulCaptured
                    - successfulVoided
                    - activeVoidReserved
                    - successfulRefunded
                    - activeRefundReserved
```

### Remaining ambiguity

Provider-specific void capability/timing and definitive/unknown outcome mapping,
refund approval thresholds, cash-refund shift ownership and tender policy remain
`OPEN DECISION`. They must implement this invariant rather than redefine it.

## 5. Change 3 — Money and promotion semantics

### Decision

The first supported calculation policy is `VND_V1`:

- currency is VND and amounts are exact integer đồng (`DECIMAL(19,0)` is the
  conceptual persistence representation); binary floating point is forbidden;
- fractional đồng uses decimal `HALF_UP` to one đồng;
- an item promotion rounds once per OrderItem line;
- automatic order and Voucher totals each round once at Order level, then
  allocate proportionally by largest fractional remainder and stable OrderItem
  ID; child allocations must sum exactly to the parent amount;
- the first stack allows at most one item automatic promotion per line, one
  automatic order promotion, one Voucher and one shipping benefit, in that
  order; same-layer candidates are mutually exclusive and resolve by priority
  descending then stable definition ID;
- every discount is capped by its base, shipping discount is capped by shipping
  fee, and final payable is non-negative;
- promotion time uses `Asia/Ho_Chi_Minh` with `[startInclusive, endExclusive)`;
- initial tax is explicitly zero. A non-zero/legal-tax model requires a later
  calculation-policy version.

```text
itemGross = sum(baseUnitPrice * quantity)
discountedItemSubtotal = itemGross - itemDiscount
merchandiseNet = discountedItemSubtotal - orderDiscount - voucherDiscount
shippingNet = shippingFee - shippingDiscount
tax = 0
finalPayable = merchandiseNet + shippingNet + tax
```

Order placement snapshots the calculation version, base prices, quantities,
applied definition versions, all component totals, per-line/per-unit allocations,
rounding evidence, shipping and final payable. Later rule edits do not recalculate
an Order.

Refund uses stored paid allocations. For the required example, a 100,000 VND
Voucher on 600,000/400,000 VND lines allocates 60,000/40,000, so Item A's maximum
merchandise refund is 540,000 VND before prior-refund/capture caps. Partial item
refund does not include shipping; full cancellation before fulfillment may
refund stored `shippingNet`; fulfilled shipping is non-refundable in the minimum
policy. Promotion/Voucher discount is not cash and Voucher is not automatically
reissued.

### Invariants

```text
sum(line order-discount allocations) = orderDiscount
sum(line voucher allocations) = voucherDiscount
finalPayable = merchandiseNet + shippingNet + tax
finalPayable >= 0
```

### Remaining ambiguity

Legal tax/e-invoice behavior beyond `tax = 0`, advanced same-layer stacking,
advanced goodwill/shipping refund, Voucher reversal/reissue and merchant-specific
promotion types remain `OPEN DECISION` or `DEFERRED`.

## 6. Change 4 — Reporting glossary

### Decision

[Reporting Glossary](REPORTING_GLOSSARY.md) is the authoritative contract for
Gross Sales, Discount, Voucher Discount, Shipping Revenue/Discount, Tax, Paid,
Voided, Refunded and Net Sales, Cancelled Orders/Amount, Completed/Paid Orders,
AOV, Branch Sales, Product Sales and Inventory Value.

Each metric identifies definition, source fact, included/excluded facts, refund
and cancellation treatment, branch attribution and time basis. Financial reports
derive from immutable `VND_V1` Order snapshots plus successful append-only
Payment/void/Refund facts; inventory quantity reports derive from StockMovement
and InventoryBalance. UI state, mutable invoice text, cached dashboard totals and
controller arithmetic are forbidden sources of truth.

Financial events are stored in UTC and grouped by `Asia/Ho_Chi_Minh` calendar
using `[from, to)`. Captures, voids and refunds remain in their own successful
event periods. Sales belong to the immutable Order responsible Branch: POS uses
Register Branch, while online reports consume the server-assigned responsible
Branch. Transfers use source/destination Location and are never sales.

Inventory monetary value is explicitly unavailable because no acquisition-cost
or valuation-method fact exists. Quantity, movement and in-transit reporting
remain supported without inventing a value from selling price.

`Product Sales` subtracts successful item `VoidAllocation` as well as successful
item `RefundAllocation`. Partial successful void amounts allocate across
remaining immutable paid item/shipping components using the `VND_V1` largest-
remainder rule, so a net metric follows its successful financial reversals.

### Invariant

```text
Net Sales = successful captures/cash
          - successful voids
          - successful refunds
```

### Remaining ambiguity

The online responsible-branch allocation algorithm and acquisition-cost/
inventory-valuation model remain `OPEN DECISION`. Reporting consumes persisted
ownership and reports no Inventory Value until those source facts exist.

## 7. Affected documents

| Document | Reason |
|---|---|
| [Project Overview](PROJECT_OVERVIEW.md) | Records the bounded v1.1.1 correction, status and unchanged gates |
| [Actors and Use Cases](ACTORS_AND_USE_CASES.md) | Aligns checkout placement with claim, normalization, hierarchy and protected revalidation |
| [Architecture](ARCHITECTURE.md) | Owns the canonical lock matrix/races, refund failure protocol, money model and reporting sources |
| [Domain Model](DOMAIN_MODEL.md) | Adds immutable monetary allocation and RefundAttempt/active-reservation semantics |
| [Business Rules](BUSINESS_RULES.md) | States enforceable lock, money, refund, promotion and reporting invariants |
| [Lifecycles](LIFECYCLES.md) | Separates logical Refund from immutable attempts and definitive/unknown outcomes |
| [Promotion Engine](PROMOTION_ENGINE.md) | Freezes first-slice stacking, rounding, snapshots and refund allocation |
| [Reporting Glossary](REPORTING_GLOSSARY.md) | New authoritative metric/source/time/branch contract |
| [ADR governance](ADR/README.md) | Advances the proposed Blueprint gate to v1.1.1 acceptance review |
| [ADR-0004](ADR/0004-location-aware-inventory.md) | Adds global hierarchy use, demand normalization and same-key retry |
| [ADR-0005](ADR/0005-idempotent-payment-refund.md) | Freezes definitive failure, unknown and retry amount semantics |
| [ADR-0007](ADR/0007-central-pricing-promotion.md) | Freezes `VND_V1`, first stack, allocation and snapshot/refund policy |
| [ADR-0010](ADR/0010-checkout-consistency-and-recovery.md) | Makes checkout/callback lock and recovery protocol explicit |
| [Security Model](SECURITY_MODEL.md) | Aligns mandatory older reset-token revocation wording |
| [ADR-0003](ADR/0003-order-centered-commerce.md) | Records confirmed-cancellation ownership and compensation boundary |
| [Blueprint v1.1 Changelog](BLUEPRINT_V1_1_CHANGELOG.md) | Clarifies that its historical backorder item is disabled for the current baseline |
| This changelog | Records traceability and acceptance state |

No Frontend, AI, modular-monolith or infrastructure decision was redesigned.

## 8. Decisions frozen by the v1.1.1 correction candidate

At correction time, “frozen” meant fixed inside the then-`PROPOSED` candidate so
later review could evaluate one meaning. The independent acceptance and final
MVP approval recorded below subsequently moved the applicable architecture ADRs
to `ACCEPTED`.

- one increasing cross-domain lock hierarchy including Fulfillment/Return and
  same-key recovery protocol;
- duplicate inventory/benefit demand normalization before locking;
- `activeRefundReserved` lifecycle and immutable RefundAttempt retry semantics;
- shared successful/pending void and Refund financial capacity;
- confirmed pre-dispatch cancellation restoration without Reservation rewrite;
- exact `VND_V1` equation, precision, rounding point/mode and zero-tax policy;
- first supported promotion/Voucher/shipping stack and deterministic tie-break;
- immutable allocation/snapshot and minimum partial-refund treatment;
- reporting metric glossary, successful-event time basis and Branch/Location
  attribution;
- no Inventory Value until authoritative cost/valuation facts exist.

## 9. Decisions intentionally still open after the correction pass

The following were intentionally left `OPEN DECISION`/`DEFERRED` by the
correction pass because none was required to close H-01 through H-04. The later
MVP revision resolved some implementation baselines; the current authoritative
gate list is in
[MVP_IMPLEMENTATION_BASELINE.md](MVP_IMPLEMENTATION_BASELINE.md#remaining-entry-gates).

- online responsible Branch/Location allocation algorithm and reservation TTL;
- provider selection, tender/cash/split-payment policy, payment grace/retry values
  and cash-refund shift ownership;
- legal tax/e-invoice policy after the initial explicit `tax = 0` model;
- advanced promotion effects/stacking, Voucher reissue/reversal and
  audience/campaign limits;
- return eligibility window/condition/approval and advanced shipping/goodwill
  refund policy;
- permission catalogue, cross-branch grants and approval thresholds;
- session/MFA/step-up and password-reset channel/TTL/rate/password policy;
- API version/public-ID/error/pagination conventions;
- AI provider, privacy/consent, evaluation and activation thresholds;
- acquisition cost and inventory valuation method;
- optional notification channels, media storage and advanced infrastructure.

## 10. Validation results

Targeted cross-domain consistency was checked for:

- Checkout: Order, Inventory, Voucher and Payment use one non-cyclic hierarchy;
- Cancellation: Order and Fulfillment serialize dispatch/cancel before Payment,
  Reservation and Inventory restoration; committed Reservation remains history;
- Refund: Order/Return evidence, RefundAttempt and financial facts remain separate
  but correlated, with amount authority under Payment;
- Financial reversal: successful void, completed Refund and active void/refund
  reservations consume one captured capacity under Payment;
- Promotion: Definition/Voucher selection becomes immutable Order allocation used
  by Refund and Reporting;
- Reporting: Order snapshots plus successful Payment/void/Refund facts reconcile
  metrics without UI authority;
- Branch: Branch owns commercial attribution, Location owns inventory/transfer
  facts, and reports keep those dimensions distinct.

Markdown headings, code fences, tables, relative links, terminology, ADR status
and required `CONFIRMED`/`PROPOSED`/`OPEN DECISION`/`LEGACY-ONLY` boundaries were
checked across affected documents. No application source, migration, dependency,
infrastructure or `QLCHGiay/` file was changed by this correction.

The acceptance-correction pass also aligned mandatory older reset-token
revocation, preserved current backorder-disabled policy, and made held-Voucher
release choose `REVOKED`, `EXPIRED` or `ISSUED` under the existing usage lock and
server Clock without resurrecting invalid benefits.

## 11. Final status

The correction agent did not approve its own work. The subsequent governance
progression was:

```text
Independent Acceptance Review: REJECT / REVISE
Correction
Independent Acceptance Re-Review: ACCEPT WITH CONDITIONS
MVP Baseline Planning
Independent Baseline Approval: APPROVE WITH CHANGES
Baseline Revision
Final MVP Baseline Approval: APPROVE
```

The baseline revision closed the final six approval items:

1. RR-01 component-level Void/Refund capacity and atomic allocation lifecycle.
2. Removal of Voucher and Return/Refund from mandatory core scope.
3. Standard servlet-session baseline with Spring Session JDBC deferred.
4. Distinct HTTP `412 If-Match` and `409` conflict semantics.
5. Client idempotency limited to retry-sensitive commands.
6. Explicit separation of Foundation work from five product vertical slices.

As of 2026-08-24, the resulting status is:

**APPROVED MVP IMPLEMENTATION BASELINE**

This approval applies to the scope and implementation conventions indexed in
[MVP_IMPLEMENTATION_BASELINE.md](MVP_IMPLEMENTATION_BASELINE.md). It does not
globally convert every `PROPOSED` business behavior to `CONFIRMED`, admit
optional/deferred capabilities, or close slice-specific `OPEN DECISION` gates.
