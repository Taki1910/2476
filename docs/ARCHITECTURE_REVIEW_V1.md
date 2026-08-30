# Architecture Review V1

- Review target: Blueprint v1 (`PROPOSED FOR REVIEW`)
- Review date: 2026-08-23
- Reviewer posture: independent adversarial architecture review
- Scope: governance, all Blueprint documents, ADRs, and legacy reuse baseline
- Implementation reviewed: none exists

## 1. Executive summary

Blueprint v1 has the right strategic direction: modular monolith, order-centered
commerce, explicit location inventory, idempotent finance, server-side scoped
authorization, REST/SPA separation, and a safe AI boundary. Those decisions
should survive review.

It is not ready to become implementation source of truth. The documents name
the right domains but do not yet define the cross-domain consistency protocol
that decides what happens when inventory, payment, cancellation, voucher, and
fulfillment operations race or partially succeed. Several proposed lifecycles
also mix independent state dimensions, and branch/location ownership is too
ambiguous to enforce isolation correctly.

The most serious governance problem is that the overall Blueprint is proposed
while all nine ADRs are marked `Accepted`. Some ADR decisions are directly
confirmed by `AGENTS.md`; others add unreviewed topology or implementation
detail. Because approved ADRs outrank the Blueprint documents, this can promote
proposals into authoritative decisions without review.

Recommendation: retain the foundations, correct the ADR statuses, resolve the
six blockers below, narrow the capstone delivery scope, and publish Blueprint
v1.1 before implementation planning.

## 2. Overall assessment

| Area | Assessment |
|---|---|
| Strategic architecture | Strong |
| Domain ownership direction | Strong but incomplete at boundaries |
| Inventory correctness | Correct invariant; incomplete operation protocol |
| Financial correctness | Correct intent; incomplete ledger and race handling |
| Security direction | Strong; revocation/scope mechanics incomplete |
| Promotion/voucher | Good decomposition; checkout concurrency unresolved |
| AI safety | Strong and appropriately conservative |
| API/frontend separation | Strong principles; contracts intentionally absent |
| Reporting/notification | Under-specified |
| Capstone feasibility | At risk without explicit scope tiers |
| Governance consistency | Defective due to accepted ADR/proposed Blueprint conflict |

The Blueprint is architecture-shaped rather than implementation-shaped, which
is appropriate at this stage. The defect is not missing classes or tables; it
is missing decisions at transaction and lifecycle seams.

## 3. BLOCKER findings

### B-01 — Checkout has no authoritative cross-domain consistency protocol

- **Problem:** Order, Inventory, Payment, Pricing/Promotion, and Voucher have
  separate ownership, but no document defines the checkout coordinator,
  ordering of durable writes, retry boundary, or compensation/reconciliation
  rules.
- **Why it matters:** A modular monolith avoids distributed infrastructure, but
  it does not automatically solve partial success across aggregates and
  asynchronous payment providers. The system needs one explicit protocol.
- **Concrete failure scenario:** Online checkout reserves the final unit and
  creates a pending payment. The provider captures money, the client times out,
  and the callback arrives later. A retry creates another attempt while the
  reservation expires. The current documents do not say whether the order is
  confirmed, inventory is re-reserved, payment is refunded, or staff must
  reconcile it.
- **Affected domains/documents:** Order, Inventory, Payment, Promotion/Voucher;
  `DOMAIN_MODEL.md`, `LIFECYCLES.md`, `ARCHITECTURE.md`, ADR-0003/0004/0005/0007.
- **Recommended resolution:** Define a `Checkout` application protocol with a
  durable operation/correlation identity. Specify separate POS and online
  sequences, commit point, same-database atomic steps, asynchronous steps,
  legal replays, timeout recovery, and reconciliation states. Use SQL
  transactions and idempotent records first; no broker is required.
- **ADR required:** Yes — checkout transaction/orchestration semantics cross
  several domain boundaries.
- **Blueprint must change:** Yes.

### B-02 — Inventory reservation semantics are not sufficient for concurrency

- **Problem:** `available = onHand - reserved` is correct, but allocation,
  reservation identity, line-level quantities, expiry fencing, partial commit,
  and balance-update locking are undefined.
- **Why it matters:** The formula alone cannot prevent lost updates or a stale
  expiry worker from releasing a reservation currently being committed.
- **Concrete failure scenario:** Two customers simultaneously reserve the last
  unit at one location. Both read `available = 1`; without one conditional
  update/locked balance row, both reservations succeed. In another race, the
  expiry worker releases reservation R while checkout commits R, causing
  `reserved` to be decremented twice or on-hand to be committed without a valid
  reservation.
- **Affected domains/documents:** Inventory, Order, Branch/Location;
  `BUSINESS_RULES.md`, `DOMAIN_MODEL.md`, `LIFECYCLES.md`, ADR-0004.
- **Recommended resolution:** Freeze a minimum inventory protocol: balance key
  `(variant, location)`, terminal reservation transitions, unique operation
  keys, atomic conditional reserve, lock/version strategy, idempotent
  release/commit, expiry comparison using expected status/version, movement
  identity, and failure/retry rules. Decide whether one order line can allocate
  across locations; defer split allocation for the capstone if unnecessary.
- **ADR required:** Extend ADR-0004 or add a focused inventory transaction ADR.
- **Blueprint must change:** Yes.

### B-03 — Financial state is missing an atomic accounting model

- **Problem:** Payment attempts and provider transactions are named, but there
  is no definition of amounts, currency, authorization/capture totals,
  remaining refundable amount, refund allocation locking, cash tender, or the
  invariant connecting Order payable total to financial transactions.
