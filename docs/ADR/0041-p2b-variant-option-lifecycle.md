# ADR-0041: P2B variant option identity and sale lifecycle

- Status: `ACCEPTED`
- Date: 2026-09-28
- Extends: ADR-0004, ADR-0019, ADR-0020 and ADR-0024

## Context

The catalog could create and publish SKU rows but had no retirement path,
canonical EU size identity or database-enforced active option uniqueness.
Retirement also introduces a correctness race: a checkout or POS sale must not
establish a new transaction after the variant has stopped accepting sales, while
an already-established order/reservation/sale must not be retroactively undone.

## Decision

P2B keeps one modular catalog owner and SQL Server persistence:

- SKU is globally unique, immutable and never reused.
- The active option key is `(Product, normalizedColor, normalizedEuSize)`.
- Size remains implicit EU. Numeric strings normalize to `DECIMAL(5,2)` and
  must be positive. No size-system abstraction is introduced.
- Color normalization trims only outer whitespace and folds case under an
  explicit accent-sensitive SQL Server collation. Search aliases do not affect
  persistence identity.
- A filtered unique index permits at most one `DRAFT` or `PUBLISHED` row for an
  option while allowing multiple `RETIRED` historical SKUs.
- V31 admits `RETIRED`. V32 performs invalid-size and active-duplicate guards
  before adding persisted normalized columns, a positive-size check and the
  filtered unique index. Conflicts require explicit human remediation; no SQL
  merges inventory or reservations.

The lifecycle is exactly:

```text
DRAFT -> PUBLISHED | RETIRED
PUBLISHED -> RETIRED
RETIRED -> DRAFT
```

Lifecycle commands use optimistic entity versions and a pessimistic Variant
lock. Checkout (single and cart) and POS acquire the same lock before final sale
eligibility validation. Lock order decides the race: retirement first rejects
the new sale; sale establishment first completes and retirement does not undo
it. Hold expiry deliberately ignores Variant lifecycle.

Publication readiness requires a current positive VND price and `onHand > 0`
at an enabled Location. Availability remains separate and may be zero because
all on-hand units are reserved.

Product creation is resumable. Managers may optimistically update name,
category, collection, hero image and primary image. Public identity and
Variant ownership/SKU/color/size are immutable. Historical orders do not
currently snapshot Product name.

## Consequences

- Retirement blocks only future sales; it preserves prices, inventory,
  reservations, orders, content, merchandising and audit evidence.
- Restore returns to Draft and can conflict if a replacement active option now
  occupies the key.
- Product lifecycle remains a read projection, not another persisted state.
- Multi-system sizing, Product-name order snapshots, brand/description and
  color-linked gallery modeling remain separate future decisions.
- V32 is not integration-certified until clean and V30-upgrade paths, guarded
  rollback, collation and concurrency behavior pass on a real SQL Server.
