# ADR-0007: Centralize Pricing, Promotion, Campaign, and Voucher Evaluation

- Status: `ACCEPTED`
- Accepted: 2026-08-24
- MVP scope: Base pricing core; limited Voucher optional; advanced promotion deferred
- Date: 2026-08-23
- Decision class: Safe evaluation boundary and first-slice money policy; advanced commercial rules remain deferred
- Decision source: Blueprint v1.1.1 H-01/H-03 plus AR-06 correction

## Context

Customer Web, POS, Admin, campaigns, and AI must not calculate or grant different
prices. Promotions can target products, variants, categories, order thresholds,
shipping, branches, channels, and customer audiences. Limited vouchers also need
ownership and concurrency control.

## Decision

Use one deterministic server-side pipeline around:

- `PromotionDefinition` for eligibility/effects/priority/exclusivity/limits;
- `Campaign` for schedule, channel, audience, and delivery purpose;
- `Voucher`, `VoucherIssuance`, and `VoucherRedemption` for code/ownership/usage;
- evidence-rich `PriceQuote` for preview;
- immutable confirmed price/discount evidence on `Order`/`OrderItem`.

The initial `VND_V1` pipeline uses exact integer đồng and decimal `HALF_UP` when a
fractional đồng must be rounded. It applies at most one item automatic promotion
per line, then at most one automatic order promotion, then at most one Voucher,
then at most one shipping benefit. Within a layer, highest priority wins and a
stable definition ID breaks ties; lowest-price-wins is not a universal default.
Item effects round once per line. Order/Voucher effects round once at Order level
and allocate to eligible lines by largest fractional remainder, then stable
OrderItem ID. Components are capped at their bases and final payable is
non-negative.

```text
itemGross = sum(baseUnitPrice * quantity)
discountedItemSubtotal = itemGross - itemDiscount
merchandiseNet = discountedItemSubtotal - orderDiscount - voucherDiscount
shippingNet = shippingFee - shippingDiscount
tax = 0
finalPayable = merchandiseNet + shippingNet + tax
```

Order/Voucher parent amounts must equal their immutable line allocations exactly.
Promotion windows use `Asia/Ho_Chi_Minh` and `[startInclusive, endExclusive)`.

Preview never consumes stock or benefit. Order placement locks and revalidates
limited vouchers/usage counters, reserves the benefit atomically with order and
inventory reservation, and snapshots the result. Successful order confirmation
redeems the benefit; expiry/cancellation releases it exactly once. Refund does
not restore a voucher without an explicit policy command. Merchandise refund is
capped by stored per-line/per-unit paid allocation; partial item refund excludes
shipping, while full pre-fulfillment cancellation may refund stored shippingNet.

Placement follows the global hierarchy in `ARCHITECTURE.md`; specifically,
rank-4 Price/Promotion/Voucher versions and usage are fenced and revalidated
before rank-5 Reservation and rank-6 InventoryBalance effects. No inventory flow
may lock a Balance and then request a Voucher fence.

A valid Voucher reservation lease is honored until its hold deadline. Natural
expiry or administrative revocation blocks new holds but does not rewrite that
lease. On release, the same Issuance/usage lock yields `REVOKED` when an effective
revocation exists, otherwise `EXPIRED` when validity ended, otherwise `ISSUED`.
Idempotent replay has no second usage effect.

Campaign/AI delivery can create a draft or send an existing issued benefit, but
cannot bypass pricing validation, issue/redeem limits, consent, unsubscribe,
deduplication, or authorized activation.

## Consequences

- Controllers, clients, campaigns, and AI never calculate authoritative prices.
- The same rule evaluator supports POS and online channel/scope differences.
- Historical orders retain enough evidence to explain totals after definitions
  change.
- Advanced effect types, non-zero tax/legal invoice, multiple same-layer stacking,
  usage-limit values, audience eligibility, voucher reservation TTL, reversal,
  and approval policy remain business decisions.
- Voucher and global-limit races require database-backed concurrency tests.

## Risks and mitigations

- Rule explosion: first release supports a deliberately small confirmed matrix;
  do not build a general scripting language.
- Non-determinism: fixed normalization/order/tie-break/rounding and versioned
  definitions make results reproducible.
- Preview/checkout drift: label quotes as expiring and revalidate atomically at
  placement.
- Marketing abuse: enforce consent, preferences, audience snapshots,
  unsubscribe suppression, rate policy, and deduplication.

## Rejected alternatives

- Separate POS and online pricing implementations.
- Legacy non-overlap/best-price behavior as an unexamined requirement.
- Mutable order totals that depend on current promotion rows.
- AI/campaign code directly granting discounts.
- A speculative expression language or rules-engine dependency in Blueprint.
