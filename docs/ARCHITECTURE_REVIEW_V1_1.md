# Architecture Review V1.1

- Review target: Blueprint v1.1 (`PROPOSED FOR REVIEW`)
- Review date: 2026-08-23
- Reviewer posture: final independent adversarial architecture review
- Scope: governance, all Blueprint documents, all ADRs, Review V1, and the v1.1 changelog
- Implementation reviewed: none exists

## 1. Executive Summary

Blueprint v1.1 fixes the fundamental defects that caused Blueprint v1 to be
rejected. It now has a credible modular-monolith consistency model, explicit
location inventory, durable checkout/payment intent, append-only financial
facts, scoped multi-branch authorization, independent lifecycles, reliable
SQL-backed side effects, and correct ADR governance.

The six previous blockers are closed at architectural-boundary level. Concrete
failure timelines no longer require distributed transactions, a broker, Redis,
or a full accounting ledger. The design is suitable for a capstone if delivery
is restricted to the stated essential and demonstration slices.

The architecture is not ready to be frozen unchanged. Four bounded `HIGH`
issues remain:

1. the documents require a global cross-aggregate lock order but do not define
   one, and they disagree on whether voucher revalidation occurs before or after
   locking;
2. a definitive provider refund failure does not have an explicit rule for the
   pending refundable-amount reservation;
3. the minimal money equation, rounding/allocation, and promotion stacking
   policy are still too open to produce one reproducible payable/refundable
   amount;
4. essential reports lack a complete metric/source glossary, especially for
   shipping, voucher allocation, reversals, and inventory value.

These are not reasons to redesign the domain boundaries. They require a focused
Blueprint v1.1.1 correction and business decision freeze before ADR acceptance
or feature implementation.

## 2. Verdict

**APPROVE WITH CHANGES**

| Severity | Count | Meaning |
|---|---:|---|
| `BLOCKER` | 0 | No fundamental redesign is required |
| `HIGH` | 4 | Must be resolved before architecture freeze and dependent implementation |
| `MEDIUM` | 7 | Must be resolved before the affected slice/API contract |
| `LOW` | 2 | Clarification/terminology improvements |
| `INFORMATIONAL` | 4 | Verified strengths or safe deferrals |

The architecture is fundamentally sound. The required changes are local to
protocol precision, business-policy closure, and reporting definitions.

## 3. Comparison with Review v1

| Review V1 concern | Blueprint v1.1 result | Independent assessment |
|---|---|---|
| No checkout coordinator/protocol | Durable CheckoutAttempt, atomic placement, separate PaymentAttempt, reconciliation | Resolved; one cross-domain lock-order detail remains |
| Inventory invariant without concurrency protocol | Balance key, pessimistic locks, terminal reservation transitions, movement IDs | Resolved; duplicate-key demand normalization should be explicit |
| Incomplete financial/refund accounting | Append-only facts, Payment lock, pending refund reservation | Resolved structurally; definitive refund failure remains ambiguous |
| Ambiguous branch/location ownership | Branch commercial owner; Location stock node; Register/Shift and grants | Resolved |
| Incomplete/mixed lifecycles | Independent Cart/Order/Payment/Reservation/Fulfillment/Return/Refund/etc. | Resolved at architecture level |
| Accepted ADRs under proposed Blueprint | ADR lifecycle added; every ADR is Proposed | Resolved |
| Quote/voucher TOCTOU | Placement revalidation and atomic benefit hold | Mostly resolved; ordering text must be unified |
| Stale employee sessions | `authVersion` plus current account/assignment checks | Resolved |
| Missing POS cash accounting | Register/Shift/tender entries and expected-cash equation | Resolved conditionally; cash refund ownership still needs one rule |
| Missing notification/audit reliability | Transactional intent/audit and leased SQL delivery | Resolved |
| Missing report facts | Branch/event attribution and gross/capture/refund/net concepts | Improved but not complete enough for essential reports |
| Capstone scope explosion | Essential/demonstration/optional/deferred tiers | Resolved if implementation follows the tiers |

Blueprint v1.1 is a substantive correction, not a cosmetic response to Review
V1. The remaining issues do not invalidate its foundational direction.

## 4. Previous Blocker Re-validation

| Previous blocker | Re-test result | Status |
|---|---|---|
| B-01 Checkout consistency | Local placement and confirmation use SQL transactions; external payment is durable and reconciled | Closed with H-01 follow-up |
| B-02 Inventory concurrency | Shared balance locks and conditional Reservation terminality preserve quantities | Closed with duplicate-line clarification |
| B-03 Financial/refund model | Payment lock and pending refund reservation prevent concurrent over-refund | Closed with H-02 follow-up |
| B-04 Multi-branch scope | Scope ownership and server derivation are explicit and testable | Closed |
| B-05 Lifecycles | State dimensions are independent and have guarded coupling | Closed |
| B-06 ADR governance | 12/12 decision ADRs are Proposed; Blueprint is Proposed | Closed |

No previous blocker remains at `BLOCKER` severity.

## 5. Checkout Review

### Timeline challenges

| Scenario | State after race/restart | Protection | Verdict |
|---|---|---|---|
| A. Placement commits, response times out, client retries | Existing CheckoutAttempt, pending Order, Reservation and voucher hold remain | Scoped idempotency key + fingerprint + unique database key; identical retry returns recorded result | Safe if duplicate-key collision is re-read, not translated to generic failure |
| B. Provider succeeds and application crashes | Local PaymentAttempt remains durable `PENDING/UNKNOWN`; provider has financial fact | Authenticated callback or provider query/poll applies the fact idempotently after restart | Safe only for a selected provider/adapter that supports reconciliation by durable reference |
| C. Provider succeeds, webhook delayed, Reservation expires | Order/Reservation become terminal; later capture is recorded but cannot reopen Order | Expiry and confirmation condition on the same records; late capture enters void/refund reconciliation | Safe; temporary `CANCELLED/EXPIRED + CAPTURED` is explicitly modeled |
| D. Cancellation races capture | The transaction obtaining the relevant Order/resource locks first performs the legal transition | Conditional Order/Reservation/Voucher transitions; loser re-reads and reconciles | Correct invariant, but lock acquisition hierarchy is incomplete |
| E. Duplicate request while first placement executes | One CheckoutAttempt insert/claim must win; second blocks/fails uniqueness then returns winner outcome | Unique scoped idempotency key and whole-placement transaction | Safe if the idempotency record is claimed before other effects and collision recovery is contractual |