- **Why it matters:** Status enums and idempotency keys do not prevent double
  refunds, excess capture, or a paid order with incompatible totals.
- **Concrete failure scenario:** Two staff members concurrently request partial
  refunds of 70% against the same captured transaction. Each reads 100%
  refundable and both pass validation, producing 140% refunded. A second case:
  cancellation marks an order cancelled while a delayed capture webhook marks
  payment captured, leaving money taken for a cancelled order with no required
  void/refund workflow.
- **Affected domains/documents:** Payment, Refund, Order, POSRegister;
  `BUSINESS_RULES.md`, `DOMAIN_MODEL.md`, `LIFECYCLES.md`, ADR-0005.
- **Recommended resolution:** Define an append-only financial transaction
  model and atomic aggregate invariants: currency consistency, captured amount
  not above authorized/payable policy, refunded amount not above captured minus
  prior successful/pending reserved refunds, unique provider events, one
  idempotent result per command key, and explicit reconciliation/unknown state.
  Define cash as a tender recorded against a cashier shift, not a fake provider
  payment.
- **ADR required:** Yes — extend ADR-0005 and add POS cash reconciliation only
  if cash is confirmed.
- **Blueprint must change:** Yes.

### B-04 — Branch, sales location, fulfillment location, and stock location are ambiguous

- **Problem:** Order “references branch”; POSRegister optionally references a
  sales-floor location; warehouses may serve multiple branches; allocation may
  use locations. The authoritative ownership/scope of an order is not defined.
- **Why it matters:** Authorization, stock selection, reporting, returns, and
  fulfillment can each derive a different branch and silently leak data or move
  stock incorrectly.
- **Concrete failure scenario:** An employee assigned to Branch A submits
  `branchId=B` and `locationId` for Warehouse W. If the application trusts one
  request field or checks only the order branch, the employee can reserve B/W
  stock, see another branch's order, or post a return into the wrong location.
- **Affected domains/documents:** Branch/Location, Identity & Access, Order,
  Inventory, Fulfillment, Reporting, Promotion; `DOMAIN_MODEL.md`,
  `SECURITY_MODEL.md`, `ACTORS_AND_USE_CASES.md`, ADR-0006.
- **Recommended resolution:** Define authoritative scope derivation per use
  case: selling branch, register, source/allocation location(s), fulfillment
  location, return location, and reporting attribution. Branch/location IDs
  must be resolved from authorized assignments and server-owned relationships,
  not accepted as unverified client scope.
- **ADR required:** Yes — extend ADR-0006 or create a multi-branch ownership ADR.
- **Blueprint must change:** Yes.

### B-05 — Core lifecycles are not complete enough to plan implementation

- **Problem:** Exact states are correctly marked proposed, but critical
  lifecycles combine payment, order, and fulfillment concerns and omit
  reconciliation/exception transitions.
- **Why it matters:** State ownership determines which aggregate may accept a
  command and which side effects are legal. Ambiguous state produces circular
  updates and impossible recovery.
- **Concrete failure scenario:** A return request and delivery-complete callback
  race. The Return lifecycle checks an order item while Fulfillment changes
  delivery state. No transition fence defines whether the request is rejected,
  deferred, or becomes eligible. Similarly, `PENDING_PAYMENT -> CONFIRMED`
  assumes payment success, which does not fit COD.
- **Affected domains/documents:** Order, Payment, Fulfillment, Return, Refund,
  POS shift/register; `LIFECYCLES.md`, `BUSINESS_RULES.md`.
- **Recommended resolution:** Model independent state dimensions and their
  coupling rules: Order commercial state, Payment state, Fulfillment state,
  Reservation state, Return state, Refund state, and CashierShift state. Add
  transition matrices with actor, precondition, atomic fence, side effects,
  failure, and idempotent replay. Freeze only states required by the selected
  capstone flows.
- **ADR required:** Yes for lifecycle/consistency semantics; individual labels
  may remain in lifecycle documentation.
- **Blueprint must change:** Yes.

### B-06 — ADR acceptance status violates the review/source-of-truth model

- **Problem:** Blueprint v1 is `PROPOSED FOR REVIEW`, but ADR-0001 through
  ADR-0009 are all `Accepted`. Approved ADRs outrank Blueprint documents. Some
  ADR text is directly confirmed governance; other text adds unapproved choices
  such as topology and frontend workspace details.
- **Why it matters:** A later agent must obey accepted ADRs even if this review
  rejects part of Blueprint v1. This silently promotes proposals and makes
  review ineffective.
- **Concrete failure scenario:** An implementation agent treats “one SQL Server
  database initially” or the exact Vue workspace shape as irrevocably approved
  because the ADR is accepted, despite the overall Blueprint awaiting review.
- **Affected domains/documents:** All; `PROJECT_OVERVIEW.md`, `ARCHITECTURE.md`,
  every ADR, source-of-truth hierarchy in `AGENTS.md`.
- **Recommended resolution:** Mark ADRs `Proposed` pending Blueprint v1.1
  approval, or split each ADR into governance-confirmed decisions and proposed
  consequences. After review, explicitly accept only the frozen decisions
  listed in section 19.
- **ADR required:** No new ADR; correct ADR governance/status metadata.
- **Blueprint must change:** Yes.

## 4. HIGH findings

### H-01 — Quote, promotion, and voucher consumption has a TOCTOU gap

- **Problem:** The pipeline promises checkout revalidation and atomic
  redemption but does not define whether quote expiry, promotion edits, voucher
  expiry, and inventory reservation share one decision boundary.
