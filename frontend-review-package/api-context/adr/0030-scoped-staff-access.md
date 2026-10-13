# ADR-0030: Scoped staff access

Status: ACCEPTED

## Decision

Operational authority is the union of a base role bundle and direct capabilities, constrained independently by active exact-Location assignments. Phase B adds `STAFF_MANAGE_SCOPED`; it does not add a Manager role, EmployeeProfile, register assignment, or new IAM table.

Scoped staff managers may manage only Cashier and Operations accounts that share an active exact Location. They cannot change themselves or Customer, Provider, or Administrator identities. They may delegate only `POS_SELL` and `FULFILL_ORDER`, and only when they hold the delegated capability themselves. Same-branch access does not imply same-Location access.

`IDENTITY_MANAGE` remains the separate global identity boundary. Permission and assignment changes increment the target account's existing `authVersion` in the same transaction as the audit event. Registers continue to derive from enabled registers at assigned Locations, so another assignment table would duplicate authority.

## Consequences

The frontend requests server-derived manageable Locations and capabilities. It never submits an arbitrary trusted permission. Revocation invalidates stale sessions, while each operational use case continues to enforce its own permission and exact Location scope.
