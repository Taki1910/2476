# Actors and Use Cases — Blueprint v1.1.1

> Architecture status: **ACCEPTED FOR THE APPROVED MVP BASELINE**
>
> Requirement labels: `CONFIRMED`, `PROPOSED`, `OPEN DECISION`,
> `LEGACY-ONLY`, `DEFERRED`.

## Actor catalogue

| Actor | Responsibility | Enforced scope | Status |
|---|---|---|---|
| Guest Customer | Browse and possibly checkout without an account | Public catalog; guest checkout policy remains open | `PROPOSED` |
| Registered Customer | Manage profile, cart, orders, returns, follows, and notifications | Own records only | `CONFIRMED` concept |
| Cashier | Operate POS sales and permitted returns | Assigned branch, register, and active shift | `PROPOSED` role |
| Store Manager | Operate a branch and approve governed local exceptions | Assigned branch(es) | `PROPOSED` role |
| Inventory Staff | Receive, count, adjust, and transfer stock | Assigned locations and permitted transfer routes | `PROPOSED` role |
| Fulfillment Staff | Pick, pack, hand over, and ship | Assigned fulfillment locations | `PROPOSED` role |
| Customer Support | Assist customers without implicit financial authority | Customer/order access granted by use case and organization policy | `PROPOSED` role |
| Finance/Refund Approver | Reconcile payments and approve governed refunds | Explicit organizational/branch scope and thresholds | `PROPOSED` role |
| Catalog/Merchandising Staff | Manage catalog, pricing, promotions, campaigns, and content | Granted catalogue/campaign scope | `PROPOSED` role |
| Administrator | Manage identities, grants, and configuration | Explicit grants; no hidden superuser bypass | `CONFIRMED` concept |
| Payment Provider | Reports payment/refund outcomes | Authenticated integration identity and unique provider event | `PROPOSED` integration actor |
| Carrier/Fulfillment Provider | Reports delivery events | Authenticated integration identity | `PROPOSED` integration actor |
| Scheduled Worker | Expires holds and runs reconciliation/notification jobs | Least-privileged service identity | `PROPOSED` system actor |
| AI Provider/Model | Returns structured recommendations or drafts | No transactional or publication authority | `CONFIRMED` boundary |

Role names are provisional. Authorization is based on stable permissions plus
server-resolved scope, never UI visibility, localized labels, or a client-sent
branch alone.

## Approved MVP actor baseline

Implementation-facing MVP actors are Registered Customer, Cashier, Back Office
Operations Staff, Administrator, and the Payment Provider integration identity.

Store Manager, Inventory Staff, Catalog/Merchandising Staff, and Fulfillment
Staff remain conceptual actors. The MVP may demonstrate those responsibilities
through one Operations role with explicit individual permissions; this does not
collapse permission boundaries. Administrator authority derives from explicit
grants and has no hidden unrestricted bypass. Provider authority is limited to
authenticated payment-event application.

Customer Web is core, POS is cash-only and limited, and Back Office is limited
to identity/scope setup, Branch/Location, catalog/base price, Inventory,
pickup/cancellation operations, and basic reconciliation reporting. The exact
scope is authoritative in
[MVP_IMPLEMENTATION_BASELINE.md](MVP_IMPLEMENTATION_BASELINE.md).

## Scope rules shared by staff use cases

- A `Branch` is the commercial owner of an order and staff operation.
- A `Location` is the physical stock node. A shared warehouse may serve several
  branches only through explicit grants/routes.
- A `POSRegister` belongs to exactly one branch and one sales-floor location.
- A `CashierShift` links one employee, register, opening/closing facts, and cash
  accountability; a login session is not a shift.
- The server resolves allowed branch/location scope from assignments and the
  current use case. Cross-branch access is denied unless explicitly granted.
- Customer profiles are central. Staff access to customer data is use-case and
  permission limited, not automatically granted by branch membership.

## Blueprint use-case catalogue

This catalogue preserves accepted architecture for future capabilities. Only
the subset identified as core in the approved MVP baseline is an active
implementation commitment. Limited Voucher and password reset are optional;
guest, delivery, Return/Refund, scheduling infrastructure, advanced promotion,
and AI use cases are deferred.

### Identity and access

- Authenticate, logout, list/revoke sessions, and respond to account disable or
  credential change.
- Create/disable an account and link it to an employee or customer profile.
- Assign/revoke role, permission, branch, and location grants.
- Request and complete password reset through an expiring, single-use token.
- Record and review security events subject to audit-read permission.

### Catalog, pricing, and merchandising

- Create/update/archive Product and ProductVariant while preserving an immutable
  SKU identity once operationally referenced.
- Manage category, color, material, canonical size data, media, and publication.
- Define base prices and request a non-authoritative preview `PriceQuote`.
- Define promotion/campaign/voucher rules; preview, activate, pause, expire, and
  inspect usage according to permission.
- Create product-follow audiences and deliver deduplicated eligible campaigns
  while respecting unsubscribe and communication policy.

### Branch, register, and inventory

- Manage Branch, Location, Warehouse-as-location, POSRegister, staff assignments,
  and explicit shared-location access.
- Receive, reserve, release, commit, adjust, transfer, return, and reconcile stock.
- Query on-hand, reserved, available, and in-transit stock by variant/location.
- Open and close a cashier shift and reconcile cash/tenders (`PROPOSED`).

### Shared order placement

POS and online channels share one server-side order-placement use case:

1. Claim/resolve the CheckoutAttempt by scoped idempotency key and request
   fingerprint before any business effect.