- **Why it matters:** A customer can see one price, pay another, or consume a
  voucher without obtaining an order.
- **Concrete failure scenario:** A voucher has one use left. Two checkouts both
  quote it; the voucher expires between quote and confirmation, or both redeem
  concurrently. One payment may already be authorized before eligibility is
  rejected.
- **Affected domains/documents:** Pricing, Promotion/Voucher, Order, Payment;
  `PROMOTION_ENGINE.md`, `LIFECYCLES.md`, ADR-0007.
- **Recommended resolution:** Define quote as advisory until a single checkout
  command revalidates authoritative prices/eligibility and atomically reserves
  or redeems limited usage. Specify price-change UX, quote expiry, voucher
  reservation/release, and payment ordering.
- **ADR required:** Extend ADR-0007 and checkout ADR.
- **Blueprint must change:** Yes.

### H-02 — Disabled employee/session revocation is only an intention

- **Problem:** Security requires revocation, but does not define how each
  request detects disabled accounts, changed permissions, expired branch
  assignments, or closed shifts.
- **Why it matters:** A previously valid session can retain authority after the
  employee is disabled.
- **Concrete failure scenario:** A manager disables a cashier, but the cashier's
  existing cookie remains valid and can still sell, refund, or adjust inventory
  until natural session expiry.
- **Affected domains/documents:** Identity & Access, Employee, Branch, POS;
  `SECURITY_MODEL.md`, `LIFECYCLES.md`, ADR-0006.
- **Recommended resolution:** Define session/authorization version or
  server-side session record, request-time account/assignment validation for
  sensitive commands, revocation propagation expectations, and shift closure
  behavior. Keep Redis out unless deployment proves it necessary.
- **ADR required:** Extend ADR-0006.
- **Blueprint must change:** Yes.

### H-03 — POS cash/register/shift accounting is absent from the financial model

- **Problem:** CashierShift and tender reconciliation are proposed use cases,
  but no aggregate, lifecycle, balance invariant, or authorization model exists.
- **Why it matters:** POS cannot explain cash expected versus cash counted, who
  owned the drawer, or how refunds affect the till.
- **Concrete failure scenario:** Two cashiers use one register concurrently;
  one cash refund occurs after the first shift closes. Revenue and cash drawer
  variance cannot be assigned correctly.
- **Affected domains/documents:** POSRegister, Employee, Order, Payment,
  Reporting; `ACTORS_AND_USE_CASES.md`, `LIFECYCLES.md`, `DOMAIN_MODEL.md`.
- **Recommended resolution:** If cash POS is essential, define Register and
  CashierShift ownership, one-open-shift policy, opening float, cash sale/refund
  tender entries, expected balance, closing count, variance, and manager
  override. If not essential, explicitly scope v1 to simulated electronic
  tender rather than leaving cash implied.
- **ADR required:** Yes if cash is in the capstone scope.
- **Blueprint must change:** Yes.

### H-04 — Return eligibility is not protected against fulfillment races

- **Problem:** Return references OrderItem while Fulfillment independently
  changes handover/delivery state; no atomic eligibility snapshot/fence exists.
- **Why it matters:** A return can be accepted for an item not delivered or a
  cancellation can race with dispatch.
- **Concrete failure scenario:** Customer requests return while staff marks the
  parcel handed over. Both operations validate old state and succeed, creating
  a return and continuing shipment with contradictory instructions.
- **Affected domains/documents:** Order, Fulfillment, Return, Refund, Inventory;
  `LIFECYCLES.md`, `DOMAIN_MODEL.md`.
- **Recommended resolution:** Define eligibility against authoritative
  fulfillment quantities and remaining returnable quantities using versioned
  state/locked records. Separate cancel-before-handover from return-after-
  delivery and define compensation when an external carrier event wins.
- **ADR required:** Extend lifecycle/checkout consistency ADR.
- **Blueprint must change:** Yes.

### H-05 — Customer follow/marketing/campaign ownership is missing

- **Problem:** Blueprint mentions targeting, customer segments, campaigns,
  voucher distribution, and notifications, but has no owner for product follows,
  subscriptions, audience snapshots, consent, suppression, or campaign sends.
- **Why it matters:** Promotion eligibility and message delivery can become
  coupled to mutable customer behavior without a deterministic record.
- **Concrete failure scenario:** A customer follows a product, then unfollows
  while a campaign is being selected. Multiple workers recompute the live
  audience and send the same campaign twice or send after opt-out.
- **Affected domains/documents:** Customer, Promotion/Voucher, Notification,
  Reporting; `PROMOTION_ENGINE.md`, `DOMAIN_MODEL.md`,
  `ACTORS_AND_USE_CASES.md`.
- **Recommended resolution:** Decide whether follow/marketing is in scope. If
  yes, add a minimal Customer Engagement ownership model: follow/subscription,
  consent/suppression, immutable campaign audience/send identity, recipient
  dedupe, and unsubscribe timing. If no, remove targeted/follow implications
  from v1 scope.
- **ADR required:** Only if a new domain boundary or reliable delivery protocol
  is introduced.
- **Blueprint must change:** Yes if capability remains claimed.

### H-06 — Notification/email reliability and lifecycle are under-specified

- **Problem:** Recipient/channel/preference/template/delivery concepts exist,
  but no delivery lifecycle, retry policy, dedupe scope, transactional creation,
  or mandatory-message policy is defined.
- **Why it matters:** Password reset and payment/order messages cannot be
  treated like optional marketing notifications.