Authoritative stages are coherent:

```text
Cart/preview      = advisory
Placement commit = immutable Order price + stock/voucher held
PaymentAttempt    = durable external intent; not confirmation
Capture callback = financial fact
Confirmation tx  = Order confirmed + stock committed + voucher redeemed
```

Price/promotion changes after placement do not mutate the Order. Payment failure
can retry within the hold policy; unknown provider outcome is never treated as a
safe new charge. This is a defensible protocol.

### H-01 — Cross-aggregate lock hierarchy and duplicate demand normalization are incomplete

- **Severity:** `HIGH`
- **Problem:** `ACTORS_AND_USE_CASES.md` and ADR-0010 say Inventory and limited
  Voucher records are locked before revalidation. `ARCHITECTURE.md` revalidates
  promotion/voucher before locking balances and later lists callback locks in
  another order. ADR-0010 says “define one lock order” as mitigation but does not
  actually freeze that order. Two checkout lines targeting the same
  `(location, variant)` are also not explicitly combined before validation.
- **Failure scenario:** Placement T1 locks balance B then requests voucher V;
  voucher expiry/redemption T2 locks V then requests B. SQL chooses a deadlock
  victim. In another request, two duplicated lines each validate against the
  same initial available unit; the database check rolls back rather than
  producing the intended stock-conflict result.
- **Why it matters:** Database rollback protects data, but undefined ordering
  harms liveness, makes callback/deadlock retry behavior inconsistent, and leaves
  the supposedly deterministic checkout protocol open to divergent implementations.
- **Affected domain:** Checkout, Inventory, Promotion/Voucher, Order, Payment,
  scheduled expiry and reconciliation workers.
- **Recommended resolution:** Freeze one global lock hierarchy for every
  cross-domain transaction; normalize/sum demand by `(location, variant)` and
  benefit identity before acquiring locks; revalidate usage/time/stock only
  after the protected rows or versions are fenced; define deadlock/lock-timeout
  replay using the original idempotency key. Align Architecture, use cases, and
  ADR-0010/0004/0007 to the same sequence.

## 6. Inventory Review

### Race matrix

| Race | Invariant result |
|---|---|
| Two customers buy last unit | First balance-lock holder reserves; second sees zero available and fails |
| POS vs online | Same InventoryBalance and no implicit channel priority; exactly one reserve succeeds |
| Expiry vs payment confirmation | Both require Reservation `ACTIVE` under shared locks; one terminal transition changes quantity |
| Cancellation vs Reservation commit | Same conditional terminal fence; cancellation cannot release a committed Reservation |
| Return restock vs adjustment | Both lock the balance and use unique movement operation references |
| Transfer dispatch vs sale | Both serialize on source balance; loser revalidates remaining available/on-hand |
| Duplicate SKU lines | Database checks prevent negative availability, but pre-lock demand aggregation must be added under H-01 |
| Voucher and Inventory operations | One transaction preserves atomicity; global lock order must be added under H-01 |

For reserve, the protected update is:

```text
lock InventoryBalance
check requested > 0
check onHand - reserved >= requested
reserved := reserved + requested
insert active Reservation + unique operation identity
commit
```

Commit decrements both `onHand` and `reserved`; release/expiry decrements only
`reserved`. Checks `onHand >= 0`, `reserved >= 0`, and `reserved <= onHand`
backstop application logic. Immutable movement IDs prevent double physical
effects. No unpaid-order aggregate query is used.

Deadlocks remain possible—as with any multi-row pessimistic design—but they are
not corruption if the transaction is short, fully rolled back, and replayed by
the same operation identity. H-01 is therefore `HIGH`, not a new blocker.

### Informational finding — Inventory ownership is safe to freeze

- **Severity:** `INFORMATIONAL`
- **Problem:** None found in the ownership model.
- **Failure scenario:** Legacy stock-on-variant or inferred reservation would
  reintroduce oversell, but both are explicitly deprecated.
- **Why it matters:** Inventory is the highest-risk shared resource in POS and
  online commerce.
- **Affected domain:** Catalog, Inventory, Checkout, Transfer, Return.
- **Recommended resolution:** Freeze ADR-0004 after H-01 is incorporated and
  require database/concurrency tests before any stock flow is considered done.

## 7. Financial Review

### Scenario matrix

| Scenario | Required/defined result | Assessment |
|---|---|---|
| Duplicate payment request | Same key/fingerprint returns same PaymentAttempt | Defined and enforceable by uniqueness |
| Duplicate webhook | Same provider event is persisted/processed once | Defined |
| Out-of-order webhook | Compatible facts are idempotent; conflicts enter reconciliation | Defined |
| Capture after cancellation | Capture fact retained; Order not reopened; void/refund required | Defined |
| Partial refund | Requested amount is reserved under Payment lock | Defined |
| Concurrent refunds | Payment lock serializes `successfulRefunded + pendingReserved` | Defined |
| Refund retry after HTTP timeout | Do not create a new blind refund; reconcile same operation | Direction is correct |
| Provider success/local update fails | Local transaction rolls back; replay/poll applies unique provider fact | Defined if event and state update share one transaction |
| Provider definitive refund failure | Refund becomes failed, but pending amount disposition is ambiguous | H-02 |
| Return approved/refund pending | Physical Return and financial Refund remain independent | Correct |

