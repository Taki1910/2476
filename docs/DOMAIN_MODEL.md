# Domain Model — Blueprint v1.1.1

## Status

Ownership rules inherited from governance are `CONFIRMED`; the v1.1.1
architecture is accepted for the
[approved MVP implementation baseline](MVP_IMPLEMENTATION_BASELINE.md).
Slice-specific business policy remains `OPEN DECISION`. Names are conceptual,
not table/class designs.

## Context map

```text
Identity & Access --authorizes--> Application Use Cases
Branch / Location --scopes------> Employee, Register, Inventory, Order, Reports
Catalog ----------identifies----> Pricing, Inventory, OrderItem, Promotion
Customer ---------owns----------> Cart, Follow/Consent; places Order
Pricing/Promotion -quotes-------> CheckoutAttempt
Inventory --------holds/commits-> CheckoutAttempt / Order
Order ------------requests------> Payment and Fulfillment
Fulfillment -------enables------> Return
Payment -----------enables------> Refund
Return ------------may produce-> Inventory return movement
Audit/Notification/Reporting <-- durable facts/intents from application use cases
AI/Content --------proposes-----> validated recommendations/configuration
```

CheckoutAttempt is an application-process record. It coordinates aggregate
commands and recovery; it does not acquire ownership of Inventory, Payment,
Voucher, or Order state.

## Domain boundaries

| Boundary | Aggregate roots / concepts | Ownership |
|---|---|---|
| Identity & Access | UserAccount, Role, Permission, Session, PasswordResetToken | Credentials, grants, revocation, authorization version |
| Customer | CustomerProfile, Address, Consent, ProductFollow | Commerce profile and optional engagement intent, not credentials |
| Employee | EmployeeProfile, BranchAssignment | Workforce state and scoped assignments, not credentials |
| Branch / Location | Branch, Location, LocationAccessGrant, POSRegister, CashierShift | Commercial/physical scope and register operation |
| Catalog | Product, ProductVariant, Category, Color, Material, SizeDefinition, Media | Stable catalog/SKU identity; never stock |
| Pricing | Price, PriceQuote | Effective price inputs and deterministic quote evidence |
| Promotion/Voucher | PromotionDefinition, Campaign, Voucher, VoucherIssuance, VoucherRedemption | Eligibility, distribution, usage and discount evidence |
| Cart | Cart, CartItem | Mutable customer intent |
| Checkout | CheckoutAttempt | Idempotent orchestration/recovery identity only |
| Order | Order, OrderItem, AddressSnapshot, PriceSnapshot | Commercial commitment and immutable confirmed facts |
| Inventory | InventoryBalance, Reservation, StockMovement, Transfer | Location quantity and movement history |
| Payment | Payment, PaymentAttempt, PaymentTransaction, VoidAllocation, ProviderEvent | Financial attempts, captured/reversed capacity and provider truth |
| Fulfillment | Fulfillment, Shipment | Pick/pack/pickup/delivery quantities and evidence |
| Return | ReturnRequest, ReturnItem, Inspection | Physical return eligibility, receipt and disposition |
| Refund | Refund, RefundAttempt, RefundAllocation | Financial reversal against eligible captures |
| Notification | NotificationIntent, Delivery, Preference, Template | Recipient/channel delivery and dedupe |
| Audit | AuditEvent | Immutable redacted business/security history |
| Reporting | SQL projections/read models | Derived facts only; no transactional ownership |
| AI/Recommendation | AnalysisRequest, Recommendation, Draft | Non-authoritative proposals |
| Content | PageConfiguration, Publication | Validated configuration and publish history |

## Branch and location model

### Branch

A Branch is a commercial responsibility/sales unit. Every Order has exactly one
`responsibleBranchId`, assigned by the server:

- POS: derived from POSRegister.
- Online: selected by the approved allocation/fulfillment policy, never blindly
  accepted from the client.

### Location

A Location is a stock-holding physical node with a type such as `SALES_FLOOR`
or `WAREHOUSE`. A location may be owned by one Branch or centrally/shared.
Shared locations require explicit LocationAccessGrant and central/warehouse
permissions. Location is the inventory key; Branch is not a stock balance.

