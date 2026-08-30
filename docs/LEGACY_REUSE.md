# Legacy Reuse — Blueprint v1 Baseline

## Scope and authority

The audited legacy application lives under `QLCHGiay/`. It is read-only
reference material and is lower authority than current requirements, ADRs, and
Blueprint rules. This document records reusable knowledge, not permission to
copy its architecture.

## Legacy inventory

- Java 17, Spring Boot, Spring MVC, Thymeleaf, Spring Data JPA, Spring Security,
  Bean Validation, SQL Server, Lombok.
- Single monolith organized by controller/service/repository/model/config.
- Server-rendered internal staff UI; no REST API or separate customer SPA.
- Domains present: product-like rows/attributes, customer, employee/account,
  supplier, promotion, order/invoice/lines, payment, work session,
  notification, invoice history, reports.
- Tests cover many controller/rule cases but rely heavily on Mockito/template
  inspection; concurrency and database integration coverage is insufficient.

## Classification

| Legacy component | Status | Reuse level | Reason | Required treatment |
|---|---|---|---|---|
| Product vocabulary and attributes | Valuable | B — Refactor | Useful catalog knowledge | Split Product/ProductVariant; add SKU; remove stock |
| Category/Color/Material/Size | Valuable | B — Refactor | Reusable master data concepts | Remove stock columns; define uniqueness/status |
| Customer archive/snapshot intent | Valuable | B — Refactor | Preserves history | Separate account/profile; confirm contact/age rules |
| Employee profile/inactive intent | Valuable | B — Refactor | Useful workforce history | Separate account, roles, branch assignment |
| Promotion calculation tests | Valuable | B — Refactor | Server-side deterministic intent | Central pipeline; scope/stacking/usage rules |
| Invoice/order-item snapshots | Valuable | A/B | Strong historical behavior | Move into confirmed OrderItem/document snapshots |
| Quantity/non-negative DB checks | Valuable | B — Refactor | Critical integrity backstops | Apply to new ownership/schema |
| Pessimistic product locking concept | Reference | C | Shows concurrency awareness | Re-design around InventoryBalance/location |
| Notification recipient/dedupe concept | Reference | B/C | Useful primitive | Add event, channel, preference, delivery state/scope |
| Invoice edit history concept | Reference | B | Useful audit intent | Replace with immutable cross-domain AuditEvent |
| Report metrics | Reference | C | Business questions may be useful | Build read models/queries over new facts |
| Thymeleaf UI/theme/forms | Reference | C | Internal UX examples | Rebuild separate Customer/POS/Admin SPA areas |
| Repositories/controllers/entities | Unsuitable | D — Rewrite | Architecture and boundaries conflict | Do not copy |
| `SanPham.tonKho` | Forbidden | E — Deprecated | Locationless stock | Replace with InventoryBalance |
| Lookup-table stock columns | Forbidden | E — Deprecated | Duplicated, meaningless ownership | Drop from new schema |
| Inferred reservation from unpaid invoice | Forbidden | E — Deprecated | No explicit record/expiry/location | Explicit Reservation |
| `DonHang -> HoaDon` duplication | Forbidden | E — Deprecated | Conflicting mutable totals/status | Order-centered model |
| Single cash payment per invoice | Forbidden | D/E | Cannot support retries/providers/refunds | Payment domain rewrite |
| Localized role/status parsing | Forbidden | E — Deprecated | Unsafe and unstable | Stable IDs/enums and transitions |
| Legacy plaintext password fallback | Forbidden | E — Deprecated | Security debt | Controlled migration/reset only, never permanent |
| Legacy login/work-session architecture | Unsuitable | D — Rewrite | Browser login conflated with cashier shift | Account/session and POS shift separation |

## Domain-specific findings

### Catalog

Legacy `SanPham` is effectively a ProductVariant while UI groups rows by
product name. `ChiTietSanPham` mixes supplier, media, brand, origin, and status
without clear cardinality. Reuse terminology/data only after mapping and
deduplication.

### Inventory

On-hand lives on `SanPham`. Available stock is inferred as on-hand minus unpaid
invoice lines; reservations have no row, location, expiry, owner, or lifecycle.
Payment locks product rows and decrements stock transactionally, but it may
consume stock treated as committed by another unpaid invoice. This
implementation must not migrate.

### Order and payment

Creating an unpaid invoice also creates a duplicated order. Payment recalculates
promotion, decrements stock, marks order/invoice paid, and creates one hardcoded
cash payment. Snapshot logic is worth preserving; aggregate and financial
implementation must be rewritten.

### Security

BCrypt, server-side route checks, failed-login tracking, and owner-scoped
notification reads show useful intent. Binary string roles, session-stored
entities, manual-only recovery, special admin behavior, and plaintext legacy
comparison are reference-only or deprecated.

### Frontend

Legacy screens target internal staff. Product/customer form validation and
information hierarchy may inspire the new UI, but flows and templates are not
compatible with Vue REST-first customer/POS/admin experiences.

## Legacy-only business rules awaiting confirmation

- Customer purchase age 15.
- Employee age 18.
- Vietnamese-only mobile format.
- Price step of VND 1,000.
- Active product requires image.
- Promotion non-overlap and best-price-wins.
- Cancellation limited to unpaid invoice.
- Login warning/lock thresholds.

These rules must not enter implementation until promoted to `CONFIRMED` in
`BUSINESS_RULES.md` by an explicit decision.

## Migration knowledge dependency

```text
Legacy data mapping
  -> Catalog identity cleanup
  -> ProductVariant/SKU mapping
  -> Location inventory opening balances
  -> Customer/Employee profile mapping
  -> Historical order/item/payment snapshot import policy
```

Actual data migration scope, cutover, reconciliation, and whether historical
transactions are imported or retained read-only are `OPEN DECISION`.

## Final disposition

- `KEEP`: validated historical snapshot intent and critical integrity intent.
- `REFACTOR`: catalog vocabulary, customer/employee profile rules, promotion
  calculation concepts, notification/audit concepts.
- `REWRITE`: inventory, order, payment/refund, identity/authorization, API,
  reporting, and frontend workflows.
- `DROP`: duplicate stock columns, inferred reservations, invoice-centric order
  duplication, localized state/role logic, plaintext compatibility.
- `BUILD NEW`: branches/locations/registers, online fulfillment, returns,
  refunds, vouchers, cross-domain audit, customer security, REST/SPA, and AI
  governance.