`total_refunded <= total_captured` is enforceable without a full ledger because
every amount-changing command locks the same Payment summary/root and totals are
derived from successful append-only transactions plus active pending refund
reservations. All code paths—including callbacks and reconciliation—must use
that lock. A status field alone must never add/subtract money.

### H-02 — Definitive refund failure does not define pending-amount disposition

- **Severity:** `HIGH`
- **Problem:** Refund creation reserves `pendingRefundReserved`. The lifecycle
  explicitly releases it for rejection/cancellation, retains unknown results for
  reconciliation, and converts it on success, but says only that `FAILED` “may
  be retryable.” It does not state whether a definitive provider failure releases
  the reservation or keeps it for a retry attempt.
- **Failure scenario:** A refund for the full captured amount receives a
  definitive provider rejection. If the pending amount remains forever, no
  legitimate refund can proceed. If it is released on an ambiguous timeout and
  the provider later reports success, another refund can reserve the same amount
  and total refunds can exceed capture.
- **Why it matters:** The missing distinction directly controls the
  over-refund invariant and financial recovery behavior.
- **Affected domain:** Refund, Payment, provider adapter, reconciliation,
  customer support and reporting.
- **Recommended resolution:** Distinguish definitive failure from
  `UNKNOWN/RECONCILIATION_REQUIRED`. Under the Payment lock, definitive failure
  appends its fact and releases the pending reservation exactly once; unknown
  retains it. A retry creates/reuses an attempt under the same logical Refund and
  operation identity. Success atomically converts pending to successful refunded
  amount. Add concurrency tests for failure, late success, and retry.

## 8. Multi-Branch Review

### Access challenge

| Resource/action | Branch A employee default | Shared/central access |
|---|---|---|
| Branch B inventory | Denied | Explicit LocationAccessGrant/warehouse permission only |
| Shared warehouse inventory | Denied unless route/grant permits the use case | Warehouse operator or explicit allocator grant |
| Branch B Order | Denied | Explicit support/finance/cross-branch permission and scope |
| Customer profile | No global browse; minimum fields for an authorized Order/use case | Explicit support use case with masking |
| Branch B report | Denied | Explicit report permission plus Branch B/organization scope |
| Branch-scoped promotion | Denied outside grant | Explicit merchandising scope |
| Branch B Register/Shift | Denied | Register branch/location and active assignment must match |

Assignment/grant changes increment `authVersion`, and sensitive requests also
re-evaluate current account, employee, assignment, permission, and target scope.
An old cookie therefore cannot retain Branch A authority after revocation.
Central administration is an explicit grant, not a magic account bypass.

Collection endpoints, search, autocomplete, reports, notification reads, and
ID-based lookups must all apply the same server-side ownership/scope policy before
pagination/results, not filter a global result in Vue.

### M-01 — The minimum permission/scope matrix is still an implementation gate

- **Severity:** `MEDIUM`
- **Problem:** The authorization formula is sound, but exact permissions,
  cross-branch support/finance access, approval thresholds, and shared-location
  routes remain open.
- **Failure scenario:** Two teams independently interpret `MANAGER` as branch-only
  versus organization-wide and ship inconsistent search/report/refund access.
- **Why it matters:** The mechanism is safe, but contracts and negative tests
  cannot be written without the minimal grant catalogue.
- **Affected domain:** Identity, Employee, Branch/Location, Orders, Inventory,
  Promotions, Reports, Support and Admin.
- **Recommended resolution:** Before sensitive API planning, freeze a small
  permission-by-use-case matrix with explicit branch/location scope and no
  implied superuser. Defer maker-checker and fine-grained thresholds unless a
  selected demo flow needs them.

## 9. Lifecycle Review

| Lifecycle | Completeness assessment | Remaining gate |
|---|---|---|
| Cart | Minimal operational states are adequate | Identity/merge and concurrent edit policy |
| CheckoutAttempt | Covers hold, payment, terminal failure, expiry and reconciliation | Global lock order |
| Order | Separates commercial state from payment/fulfillment | Cancellation policy and direct-vs-compensated path |
| PaymentAttempt | Covers pending, success, failure, unknown/reconciliation | Provider capabilities |
| Reservation | Correct terminality and idempotent effects | TTL values and duplicate-line normalization |
| Fulfillment | Adequate pickup/delivery states with return-to-sender | Selected demonstration mode |
| Return | Versioned eligibility, partial receipt and disposition | Business window/condition policy |
| Refund | Amount reservation and async outcomes | H-02 failure semantics |
| Voucher | Issue/reserve/redeem/release/reverse model | M-02 expiry while reserved |
| User/Customer/Employee | Correct separation and revocation coupling | Recovery/verification policy |
| CashierShift | Correct opening/open/closing/reconciliation model | Selected tender and refund shift rule |
| Notification | Durable claim/retry/suppress/fail states | Channel/retry thresholds |

Impossible combinations are handled as follows:

- `Order=CANCELLED`, `Payment=CAPTURED`, `Reservation=ACTIVE` is not a stable
  legal end state. Cancellation/expiry releases the Reservation; a late capture
  creates reconciliation and mandatory void/refund without reopening the Order.
- `Refund=SUCCEEDED`, `Return=REJECTED` is valid only for an explicitly
  authorized refund-without-return reason. A Refund linked to a rejected Return
  must fail eligibility.
- `Voucher=REDEEMED`, failed Order confirmation cannot result from separate
  commits because redemption and confirmation share one transaction.

### M-02 — Voucher hold expiry/revocation transition is underspecified

- **Severity:** `MEDIUM`
- **Problem:** Placement promises to honor a reserved voucher until the payment
  deadline, but the Voucher lifecycle only shows `RESERVED -> ISSUED` on release.
  It does not state the result when the VoucherDefinition/Issuance expires or is
  revoked while the Order hold is active.
