# ADR-0044: Client-owned transaction-contention retry

- Status: Accepted
- Date: 2026-10-08
- Clarifies: ADR-0026, ADR-0032 and BR-LOCK-105

## Context

SQL Server can select a transaction as a deadlock victim (`1205`) or end a
lock wait (`1222`). Both failures roll back the complete local transaction.
The API needs a stable, safe signal that lets an idempotent caller decide
whether to repeat the same command without exposing SQL, JDBC or Hibernate
details.

## Decision

Only SQL Server error numbers `1205` and `1222`, found in the throwable cause
chain or `SQLException#getNextException()` chain, are transaction-contention
failures. SQLState `40001`, a Spring lock exception, or message text alone is
not sufficient classification.

After the local transaction has rolled back, the API returns HTTP `503` with:

```json
{
  "status": 503,
  "detail": "This request conflicted with another operation. Try the same action again.",
  "code": "CONCURRENCY_RETRY_REQUIRED",
  "retryable": true,
  "reuseSameIdempotencyKey": true
}
```

The caller owns the retry. A retry sends the exact same payload with the same
idempotency key. A changed payload is a new command identity and must use a new
key. The server does not automatically replay the transaction.

## Consequences

- One explicit retry cannot create a second business effect when the command's
  idempotency contract is implemented correctly.
- Non-contention database failures retain their existing error mapping and are
  not advertised as retryable contention.
- Responses and UI copy remain safe and never expose raw database details.
- Existing lock ordering and transaction boundaries remain authoritative; this
  decision adds no production schema migration.

## Exclusions

- No Spring Retry or global retry framework is introduced.
- No provider or other external API call is retried by this policy.
- No `Retry-After` header is emitted until a production wait policy is approved.
- No production lock-wait duration or automatic backoff policy is approved here.
- A critical mutation without an idempotency identity cannot adopt this retry
  contract until that blocking gap is resolved.
