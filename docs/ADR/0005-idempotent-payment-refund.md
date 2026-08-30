# ADR-0005: Separate Idempotent Payment and Refund Domains

- Status: `ACCEPTED`
- Accepted: 2026-08-24
- MVP scope: Payment/Void core; Return/Refund product workflow deferred
- Date: 2026-08-23
- Decision class: Safe accounting boundary; provider/tender policies remain open
- Decision source: Blueprint v1.1.1 H-02/H-03 plus AR-01 correction

## Context

Payments and refunds may be asynchronous, retried, partially successful,
reported out of order, or delivered after an order expires. The legacy
one-payment-per-invoice cash model cannot preserve provider evidence or stop
concurrent refunds from exceeding captured value.

## Decision

Payment owns a logical `Payment`, one or more `PaymentAttempt`s, append-only
financial transactions/events, provider references, and reconciliation state.
Refund is separate and references eligible successful capture/payment
transactions. Return and restock state remain outside the financial aggregate.

### Financial invariants

For a currency-consistent payment:

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

For each immutable paid component `c`:

```text
successfulVoid(c) + successfulRefund(c)
  + activeVoid(c) + activeRefund(c) <= capturedComponentCapacity(c)
remainingComponentCapacity(c) = capturedComponentCapacity(c)
                              - successfulVoid(c)
                              - successfulRefund(c)
                              - activeVoid(c)
                              - activeRefund(c)
```

Every active reversal attempt has positive immutable component allocations whose
sum equals its reserved amount. The MVP's one full capture or full cash
settlement establishes `capturedComponentCapacity(c)` from the immutable Order
paid component. Partial and split capture are `DEFERRED`; later support requires
immutable successful-capture component allocations first.

An order can be financially confirmed only from verified successful tender facts
whose net accepted amount satisfies the order amount under the approved tender
policy. Historical successful transactions are append-only; corrections use new
events/transactions, not destructive edits.

A successful financial void is an immutable reversal of previously successful
captured exposure. Authorization-only cancellation is a separate fact and does
not change captured exposure. Void and Refund consume the same financial
capacity under the Payment lock. Before an external void, its target is recorded
as `activeVoidReserved`; unknown retains it, definitive failure releases it once,
and success converts it to `successfulVoided`. Successful void stores immutable
component allocations from remaining Order paid allocations for reporting.

### Idempotency and locking

- Payment and Void initiation use client operation identity and request
  fingerprint. Refund initiation does so only if the deferred Refund slice is
  admitted.
- Each provider event has a unique provider/merchant/event key.
- Starting a refund locks the relevant `Payment`, recomputes refundable amount,
  and reserves the pending amount in one database transaction before any external
  call.
- Provider timeout or unknown outcome retains that attempt's active reservation
  and blocks a blind retry until reconciliation.
- Success atomically converts that attempt's active reservation into a successful
  refunded fact. A contractually definitive failure releases only that attempt's
  reservation exactly once under the Payment lock.
- Retry keeps the logical Refund and client operation identity but creates a new
  immutable RefundAttempt/provider idempotency reference after capacity is
  reacquired. An old attempt event cannot release or complete a newer attempt.
- Applying provider outcomes locks the affected financial records, validates
  merchant/payment/amount/currency association, records evidence, updates the
  lifecycle conditionally, and queues side effects atomically.
- Identical duplicates return the recorded result; contradictory/out-of-order
  events create `RECONCILIATION_REQUIRED` and do not overwrite a known fact.
- A void and Refund cannot reserve overlapping captured exposure. Both use the
  same Payment lock, amount guard and immutable attempt/provider identities.
- Attempt allocations have only `ACTIVE -> SUCCEEDED` and
  `ACTIVE -> RELEASED`. Unknown provider state retains `ACTIVE`. Success or
  definitive failure converts every allocation plus the attempt aggregate in
  one Payment-lock transaction; partial conversion/release is forbidden.
- Provider outcomes correlate to the exact attempt generation. Reports consume
  only `SUCCEEDED` allocations; callbacks and reports never reconstruct them.

### Cash

Cash is a real tender recorded against an active cashier shift/register, not a
fake external-provider transaction. For the limited MVP POS sale, payment
success, Order confirmation, Inventory commit, and Shift cash entry share one
local database transaction. Voucher redemption joins that transaction only if
the optional Voucher slice is admitted. Cash drawer variance/approval policy
remains open.

## Consequences

- A provider call never runs inside a database transaction. Durable pending state
  precedes the call; authenticated callback/poll/reconciliation completes it.
- Refund success does not imply returned or restocked inventory.
- Refund amount is derived from immutable `VND_V1` OrderItem/service allocations
  remaining after successful Void/Refund allocations and active reversal fences;
  current Promotion/Voucher definitions are never recalculated.
- Late payment after order expiry/cancellation cannot silently reopen the order;
  it follows a governed void/refund/reconciliation path.
- Unique keys and financial constraints require integration and concurrency tests.
- Provider choice, void capability/timing and outcome mapping, tender list,
  non-zero tax/legal invoice, refund approval thresholds,
  and cash policy require business confirmation.
- Partial and split capture are explicitly deferred from MVP.

## Risks and mitigations

- Provider state may differ from local state: preserve provider evidence and run
  deterministic reconciliation.
- Duplicate callbacks/commands: database uniqueness and idempotent state
  transitions make retries safe.
- Concurrent refunds: active amount reservation under a Payment lock prevents
  over-refund.
- Concurrent void/refund: active void reservation and the shared capacity guard
  prevent double reversal of the same captured exposure.
- Notification failure: durable after-commit work retries independently and does
  not reverse financial truth.

## Rejected alternatives

- One mutable payment row per order/invoice.
- Updating only an Order payment-status field from a callback.
- Calling a provider inside the order transaction.
- Treating return, refund, and restock as one status.
- Representing cash as a successful provider callback.