- **Failure scenario:** An issuance is reserved at 23:59, the campaign ends at
  midnight, and payment fails at 00:02. Returning it to `ISSUED` could make an
  expired benefit claimable; revoking it during the hold could also contradict
  the placed Order guarantee.
- **Why it matters:** Usage availability and historical price guarantees can
  diverge even though redemption itself is locked.
- **Affected domain:** Promotion/Voucher, Checkout, Order and scheduled expiry.
- **Recommended resolution:** Freeze that a valid placement lease is honored
  until its hold deadline unless an explicitly defined security revocation may
  break it. On release, transition to `ISSUED`, `EXPIRED`, or `REVOKED` according
  to the authoritative server clock/current definition state. Use the same lock
  and one terminal operation key.

### M-03 — Cart concurrency and guest identity are not yet contract-ready

- **Severity:** `MEDIUM`
- **Problem:** Cart states exist, but concurrent tab edits, anonymous cart
  identity, login merge, quantity overwrite semantics, and version conflict are
  open.
- **Failure scenario:** Two tabs update the same variant from quantity one to two
  and three; last-write behavior silently loses intent, then checkout fingerprints
  a cart the customer did not see.
- **Why it matters:** Cart is advisory, so stock/finance remain safe, but online
  API behavior and UX can be inconsistent.
- **Affected domain:** Customer, Cart, Checkout and REST/frontend.
- **Recommended resolution:** Before Cart API contracts, choose one owner/session
  identity, version field or command precondition, deterministic quantity update,
  and login/guest merge policy. Do not let Cart acquire stock authority.

## 10. Promotion/Voucher Review

### Required scenario outcomes

| Scenario | Blueprint result | Review |
|---|---|---|
| A. 20% product + 10% order voucher + free shipping | Pipeline can order and explain all adjustments, but exact amount depends on open stacking/base/rounding rules | H-03 must close the first-slice matrix |
| B. Two eligible vouchers | Selection/combination depends on approved exclusivity/stacking; no universal best-price behavior | Acceptable only as a gated decision |
| C. Global usage limit one, two redeemers | Locked usage record or guarded counter allows one reservation | Safe |
| D. Follow, issue, unfollow, follow | Unique campaign/customer/voucher issuance prevents another grant from the same campaign; unsent marketing suppressed | Safe |
| E. Homepage claim repeated | Display is not entitlement; customer issuance uniqueness makes repeated claim idempotent | Safe for customer issuance; public-code abuse policy remains open |
| F. Expiration exactly at placement | Server clock and time-boundary rule decide while locked | Boundary/timezone must be frozen |
| G. Admin changes promotion after quote | Placement re-evaluates; changed total requires explicit acceptance; placed Order keeps snapshot | Safe |
| H. Online-only Branch A voucher, Order owned by Branch B | Ineligible because evaluation uses server-owned Order branch/channel context | Safe if contract never trusts client scope |
| I. Partial refund after discounted order | Refund cannot exceed paid/captured amount; line allocation evidence exists conceptually | Allocation/rounding rule must be frozen under H-03 |

The domain decomposition—Definition, Campaign, Voucher, Issuance, Redemption,
Quote and Order evidence—is strong. It avoids code-only coupons and prevents AI,
campaign delivery, or clients from granting discounts.

### H-03 — Minimum money equation and first promotion/refund allocation policy are not frozen

- **Severity:** `HIGH`
- **Problem:** The Blueprint deliberately leaves currency precision, rounding,
  tax, adjustment bases/order, stacking, caps, and line allocation open. It also
  lacks one explicit Order invariant equating snapshot components to payable
  total. Therefore Scenario A and a partial discounted-line refund have no
  single reproducible numerical answer.
- **Failure scenario:** On a 100,000 VND item, one implementation applies the 10%
  order voucher to the original subtotal, another to the post-20%-promotion
  subtotal, and a third rounds each unit before quantity multiplication. All
  conform to the current generic pipeline but charge/refund/report different
  amounts.
- **Why it matters:** Payment capture, refund caps, receipt totals, promotion
  evidence, and reports must reconcile to exactly the same immutable arithmetic.
- **Affected domain:** Pricing, Promotion/Voucher, Order, Payment, Refund,
  receipt/invoice and Reporting.
- **Recommended resolution:** Before financial implementation, freeze a minimal
  money specification: currency/minor-unit scale; tax scope; rounding point/mode;
  item-adjustment then order-adjustment then shipping-adjustment order; voucher
  stacking for the demonstration set; maximum/zero-price rule; inclusive/exclusive
  time boundary and business clock; deterministic allocation of order discounts
  to lines. Add the invariant `payable = item subtotal - allocated item/order
  discounts + shipping - shipping discounts + tax`, constrained non-negative,
  with snapshot components summing exactly after allocation.

## 11. Security Review

### Scenario results

| Security scenario | Outcome |
|---|---|
| Reset token replay | Hash lookup + locked single-use state yields one success |
| Multiple reset requests | Business Rules/Lifecycle revoke earlier active tokens |
| Old token after new token | Must be rejected under BR-SEC-102 |
| Password changed with old sessions | `authVersion` increment rejects old sessions |
| Employee loses branch access | Grant change increments version; request also rechecks current assignment |
| Account disabled with active cookie | Account status/version check denies next request |
| Reset endpoint abuse | Generic response, multi-signal rate limit, audit; exact thresholds remain implementation policy |

HttpOnly server sessions plus CSRF are appropriate for the first-party capstone
clients. Redis/JWT are unnecessary without a deployment/mobile requirement.
Provider callbacks have separate integration authentication and replay keys.

### M-04 — Password-reset token multiplicity is contradictory across documents

