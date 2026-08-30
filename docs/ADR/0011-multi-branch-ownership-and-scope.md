# ADR-0011: Define Branch, Location, Register, and Order Ownership

- Status: `ACCEPTED`
- Accepted: 2026-08-24
- MVP scope: Core architecture baseline
- Date: 2026-08-23
- Decision class: Safe ownership vocabulary; allocation/grant policies require confirmation
- Decision source: Architecture Review V1 blocker resolution

## Context

The new platform must support multiple branches, warehouses, POS registers, staff
assignments, online fulfillment, and reports. Using branch and warehouse as
interchangeable fields creates authorization leaks, ambiguous revenue, and stock
that cannot be allocated consistently.

## Decision

- `Branch` is a commercial/organizational unit and responsible owner of an Order.
- `Location` is a physical inventory node with a type such as `SALES_FLOOR` or
  `WAREHOUSE`.
- Every Order has exactly one server-assigned `responsibleBranchId`.
- Each first-release OrderItem is allocated from exactly one Location; different
  lines may use different locations only if later confirmed. Split quantity of a
  single line across locations is deferred.
- A warehouse may be shared across branches through explicit access/fulfillment/
  transfer grants; it is not duplicated merely to manufacture branch ownership.
- `POSRegister` belongs to exactly one Branch and one sales-floor Location.
- `CashierShift` links employee/register/tender accountability and is independent
  from authentication session.
- Staff authorization combines permission with assignments/grants. Clients can
  request a context but cannot grant themselves branch/location access.
- Customer identities are organization-wide; staff access to customer/order data
  is controlled by use case and permission.

Reports and audit identify both commercial branch and physical stock location
where relevant. Transfer, fulfillment, sale, payment, and refund dates are not
collapsed into one business date.

## Consequences

- POS branch/location/register are deterministic from the active shift.
- Pickup and delivery need a server allocation policy to assign responsible
  branch and stock location.
- Shared-location queries and mutations require explicit scope checks.
- Schema/API/report contracts must name branch and location semantics rather than
  a generic `storeId`.
- Allocation priority, cross-branch support/finance access, virtual locations,
  damaged/quarantine stock, and shared-warehouse charging remain open.

## Risks and mitigations

- Ambiguous online ownership: choose and document one allocation policy before
  implementation; persist the result on Order.
- Cross-branch data leakage: authorize target resources server-side and test
  negative access paths.
- Reporting double count: define measures by immutable events and keep branch and
  location dimensions explicit.
- Over-modeling: start with sales-floor/warehouse and explicit grants; defer
  organization hierarchy and arbitrary location graphs.

## Rejected alternatives

- `Branch == Warehouse == Location`.
- Trusting a client-sent branch/location as authorization.
- Global stock with a branch display filter.
- One register/one user assumptions.
- Duplicating a physically shared warehouse per branch.
