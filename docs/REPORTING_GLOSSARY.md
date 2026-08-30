# Reporting Glossary — Blueprint v1.1.1

> Architecture status: **ACCEPTED FOR THE APPROVED MVP BASELINE**

## Purpose and authority

This document is the authoritative definition of Blueprint reporting metrics.
Reports are read models only. They derive from immutable domain facts and never
become transaction/write authority.

Core MVP implements only basic reconciliation reporting. Voucher, delivery, and
Return/Refund metric definitions apply only if those optional/deferred slices
are later admitted.

Authoritative facts:

- Order/OrderItem `VND_V1` placement snapshots and adjustment allocations;
- successful append-only PaymentTransaction capture/void/cash-tender facts and
  immutable `VoidAllocation` components;
- successful Refund financial facts and RefundAllocation components;
- Fulfillment/Return quantity and handover/receipt facts;
- StockMovement plus current InventoryBalance for inventory quantities;
- immutable Order responsible Branch and physical Location references.

Forbidden sources include Vue/UI state, mutable invoice/receipt text, localized
status labels, cached dashboard totals, controller-created arithmetic and current
Promotion/Price definitions applied retroactively.

## Common monetary and time semantics

- Currency/calculation policy: `VND_V1`, exact integer đồng, no binary floating
  point.
- Initial tax: `0`; legal tax reporting is unavailable until a later approved
  calculation-policy version exists.
- Event instants are stored in UTC. Calendar/day grouping converts them to
  `Asia/Ho_Chi_Minh` and uses half-open intervals `[from, to)`.
- Report responses expose `generatedAt`, source `asOf`, projection freshness and
  applied Branch/Location/time filters.
- A later refund does not rewrite the original capture period. Capture, void and
  refund appear on their own successful financial-event dates.
- Sales/refunds attribute to immutable Order `responsibleBranchId`: POS derives
  it from Register; online persists the result of the still-open allocation
  policy. Fulfillment Location does not take sales credit.
- Inventory and transfer metrics use Location. A transfer is never sales: dispatch
  belongs to source Location/time; receipt belongs to destination Location/time.

## Snapshot component glossary

```text
itemGross              = sum(baseUnitPrice * quantity)
discount               = itemDiscount + automaticOrderDiscount
voucherDiscount        = selected Voucher allocation total
merchandiseNet         = itemGross - discount - voucherDiscount
shippingRevenue        = shippingFee - shippingDiscount
tax                    = 0
orderFinalPayable       = merchandiseNet + shippingRevenue + tax
```

Order-level and Voucher totals equal the sum of their immutable OrderItem
allocations. Shipping remains a separate service component.

### Successful-void allocation

A successful financial void reverses previously successful captured exposure;
an authorization-only cancellation is a distinct fact and has no captured-value
allocation. Before provider work, the reversal attempt stores positive,
immutable component allocations against OrderItem `lineRefundableBase`,
`shippingNet`, and tax where applicable. Their sum equals the attempt reserved
amount.

The MVP's successful full capture or cash settlement establishes
`capturedComponentCapacity(c)` from immutable Order paid component amount `c`.
Partial/split capture is `DEFERRED`. Active Void/Refund allocations consume
remaining component and aggregate capacity under the Payment lock. Unknown
provider outcome leaves allocations `ACTIVE`; atomic success marks every attempt
allocation `SUCCEEDED`, and definitive failure marks every attempt allocation
`RELEASED`. Reports consume only `SUCCEEDED` allocations and never reconstruct
them from callbacks or current Order definitions.

## Metric definitions