### POSRegister and CashierShift

- POSRegister belongs to exactly one Branch and one sales-floor Location.
- CashierShift binds one cashier, register, branch and time window.
- Cash tender entries belong to the active shift.
- `CONFIRMED`: only one `OPEN` shift per register and one per cashier, protected
  by SQL Server filtered uniqueness.
- The implemented limited slice records immutable exact `CashTender` entries;
  expected cash is their successful sum. Opening float, counted cash, variance,
  refunds and cash adjustments are deferred.
- Register authorization is derived from the cashier's active exact-Location
  assignment. Explicit cashier-to-register assignment is deferred.

### Scope ownership

| Data/action | Authoritative scope |
|---|---|
| POS order | Register Branch; source Location is Register sales floor |
| Online order | Server-assigned responsible Branch; selected fulfillment Location |
| Inventory | Location plus LocationAccessGrant |
| Fulfillment | Fulfillment Location and Order responsible Branch |
| Return intake | Authorized return Location; original order remains branch-owned |
| Customer profile | Central customer ownership; staff sees only fields required for authorized use case/order |
| Promotion | Explicit `GLOBAL`, channel, branch-set and/or location-set scope |
| Sales report | Order responsible Branch; fulfillment and inventory reports remain location-attributed |

## Product and ProductVariant

- Product owns common name, description, brand/category association and media.
- ProductVariant owns immutable SKU, Product link, sellable size/color/material
  combination, barcode if confirmed, and lifecycle status.
- A variant with inventory/order history is archived, never repurposed to a new
  option combination.
- `PROPOSED`: SKU is globally unique and immutable.
- `PROPOSED`: SizeDefinition stores canonical value plus size system/region;
  display labels and future conversion are separate.
- `PROPOSED`: the active sellable option combination is unique within Product
  and size-system context; archived history cannot be repurposed.
- Price is not owned by ProductVariant. Pricing references Variant plus optional
  channel/branch scope and effective interval.
- `OPEN DECISION`: required shoe size systems, barcode/scanner scope, and
  branch/channel price variation.

## Inventory aggregate

### InventoryBalance

Identity: `(productVariantId, locationId)`.

```text
onHand >= 0
reserved >= 0
reserved <= onHand
available = onHand - reserved
```

`available` is derived, not independently writable. Balance has a version and
is locked during quantity-changing commands.

### Reservation

Reservation owns variant, location, quantity, owner CheckoutAttempt/Order,
expiry, state, and version. One first-release order line allocates from one
Location. Split allocation is `DEFERRED`.

### StockMovement

StockMovement is immutable evidence with movement type, variant, location,
signed quantity, business reference, operation key, actor/source and timestamp.
A unique `(movementType, businessReference, lineReference)` prevents duplicate
physical effects.

### Transfer

Transfer owns source, destination, lines and in-transit state. Dispatch reduces
source on-hand; in-transit quantity is not available at either location;
receipt increases destination on-hand. Source/destination balances are locked
in canonical key order.

## Cart, checkout and order

### Cart

Cart is mutable and may contain stale prices/availability. It never reserves
stock or consumes vouchers by itself. CheckoutAttempt snapshots requested cart
lines and authoritative customer/channel context.

### CheckoutAttempt

CheckoutAttempt owns:

- public operation ID and client idempotency key;
- authenticated actor/customer, channel and server-derived scope;
- request fingerprint;
- quote/order/payment/reservation/voucher references;
- hold deadline, state and last failure/reconciliation reason.

Same idempotency key plus same fingerprint returns the prior result. Same key
plus different fingerprint is a conflict.

### PriceQuote

PriceQuote contains authoritative calculation evidence and expiry/config
versions. It is advisory until revalidated at Order placement. At successful
placement its amounts/promotion evidence become immutable Order snapshots.

### Order

