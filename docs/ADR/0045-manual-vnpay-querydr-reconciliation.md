# ADR-0045: Manual VNPAY QueryDr reconciliation

- Status: Accepted
- Date: 2026-10-11
- Clarifies: ADR-0022 and ADR-0044
- Requirement: REQ-PAY-004 / AUD-02

## Context

A VNPAY attempt can remain `PENDING` after a lost or ambiguous callback, while a
late verified result can already be `REVIEW_REQUIRED`. Starting another payment
attempt would risk a duplicate charge. Operations therefore need one explicit,
auditable way to query VNPAY for the original transaction without weakening the
existing Order, Payment, Reservation, promotion or inventory invariants.

## Decision

An authenticated operator with persisted `PAYMENT_RECONCILE` permission and
access to the attempt's exact fulfillment Location may invoke:

```text
POST /api/v1/operations/payment-attempts/{attemptId}/reconcile
Idempotency-Key: <1..128 characters>
```

Only existing VNPAY attempts in `PENDING` or `REVIEW_REQUIRED` are eligible. The
command never creates a PaymentAttempt. It uses the stored merchant reference,
original attempt time, provider transaction number when known, and immutable
VND amount.

The provider adapter implements VNPAY QueryDr 2.1.0. The signed request contains
`vnp_RequestId`, version `2.1.0`, command `querydr`, merchant code, original
transaction reference/date, request time, server IP and order information. The
request HMAC-SHA512 source is the official pipe-delimited field order. The
response HMAC is verified before any response field is trusted. Merchant,
command, reference, amount (VND multiplied by 100), transaction type `01`, and
the provider transaction number when already known must match the original attempt.
Response code and transaction status must be exactly two digits, and the
provider transaction number must contain 1–32 digits.

Verified response mapping is:

| QueryDr response | Local outcome |
|---|---|
| response `00`, transaction status `00` | `CONFIRMED_PAID` |
| response `00`, transaction status `02` | `CONFIRMED_FAILED` |
| non-`00` response, or status `01`, `05`, `06`, `09` | `UNRESOLVED` |
| any other well-formed signed status | `REVIEW_REQUIRED` |

Invalid signatures, malformed data or mismatched identity/amount are a safe
`PAYMENT_RECONCILIATION_RESULT_MISMATCH`; transport/non-2xx failures are a safe
provider-unavailable failure. Neither changes Payment or Order state.

The workflow is three short local transactions around exactly one provider
call: claim the actor/key/attempt command; call QueryDr with no database
transaction open; persist verified evidence; then apply that saved evidence
through `VerifiedPaymentResultService`. A local SQL Server `1205` or `1222`
during apply uses ADR-0044. Explicit exact retry with the same key reuses saved
evidence and does not call VNPAY again. The server never automatically retries
QueryDr.

Idempotency is scoped by actor and key. Same actor/key/attempt replays the saved
result. Reusing that actor/key for another attempt returns
`IDEMPOTENCY_KEY_CONFLICT`. A provider outage closes that command safely; a new
explicit manual query uses a new key.

A definitive result may make only a legal existing transition. In particular,
QueryDr cannot reopen a cancelled/expired Order or restore released inventory.
An existing `REVIEW_REQUIRED` attempt remains review work when those invariants
forbid applying a confirmed result. Concurrent operators may query the provider,
but the existing financial locks and idempotency constraints permit at most one
local payment transition and one corresponding applied audit fact.

## Consequences

- Ambiguous attempts can be resolved without a blind second charge.
- Provider I/O is outside database transactions and persisted evidence survives
  a retryable local apply contention.
- Authorization is server-side and combines permission with exact Location
  scope; the UI is only a convenience gate.
- Responses and logs expose no signing secret, raw provider payload, SQL, JDBC
  or Hibernate detail.
- V35 adds the permission and durable command/evidence table.

## Exclusions

- Scheduled/background reconciliation and automatic provider polling are not
  implemented.
- Live VNPAY certification is not implemented by local adapter tests.
- No automatic provider retry, new payment attempt, refund/void orchestration,
  new payment provider or generic reconciliation framework is introduced.

## Authority

The provider wire contract follows the official VNPAY
[QueryDr/Refund documentation](https://sandbox.vnpayment.vn/apis/docs/truy-van-hoan-tien/querydr&refund.html)
and VNPAY Payment Gateway technical specification 2.1.0. Local state changes
remain governed by ADR-0022 and the current lifecycle/business rules.