- **Severity:** `MEDIUM`
- **Problem:** BR-SEC-102 and `LIFECYCLES.md` require a new reset token to revoke
  older active tokens, while `SECURITY_MODEL.md` says it “may” invalidate them
  according to a future policy.
- **Failure scenario:** An implementation follows the Security document and
  allows multiple tokens; the user assumes the older emailed token was revoked,
  but an attacker can still consume it.
- **Why it matters:** The source hierarchy makes BR-SEC-102 authoritative, but a
  security lifecycle should not contain conflicting normative wording.
- **Affected domain:** Identity, Password Recovery, Session revocation and Audit.
- **Recommended resolution:** Align every document on revoking all older active
  tokens for the same account/purpose in the new-token transaction. Keep channel,
  TTL, and rate thresholds open.

### Informational finding — AI/provider data is outside credential authority

- **Severity:** `INFORMATIONAL`
- **Problem:** None found in the trust-boundary direction.
- **Failure scenario:** Prompt or provider payload attempts to include secrets,
  tokens, payment credentials, or authorization grants.
- **Why it matters:** External adapters are untrusted data boundaries.
- **Affected domain:** Security, AI, Payment and Audit.
- **Recommended resolution:** Preserve data minimization, redaction, callback
  authentication, and negative security tests in API/provider contracts.

## 12. AI Review

The proposed flow is safe:

```text
bounded input -> adapter -> structured proposal -> schema validation
             -> deterministic validation -> preview -> authorized use case
```

AI cannot receive repositories/SQL/privileged commands, cannot activate or issue
discounts, cannot alter authorization, and cannot publish itself. Page output is
allowlisted structured configuration, not arbitrary HTML/JS. Product references
are checked at validation and degrade safely if later unavailable. Customer
recommendation failure falls back to ordinary commerce and never blocks checkout.

Foot images/measurements remain deferred until consent, retention, deletion,
quality and provider decisions exist. Declared-measurement recommendation is a
safer optional demonstration. No vector database, GPU service, fine-tuning, or
broker is justified.

### Informational finding — AI boundary is safe to freeze

- **Severity:** `INFORMATIONAL`
- **Problem:** No architecture defect found.
- **Failure scenario:** A malicious/invalid AI proposal references an unpublished
  Product, an unbounded discount, or an unsafe component.
- **Why it matters:** Non-deterministic output must not become transactional truth.
- **Affected domain:** AI/Recommendation, Content, Catalog, Promotion and Security.
- **Recommended resolution:** Freeze ADR-0008; keep all providers, personal-data
  collection and customer-facing accuracy claims gated by later ADR/evaluation.

## 13. REST/API Review

The architecture can support Customer Web, POS, Admin and a later mobile client
without exposing persistence entities. DTOs, stable public IDs, server validation,
scope, pagination/filter allowlists, error codes, correlation IDs, idempotency,
and conflict behavior are explicit contract requirements. Vue is presentation,
not domain authority.

Search/autocomplete/list endpoints must filter authorized scope in the query
before pagination. A caller who knows a public ID still receives ownership/scope
authorization. Webhooks use integration authentication rather than browser
sessions. Status queries support payment/checkout timeout recovery.

### M-05 — Retryable API conflict semantics need one cross-contract convention

- **Severity:** `MEDIUM`
- **Problem:** Contract requirements list idempotency scope, fingerprint mismatch,
  retention, optimistic conflict and pending status, but do not freeze the
  response convention for concurrent in-progress duplicates, deadlock/lock
  timeout, completed replay, or expired idempotency records.
- **Failure scenario:** POS interprets a lock timeout as a failed sale and creates
  a new key while Customer Web polls the same condition, producing different
  retry safety across clients.
- **Why it matters:** The database protocol is safe only if clients do not turn a
  transient/unknown outcome into a new financial/checkout command.
- **Affected domain:** REST contracts, Checkout, Payment, Refund, Inventory,
  frontend recovery UX.
- **Recommended resolution:** Before the first command contract, define stable
  states/errors for `IN_PROGRESS`, completed replay, fingerprint conflict,
  concurrency conflict, unknown outcome and key expiry. A deadlock victim retries
  server-side or returns a safe retry using the same key; never instruct a new key
  when outcome may exist.

## 14. Database/Transaction Review

| Critical invariant | Domain | Application transaction | Database | Required test |
|---|---|---|---|---|
| `onHand >= reserved >= 0` | Inventory guards | Balance lock and terminal transition | CHECK + unique balance/operation/movement | Last-unit and expiry races |
| Reservation uniqueness/effect once | Reservation state | Idempotent reserve/commit/release | Unique owner/line/operation + version/state | Duplicate request/callback |
| Voucher usage limit | Eligibility/issuance | Locked/guarded reserve/redeem | Unique issuance/redemption and guarded counter | Two final uses |
| Payment command/event idempotency | Payment | Conditional transition | Unique command fingerprint/provider event/reference | Replay/out-of-order |
| Refund never exceeds capture | Payment/Refund rule | Payment lock and pending amount reservation | Money checks/unique refs; cross-row sum protected by lock | Concurrent partial refunds |
| Branch/location scope | Authorization policy | Resolve/recheck actor and target | Ownership FKs/grants/indexes where possible | Negative ID/list/search tests |
| SKU/option identity | Catalog | Archive-not-repurpose | Unique SKU and selected active combination | Concurrent create/update |
| Order snapshot immutability | Order | No edit transition after placement/confirmation | Schema/role/update path restrictions | Mutation rejection/reconciliation |
| Audit immutability | Audit policy | Required insert in protected transaction | Append-only application role; no cascade | Mutation rollback/redaction/delete denial |
| Order monetary equation | Pricing/Order | Must compute/snapshot once | Selected non-negative/currency checks | Missing under H-03 |
| Report reconciliation | Reporting definitions | Projection/query only | Immutable source indexes | Missing under H-04 |