Order owns responsible Branch, channel, customer/address snapshot when
applicable, currency, totals and immutable OrderItems/adjustments. Payment,
Reservation and Fulfillment statuses are not collapsed into one Order status.

The limited POS command creates the shared Order directly as `channel=POS` and
`PAID`, with no customer owner or Reservation. Its single OrderItem snapshots
the current server PriceVersion, SKU, size, quantity one and exact VND amount.
The linked `PosCashSale` owns Shift-scoped idempotency and the variant
fingerprint; `CashTender` is the financial fact and no PaymentAttempt is made.

### Monetary snapshot

The initial model uses VND integer đồng and calculation policy version
`VND_V1`. Order/OrderItem snapshots preserve base unit price, quantity, item
gross, selected promotion identities/versions, item discount, allocated automatic
order discount, allocated Voucher discount, final merchandise line amount,
shipping fee/discount, `tax = 0`, final payable and per-unit refund allocations.

```text
discountedItemSubtotal = itemGross - itemDiscount
voucherBase            = discountedItemSubtotal - orderDiscount
merchandiseNet         = voucherBase - voucherDiscount
shippingNet            = shippingFee - shippingDiscount
finalPayable           = merchandiseNet + shippingNet + tax
```

Order-level and Voucher discount totals are rounded once and allocated to
eligible lines by the largest-remainder rule defined in `PROMOTION_ENGINE.md`.
Every allocation sums exactly to its Order component. Current Price/Promotion
rows are never used to recalculate historical refunds or reports.

## Payment and Refund

### Payment

Payment belongs to one Order and one currency. It owns attempts and derives
authorized/captured/refunded totals from successful append-only transactions.

### PaymentAttempt

PaymentAttempt owns provider/tender, idempotency key, request fingerprint,
amount, status, provider reference and correlation to CheckoutAttempt.

The implemented Vertical Slice 5 subset stores immutable Order amount/currency
and supports terminal `SUCCEEDED`, `FAILED`, and `CANCELLED` from `PENDING`.
Provider/tender selection and retry remain deferred.

### PaymentTransaction and ProviderEvent

PaymentTransaction records `AUTHORIZE`, `CAPTURE`, `VOID` or provider-equivalent
financial facts with amount/status/reference. ProviderEvent is persisted once
by unique provider/event ID before legal transition processing. Out-of-order or
contradictory terminal events move the attempt to reconciliation rather than
rewriting history.

The implemented `PaymentProviderEvent` receipt is scoped by provider account and
opaque case-sensitive event ID. It stores the exact attempt/outcome fingerprint,
received/applied timestamps, and applied/rejected result snapshot. For this
bounded slice it is the durable trusted result fact; a separate
PaymentTransaction/capture model and reconciliation workflow remain deferred.

A successful financial `VOID` is an immutable reversal of previously successful
captured exposure. An authorization-only cancellation does not reduce captured
exposure and is recorded as that distinct provider fact. Successful void and
Refund allocations consume the same captured financial capacity. A pending or
unknown void fences its target amount under the existing Payment lock so an
overlapping Refund cannot reserve it before the void becomes successful or
definitively fails.

### Refund

Refund owns amount, currency, target captured transaction(s), optional Return
reference, reason, approval, idempotency key and state. Physical Return is
optional only when an approved refund-without-return policy permits it.

Refund creation atomically reserves refundable amount under a Payment lock. Its
amount is based on remaining immutable OrderItem/shipping allocations and the
eligible captured amount after successful/active void exposure, never current
promotion definitions.

### ReversalAttempt, component allocation, and active reservation

RefundAttempt is immutable provider-operation evidence with attempt sequence,
provider/idempotency reference, amount, status and timestamps. A failed attempt
is never changed into a retry; retry creates a new attempt under the same Refund.
Void and Refund attempts allocate their reserved amount to immutable paid Order
components. Component identity and allocation amount never change.

```text
successfulCaptured >= 0
successfulVoided >= 0
successfulRefunded >= 0
activeVoidReserved >= 0
activeRefundReserved >= 0
successfulVoided + successfulRefunded
  + activeVoidReserved + activeRefundReserved <= successfulCaptured
remainingRefundable = successfulCaptured
                    - successfulVoided
                    - activeVoidReserved
                    - successfulRefunded
                    - activeRefundReserved
```

