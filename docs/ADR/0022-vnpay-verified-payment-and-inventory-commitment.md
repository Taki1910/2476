# ADR-0022: VNPAY Verified Payment and Inventory Commitment

- Status: `ACCEPTED`
- Accepted: 2026-08-27
- MVP scope: Vertical Slice 4 (v10)
- Supersedes: ADR-0015 and ADR-0016 for online electronic payment
- Decision class: Payment and inventory transaction semantics

## Context

The simulated provider-result boundary does not establish trust for a public
payment provider. VNPAY initiation, Return and IPN have different purposes, and
payment confirmation races the server-owned checkout reservation deadline.
Treating successful payment as physical inventory issue would also collapse the
financial and fulfillment boundaries.

## Decision

The authenticated initiation command accepts only the owned Order and an
idempotency key. The server derives the immutable payable amount and currency,
creates one VNPAY `PaymentAttempt`, and signs the provider URL with HMAC-SHA512.
The attempt stores a unique merchant transaction reference and a provider
deadline no later than the adopted Reservation deadline. A failed attempt may
be followed by a new attempt with a new idempotency key; at most one attempt is
`PENDING` for a Payment.

The browser Return route is presentation-only. It verifies signed context and
redirects to a fixed SPA result route, but never confirms payment. Only the
public VNPAY IPN route may submit a provider result. The server validates the
signature, merchant, merchant reference, exact VND amount, response/transaction
status, provider transaction reference and payment time before entering the
application transaction.

Verified application uses the lock order:

```text
Order -> Payment -> PaymentAttempt -> InventoryReservation -> InventoryBalance
```

Separate short scalar lookups resolve correlation IDs before this transaction;
they do not retain shared locks while the mutation locks are acquired.

A verified compatible success before expiry atomically changes:

```text
PaymentAttempt PENDING -> SUCCEEDED
Order PENDING_PAYMENT  -> PAID
Reservation ADOPTED    -> COMMITTED
```

`COMMITTED` is a non-expiring allocation to the paid Order. This payment
transaction does not change `onHand` or `reserved`; physical issue remains a
later fulfillment-owned operation. A verified failure changes only the attempt
to `FAILED` and preserves Order and Reservation.

If expiry/cancellation wins, a later verified success is recorded as
`REVIEW_REQUIRED`. It cannot reopen the Order or re-reserve/recreate stock.
Automated void/refund is deferred until a provider command contract exists.
Exact compatible replay is a no-op; uniqueness on merchant and provider
transaction references backstops idempotency.

## Consequences

- `PAID` is authoritative only after verified provider success and inventory
  commitment in one local transaction.
- Return query parameters never establish financial state.
- No database lock is held while redirecting the customer to VNPAY.
- The legacy synthetic provider endpoint exists only in the `test` profile for
  historical regression fixtures and is absent from production wiring.
- Partial/split capture, automated reconciliation, void/refund and physical
  stock issue remain outside this slice.

## Rejected alternatives

- Trusting the browser Return as payment proof.
- Client-supplied amount or merchant transaction reference.
- Consuming on-hand stock in the payment callback.
- Reopening an expired/cancelled Order after a late success.
- Adding a generic payment SDK or webhook framework for one provider.