- **Concrete failure scenario:** Password reset succeeds but email creation is
  lost after transaction commit, or two retries send the same campaign several
  times. A preference suppresses a mandatory security alert.
- **Affected domains/documents:** Notification, Security, Order, Payment,
  Marketing; `SECURITY_MODEL.md`, `DOMAIN_MODEL.md`, `BUSINESS_RULES.md`.
- **Recommended resolution:** Define notification intent and delivery states,
  unique dedupe scope, mandatory versus optional categories, transactional DB
  insertion with the originating use case where required, retry/backoff, and
  terminal failure visibility. A SQL-backed worker/outbox is sufficient for a
  capstone; no broker is justified.
- **ADR required:** Yes only for reliable delivery/transaction semantics.
- **Blueprint must change:** Yes.

### H-07 — Audit integrity, privacy, and transaction coupling are incomplete

- **Problem:** Audit fields are listed, but there is no rule for atomic audit
  creation, immutability enforcement, redaction, sensitive before/after data,
  retention, or service/system actors.
- **Why it matters:** An audit row can be missing for a successful privileged
  action, or can become a second store of passwords/tokens/PII.
- **Concrete failure scenario:** An account reset audit captures a reset token
  or password-like input in `before/after`; alternatively, inventory adjustment
  commits but audit insertion fails outside the transaction.
- **Affected domains/documents:** Audit and all sensitive domains;
  `SECURITY_MODEL.md`, `DOMAIN_MODEL.md`, `BUSINESS_RULES.md`.
- **Recommended resolution:** Define an audit data policy, atomicity by use-case
  type, immutable database permissions/constraints, redacted structured fields,
  actor types, retention/access, and behavior when audit persistence fails.
- **ADR required:** Yes for cross-domain audit transaction/retention policy.
- **Blueprint must change:** Yes.

### H-08 — Reporting lacks accounting semantics and source facts

- **Problem:** Reporting is only described as read models/projections. Revenue,
  sales date, branch attribution, refunds, cancellations, stock-in-transit,
  timezone, and data freshness are undefined.
- **Why it matters:** Two correct transactional domains can still produce a
  financially wrong report if measures use inconsistent dates or statuses.
- **Concrete failure scenario:** A sale captured on day 1 is refunded on day 2
  after fulfillment from another branch. One report counts gross revenue at the
  sales branch, another subtracts refund from fulfillment branch, and totals no
  longer reconcile.
- **Affected domains/documents:** Reporting, Order, Payment/Refund,
  Branch/Location, Inventory; `ARCHITECTURE.md`, `DOMAIN_MODEL.md`.
- **Recommended resolution:** Define minimum report facts and semantics:
  gross/net sales, tender/capture/refund dates, sales branch, fulfillment
  location, business timezone, reversals, freshness, and reconciliation source.
  For the capstone, SQL queries/projections are enough.
- **ADR required:** No unless a separate analytics infrastructure is proposed.
- **Blueprint must change:** Yes.

### H-09 — ProductVariant and price identity are not stable enough

- **Problem:** Product versus Variant ownership is correct, but required shoe
  option dimensions, size systems, SKU immutability, barcode, price ownership,
  and branch/channel price variation remain unresolved.
- **Why it matters:** Order snapshots, inventory balances, imports, scanning,
  and promotion targeting all depend on stable sellable identity.
- **Concrete failure scenario:** A size label is renamed or variant option
  combination changes after stock exists. Inventory and historical order
  references now appear to describe a different SKU; a branch-specific price
  cannot be distinguished from catalog data.
- **Affected domains/documents:** Catalog, Pricing, Inventory, Order,
  Promotion; `DOMAIN_MODEL.md`, `BUSINESS_RULES.md`.
- **Recommended resolution:** Freeze a minimal shoe model: immutable SKU/public
  sellable identity, explicit Product/Variant relation, canonical size value
  plus size-system context, option-combination uniqueness, archive-not-repurpose
  rule, and clear Price ownership/version/effective period.
- **ADR required:** Domain decision belongs in `DOMAIN_MODEL.md`; ADR only if a
  generic option architecture materially changes boundaries.
- **Blueprint must change:** Yes.

### H-10 — Multi-instance workers and callbacks lack claim/retry semantics

- **Problem:** The architecture proposes stateless backend instances and a
  scheduled worker but does not define row claiming, leases, retry ownership,
  or poison/final-failure handling.
- **Why it matters:** Multiple instances can expire one reservation twice,
  process one webhook twice, or send duplicate notifications.
- **Concrete failure scenario:** Two nodes select the same expired reservations
  and both release them. Even if reservation state is terminal, stale balance
  updates can corrupt `reserved` unless the claim and transition are atomic.
- **Affected domains/documents:** Inventory, Payment, Notification, Security;
  `ARCHITECTURE.md`, `ACTORS_AND_USE_CASES.md`.
- **Recommended resolution:** Use SQL row-level claiming/conditional updates,
  unique event/operation IDs, attempt counters, next-attempt timestamps, and
  terminal failure visibility. Keep one worker instance initially if desired,
  but preserve idempotency. Do not add Redis or a broker.
- **ADR required:** No unless asynchronous delivery architecture expands.
- **Blueprint must change:** Yes.

### H-11 — Capstone scope is too broad to implement and verify reliably

- **Problem:** The proposed first release already includes identity/RBAC,
  branches, inventory, POS, online orders, payment, cancellation, audit, and
  reporting; later documents also imply shipment, returns, vouchers,
  notifications, marketing, dynamic content, and AI.
- **Why it matters:** Correct concurrency, finance, and security work is test-
  intensive. Spreading effort across every boundary risks a wide but unreliable
  demo.