Most critical invariants now have a concrete protection strategy. H-01, H-02,
H-03 and H-04 identify the remaining prose-only or ambiguous parts.

### Informational finding — SQL Server is sufficient

- **Severity:** `INFORMATIONAL`
- **Problem:** No database technology blocker exists.
- **Failure scenario:** Introducing cache/broker state as authority would split
  the transaction boundary without need.
- **Why it matters:** The capstone benefits from one explainable consistency
  model.
- **Affected domain:** All transactional modules.
- **Recommended resolution:** Keep SQL Server, versioned migrations, constraints,
  locks and SQL-backed work claims. Choose migration tooling during planning;
  demonstrate backup/restore and reconciliation before release.

## 15. Reporting Review

Current strengths:

- sales branch is Order `responsibleBranchId`;
- stock and fulfillment retain Location dimensions;
- capture and refund use immutable financial timestamps;
- gross, discount, captured, refunded and net-captured measures are distinct;
- cancelled/expired unpaid Orders do not create captured sales;
- projections expose `asOf`/freshness and reconcile to transactions.

### H-04 — Essential reporting lacks a complete metric and source glossary

- **Severity:** `HIGH`
- **Problem:** The Blueprint does not fully define revenue versus gross/net
  sales, shipping revenue/discount, voucher discount allocation, void/payment
  reversal treatment, cancelled-sales counts versus amounts, or inventory value.
  Inventory has quantities/movements but no cost-basis owner, so “inventory
  value” has no authoritative source.
- **Failure scenario:** An Order captured on day one includes shipping and a
  voucher allocated across two lines; one line is partially refunded on day two.
  One report subtracts the refund from gross item sales, another from net
  captured including shipping, and a third values returned stock using selling
  price. All can satisfy the current broad reporting bullets while disagreeing.
- **Why it matters:** Basic reconciled reporting is classified `ESSENTIAL` and
  must not present derived financial guesses as source facts.
- **Affected domain:** Reporting, Pricing/Promotion, Order, Payment/Refund,
  Branch/Location and Inventory.
- **Recommended resolution:** Freeze a minimum report glossary and equations:
  item gross sales, item/order/voucher discounts, shipping charge/discount,
  captured/voided/refunded/net-captured amounts, cancelled order count with zero
  captured revenue, branch/date/timezone dimensions and allocation evidence.
  Explicitly defer inventory monetary value until a confirmed acquisition-cost/
  valuation model exists; report quantities and movements meanwhile.

## 16. Feasibility Review

| Classification | Recommended capstone scope | Review result |
|---|---|---|
| `ESSENTIAL` | Spring Security identity/scoped RBAC; two branches/locations/register context; Product/Variant/Price; explicit Inventory; one POS and minimal online checkout; one selected tender/provider behavior; cancellation; audit; basic reconciled reports | Feasible with concurrency/security tests |
| `DEMONSTRATION VALUE` | Shared warehouse, one promotion + one customer voucher, pickup or one simulated delivery flow, one partial return/refund, in-app notification | Add sequentially after correctness core |
| `OPTIONAL ADVANCED` | External payment sandbox, email, follow campaign, structured page publishing, declared-measurement/product recommendation | Must not block MVP |
| `DEFERRED` | Offline synchronization, split allocation/shipment, multi-provider/split tender, complex tax/multi-currency, push/WebSocket, search cluster, microservices, broker, event sourcing, image foot analysis, virtual try-on, vector/GPU infrastructure | Correctly deferred |

The scope is realistic only if “architecturally supported” is not confused with
“implemented in the first release.” One complete checkout concurrency path is
more valuable than superficial screens for every optional domain.

### M-06 — Essential versus demonstration scope is inconsistent for return/refund

- **Severity:** `MEDIUM`
- **Problem:** `PROJECT_OVERVIEW.md` places payment/refund correctness in Essential
  while one return/partial-refund flow is Demonstration; the changelog lists
  Fulfillment/Return/Refund together as Essential.
- **Failure scenario:** A team treats every return/refund/fulfillment state as MVP
  and drops concurrency/security tests to finish all screens.
- **Why it matters:** Scope ambiguity recreates Review V1 H-11 even though the
  architecture itself can support the domains.
- **Affected domain:** Release planning across Payment, Refund, Return and
  Fulfillment.
- **Recommended resolution:** Essential means the financial model must not block
  future refunds and any implemented refund must obey invariants. Implementing a
  customer return/partial-refund flow remains Demonstration Value after core
  checkout passes. Align the two classification tables.

## 17. Technology Review

| Technology | Decision |
|---|---|
| Modular monolith | Justified and safe to freeze |
| Spring Boot / Spring Security | Explicit baseline in this review brief; freeze it rather than leave open |
| REST + Vue | Justified |
| SQL Server + versioned migrations | Justified |
| SQL-backed sessions/jobs/intents | Sufficient baseline |
| Redis | Not justified |
| Kafka/RabbitMQ | Not justified |
| Elasticsearch/OpenSearch | Not justified before measured SQL-search failure |
| WebSocket | Not justified; polling/refresh is enough |
| Microservices/Kubernetes/distributed transactions | Not justified |
| Event sourcing/full accounting ledger | Not justified |
| Complex AI infrastructure/vector/GPU | Not justified |

### M-07 — Backend framework is incorrectly left open against the current baseline

- **Severity:** `MEDIUM`
- **Problem:** `PROJECT_OVERVIEW.md` and `ARCHITECTURE.md` say Spring Boot/Spring
  Security still needs confirmation, while the current independent-review brief
  explicitly defines the baseline as Spring Boot, REST, Vue and SQL Server.
- **Failure scenario:** Implementation planning stalls or evaluates an unrelated
  backend stack despite governance, legacy expertise, and the current explicit
  baseline.
