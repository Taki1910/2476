# ADR-0046: Scoped inventory transfer lifecycle

- Status: Accepted
- Date: 2026-10-12
- Clarifies: ADR-0004 transfer section and the former proposed transfer lifecycle
- Requirement: REQ-INV-002 / AUD-03 / REC-002 transfer scope

## Context

The accepted inventory model requires explicit location-aware transfer, but the
earlier blueprint left approval, cancellation, discrepancy and loss handling
open. AUD-03 admits only the smallest complete physical transfer that preserves
stock, provenance, authorization, idempotency and concurrency invariants.

## Decision

The implemented lifecycle is:

```text
DRAFT -> DISPATCHED -> RECEIVED
```

A transfer has distinct enabled source and destination Locations and one to one
hundred unique ProductVariant lines with strictly positive requested quantities.
The actor must hold `INVENTORY_TRANSFER` and active access to both exact
Locations for every command and replay.

Creating a Draft changes no balance. Dispatch atomically validates every line
against source `available = onHand - reserved`, then subtracts requested
quantity from source on-hand without changing reserved. It records the full
requested quantity as dispatched and appends one immutable `TRANSFER_OUT`
movement per line. The dispatched quantity is durable in-transit stock and is
available at neither Location.

Receipt is all-or-nothing. It adds every dispatched line to destination on-hand,
leaves destination reserved unchanged, records received equal to dispatched and
appends one immutable `TRANSFER_IN` movement per line. Partial receipt and
post-dispatch reversal are not admitted.

Create, dispatch and receive each require an actor-scoped `Idempotency-Key`.
The same key, action and canonical payload returns the recorded transfer; reuse
for a different action or payload returns `IDEMPOTENCY_KEY_CONFLICT`. The client
may explicitly retry an exact failed command with the same key under ADR-0044;
the server never retries automatically.

Each mutation is one local transaction. After the command identity fence, the
transfer/variant targets and every source/destination InventoryBalance are
locked in deterministic internal-ID order, with balances ordered by Location
then ProductVariant. All lines are validated before any balance changes.
`TRANSFER_OUT`, `TRANSFER_IN`, lifecycle state and audit evidence commit with
their balance changes. SQL Server `1205`/`1222` rolls the whole transaction back
and uses the safe ADR-0044 503 contract.

The REST contract is rooted at
`/api/v1/operations/inventory/transfers`: scoped list/options reads, create, and
`/{transferId}/dispatch` and `/{transferId}/receive` commands. Public UUIDs are
used at the boundary; JPA entities are not exposed.

## Consequences

- A multi-line transfer cannot partially dispatch or receive.
- Competing sales, reservations and transfers serialize on the same balances;
  canonical ordering prevents opposite-direction transfers from reversing lock
  order.
- Movement provenance retains transfer identity and source/destination evidence
  without inventing an in-transit Location.
- V36 adds the permission, transfer/line/command persistence, movement types,
  constraints and indexes.

## Exclusions

- Approval/rejection/cancellation, discrepancy, loss/damage, partial receipt and
  post-dispatch reversal workflows are not implemented.
- Generic physical Return is not implemented. It remains blocked by
  REQ-FUL-003 Return/inspection evidence and is not represented as Transfer.
- No automatic concurrency retry, background transfer processing, external
  provider call or new infrastructure is introduced.