| Name | Definition | Source Fact | Included States/Facts | Excluded States/Facts | Void/Refund Treatment | Cancellation Treatment | Branch Attribution | Time Basis |
|---|---|---|---|---|---|---|---|---|
| Gross Sales | Sum of `itemGross` snapshots for Orders when they first achieve valid financial confirmation | Order/OrderItem snapshot joined to confirmation supported by successful capture/cash or the explicit zero-payable path | Financially confirmed Orders once, including zero-payable Orders | Pending/failed/expired without confirmation; shipping/tax | Original gross is not rewritten; refunds are reported separately | Unconfirmed cancellation excluded; confirmed-then-cancelled remains historical gross with void/refund facts | Order responsible Branch | First valid financial confirmation instant |
| Discount | Sum of snapshotted item-promotion plus automatic order-promotion allocations; excludes Voucher and shipping discount | OrderItem adjustment snapshots | Financially confirmed Orders | Unapplied candidates, Voucher, shipping discount, current Promotion definitions | Not recomputed; refunded merchandise uses stored net allocation | Same recognition as Gross Sales | Order responsible Branch | Financial confirmation/capture instant |
| Voucher Discount | Sum of immutable Voucher discount allocations on financially confirmed Orders | VoucherRedemption evidence + OrderItem Voucher allocations | Redeemed Voucher on confirmed Order | Displayed/issued/reserved but unredeemed Voucher | Never paid as cash; line refund uses paid amount after allocation; no automatic Voucher reissue | Failed/unpaid Order contributes zero; later cancellation does not rewrite snapshot | Order responsible Branch | Financial confirmation/redemption instant |
| Shipping Revenue | `shippingFee - shippingDiscount` recognized for a financially confirmed online Order | Order shipping snapshot | Confirmed Orders with positive `shippingNet` | POS/no-shipping and unpaid Orders | A successful shipping VoidAllocation or RefundAllocation reduces Net Sales on its reversal event; partial item refund does not refund shipping | Full pre-fulfillment cancellation may reverse shippingNet; original recognition remains with void/refund separately | Order responsible Branch; Fulfillment Location is a separate dimension | Financial confirmation/capture instant |
| Shipping Discount | Snapshotted shipping benefit, capped at shipping fee | Order shipping adjustment snapshot | Confirmed Order with selected shipping benefit | Candidate/ineligible benefit | Has no cash value; refund is limited to shipping amount actually paid | Unpaid cancellation contributes zero | Order responsible Branch | Financial confirmation instant |
| Tax | `0` for every `VND_V1` Order | Order tax snapshot/calculation-policy version | All initial Orders with explicit zero | Any invented/controller tax | No tax refund exists in VND_V1 | No tax cancellation amount | Order responsible Branch | Order financial-confirmation instant; no separate tax event exists in `VND_V1` |
| Paid Amount | Sum successful captured/cash-tender amount minus successful void amount; before successful refunds | Append-only PaymentTransaction/tender facts | `CAPTURE_SUCCEEDED`, accepted cash tender, `VOID_SUCCEEDED` as subtraction | Authorized/pending/failed/unknown attempts | Refund does not alter Paid Amount; it appears in Refunded Amount | Unpaid cancellation zero; successful void subtracts on void time | Original Order responsible Branch | Capture/cash/void transaction instant |
| Voided Amount | Sum successful financial void transactions | PaymentTransaction `VOID_SUCCEEDED` + immutable VoidAllocation | Successful void facts whose target is captured exposure | Authorization-only cancellation and requested/pending/failed/unknown financial void | Consumes the same captured/component capacity as Refund | Common after cancellation before settlement | Original Order responsible Branch | Successful void instant |
| Refunded Amount | Sum successful Refund financial transactions, allocated to item and optional shipping components | Refund + successful RefundAttempt/PaymentTransaction + RefundAllocation | Successful refund facts only | Requested/approved/processing/unknown/definitively failed attempts | Is the refund measure; never exceeds captured value with active reservations considered | Cancellation affects this only when its compensation refund succeeds | Original Order responsible Branch | Successful provider/cash refund instant |
| Net Sales | `Paid Amount - Refunded Amount` for the selected event interval; Paid Amount already subtracts successful voids | Payment/void/refund financial facts | Successful financial events in interval | Order status text and pending financial work | Refund reduces Net Sales on refund-success date, including refund of earlier-period capture | Unpaid cancellation zero; paid cancellation affects Net Sales through successful void/refund only | Original Order responsible Branch | Each capture/void/refund event instant, grouped independently |
| Cancelled Orders | Count distinct Orders when they first enter `CANCELLED` or `EXPIRED`; expose whether any successful capture existed so unpaid cancellation is a zero-paid cohort | Order cancellation/expiry transition + Payment financial facts | Each cancelled/expired Order once | Active or completed non-cancelled Orders | Later Refund does not change the count; captured cancellations retain the captured/refunded dimensions | This is the cancellation count; its unpaid cohort contributes zero Paid Amount and Net Sales | Order responsible Branch | Cancellation/expiry transition instant |
| Cancelled Amount | Operational demand value: sum snapshotted `finalPayable` for Orders entering `CANCELLED` or `EXPIRED`; not revenue | Order snapshot + cancellation/expiry transition fact | Cancelled/expired Orders | Active/confirmed non-cancelled Orders | Refund does not change this operational metric | This is the cancellation metric; never added to sales | Order responsible Branch | Cancellation/expiry transition instant |
| Completed Orders | Count distinct Orders whose selected fulfillment completes (`HANDED_OVER`, `DELIVERED`, or POS immediate handover) | Fulfillment/POS handover facts + Order | Confirmed and physically completed Orders | Pending/picking/shipped/failed/cancelled before handover | Later Return/Refund does not erase historical completion; separate metrics show them | Cancelled before completion excluded | Order responsible Branch; Fulfillment Location available as dimension | Handover/delivery completion instant |
| Paid Orders | Count distinct Orders when they first achieve valid financial confirmation | Order confirmation supported by successful capture/cash or the explicit zero-payable path | Each financially confirmed Order once, including zero-payable confirmation as a separately flagged order | Pending/failed/unknown/unpaid cancelled Orders | Later refund does not erase historical paid count | Paid-then-cancelled remains historically paid; financial reversal is separate | Order responsible Branch | Financial confirmation instant |
| Average Order Value | Sum snapshotted `finalPayable` for Orders first paid in the interval divided by Paid Orders in that interval; `NULL` when count is zero | Order snapshot + financial confirmation fact | Same cohort as Paid Orders | Refund events from any period and unpaid Orders | Later refunds do not restate AOV; Net Sales reports their effect | Confirmed-then-cancelled remains in the historical confirmation cohort | Order responsible Branch | First financial confirmation instant |
| Branch Sales | Net Sales grouped by immutable Order responsible Branch | Payment/void/refund facts + Order branch | Same facts as Net Sales | Fulfillment branch as sales owner; client-selected branch | Refund subtracts from original sales Branch on refund-success date | Same as Net Sales | POS Register Branch; online server-assigned responsible Branch | Financial event instant |
| Product Sales | Net merchandise financial value by ProductVariant: confirmed `lineRefundableBase` minus successful item VoidAllocations and successful item RefundAllocations; report confirmed units and returned units separately | OrderItem monetary snapshot + financial confirmation + immutable item VoidAllocation/RefundAllocation + Return quantities | Financially confirmed OrderItems, including zero-payable lines; successful item void/refund allocations as subtraction | Shipping/tax, current catalog price/promotion, pending/unknown reversals | Subtract successful item VoidAllocation on void time and successful item RefundAllocation on refund time; never recompute discount | Unconfirmed cancelled items excluded; confirmed cancellation reduces the metric through successful allocated void/refund facts | Order responsible Branch; ProductVariant dimension | Confirmation, successful item-void and successful item-refund event instants |
| Inventory Value | Not supported in Blueprint v1.1.1 because no acquisition-cost or valuation-method fact exists | None | None | Selling price multiplied by on-hand, UI price, estimated supplier cost | Not applicable | Not applicable | Inventory quantities use Location, not sales Branch | Deferred until an approved cost/valuation model exists |