- **Concrete failure scenario:** The team builds screens for all modules but
  skips real concurrency, refund, session revocation, and branch-isolation tests
  to meet the deadline—the exact risks this Blueprint is meant to prevent.
- **Affected domains/documents:** Entire Blueprint; `PROJECT_OVERVIEW.md`.
- **Recommended resolution:** Freeze a demonstrable correctness core and label
  later slices. Recommended core: identity/scoped RBAC, two branches/locations,
  catalog/variant, explicit inventory, POS checkout, minimal online checkout,
  one simulated idempotent electronic provider plus cash only if shift model is
  included, cancellation, audit, and reconciled reports. Add one promotion and
  one partial-refund/return happy path only after core concurrency tests pass.
  AI and dynamic page publishing should be optional demonstration slices.
- **ADR required:** No; this is release-scope governance.
- **Blueprint must change:** Yes.

## 5. MEDIUM findings

| ID | Finding | Recommended treatment |
|---|---|---|
| M-01 | `BR-INV-002` confirms all quantities non-negative while `BR-INV-102` only proposes disabling negative stock/backorder. | Remove redundancy and explicitly distinguish physical on-hand from order backorder demand. |
| M-02 | AGENTS says Inventory owns “returns” while Domain Model creates a Return domain. | Clarify physical Return owns eligibility/inspection; Inventory owns only resulting stock movement/disposition. |
| M-03 | Cart has no identity, expiry, merge, price-staleness, or concurrent-edit model. | Define a minimal mutable cart policy before online checkout APIs. |
| M-04 | Notification, Campaign, Product publication, Price, CashierShift, receiving/adjustment, and Page publication lack complete lifecycles. | Add only lifecycles for selected release scope. |
| M-05 | API principles omit optimistic concurrency/precondition semantics for mutable admin resources. | Define expected version/ETag or command-specific conflict behavior. |
| M-06 | API idempotency lacks key scope, payload mismatch behavior, retention, and replay response. | Specify in the relevant command contracts and database invariants. |
| M-07 | AI page validation covers publish time but not products becoming unavailable later. | Define live-reference rendering fallback, unpublish policy, and cache invalidation expectations. |
| M-08 | Customer consent, anonymization, audit retention, order-history retention, and AI-image deletion can conflict. | Create a data-retention/privacy decision matrix before storing customer/AI data. |
| M-09 | Time is a cross-domain dependency but business timezone and clock semantics are open. | Freeze one business timezone and inject a clock for tests. Store instants consistently. |
| M-10 | Public ID separation is confirmed but format is open. | Defer format until API design; do not introduce multiple ID schemes. |
| M-11 | Media storage is open and product images are likely required. | Use the simplest deployment-compatible storage for capstone; add object storage only for a concrete hosting requirement. |
| M-12 | Backup/restore, migration recovery, and reconciliation are open. | Define minimum restore and data-reconciliation demonstration before release. |
| M-13 | Backend technology is not frozen even though Spring Security is an expected target and legacy knowledge is Java/Spring. | Confirm Spring Boot/Spring Security or explicitly choose another stack before implementation planning. |
| M-14 | “Offline POS” may mean in-store rather than network-disconnected operation. | Clarify terminology; do not build offline synchronization unless explicitly required. |

## 6. LOW findings

| ID | Finding | Recommendation |
|---|---|---|
| L-01 | Concept names mix `Warehouse` as aggregate and Location type. | Use one term after hierarchy decision. |
| L-02 | `payment-refund`, `fulfillment-return`, and `ai-content` module names group domains that documents otherwise separate. | Treat module map as provisional or split names to avoid accidental coupling. |
| L-03 | Some documents say SQL Server is confirmed while one-database topology is proposed. | State “SQL Server engine confirmed; single-database topology proposed/accepted separately.” |
| L-04 | Error contract includes timestamp but no documented stable trace/correlation behavior for clients. | Define once during API contract phase. |
| L-05 | ADRs do not list supersedes/superseded-by relationships. | Add only when revisions occur. |

## 7. Domain-model concerns

### Boundary quality

The high-level boundary map is sound. The main hidden dependency is the absence
of an explicit Checkout/Commerce Process coordinator. That coordinator should
be an application process, not a new aggregate that owns Order, Inventory, and
Payment data.

The following ownership statements should be explicit in v1.1:

- Order owns commercial intent, immutable confirmed line pricing, and customer/
  channel snapshots.
- Inventory owns stock allocation and reservation terminality.
- Payment owns financial attempts/transactions and provider truth.
- Fulfillment owns physical delivery quantities and handover evidence.
- Return owns return eligibility/request/inspection.
- Refund owns financial reversal requests/results.
- Application use cases coordinate transitions without one domain writing
  another domain's repository.

### Product/ProductVariant

The split is correct, but ProductVariant must never be repurposed after stock or
history exists. A variant needs an immutable SKU identity and canonical option
snapshot. Shoe size cannot be only display text if recommendations, regional
systems, or variant uniqueness depend on it.

### Hidden coupling map

| Coupling | Missing explicit model |
|---|---|
| Quote -> Order | quote expiry/version and acceptance rule |
| Order -> Reservation | allocation identity and commit/release ownership |
| Order -> Payment | checkout operation and paid/cancelled reconciliation |
| OrderItem -> Fulfillment -> Return | delivered/returnable quantity fence |
| Payment -> Refund | atomic remaining refundable amount |
| Employee -> Session -> BranchAssignment -> Shift | immediate revocation and active context |
| Follow/Consent -> Campaign -> Notification | audience snapshot, suppression, dedupe |
| Product/Promotion -> PageConfiguration | stale reference behavior |
| Transaction facts -> Reporting | dates, branch attribution, reversals |