For every component `c` and active attempt:

```text
successfulVoid(c) + successfulRefund(c)
  + activeVoid(c) + activeRefund(c) <= capturedComponentCapacity(c)
remainingComponentCapacity(c) = capturedComponentCapacity(c)
                              - successfulVoid(c)
                              - successfulRefund(c)
                              - activeVoid(c)
                              - activeRefund(c)
sum(component allocations) = attempt reserved amount
allocation.amount > 0
```

The MVP has one full capture or full cash settlement, so component capacity is
the immutable Order paid component amount only after settlement succeeds.
Partial/split capture is `DEFERRED`; future support requires immutable successful
capture-component allocation evidence first.

Timeout/unknown retains the active reservation. Success converts the same amount
from active to successful. A contractually definitive failure releases the
attempt's reservation exactly once under the Payment lock. A retry first
reacquires capacity under that lock; an older attempt event cannot release a
newer's reservation.

Void and Refund attempts follow the same capacity rule. A successful void moves
its amount from `activeVoidReserved` to `successfulVoided`; a definitive failure
releases it once; an unknown result retains it and blocks overlapping reversal.
Attempt allocations move atomically from `ACTIVE` to `SUCCEEDED` or `RELEASED`
under the Payment lock. Unknown provider outcome keeps them `ACTIVE`; partial
conversion or release is forbidden. Provider events name the exact attempt
generation. Reports consume only `SUCCEEDED` allocations and do not reconstruct
component evidence. Physical persistence design remains an `OPEN DECISION` for
the online checkout/payment slice.

## Fulfillment and Return

- The current bounded `PickupFulfillment` is a separate aggregate linked one-to-one
  to Order. It snapshots the Order responsible Branch and exact committed-reservation
  Location, begins in `PENDING`, and has its own version. Creation leaves the paid
  Order, committed Reservation, and InventoryBalance unchanged.
- Starting pickup preparation moves only `PickupFulfillment` from `PENDING` to
  `PICKING` and records immutable `pickingStartedAt`; the audit actor identifies
  who performed the transition without creating picker ownership domain state.
- POS immediate handover creates the same Fulfillment aggregate directly in
  `HANDED_OVER`, attributed to the Register Location. It does not require a
  Reservation or Shipment aggregate and does not mutate inventory a second time.
- Online Fulfillment owns allocated/picked/packed/handed-over/delivered
  quantities per OrderItem.
- Return eligibility uses authoritative delivered/handed-over quantity minus
  prior accepted/received return quantities under a versioned transaction.
- Inspection determines restock/quarantine/damaged disposition. Only an
  approved disposition creates an Inventory return movement.
- Refund success never directly changes stock.

### Confirmed cancellation and stock restoration

The cancellation application use case coordinates owners; Order owns the
commercial transition, Fulfillment owns the dispatch/handover fence, and
Inventory owns stock restoration.

- Unconfirmed expiry releases an `ACTIVE` Reservation and never creates a stock-
  restoration movement because on-hand was not committed.
- If business policy allows cancellation after confirmation but before dispatch/
  handover, one local transaction locks Order, Fulfillment, Payment when needed,
  Reservation and InventoryBalance in the global order. It conditionally moves
  Order to `CANCELLED`, cancels the still-cancellable Fulfillment, leaves on-hand
  unchanged, decrements reserved at the original allocation Location, and appends one
  immutable `CANCELLATION_RESTORE` StockMovement per Order plus required
  AuditEvent.
- The Reservation becomes `CANCELLED_RESTORED` while its `committedAt` remains
  immutable historical evidence; it is never recreated or changed back to `ACTIVE`. Unique
  `(CANCELLATION_RESTORE, orderId)` operation identity prevents a
  duplicate restoration.
- Dispatch and cancellation both lock Order then Fulfillment. Exactly one may
  leave the cancellable Fulfillment state. If dispatch wins, direct cancellation
  cannot restore stock and compensation follows return-to-sender/Return policy.