## Inventory quantity reporting

Although monetary Inventory Value is deferred, the following remain valid:

- current on-hand, reserved and derived available by ProductVariant/Location as
  an `asOf` balance;
- received, adjusted, sold/committed, returned, transfer-dispatched and transfer-
  received quantities from immutable StockMovement event time;
- in-transit quantity by Transfer source/destination and dispatch/receipt state.

Inventory quantity reports do not infer stock from Order or Invoice state.

## Reconciliation identities

For any filter range and Branch set:

```text
Net Sales = successful captures/cash
          - successful voids
          - successful refunds
```

For one Order over its full history:

```text
orderFinalPayable = merchandiseNet + shippingRevenue + tax
successfulVoided + successfulRefunded
  + activeVoidReserved + activeRefundReserved <= successfulCaptured
remainingRefundable = successfulCaptured
                    - successfulVoided
                    - activeVoidReserved
                    - successfulRefunded
                    - activeRefundReserved
```

For each paid component `c`:

```text
successfulVoid(c) + successfulRefund(c)
  + activeVoid(c) + activeRefund(c) <= capturedComponentCapacity(c)
remainingComponentCapacity(c) = capturedComponentCapacity(c)
                              - successfulVoid(c)
                              - successfulRefund(c)
                              - activeVoid(c)
                              - activeRefund(c)
```

Gross Sales/Discount/Voucher/Shipping describe the immutable commercial
snapshot; Payment/Void/Refund facts describe money movement. They are connected
by Order and allocation IDs but must not be conflated.

## Open decisions retained

- Online allocation policy still decides the responsible Branch before Order
  placement; reporting only consumes the persisted result.
- Legal tax/e-invoice metrics require a later calculation-policy version.
- Advanced shipping-refund/goodwill policy is deferred.
- Acquisition cost and inventory valuation method are open; Inventory Value
  remains unavailable rather than guessed.
- Separate analytics infrastructure remains unnecessary unless SQL reporting is
  later measured insufficient.
