# ADR-0032: C2 automatic promotion engine

Status: Accepted

C2 supports automatic ONLINE promotions only: `ITEM_PERCENT`, `ITEM_FIXED`,
`ORDER_PERCENT`, `ORDER_FIXED`, and `FREE_SHIPPING`. Item rules use Product
scope; buy quantity aggregates variants of scoped Products. Fixed item benefits
apply per unit and are capped by unit price. Item percentages round HALF_UP once
per OrderItem line in VND.

Candidates sort by priority descending and revision public ID ascending. One
winner applies per line, one at order level, and one to shipping; layers stack
without exclusivity. Published revisions and Order snapshots are immutable.
Checkout re-evaluates quote evidence and returns `PROMOTION_QUOTE_STALE` for a
changed result. Retirement preserves identical accepted quotes; invalidation
rejects them.

Limited rules create one usage row per revision and Order: RESERVED at checkout,
REDEEMED in verified payment success, and RELEASED by unpaid cancellation or
hold expiry. Failed attempts retain the reservation and paid financial voids
retain redemption. Unlimited rules create no usage row.

Checkout locks the account idempotency fence, promotion revision rows, inventory
reservation, then inventory balance. After inserting the Order it writes
snapshots and usage. Payment/cancellation/expiry lock Order and Payment where
applicable, then promotion usage, reservation and inventory balance. SQL Server
deadlocks are failures, not silently retried.

`PROMOTION_MANAGE` protects administration. It is absent from the
`OPERATIONS` bundle and is granted directly only to `manager.demo` in demo
data. Voucher definition, claim and redemption remain outside C2.