## 8. Lifecycle concerns

### Missing or incomplete transitions

| Lifecycle | Missing decisions/transitions |
|---|---|
| Order | payment failure/expiry, confirmation reversal, partial fulfillment, cancellation requested/denied, exceptional/manual review, COD |
| Reservation | partial commit/release, renewal, ownership transfer, commit-vs-expire race, failed compensation |
| Payment | unknown/reconciliation, asynchronous success after timeout/cancel, partial capture, duplicate event conflict, chargeback if in scope |
| Refund | retry after provider failure, unknown provider result, cancellation before processing, partial allocations, concurrent refund reservation |
| Fulfillment | allocation failure, partial pick, cancellation before/after handover, delivery retry, return-to-sender completion |
| Return | eligibility check, partial receipt, inspection rejection, lost return shipment, refund-not-required path |
| Voucher | concurrent reserve, checkout failure release, redemption reversal, revocation while reserved |
| Account | disable versus lock, credential change, assignment expiry, session revocation completion |
| CashierShift | not defined: open, active, closing, reconciled, variance review, closed |
| Notification delivery | not defined: pending, claimed, sent, delivered if known, retry, failed, suppressed |
| Campaign/follow | not defined; scope itself is open |
| Cart | not defined; mutable/expired/converted/abandoned behavior open |

### Lifecycle structure recommendation

Do not force every concern into one Order status. Maintain independent state
machines and define guarded coupling rules, for example: an Order may become
commercially confirmed only when its channel-specific payment condition and
inventory condition are satisfied; Fulfillment state remains separate.

## 9. Business-rule concerns

### Missing invariant placement matrix

| Invariant | Domain | Application | Database | API | Tests |
|---|---:|---:|---:|---:|---:|
| Order total equals immutable line/adjustment totals under one currency/rounding policy | Yes | Yes | Selected checks | Never trust totals | Unit + integration |
| Available stock and terminal reservation transitions | Yes | Transaction/lock | Checks, version, unique operation | Conflict response | Concurrency |
| One provider event processed once | No | Yes | Unique provider/event ID | Authenticated callback | Replay integration |
| Capture/refund totals never exceed eligible amount | Yes | Locked transaction | Money/check/unique constraints where feasible | Idempotency key | Concurrency |
| Voucher usage cannot exceed limit | Yes | Atomic redemption | Unique/count guard design | Do not trust eligibility | Concurrency |
| Branch/location access | Policy | Resolve scope | FK/ownership where possible | Ignore/reject unauthorized scope | Negative integration |
| Confirmed price snapshot immutable | Yes | Confirmation use case | Update restrictions/schema design | No mutable endpoint | Integration |
| Reset token single-use and expiring | Yes | Atomic consume | Unique hash/status/version | Generic response | Replay integration |
| Audit redaction/immutability | Policy | Atomic write | Append-only permissions/constraints | Never accept audit payload | Security integration |
| Return/refund remaining quantity/amount | Yes | Locked transaction | Unique allocation references | Conflict response | Concurrency |

### Rules needing confirmation before implementation

Currency/rounding/tax, branch price scope, backorder, reservation TTL,
allocation splitting, order confirmation condition, cancellation boundary,
cash tender, refund eligibility, and role/approval thresholds are genuinely
blocking for their respective use cases.

## 10. Security concerns

The principles are good and materially safer than legacy. Required v1.1
clarifications:

- distinguish authentication session validity from current authorization and
  branch assignment;
- define immediate/maximum-latency revocation for disabled employees;
- define scope derivation for every branch-sensitive command and query;
- make reset-token consume and password change one transaction;
- define multiple outstanding reset tokens and token replay behavior;
- separate mandatory security messages from marketing preferences;
- forbid sensitive values in audit/log/AI prompts;
- define webhook authentication and secret rotation at provider-selection time;
- decide CSRF/session or token model before API security implementation.

Password-reset replay is conceptually blocked by single-use tokens, but this
must become an atomic database invariant/test, not only prose.

## 11. Inventory/payment correctness concerns

### Scenario verdicts

| Scenario | Current Blueprint | Required v1.1 outcome |
|---|---|---|
| Two customers buy final unit | Invariant stated; lock protocol missing | Exactly one reserve/commit succeeds; loser gets deterministic conflict |
| POS and online consume same location stock | Not resolved | Same Inventory operation and balance lock regardless of channel |
| Two branches compete | Ambiguous because allocation scope open | Independent location balances; shared warehouse uses one warehouse balance and authorized allocator |
| Duplicate payment webhook | Intent covered | Unique event record + atomic idempotent transition/result |
| Payment succeeds, client times out | Missing recovery protocol | Retry/query by operation ID returns recorded attempt/order outcome |
| Cancel races capture | Missing | One fenced winner plus mandatory void/refund/reconciliation path |
| Refund requested twice | Intent covered, lock missing | Same key returns same refund; different concurrent requests cannot exceed refundable amount |
| Partial refund then another | Rule stated, atomic allocation missing | Reserve refundable amount atomically and count only defined financial states |
| Reservation expiry races commit | Missing | Conditional terminal transition/version prevents double balance mutation |
| Return races fulfillment completion | Missing | Eligibility/transition is versioned against fulfillment quantities/state |

The architecture should prefer one SQL transaction for operations wholly in
the modular-monolith database. External payment outcomes require durable
pending/unknown/reconciliation states rather than pretending the provider call
is part of the database transaction.

