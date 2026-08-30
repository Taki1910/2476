# Promotion and Voucher Architecture — Blueprint v1.1.1

## Status

Central deterministic evaluation and historical snapshots are `CONFIRMED`
governance. The v1.1.1 monetary/stacking correction is accepted architecture if
the capability is implemented. Voucher is not part of core MVP Stage 3: the
core quote uses base price without Voucher. Limited Voucher is `SHOULD /
OPTIONAL` and requires separate admission; advanced rule types remain
deferred/open scope. See
[MVP_IMPLEMENTATION_BASELINE.md](MVP_IMPLEMENTATION_BASELINE.md).

## Ownership model

| Concept | Owns | Does not own |
|---|---|---|
| PromotionDefinition | Eligibility, effect, priority, exclusivity, scope, time and usage policy | Customer delivery or historical Order price |
| Campaign | Business/marketing grouping, schedule, audience rule and content references | Direct discount mutation |
| Voucher | Redeemable definition/code behavior and limits | Customer ownership by itself |
| VoucherIssuance | Voucher grant/visibility ownership for one customer/audience result | Redemption |
| VoucherRedemption | Reservation/consumption/reversal evidence tied to checkout/order | Promotion calculation |
| PriceQuote | Deterministic evaluated evidence with versions/expiry | Permanent entitlement |
| Order | Immutable confirmed applied adjustment snapshots | Editable PromotionDefinition |

## Supported rule vocabulary

Architecture supports product, variant, category, order-subtotal threshold,
percentage/fixed order discount, free/discounted shipping, voucher,
customer-specific voucher, campaign, usage/per-customer limits, time windows,
channel, branch/location scope and customer eligibility.

Support in the architecture does not mean all rule types are implemented in the
capstone. One approved subset must be frozen before work.

## Deterministic evaluation pipeline

```text
1. Resolve server-owned customer/channel/Branch/Location and one placement Clock
2. Resolve VND base unit prices and select active `[start, end)` candidates
3. Filter item candidates by variant/product/category and scope/eligibility
4. Choose at most one item promotion per line by priority then stable ID
5. Calculate/round each line item discount; sum discounted item subtotal
6. Choose/calculate at most one automatic order promotion on that subtotal
7. Allocate rounded order discount to eligible lines by largest remainder
8. Validate/choose at most one Voucher on the post-order-discount base; allocate it
9. Resolve shipping fee and at most one free/discount-shipping benefit
10. Set initial tax to zero; validate caps/non-negative components/equation
11. Produce versioned PriceQuote/Order allocation evidence and final payable
```

The first supported stacking matrix allows one selected benefit from each layer:
item automatic, order automatic, Voucher and shipping. These layers stack in
that order. Candidates within a layer are mutually exclusive; tie-break is
`priority descending`, then stable PromotionDefinition/Voucher ID ascending.
“Lowest price wins” is not a rule. More flexible exclusive groups or multiple
same-layer stacking are `DEFERRED` and require a later policy/version.

### VND_V1 rounding and allocation

- Store/calculate exact integer đồng; never use binary floating point.
- Percentage discount exact totals use decimal arithmetic and `HALF_UP` once to
  one đồng at their line or Order component.
- Item promotion rounds once per OrderItem line, not per displayed unit.
- Order and Voucher totals round once at Order level. Allocate proportionally to
  eligible line bases: floor exact shares, distribute remaining đồng by largest
  fractional remainder, then stable OrderItem ID ascending.
- If partial quantity needs per-unit allocations, apply the same method within
  the line. Stored allocations always sum exactly to their rounded parent.
- Every discount is capped at its base; shipping discount is capped at shipping
  fee; final payable cannot be negative.
- Promotion windows use `Asia/Ho_Chi_Minh` business time, are persisted/compared
  as instants, and apply on `[startInclusive, endExclusive)`.

Initial tax is deliberately `0`. Legal tax/e-invoice requirements remain open;
a taxed model requires a new calculation-policy version before taxed Orders.

## Quote and checkout authority

- Cart display quote is time-bound and advisory.
- Order placement re-evaluates authoritative prices and all promotion/voucher
  rules at one server Clock instant while rank-4 definition/usage records are
  fenced, before rank-5 Reservation and rank-6 Balance effects commit in the
  same transaction.
- Successful placement snapshots the price/promotion calculation for that
  pending Order; it is honored until the approved payment deadline.
- Promotion/price changes after placement do not alter that Order.
- If placement revalidation differs from the customer-visible quote, checkout
  returns a price-change response and requires explicit retry/acceptance; it
  does not silently charge a new total.
- Voucher is `RESERVED`, not redeemed, at placement. Confirmation redeems it;
  failure/expiry releases it.

## Voucher distribution

### Public surfaces

Homepage/product/checkout may display an eligible Voucher/Promotion offer. A
display is not issuance or guaranteed eligibility; checkout revalidates.

### Customer delivery

Email/in-app notification may either surface a public offer or deliver a
customer-owned VoucherIssuance. Creation is deduplicated by
`(campaign, customer, voucher)` and delivery by
`(campaign, recipient, channel, purpose, templateVersion)`.

