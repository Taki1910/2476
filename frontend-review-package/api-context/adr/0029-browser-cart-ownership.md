# ADR-0029: Browser cart ownership and login merge

- Status: `ACCEPTED` for Phase A customer UX hardening
- Date: 2026-09-05
- Extends: ADR-0026
- Decision class: safe to freeze for the current client-side cart

## Context

The current SPA persists cart display metadata under one browser-wide key. A
logout leaves that cart active, so a guest or another account on the same browser
can see and mutate the previous customer's demand. The backend has no durable Cart
aggregate yet; authoritative price, stock, checkout ownership and idempotency
already remain server-owned.

## Decision

Persist one versioned cart envelope per owner: `guest` or the authenticated
account public ID. The envelope repeats its owner and is restored only when that
owner matches the active state.

On login, merge the guest cart into the account cart by normalized variant ID.
Account metadata and ordering win; quantities add and cap at the confirmed limit
of 10 per variant; new guest lines append until the 50-line limit. Consume the
guest envelope after the merge so refresh cannot merge it twice.

On logout or session expiry, switch active state to a clean guest cart. Keep the
account envelope isolated so that the same customer can resume it later. Account
switching loads only the new owner's envelope. Discard the old unowned storage
key because its customer cannot be established safely.

A late checkout response may remove purchased units only when the submitted
account still owns the active cart. Checkout command recovery remains separately
stored per account and retains its existing idempotency key.

## Consequences

- No server schema or API change is required.
- Client cart amounts remain display metadata and never become checkout authority.
- Clearing browser storage still removes carts; cross-device carts remain deferred.
- Client storage protects UI isolation, not confidentiality from a user with
  browser developer access. A server Cart is required if durable cross-device or
  stronger at-rest privacy becomes a confirmed requirement.

## Rejected alternatives

- One shared browser cart: leaks demand across authenticated identities.
- Blindly adopt the old unowned key: its owner cannot be proven.
- Add a server Cart now: no current requirement justifies the schema and API scope.
