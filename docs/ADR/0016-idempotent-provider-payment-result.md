# ADR-0016: Apply an Idempotent Provider Payment Result

- Status: `SUPERSEDED` by ADR-0022 for online electronic payment
- Accepted: 2026-08-25
- MVP scope: Vertical Slice 5 only
- Decision class: Explicit current slice requirement implementing ADR-0005
- Extends: ADR-0014 reservation lifecycle and ADR-0015 payment lifecycle

## Context

A durable pending PaymentAttempt needs one trusted provider-result boundary that
cannot double-consume inventory or race Order cancellation into mixed state.
Provider transport, retries, reconciliation, refund, and fulfillment remain
separate decisions.

## Decision

An authenticated account with persisted `PAYMENT_EVENT_APPLY` may submit only an
opaque provider event ID, PaymentAttempt public UUID, and `SUCCESS` or `FAILURE`.
The provider account UUID plus exact case-sensitive event ID is the idempotency
scope. A durable `PaymentProviderEvent` receipt records the request fingerprint
and its applied or rejected disposition. Identical replay returns the recorded
result; mismatched reuse conflicts.

Application follows the shared lock order:

```text
ProviderEvent claim -> Order -> Payment -> PaymentAttempt
                    -> InventoryReservation -> InventoryBalance
```

`SUCCESS` atomically performs:

```text
PaymentAttempt PENDING       -> SUCCEEDED
Order PENDING_PAYMENT        -> PAID
Reservation ADOPTED          -> CONSUMED
onHand                       -> onHand - quantity
reserved                     -> reserved - quantity
```

Because both quantities decrease equally, `available = onHand - reserved` is
unchanged. `FAILURE` performs only `PaymentAttempt PENDING -> FAILED`; Order,
Reservation, and inventory remain unchanged. `SUCCEEDED`, `FAILED`, and
`CANCELLED` attempts are terminal.

A cancellation winner leaves the attempt cancelled and reservation released;
a later provider result is durably rejected and cannot reopen it. A success
winner makes the Order paid and blocks the existing unpaid cancellation command.
Required payment audit and all state changes share the transaction.

## Consequences

- Provider event application is permission-based, not a role-name bypass.
- Retry after a failed attempt is blocked until an explicit retry policy exists.
- `PAID` means paid inventory consumption only, not fulfillment or completion.
- `CONSUMED` is the current single-line reservation's physical sale terminal;
  no StockMovement ledger is introduced by this bounded slice.
- Provider calls/signatures, raw payload storage, adapters, reconciliation,
  PaymentTransaction expansion, void/refund, and fulfillment remain deferred.

## Rejected alternatives

- Updating only Order status from a callback.
- Releasing reserved without decrementing on-hand.
- A generic provider/event framework or gateway adapter.
- Reopening cancelled state or automatically refunding a late result.