- **Why it matters:** This is an authority/status mismatch, not a technology
  uncertainty.
- **Affected domain:** Architecture governance, backend/security planning and ADRs.
- **Recommended resolution:** Treat the explicit current baseline as confirmed;
  update the Blueprint/appropriate ADR during v1.1.1 without installing anything.

## 18. Open Decision Review

| Open decision group | Blocks now? | Affected domains | Recommended default/decision | Deferrable? |
|---|---|---|---|---|
| Backend framework | Yes, all backend planning | Architecture/Security | Freeze Spring Boot + Spring Security per current requirement | No |
| VND scale, tax, rounding, receipt/invoice | Yes, financial work | Pricing/Order/Payment/Refund/Reports | One VND minor-unit/rounding rule; tax excluded unless required; simple receipt, not assumed legal e-invoice | No |
| Promotion types/stacking/caps/time/allocation | Promotion slice and discounted refunds | Promotion/Order/Refund | Small explicit matrix; no DSL; server clock and deterministic line allocation | No for slice; otherwise defer slice |
| Online responsible branch/allocation | Online placement | Branch/Inventory/Order/Fulfillment | Persist server-selected branch/location; simplest pickup or one location per line | No for online |
| Reservation TTL/payment grace/retry | Online electronic payment | Checkout/Inventory/Payment | One channel-specific hold and explicit late-capture path | No |
| POS tenders/cash/split | POS | Payment/Register/Shift/Reports | Choose one tender; if cash, use Shift; defer split tender | No for POS |
| Cash refund/closed original shift | Cash refund slice | Refund/Shift/Reports | Post refund to current authorized open shift, reference original payment/shift | No if cash refund included |
| Cancellation/return/refund policy | Selected cancellation/return slice | Order/Fulfillment/Return/Refund | Minimal delivered-quantity/window/reason/approval policy | Return can defer; cancellation cannot |
| Transfer/backorder/quarantine/count variance | Inventory operations beyond reserve/sale | Inventory/Branch | Backorder off; defer advanced dispositions; define one audited adjustment flow | Mostly |
| Permission/grant/support/maker-checker | Sensitive API contracts | Identity/all business domains | Minimal use-case permission matrix and explicit scope; defer complex thresholds | Core matrix: no |
| Session duration/MFA/step-up/device | Authentication | Identity/Security | HttpOnly server session; reasonable idle/absolute expiry; defer MFA unless required | Partly |
| Reset channel/TTL/rate/password policy/verification | Forgot password | Identity/Notification | One verified channel, short single-use token, older tokens revoked | No for recovery |
| Account profile linking/guest merge | Online account/cart | Identity/Customer/Cart | One account-to-profile baseline; choose explicit guest policy before online API | No for affected flow |
| Shoe size systems/barcode/branch price | Catalog/POS/AI | Catalog/Pricing | One canonical required size system; barcode only if scanner demo; global price unless branch pricing required | Size: no; rest may defer |
| Notification channels/templates/retry thresholds | Notification slice | Notification/Security/Order | In-app first; email optional; SQL retry/failure visibility | Email may defer |
| Follow/marketing/consent/frequency | Campaign slice | Customer/Promotion/Notification | Keep optional; one snapshot/dedupe policy if demonstrated | Yes |
| API version/public ID/error/pagination | First API contracts | REST/Frontend | One consistent public ID and error/idempotency convention; no speculative multi-version support | Core convention: no |
| Vue tooling/deployment/component library | Frontend planning | Frontend | One workspace unless proven otherwise; choose installed/minimal tools later | Yes |
| Browser/POS hardware/offline mode | POS/client planning | Frontend/POS | Online in-store POS; scanner/printer optional adapters; no offline sync | Yes unless explicitly required |
| SEO/SSR/analytics/accessibility level | Customer frontend | Frontend | SPA first; accessibility baseline mandatory; defer SSR/analytics | Mostly |
| AI provider/privacy/consent/evaluation | AI slice | AI/Customer/Content/Security | No images initially; declared inputs; provider ADR and evaluation before release | Yes |
| Media hosting | Product publication/deployment | Catalog/Content | Simplest deployment-compatible storage | Until product media implementation |
| Migration tool/public ID/schema/retention/backup | Persistence planning | Database/Audit | Choose one migration tool and ID format; define minimum restore/reconciliation before release | Tool/ID before schema; retention partly |

Open decisions are generally well gated. H-03/H-04 and the core rows marked
“No” must be resolved before their dependent implementation; optional provider,
AI, marketing and production-scale infrastructure choices can remain deferred.

## 19. Architectural Smells

| Smell searched | Result |
|---|---|
| Entity owning too much | Not found; CheckoutAttempt coordinates without owning domain state |
| Controller as application service | Explicitly forbidden |
| Cross-domain repository writes | Explicitly forbidden |
| Mutable historical records | Order snapshots, movements, financial facts and audit are immutable |
| String/localized states or roles | Explicitly deprecated |
| Duplicate frontend business logic | Explicitly forbidden |
| Implicit branch scope | Replaced by explicit ownership/grants and server derivation |
| Financial status as source of truth | Avoided through append-only transactions; H-02 needs one terminal rule |
| Inventory derived from orders | Explicitly deprecated |
| AI as authority | Explicitly forbidden |
| Notification coupled to external send | Avoided through transactional intent + SQL worker |
| Reports over mutable UI models | Explicitly forbidden; H-04 needs metric completion |
| “Temporary” distributed infrastructure | None proposed |
| Generic promotion rule engine | Explicitly rejected |

### LOW-01 — Warehouse vocabulary can still drift

- **Severity:** `LOW`
- **Problem:** Documents correctly model Warehouse as a Location type but still
  sometimes list “Branch / Location / Warehouse” as peer concepts.