## 12. Promotion/voucher concerns

The definition/issuance/redemption split is strong. Remaining risks:

- quote evidence does not define canonical allocation/rounding;
- promotion edits during checkout need versioned evaluation behavior;
- voucher expiry and limited-use consumption need one atomic checkout fence;
- customer-specific ownership and public code guessing/abuse policy are open;
- cancellation/refund redemption reversal can be exploited unless rules are
  explicit;
- campaign distribution, follow/unfollow, consent, dedupe, and suppression have
  no owner;
- reporting cannot reproduce applied discounts without rule/config snapshots.

For the capstone, implement at most a small approved promotion set and one
voucher mode after checkout correctness is frozen. A general rule DSL is likely
overengineering.

## 13. AI concerns

The AI boundary is the strongest part of the Blueprint. It correctly rejects
direct critical mutations and executable generated pages.

Remaining concerns:

- dynamic configuration needs referential validity at publish and safe behavior
  when products/promotions later become unavailable;
- approval must carry actor, validated configuration version, AI provenance,
  and audit record;
- customer foot images/measurements require explicit consent, retention, and
  deletion before collection;
- “promotion recommendation” must remain a draft and cannot bypass price or
  campaign permissions;
- model/provider outages need a plain non-AI flow.

AI-generated invalid discounts are already blocked conceptually. AI-generated
page references are only partially handled and require render-time fallback.

## 14. API/frontend concerns

REST/SPA separation is sound. Before contracts are approved, define:

- stable error codes and conflict semantics;
- idempotency key scope, payload mismatch, retention, and replay body/status;
- optimistic concurrency for admin edits;
- authenticated webhook endpoints outside user-session assumptions;
- current branch/register/shift context derived and validated server-side;
- customer ownership and cross-branch negative tests;
- quote expiry and stale-data recovery UX;
- payment-timeout recovery by operation/order ID;
- authoritative availability language rather than exact stock promises where
  allocation can change.

Do not add real-time infrastructure merely to refresh stock. Normal API refresh
or short polling is sufficient until a measured UX requirement says otherwise.

## 15. Technology concerns

### Appropriate now

- Modular monolith.
- SQL Server and versioned migrations.
- REST backend and Vue SPA.
- Database transactions, locks/versions, unique constraints, and SQL-backed
  retry/worker tables.
- Spring Boot/Spring Security is the likely simplest backend choice given the
  stated target and team legacy knowledge, but it should be explicitly frozen.

### Premature

- Redis for stock, sessions, or idempotency without measured multi-instance
  need.
- Kafka/RabbitMQ for internal domain communication.
- Elasticsearch/OpenSearch before SQL search is shown insufficient.
- WebSockets for inventory/notification freshness.
- Object storage before hosting/media requirements demand it.
- Microservices, service discovery, distributed transactions, Kubernetes.
- Vector database, dedicated inference service, fine-tuning, GPU deployment.
- A generic promotion DSL/rule engine.

None of those technologies resolves the missing business decisions or
transaction protocols.

## 16. Feasibility concerns

| Tier | Scope |
|---|---|
| Essential for correctness | Identity/scoped authorization, Product/Variant, Branch/Location, explicit Inventory, Order, selected POS/online checkout protocol, idempotent selected payments, audit, concurrency/security tests |
| Valuable demonstration | Two branches plus shared warehouse, one promotion/voucher type, one return and partial refund flow, in-app notification, reconciled branch report |
| Advanced optional | External payment sandbox/webhook, email, shipping integration, dynamic page configuration, limited AI recommendation/content draft |
| Likely overengineering | Multi-currency/tax engine without requirement, split shipment/allocation, many providers, push/WebSocket, advanced campaign automation, generic rule DSL, virtual try-on, microservices/broker/search cluster |

Academic feasibility improves by narrowing behavior, not by weakening
invariants. A simulated payment provider can still demonstrate webhook replay,
timeouts, idempotency, and refund concurrency correctly.

The main underengineering risk is implementing these domains as CRUD tables
with status fields while omitting transition guards, database uniqueness,
transaction boundaries, branch-negative tests, and concurrent checks. That
would produce a smaller codebase but an academically weaker and financially
unsafe system. The correct simplification is fewer approved flows with complete
invariants, not more flows with superficial validation.

## 17. Open-decision review