- After dispatch/handover, stock can return only through the Return inspection/
  disposition flow and its idempotent Inventory return movement.

External financial void/refund occurs only from durable intent after the local
cancellation transaction commits. When captured exposure exists, that transaction
creates the selected void/refund intent and fences its amount under Payment.
Provider failure leaves the Order operationally `CANCELLED` with a separate
failed/unknown/review financial state; it never repeats inventory restoration.

## Customer engagement, notification and campaign targeting

ProductFollow is optional `DEMONSTRATION VALUE`, not essential commerce state.
If enabled, Campaign snapshots eligible customers at issuance/send preparation.
Unfollow/consent withdrawal suppresses unsent marketing delivery but does not
silently delete historical voucher issuance. Unique campaign-recipient-purpose
keys prevent duplicate issuance/delivery.

## Audit integrity

AuditEvent is append-only and records actor type (`HUMAN`, `SERVICE`,
`INTEGRATION`, or `SYSTEM`), actor reference where applicable, action, resource
type/public reference, branch/location scope, correlation/idempotency reference,
timestamp, result and redacted structured change/reason fields. It never stores
credentials, reset tokens, secrets, raw payment credentials, or unnecessary AI/
personal data.

Required audit creation participates in the privileged command transaction; a
failure to persist it fails that command. The application role cannot update or
delete audit history, parent relationships never cascade-delete it, and retention
or archival requires a separately approved policy and privileged operation.

## Cross-boundary consistency rules

- Cross-domain commands are coordinated by application use cases using stable
  IDs; aggregates are never modified through another domain's repository.
- Same-database steps that form one business decision use one local transaction.
- Transactions touching multiple business owners follow the canonical upward
  lock hierarchy in `ARCHITECTURE.md`, including Fulfillment/Return before
  Payment and Inventory; physical Return restock and financial Refund remain
  separate commands so neither reverses that order.
- External provider calls are outside database transactions and therefore use
  durable pending/reconciliation state plus idempotent callbacks.
- Audit and required NotificationIntent are inserted transactionally with the
  originating command when loss would violate the use case.
- Read models can join domains but never become a write source of truth.

## Deferred model complexity

Multi-location split allocation, multi-currency, generic product-option DSL,
multi-provider routing, chargebacks, exchanges, loyalty/gift cards and full
marketing automation remain deferred unless explicitly promoted.
# Customer storefront read model and PriceQuote

```text
StorefrontProduct (read model)
  -> published ProductVariant
  -> effective VariantPrice version
  -> customer-safe Inventory availability

VariantPrice (versioned)
  - publicId
  - variantId
  - amount (exact integer VND)
  - validFrom
  - validTo (nullable only for the current version)

PriceQuote (immutable evidence)
  - publicId
  - ownerAccountId
  - priceVersionId
  - amount / currency
  - quotedAt / expiresAt
```

Catalog, Inventory, and Pricing retain ownership. The storefront projection joins their read data but is not a new source of truth. PriceQuote belongs to Pricing and carries no Inventory Reservation, Order, or Payment state.

## Implemented quote checkout evidence

Vertical Slice 3 coordinates existing owners without moving their data:

```text
PriceQuote (Pricing, immutable)
  -> InventoryReservation (Inventory, ADOPTED, Location + independent expiresAt)
  -> Order / OrderItem (Order, PENDING_PAYMENT)
```

`PriceQuote.expiresAt` is the checkout-validation deadline. A successful
checkout creates the Reservation at authoritative server time and assigns
`InventoryReservation.expiresAt = reservationCreatedAt + configured
Reservation TTL`; later quote expiry does not expire that hold.

The single OrderItem snapshots variant public identity, SKU, canonical size,
quantity one, exact quoted VND unit price and total. Order also records the
PriceQuote and price-version public identities, owning customer, responsible
Branch, reservation, creation time, and scoped idempotency key. Catalog and
current Pricing facts never recalculate the historical Order.
