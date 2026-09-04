# ADR-0026: Atomic multi-item online commerce

- Status: `ACCEPTED` for the explicitly requested Phase 15B implementation
- Date: 2026-08-31
- Extends: ADR-0019, ADR-0021–0025
- Supersedes: the one-item/one-reservation restrictions of earlier online slices,
  and the one-movement-per-order restriction of ADR-0023. POS remains unchanged.

## Purpose, actors and boundary

Customers must buy several variants in one order and one payment. Pricing,
Inventory, Order, Payment and Fulfillment retain their existing ownership. This
is a modular-monolith evolution, not a frontend basket of independent orders.
Delivery, split-location fulfillment, partial payment/void and AI are excluded.

## Order and inventory invariants

- An Order owns one or more immutable OrderItems. Duplicate variant demands are
  merged before validation; the resulting quantity must be 1–10 per variant.
  Requests are bounded to 50 input lines as an API resource limit.
- Each online OrderItem has its own unique adopted Reservation and immutable
  variant, location, quantity, SKU, size, color and price-version/amount evidence.
  One reservation never represents several variants. POS retains one item and
  no reservation.
- This pickup release allocates the complete order at one enabled Location in
  one enabled Branch. The first common eligible Location by internal ID wins.
  If no common location can satisfy all lines, reject the whole checkout with
  `NO_COMMON_PICKUP_LOCATION`; do not split fulfillment or silently split orders.
  This preserves the existing one-order/one-location PickupFulfillment contract.
- All lines share one server-created hold deadline (`now + reservation-ttl`).
  Quote expiry and reservation expiry remain independent.
- Order total is the checked sum of checked integer-VND line subtotals and must
  not exceed 9,007,199,254,740,991. Client money is never accepted.
- The new cart quote also rejects totals above the existing VNPAY adapter's
  9,999,999,999 VND ceiling, so this single-provider checkout cannot create an
  order its only payment route cannot pay. The narrower provider bound does not
  replace integer safety or rewrite legacy prices/orders.

## Quote and public contracts

Choose a cart-level quote with N immutable quote lines. The old PriceQuote is
retained for existing single-item consumers and regression contracts; it is not
overloaded with inconsistent multi-item semantics.

`POST /api/v1/storefront/cart-quotes` accepts `{items:[{variantId,quantity}]}`.
It requires current `CATALOG_BROWSE`, validates/normalizes all lines, resolves
effective published price versions, checks quantity availability/common pickup
location, and appends one quote with a 15-minute server TTL. It reserves no stock.
The response contains `id`, `quotedAt`, `expiresAt`, `currency`, `totalAmount`
and `items` (variant/product name, SKU/size/color, price-version ID, quantity,
unit price and subtotal). A valid quote continues to honor its immutable price
even if the catalog price later changes, as in ADR-0019.

`POST /api/v1/orders/cart-checkout` accepts `{quoteId,items:[{variantId,quantity}]}`
and `Idempotency-Key`; it requires current `ORDER_PLACE` and `CHECKOUT_RESERVE`.
Normalized demand must exactly match the owned quote. The server revalidates
TTL, publication, common enabled location and locked availability, then creates
all reservations and exactly one pending order in one transaction. Any invalid
line, stock conflict, audit failure or persistence failure rolls everything back.
No payment/provider call occurs inside checkout.

Errors use the existing ProblemDetail contract. Line conflicts may additionally
carry `variantId` (never internal inventory quantities or SQL details), allowing
VI/EN copy to name the affected cart line. Expired/consumed/foreign quotes and
conflicting keys remain explicit errors. There is no client-selected location,
price, owner or deadline.

Order history/detail add `items`, `itemCount`, `quantity` (total units) and the
authoritative `totalAmount`. Historical single-line scalar fields remain for
compatibility, but variant/price/reservation scalar fields are null for a
multi-item order, never misleading first-line substitutes. Pickup queue/detail
return one task per order with all item summaries. No broad workspace redesign.

## Idempotency and locks

Normalize by variant UUID, merge duplicates, sort canonical UUID strings. The
fingerprint includes the quote identity and every normalized variant/quantity.
Equivalent input ordering is equivalent; changed quantity, variant or quote
under the same key is a conflict. The existing customer account-row command
fence and `(owner,key)` unique index are reused. A quote creates at most one
order, with a filtered unique cart-quote index as the final backstop. Replay
returns the existing order even after its quote TTL has elapsed.

Expiry normalization happens before acquiring inventory locks. Checkout locks
candidate balances in ascending `(variant internal ID, location internal ID)`
order, rechecks all demand, selects the first common location and only then
mutates. Candidate locations are not locked in frontend array order.

