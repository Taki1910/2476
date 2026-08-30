# ADR-0009: Use SQL Server with Versioned Migrations

- Status: `ACCEPTED`
- Accepted: 2026-08-24
- MVP scope: Core persistence baseline
- Date: 2026-08-23
- Decision class: Approved MVP persistence baseline; physical schema remains open
- Decision source: repository governance plus Blueprint v1.1 analysis

## Context

The platform needs transactional constraints for inventory, payments,
idempotency, vouchers, and audit. Legacy startup `schema.sql` is not a reliable
new-project migration history.

## Decision

Use SQL Server as the transactional database and Flyway for versioned migrations
before schema implementation. Use `BIGINT IDENTITY` internal keys and
application-generated UUID v4 public IDs; internal IDs are never exposed in
public contracts. Use Java `Instant`, SQL Server `datetime2(6)`, UTC-only
persistence, stable string status codes, optimistic versions where stale writes
matter, and exact VND `DECIMAL(19,0)`. Enforce critical invariants with foreign
keys, checks, unique constraints, and indexes.

## Consequences

- Flyway is selected but not installed or configured by this governance update.
- Every risky migration needs rollback/forward-fix and data reconciliation
  planning.
- Schema naming, retention, archival, and physical table design remain open.
- Critical financial, inventory, and audit facts are append-only with no cascade
  deletion of authoritative history. Real SQL Server is required for concurrency
  verification.

## Rejected alternatives

- Reusing legacy `schema.sql` at application startup.
- Relying only on Java validation for concurrent invariants.