- **Failure scenario:** Schema/API teams create a separate Warehouse aggregate
  duplicating Location ownership.
- **Why it matters:** It can produce redundant FKs and scope checks.
- **Affected domain:** Branch/Location, Inventory and API naming.
- **Recommended resolution:** Use `Location(type=WAREHOUSE)` consistently unless
  a future ADR proves Warehouse needs independent behavior.

### LOW-02 — Grouped module names may imply ownership coupling

- **Severity:** `LOW`
- **Problem:** Logical module names such as `fulfillment-return` and
  `cart-order-checkout` group boundaries that the domain model keeps distinct.
- **Failure scenario:** Implementers let Return write Fulfillment repositories or
  let Cart become the transaction owner because they share one package/module.
- **Why it matters:** Naming can become accidental architecture.
- **Affected domain:** Source/package organization.
- **Recommended resolution:** Treat the module list as provisional and preserve
  aggregate/application boundaries even if related packages share one build
  module. Do not create extra deployables merely to fix names.

## 20. Remaining Risks

| Risk | Level | Control/exit evidence |
|---|---|---|
| Cross-domain deadlock/liveness | High until H-01 | Frozen lock hierarchy, idempotent deadlock tests |
| Refund amount leak/late success | High until H-02 | Definitive-vs-unknown transition tests |
| Divergent payable/refundable amounts | High until H-03 | Approved money/stacking/allocation examples |
| Misleading financial reports | High until H-04 | Metric glossary and reconciliation fixtures |
| Scope leakage through queries | Medium | Permission matrix and negative list/search/ID tests |
| Capstone breadth | Medium | Dependency-ordered delivery and hard optional/deferred gates |
| Provider reconciliation workload | Medium | Simulated provider, durable references, alert/runbook |
| Audit/notification SQL worker load | Low for capstone | Bounded batches/indexes/claim expiry |
| AI privacy/quality | Low while deferred | Provider/privacy/evaluation ADR before activation |

## 21. Required Changes

Before architecture freeze:

1. Resolve H-01 by defining one global lock hierarchy, duplicate demand
   normalization, protected revalidation order, and deadlock retry semantics.
2. Resolve H-02 with explicit pending-refund behavior for success, definitive
   failure, unknown, retry, late success and cancellation.
3. Resolve H-03 by freezing the minimal Order money equation and first supported
   promotion/voucher/rounding/allocation policy.
4. Resolve H-04 with a report metric/source glossary and explicit deferral of
   inventory monetary value until a cost basis exists.
5. Align password-reset token multiplicity to BR-SEC-102.
6. Clarify reserved-voucher expiry/revocation and Cart conflict behavior before
   those APIs.
7. Align Essential/Demonstration return/refund scope and freeze Spring Boot/
   Spring Security as required by the current baseline.

No source code, database migration, dependency installation, or infrastructure
work should precede these documentation decisions.

## 22. Freeze Candidates

The following decisions are architecture-safe and should become `ACCEPTED` only
after the Required Changes pass a short targeted review:

- legacy is reference-only and the new project is source of truth;
- modular monolith with one initial SQL Server consistency boundary;
- REST DTO/application/domain/infrastructure layering and separate Vue SPA;
- Product/ProductVariant and immutable SKU identity; no catalog stock;
- Order-centered commerce and immutable placed/confirmed snapshots;
- Location-owned Inventory with explicit Reservation and StockMovement;
- pessimistic quantity locks, conditional terminal reservation state and unique
  movement/operation identities, subject to H-01 lock hierarchy;
- separate Payment, Refund, Return and Fulfillment ownership;
- append-only financial/provider facts and Payment-locked refund arithmetic,
  subject to H-02;
- Branch commercial ownership, Location physical ownership, Register/Shift and
  explicit grants;
- server-side stable permissions and account/profile separation;
- HttpOnly revocable browser session baseline with CSRF and `authVersion`;
- centralized deterministic Promotion/Voucher authority, subject to H-03 policy;
- transactional immutable audit and SQL-backed reliable notification intent;
- SQL queries/projections for reports, subject to H-04 semantics;
- AI proposal/validation/preview/approval boundary;
- no Redis, broker, search cluster, microservices, Kubernetes, distributed
  transactions, event sourcing or advanced AI infrastructure without later need.

## 23. Deferred Decisions

The following do not block the correctness core:

- real payment/email/carrier providers after simulated adapters prove protocols;
- return/refund customer flow if kept outside the core milestone;
- advanced promotions, category/free-shipping/follower campaigns and public
  voucher automation beyond the first deterministic slice;
- follow marketing, email/push and advanced notification preferences;
- split tender, partial capture, split location/shipment and multi-currency;
- offline POS synchronization, scanner/printer/cash-drawer hardware integration;
- SEO/SSR, independent frontend deployments and analytics;
- dynamic page publishing and AI-generated content;
- customer AI, image foot analysis, virtual try-on, personalization provider;
- object storage, Redis, Kafka/RabbitMQ, WebSocket, search cluster,
  microservices/Kubernetes, event sourcing, vector/GPU infrastructure.

Deferral means no implementation claim; it does not remove the safe boundaries
already defined for later extension.

## 24. Final Verdict

Blueprint v1.1 is architecturally coherent and no longer requires fundamental
redesign. It is not yet safe to be marked Approved because four bounded high
issues affect transaction liveness, refund accounting, monetary determinism and
essential reporting. A focused documentation revision can close them without
changing the modular-monolith or domain-boundary direction.

## FINAL VERDICT

**APPROVE WITH CHANGES**

**Architecture can be frozen:** NO

**Implementation planning can begin:** NO

**Remaining blockers:** 0

**Remaining high risks:** 4

**Recommended next phase:** Blueprint v1.1.1 targeted correction and business
decision freeze for H-01 through H-04, followed by a short acceptance review;
then begin dependency-ordered implementation planning.