### Product-follow targeting

ProductFollow is optional demonstration scope. If enabled:

1. Campaign selects active, consented followers and snapshots the audience.
2. Issuance is created once per eligible customer.
3. Unfollow/consent withdrawal before send suppresses unsent marketing delivery.
4. Existing issuance remains historical/owned unless explicit revocation policy
   applies; future campaigns exclude the customer.

No live follow query repeatedly sends the campaign.

## Concurrency and abuse rules

- Issuance uniqueness prevents duplicate grants.
- Limited usage is reserved/redemeed under locked definition/issuance usage
  records or an atomic guarded counter.
- Same checkout idempotency key cannot reserve twice.
- Expiry/revocation and redemption serialize on the same Issuance/usage record.
- A valid reservation lease is honored until its hold deadline. Natural expiry
  or administrative revocation blocks new reservations but does not rewrite the
  active lease. Confirmation before the deadline may redeem it. Release locks the
  same Issuance/usage record and returns `REVOKED` when an effective revocation
  exists, otherwise `EXPIRED` when validity ended, otherwise `ISSUED`; replay has
  no second usage effect.
- Customer-specific issuance requires authenticated owner match; codes alone do
  not transfer ownership.
- Public codes require rate limiting and safe error messages.
- Cancellation after redemption follows an explicit reversal policy; reversal
  never happens simply because a refund exists.

## PriceQuote and historical evidence

PriceQuote/Order snapshots include:

- VND currency, `VND_V1` calculation/rounding version, placement Clock and quote
  expiry;
- original unit prices and quantities;
- each candidate/applied adjustment identity/version/type/scope/amount/reason;
- Voucher/Issuance evidence when used;
- item gross, item discount, discounted subtotal, allocated order and Voucher
  discounts, final merchandise line/per-unit paid amount;
- shipping fee/discount, `tax = 0`, merchandise net and final payable;
- exact per-line allocation and rounding remainder evidence required for
  return/refund/report reconciliation.

Admin edits never alter this snapshot. Historical refund/report calculation uses
the stored applied definitions and allocated amounts, not current rules.

## Minimum refund allocation policy

At placement, each eligible OrderItem receives immutable allocations for item,
automatic order and Voucher discounts. The remaining line merchandise amount is
the maximum financial value attributable to that line before prior refunds:

```text
lineRefundableBase = lineGross
                   - lineItemDiscount
                   - allocatedOrderDiscount
                   - allocatedVoucherDiscount
```

For example, Item A is 600,000 VND, Item B is 400,000 VND and a 100,000 VND
Order Voucher is the only discount. Proportional largest-remainder allocation is
60,000 and 40,000; Item A's maximum item refund is 540,000 VND, subject also to
remaining captured/refundable value and already-refunded quantity.

- Partial quantity uses the stored per-unit allocation; no current promotion is
  recalculated.
- Prior successful item `VoidAllocation` consumes the same stored paid
  allocation and reduces the remaining item refund maximum; pending/unknown void
  capacity is fenced under Payment before Refund reservation.
- Partial item return does not refund shipping.
- Full cancellation before fulfillment/service may refund stored
  `shippingNet = shippingFee - shippingDiscount`.
- After successful fulfillment, shipping is non-refundable in the minimum
  policy. Advanced goodwill/carrier/merchant shipping-refund policy is `DEFERRED`.
- Promotion/Voucher discount has no cash value. Refund returns only what the
  customer paid for the allocated item/service component.
- Refund never automatically restores or reissues a Voucher. A future explicit
  benefit-reversal policy is `DEFERRED`.
- `tax = 0`, so no tax allocation/refund exists in `VND_V1`.
- Sum of successful refunds and active refund reservations remains capped by
  successful captured value under the Payment lock.

## Security and AI

- Promotion/campaign/voucher management requires explicit merchandising
  permission and branch/global scope.
- Activation, issuance, redemption, reversal and override are audited.
- AI may propose PromotionDefinition/Campaign content in Draft only.
- AI cannot activate, issue, redeem, set unbounded discounts or bypass this
  pipeline.

## Frozen initial calculation slice

- One variant/product percentage or fixed promotion.
- One order-threshold promotion.
- One customer-specific single-use voucher.
- At most one item benefit per line, one automatic order benefit, one
  customer-specific Voucher and one shipping benefit; layers stack in the
  documented sequence.
- One branch/channel scope and time window.
- Concurrent redemption and quote-change tests.

Free shipping, category campaigns, follower targeting and richer distribution
are demonstration/optional slices, not core checkout prerequisites.

## Open decisions

1. Quote and voucher hold TTL values.
2. Explicit Voucher reissue/reversal policy beyond the frozen no-automatic-
   restoration rule.
3. Public code format, attempt limits and transferability.
4. Whether ProductFollow/campaign email is in the capstone demonstration.
5. Advanced same-layer stacking/exclusive groups and rule types beyond the
   initial calculation slice.
6. Legal tax/e-invoice behavior and a future taxed calculation-policy version.