| Current open decision | Blocking? | Information needed | Reviewer recommendation |
|---|---|---|---|
| Invoice/receipt definition and issuance | Yes for completed-sale model | Legal/course requirement, printed/electronic output | Freeze a simple receipt document unless legal e-invoice is explicitly required |
| Exact POS/online order states | Yes | Confirm payment/fulfillment modes | Separate commercial, payment, fulfillment states; approve minimal transitions |
| Providers, tenders, capture, cash reconciliation | Yes for payment/POS | Required demo providers and cash scope | One simulated electronic provider; add cash only with shift ledger |
| Reservation TTL/allocation | Yes | Checkout duration, pickup/shipping, warehouse behavior | Channel-specific TTL; one location per line for first release |
| Return/refund policy | Blocking only if feature included | Window, item condition, approval, tender | Defer from core or approve one simple delivered-item partial-return path |
| Branch/warehouse hierarchy | Yes | Physical operating model | Branch has sales-floor location; optional shared warehouse location; no split allocation initially |
| Promotion/voucher rules | Defer until feature slice | Required campaign examples | One deterministic promotion + one single-use voucher; no generic DSL |
| Guest/account/verification/retention | Guest/account blocks online flow; retention blocks production data | UX and privacy expectations | Allow guest POS; require account or explicitly approved guest policy online; minimize stored data |
| Roles/permissions/cross-branch | Yes | Course actors and approval duties | Freeze minimal permission matrix and explicit branch assignments |
| Public IDs/API versioning | Deferrable until API design | Exposure and compatibility plan | One public UUID-like ID policy; no speculative versioning before first contract |
| Shipping/carriers/regions/SLA | Defer if pickup-only; otherwise blocking | Required online fulfillment demo | Use pickup or one simulated shipping flow; no carrier platform initially |
| AI consent/provider/quality | Deferrable | Confirmed AI demo and data type | Keep AI out of core; approve only after consent/evaluation decision |
| Product option/size model | Yes for catalog/import/AI | Required size systems and recommendation scope | Canonical shoe size + system; immutable SKU combination |
| Currency/tax/rounding | Yes for finance | Target market/course fiscal requirement | Freeze VND and one deterministic rounding policy; avoid tax engine unless required |
| Session/token architecture | Yes for security implementation | Deployment topology and client requirements | Prefer secure server session/HttpOnly cookie for capstone unless mobile/token need is confirmed |
| Backend framework | Yes for implementation planning | Team competency and course constraints | Freeze Spring Boot/Spring Security if still required |
| Follow/marketing | Deferrable unless claimed | Demonstration requirement and consent | Remove from v1 or add minimal engagement ownership/dedupe model |
| Offline/degraded POS | Deferrable unless network-offline is required | Meaning of “offline POS” | Treat as in-store POS; do not build sync engine without explicit requirement |

## 18. Recommended Blueprint changes

1. Correct all ADR statuses and separate governance-confirmed decisions from
   review-pending detail.
2. Add a checkout consistency ADR and document POS versus online protocols,
   durable operation identity, commit points, timeout/retry/reconciliation, and
   compensation.
3. Expand Inventory rules with conditional reserve, terminal transitions,
   lock/version, expiry race, movement identity, and one-location-per-line scope
   for the first release.
4. Expand Payment/Refund rules with money/currency, atomic capture/refund
   allocation, unknown/reconciliation state, callback replay, and cancellation
   race handling.
5. Define selling branch, register, allocation/fulfillment/return locations, and
   server-side scope derivation.
6. Replace the single Order-centric lifecycle with independent approved state
   machines and guarded coupling rules.
7. Add a minimum permission/branch matrix and session revocation behavior.
8. Add notification delivery and audit data/atomicity policies.
9. Decide whether customer follow/marketing exists; model it minimally or
   remove the implied capability.
10. Define report facts, branch attribution, business time, and refunds.
11. Freeze minimal ProductVariant/SKU/size/price identity.
12. Recast release scope into essential, demonstration, and optional slices.

## 19. Decisions that should be frozen

After correcting ADR governance, retain and approve:

- the new project, not legacy, is source of truth;
- modular monolith and no premature distributed infrastructure;
- REST-first backend and separate Vue SPA baseline;
- Order-centered commerce without `DonHang -> HoaDon` duplication;
- Product/ProductVariant separation and no catalog-owned stock;
- location-aware Inventory with explicit reservations and movements;
- `available = onHand - reserved`;
- idempotent Payment/Refund with provider-event replay protection;
- confirmed order pricing snapshots are immutable;
- UserAccount, CustomerProfile, and EmployeeProfile separation;
- stable permission identifiers and server-side branch scope;
- versioned SQL Server migrations and critical database invariants;
- AI proposal/validation/approval boundary;
- frontend is never business or authorization source of truth;
- legacy plaintext password fallback, localized roles/status parsing, inferred
  reservations, and invoice-centric architecture remain deprecated.

## 20. Decisions that should remain open

- Exact state names until checkout/payment/fulfillment modes are confirmed.
- Payment provider and real external integration.
- Return/refund, shipping, marketing/follow, and AI release scope.
- Promotion types beyond a minimal demonstrator.
- Object/media storage provider.
- Email/push providers and push delivery.
- Redis, broker, search engine, WebSockets, microservices, and AI infrastructure
  unless concrete requirements later justify them.
- Multi-currency, complex tax, split shipment/allocation, loyalty, gift cards,
  exchanges, virtual try-on, and advanced campaign automation.

These are not excuses to postpone the blocking minimal policies in section 17.

## 21. Proposed Blueprint v1.1 changes

Blueprint v1.1 should be a focused correction, not a rewrite:

```text
1. Governance/status repair
2. Scope freeze and actor/permission matrix
3. ProductVariant/size/price identity freeze
4. Branch/location ownership model
5. Independent lifecycle matrices
6. Checkout consistency protocol
7. Inventory transaction/concurrency protocol
8. Payment/refund accounting and idempotency protocol
9. Notification/audit/reporting minimum semantics
10. Open-decision register with owner, deadline, and implementation gate
```

Suggested new ADRs:

- Checkout consistency and recovery protocol.
- Multi-branch ownership and scope derivation.
- Financial accounting/refund concurrency extension.
- POS cash/register shift, only if cash is confirmed.
- Reliable notification/audit delivery, only if the selected flows require it.

Blueprint v1.1 acceptance should require written scenario outcomes for all ten
inventory/payment race cases in section 11 and negative authorization tests for
customer ownership and cross-branch access.

---

## REVIEW STATUS

**REJECT / REDESIGN**

This rejects Blueprint v1 as an implementation source of truth, not its
foundational direction. The foundation should be preserved; transaction seams,
lifecycles, branch scope, governance status, and capstone scope must be repaired
before approval or implementation planning.