2. Resolve channel, responsible branch, allocation location, customer, and scope
   on the server; normalize requested quantity by `(location, variant)` and
   offered benefit identity.
3. In one local transaction, acquire/insert business records only in the global
   order defined by `ARCHITECTURE.md`: Order, Fulfillment/Return when involved,
   Payment if present, pricing/limited Benefit/Voucher, Reservation,
   InventoryBalance, then CashierShift if present.
4. While the protected rows/versions are fenced, revalidate prices, promotion,
   voucher ownership/limits/time, shift state, and aggregate stock demand.
5. Atomically persist `Order`/`OrderItem` snapshots, active stock reservations,
   voucher reservation where applicable, and `CheckoutAttempt` state.
6. Return the original result for an identical retry; reject key reuse with a
   different fingerprint.

The order is `PENDING_PAYMENT` after placement when payment is required. Cart
contents and preview quotes are not stock, price, or voucher guarantees.

### POS commerce

- Start a POS basket under an active register/shift; optionally identify a
  customer; scan/add items; request a quote.
- For cash, execute Payment confirmation, Order confirmation, Reservation
  commit, and CashierShift entry in one database transaction. Voucher redemption
  joins only if the optional Voucher slice is admitted.
- Electronic POS tender is `DEFERRED`. If later admitted, create a durable pending
  attempt, call the provider outside the database transaction, and apply
  authenticated callbacks idempotently using the online confirmation invariants.
- Cancel or expire an unpaid sale using a conditional terminal transition that
  releases stock and voucher holds exactly once.
- Print/reprint a receipt from stored order/payment facts. Legal invoice policy
  remains an `OPEN DECISION`.

### Online commerce

- Browse/search catalog and view a channel-appropriate availability promise.
- Maintain cart, choose delivery or pickup, supply address/pickup details, apply
  voucher, and preview checkout.
- Place an order through the shared atomic placement use case.
- Initiate provider payment only after the pending attempt is durable; process
  callbacks idempotently; show `processing` when the result is not authoritative.
- Pick/pack/ship/deliver or prepare/hand over pickup independently of payment.
- Cancel when policy permits; request return/refund and receive notifications.

If successful payment arrives after expiry/cancellation, the order is not
silently reopened. Vertical Slice 5 durably rejects the event and returns a
conflict; reconciliation and a governed void/refund path remain future scope.

### Promotion and voucher

- Define deterministic eligibility, effect, priority, exclusivity, limits,
  channel/branch scope, and validity windows.
- Issue general or customer-specific vouchers through a campaign.
- Preview a quote without consuming benefits.
- Reserve limited benefits during placement, redeem only on successful order
  confirmation, and release once on failure/expiry/cancellation.
- Reverse a redemption only through an explicit approved policy and auditable
  command; a refund does not automatically restore a voucher.

### Fulfillment, returns, and refunds

- Create one `PENDING` pickup fulfillment for an eligible paid Order at its
  server-derived Location; require `FULFILL_PICKUP` and exact active Location
  scope, preserve paid/reservation/inventory facts, and audit atomically.
- Start pickup preparation exactly once with `PENDING -> PICKING` under the same
  current permission and exact-Location scope; record the actor in audit, not as
  picker ownership, and preserve every commercial/inventory fact.
- Allocate fulfillment to one location per order line in the first release.
- Progress delivery or pickup through explicit state transitions.
- Request, authorize/reject, receive, inspect, and disposition returned items.
- Create an idempotent full/partial refund against eligible successful financial
  transactions.
- Reserve the pending refund amount so concurrent refunds cannot exceed the
  captured amount; reconcile asynchronous provider outcomes.
- Record physical return, restocking, and financial refund as independent facts.
- For an allowed confirmed pre-dispatch cancellation, lock Order then
  Fulfillment before later financial/inventory ranks, cancel Fulfillment and
  append one idempotent `CANCELLATION_RESTORE` stock movement per OrderItem.
  Dispatch winner forbids direct restoration; post-dispatch stock uses Return.
- Successful void and Refund consume the same captured financial capacity under
  Payment; a pending/unknown void fences overlapping refund capacity.

### Reporting, notification, audit, content, and AI

- Read reports with explicit sales-branch, stock-location, lifecycle, and timezone
  semantics; do not infer revenue from mutable order totals alone.
- Queue and deliver transactional notifications from committed state changes.
- Review immutable audit events within granted scope.
- Request AI recommendations or drafts; validate, preview, approve, and publish
  only through normal deterministic use cases.

## Use-case definition gate

Before implementation, every use case must document:

1. business purpose and requirement status;
2. actors, permission, ownership, and branch/location scope;
3. input, preconditions, and idempotency behavior;
4. aggregate owners and authoritative state changes;
5. database transaction and lock boundary;
6. external calls and durable recovery/reconciliation path;
7. side effects, audit, and notification expectations;
8. failure behavior and acceptance tests.

## Open decisions

- Guest checkout and account-history merge policy.
- Final role/permission catalogue, cross-branch grants, monetary/quantity
  thresholds, and maker-checker rules.
- Cash drawer variance and shift approval policy; supported tenders and split
  payment scope.
- Branch assignment and stock allocation policy for delivery and pickup orders.
- Cancellation windows, payment timeout, return eligibility, inspection,
  restocking, refund approval, and voucher reversal rules.
- Legal receipt/invoice requirements, tax calculation, rounding, and timezone
  cutoffs.
- Customer-support visibility and masking of sensitive data.