Existing-order commands retain the hierarchy:

```text
command fence -> Order -> Fulfillment (if needed) -> Payment/attempt/void
              -> ALL Reservations -> ALL InventoryBalances
```

Within Reservation rank use canonical reservation UUID order. Within Balance
rank use `(variant internal ID, location internal ID)`. Batch methods acquire
every reservation before any balance, and every required balance before changes.
Never loop a singleton reservation/balance operation for each line: that would
acquire the next Reservation after holding a Balance. No existing Order is
acquired after checkout takes Balance locks. SQL deadlock/timeout aborts the
entire transaction; the client retries the same key rather than a new purchase.
Financial replay correlation reads IDs without child write locks, then acquires
Order -> Payment -> VoidOperation/Attempt. A financial child is not a rank-zero
command fence; locking it before Order would invert the result-handler order.
Pickup handover uses the employee account as its rank-zero key fence, resolves
any replay by scalar ID, then acquires Order before Fulfillment even on replay.
Correlation reads must be separate single-table statements in short independent
read transactions. Even a read-only JOIN from child to Order can retain the
child's shared lock while waiting on Order and deadlock with a parent-first
writer; `REQUIRES_NEW` around that JOIN alone does not prevent the inversion.
Expiry normalization uses one short transaction per expired order, so cleanup
never acquires the next Order while retaining the previous order's Balance locks.

## Payment, expiry, cancellation and pickup

- One Payment/attempt covers the sum of all immutable item snapshots. Before
  initiation every hold is eligible; the provider deadline is bounded by their
  common deadline. Verified success commits every hold and marks the complete
  order paid atomically. Ineligible/late success remains `REVIEW_REQUIRED` with
  zero normal sales recognition; it never partially commits or reopens stock.
- Unpaid cancellation/expiry releases every adopted hold and cancels the order
  once. Discovery through any expired line normalizes the complete order.
- Paid pre-handover cancellation reserves full financial exposure under Payment,
  using one ORDER_ITEM VoidAllocation per line, each bounded by that line's
  captured subtotal. All allocations sum exactly to the full void attempt.
  Then every committed reservation becomes CANCELLED_RESTORED, reserved falls
  by each quantity, onHand is unchanged, and one movement per reservation is
  appended. Provider submission remains after local commit; retry never restores
  inventory again. Existing unknown/failure/capacity semantics remain unchanged.
- Pickup requires every line committed at the shared location. Handover consumes
  every line and appends one movement per reservation atomically. The existing
  Order/Fulfillment terminal fence arbitrates handover versus cancellation.

## Reporting

Order/payment/cash totals are counted once using order-level location filtering,
not a multiplying item join. Product gross uses each item's unit-price snapshot
times quantity, conditional on authoritative successful payment/tender. Successful
void allocations reduce only their referenced OrderItem. Reconciliation has one
entry per financial fact; REVIEW_REQUIRED retains zero recognized net effect.
Released reversal exceptions retain the existing per-allocation granularity;
unknown/review operations are one exception per operation, not per joined item.
For identical scope/time, product net sums to order-level net.

## Migration and compatibility

V18 appends cart quote/line tables, order cart-quote/fingerprint columns, and item
reservation/version/color columns; it backfills existing item provenance from
their old order headers. Replace unique `commerce_order_item.order_id` with
unique `(order_id,variant_public_id)`, add reservation uniqueness/FKs and quote
uniqueness, and replace pickup/cancellation movement uniqueness with per-reservation
uniqueness while preserving one POS movement per order. Legacy header references
remain only for old single-item provenance; cart orders do not populate them.
V1–V17 are not edited. Hibernate remains `ddl-auto=validate`.

## Customer behavior and acceptance

The local persisted cart is a list of display-metadata lines. Duplicate add
increments quantity; navigation/login retain it; count means total units.
Server validation is an explicit review step. Changed price or unavailable lines
are named; quote totals come from the server; a separate confirmation creates
the order. Editing demand invalidates the quote/key. An uncertain checkout retry
retains the same payload/key. My Orders and detail show every purchased line.

Required evidence: fresh SQL Server migration/validate and mandatory suites;
valid V17 upgrade; 2/3-line success; duplicate normalization; atomic rollback;
expiry; exact snapshots/totals; replay/conflict; opposite-order concurrent carts;
payment/late success; paid cancellation/restore/void; pickup; reporting; backend
ownership; unchanged single checkout/POS/security; real Vue + Spring + SQL browser
purchase/negative flows and VI/EN at 1440/1024/768/390. No fixture substitutes.
