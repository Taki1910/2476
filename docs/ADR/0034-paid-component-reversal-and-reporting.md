# ADR-0034: Snapshot paid components and historical reversal compatibility

Status: Accepted — 2026-09-13, by the user's approval to execute the remediation plan.

## Decision

D1–D4 of the [remediation master](../superpowers/plans/2026-09-13-remediation-master.md)
are accepted with their recommended options. This decision supersedes only the
conflicting monetary allocation and zero-payable scope in earlier documents.

Order owns immutable paid component facts. For each OrderItem, paid merchandise
is line gross minus its ITEM, ORDER_ALLOCATION and VOUCHER_ALLOCATION snapshots.
Parent order discounts must not be subtracted again. Paid shipping is the stored
shipping fee minus the stored shipping discount. Only strictly positive paid
components receive financial allocations; zero-price lines still participate in
inventory and fulfillment. The component sum must equal the captured Order total.
Inconsistent snapshots fail closed with `PAID_COMPONENT_SNAPSHOT_INVALID` (409).

Example: A gross 100,000 with an item discount of 50,000 and B gross 100,000
produce paid item components of 50,000 and 100,000. Shipping of 30,000 is a
separate SHIPPING component keyed by Order public ID, never part of a SKU.

New void attempts use SNAPSHOT_V2. Existing attempts are marked LEGACY_V1 without
changing amounts, identity, state or timestamps. Replay/result validation and a
definitive-failure retry retain the original calculation version. UNKNOWN and
REVIEW_REQUIRED are not permission to resend a provider request. Legacy reversals
are reported separately as unallocated legacy money; they are not reclassified
into SKU amounts without a separately approved, append-only correction design.

Extend the existing allocation table with a nullable shipping Order foreign key
and nullable item foreign key, with exactly one reference appropriate to its
type, positive amounts and unique attempt/component constraints. Keep Payment
locking and transactional capacity checks; provider calls remain outside the
transaction. Deploy the migration before the updated application; an old binary
is not a safe rollback after SHIPPING rows have been written.

Reporting preserves existing v1 settled-receipt fields. Add merchandise,
discount, shipping and reversal breakdowns. Product net uses paid merchandise
minus SNAPSHOT_V2 merchandise reversals. Capture and reversal are recognized on
their own existing event dates, using the current Vietnam date boundaries.
A reversal-only day may legitimately be negative. Authorization and branch/location
scope remain unchanged. Legacy unallocated reversal prevents interpreting product
net alone as fully reconciled historical cashflow.

## MVP scope

BR-MON-107's zero-payable confirmation is deferred. The accepted MVP requires
Order total > 0 and returns `ZERO_PAYABLE_NOT_SUPPORTED` for a zero-total quote,
without creating an Order, reservation or redemption. A free line in an otherwise
positive Order remains supported. Keep the database's positive-total constraint.

## Verification

Use M1–M10 in the [financial plan](../superpowers/plans/2026-09-13-remediation-finance-reporting.md)
as acceptance examples, including real SQL migration/constraints, service-level
cancellation/retry, reporting and frontend checks. Acceptance of this decision
does not imply that implementation or those tests have passed.
