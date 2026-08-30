# ADR-0006: Separate Identity Profiles and Enforce Scoped RBAC

- Status: `ACCEPTED`
- Accepted: 2026-08-24
- MVP scope: Core architecture baseline; password reset optional
- Date: 2026-08-23
- Decision class: Safe identity/scope boundary; permission catalogue remains open
- Decision source: Blueprint v1.1 security and branch-scope resolution

## Context

Customers, employees, administrators, registers, branches, and shared locations
have different ownership and access rules. Legacy account-to-employee coupling,
localized role text, and a single current-user assumption cannot secure multiple
clients or cross-branch operations.

## Decision

Separate `UserAccount`, `CustomerProfile`, and `EmployeeProfile`. Use stable
roles/permissions for capability and explicit assignments/grants for scope.
Authorize at the application use-case boundary using the intersection of:

```text
authentication + permission + ownership/scope + business threshold/policy
```

### Scope model

- `Branch` is the commercial/organizational owner of an order.
- `Location` is the physical stock node and may be a sales floor or warehouse.
- A shared location requires explicit access or fulfillment/transfer routes.
- `POSRegister` belongs to one branch and one sales-floor location.
- `CashierShift` is a business session linked to an employee/register; it is not
  the authentication session.
- The server resolves or verifies order branch, location, register, and shift;
  client-supplied IDs never expand access.
- Customers access their own resources. Staff access to customer/order data is
  use-case and permission limited.

### Authentication lifecycle

First-party browser clients use the standard server-side servlet session with a
Secure HttpOnly cookie, appropriate SameSite policy, and CSRF protection.
Authentication or privilege change rotates the session ID; logout invalidates
the current session. `UserAccount.authVersion` invalidates older sessions after
password change, account disable, grant change, or approved security action.
Sensitive requests re-evaluate current grants, ownership, Branch, Location,
account, profile, and assignment state.

Spring Session JDBC is deferred until multi-instance deployment, durable
sessions, restart persistence, or remote-session management requires it.

The minimum permission vocabulary is `IDENTITY_MANAGE`, `CATALOG_MANAGE`,
`PRICE_MANAGE`, `INVENTORY_VIEW`, `INVENTORY_ADJUST`, `POS_SELL`,
`FULFILL_PICKUP`, `ORDER_VIEW_SCOPED`, `ORDER_CANCEL`, `REPORT_VIEW`, and
`PAYMENT_EVENT_APPLY`. Administrator has no implicit business bypass; operational
permissions are separately granted.

Password reset uses an expiring single-use random token whose hash is stored. The
password change, token consumption, other-token revocation, `authVersion`
increment, and audit record are atomic. Public request responses do not reveal
account existence.

## Consequences

- UI routing/visibility is non-authoritative; APIs enforce ownership and scope.
- Authorization filters/checks need negative cross-branch/location tests.
- Exact role bundles, assignments, inheritance, support access, maker-checker
  rules, monetary/quantity thresholds, MFA, session timeouts/concurrency, and
  recovery details remain open.
- A future mobile/bearer-token model requires a separate ADR; JWT is not implied.
- Permanent plaintext-password fallback is forbidden.

## Risks and mitigations

- Stale grants/sessions: use current grants and `authVersion` at request time.
- Client-selected branch/location: derive scope server-side and recheck target
  resources before mutation.
- Overpowered administrators: require explicit grants and auditable sensitive
  operations; define break-glass separately if needed.
- Shared warehouse ambiguity: maintain explicit location grants/routes and report
  sales branch separately from stock location.

## Rejected alternatives

- Localized role strings or controller-only checks.
- Branch membership as permission to every shared location/customer.
- One employee login session as a cashier shift.
- Long-lived browser-readable credentials.
