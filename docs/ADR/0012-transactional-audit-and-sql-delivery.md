# ADR-0012: Transactional Audit and SQL-Backed Reliable Delivery

- Status: `ACCEPTED`
- Accepted: 2026-08-24
- MVP scope: Audit core; notification/scheduled delivery infrastructure deferred
- Date: 2026-08-23
- Decision class: Safe reliability baseline; retention/channel policies remain open
- Decision source: Architecture Review V1 H-06/H-07/H-10 resolution

## Context

Privileged actions must not commit without required audit evidence. Password
reset, order/payment, and campaign messages must not disappear between a domain
commit and an external send. Multiple backend instances must not process the
same delivery repeatedly. A capstone does not need a broker to meet these local
reliability requirements.

## Decision

### Audit

- Required `AuditEvent` is inserted in the same SQL Server transaction as the
  protected mutation; inability to persist it aborts that mutation.
- Audit records are append-only for the application role, never cascade-deleted,
  and identify human/service/integration/system actor, action, resource/scope,
  correlation, time, result, and redacted structured reason/change data.
- Credentials, reset tokens, secrets, raw payment credentials, and unnecessary
  personal/AI data are forbidden in audit payloads.

### Notification and external delivery

Automated scheduling and notification delivery infrastructure are deferred from
core MVP. The protocol below is authoritative only when a dependent optional
slice is admitted; core expiry/reconciliation remains Clock-driven and
explicitly invokable/testable.

- A business/security transaction that requires delivery inserts a durable
  `NotificationIntent` in that same transaction.
- Delivery records have a unique dedupe key and lifecycle
  `PENDING -> CLAIMED -> SENT | RETRY_WAIT | FAILED | SUPPRESSED`.
- Workers claim rows through an atomic conditional update/lock with claim owner,
  lease expiry, attempt count, next-attempt time, and last safe failure code.
- Expired claims may be reclaimed. Provider/send idempotency is used where
  available. Terminal failure remains visible for operational action.
- Mandatory security/transaction messages are not suppressed by marketing
  preferences; marketing delivery observes consent/unsubscribe and may be
  suppressed before send.

When admitted, use SQL-backed claims initially. A message broker is not required
or approved.

## Consequences

- Domain success and required audit/intent existence cannot diverge locally.
- External delivery remains eventually completed/reconciled and never controls
  transaction truth.
- Multi-instance workers are safe through conditional claims plus dedupe.
- Channel/provider choice, retry/backoff thresholds, retention/archive, payload
  encryption, mandatory-message catalogue, and operational ownership remain open.

## Risks and mitigations

- SQL worker polling can add database load: index status/next-attempt fields,
  process bounded batches, and measure before adding infrastructure.
- Poison deliveries can retry forever: cap attempts according to policy and keep
  terminal failure visible with a safe error code.
- Audit can become a shadow PII store: enforce redaction schema, permissions,
  retention review, and negative tests.
- A notification provider can accept then timeout: use provider idempotency where
  supported and retain the delivery identity for reconciliation.

## Rejected alternatives

- Best-effort in-memory events after commit.
- Sending email/webhooks inside the business transaction.
- Mutable audit history owned by each parent aggregate.
- Adding Kafka/RabbitMQ solely for preparation.
