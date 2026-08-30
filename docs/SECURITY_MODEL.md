# Security Model — Blueprint v1.1.1

> Architecture status: **ACCEPTED FOR THE APPROVED MVP BASELINE**
>
> MVP scope and gates: [MVP_IMPLEMENTATION_BASELINE.md](MVP_IMPLEMENTATION_BASELINE.md)

## Security objectives

- Protect customer, employee, financial, inventory, credential, and audit data.
- Enforce ownership and branch/location scope at the server use-case boundary.
- Make authentication, authorization, financial, and sensitive operational
  actions auditable.
- Make retries, callbacks, password recovery, and session revocation safe under
  concurrency.

## Identity and profile separation

```text
UserAccount --optional link--> CustomerProfile
UserAccount --optional link--> EmployeeProfile --assignments--> Branch/Location
```

- `CONFIRMED`: Credentials, account status, authentication version, and sessions
  belong to `UserAccount`.
- `CONFIRMED`: Customer/employee profiles do not grant permission themselves.
- `CONFIRMED`: Disabled profile and disabled account are distinct facts with
  explicit use-case effects.
- Approved MVP baseline: password change, account disable, and grant change
  increment `authVersion`; sessions with an older version are rejected.
- `OPEN DECISION`: Whether one account may link to both profile types and how
  employee departure affects a separate customer relationship.

## Authorization and multi-branch scope

Authorization is an intersection, not a role-name check:

```text
allow = authenticated
     AND permission granted
     AND resource ownership/scope satisfied
     AND use-case policy/threshold satisfied
```

- Use stable role and permission identifiers, never localized text.
- Customer access is limited to owned resources unless a specific support use
  case grants otherwise.
- Staff access combines permissions with branch/location assignments.
- The server derives or verifies `responsibleBranchId`, allocation `locationId`,
  register, and shift. A client-provided identifier never expands access.
- Shared warehouses require explicit location grants or transfer/fulfillment
  routes; membership in one branch does not imply access to every location.
- Finance, audit-read, inventory-adjustment, price/promotion activation, and
  identity administration are separate permissions.
The minimum MVP permission vocabulary is:

```text
IDENTITY_MANAGE
CATALOG_BROWSE
CATALOG_MANAGE
PRICE_MANAGE
INVENTORY_VIEW
INVENTORY_ADJUST
CHECKOUT_RESERVE
ORDER_PLACE
POS_SELL
FULFILL_PICKUP
ORDER_VIEW_SCOPED
ORDER_CANCEL
REPORT_VIEW
PAYMENT_EVENT_APPLY
PAYMENT_INITIATE
```

`CATALOG_BROWSE`, `CHECKOUT_RESERVE`, and `ORDER_PLACE` are deliberate Customer
bundle grants for authenticated storefront browsing, checkout reservation, and
owned Order creation/read. Quote ownership is concealed as not found; Order
ownership is checked server-side. These grants do not expose exact stock,
Location choice, another customer's quote/reservation/Order, staff mutation, or
Payment authority. Operational permissions do not imply storefront access.

Customer access is ownership based. Cashier has `POS_SELL`, scoped Order
visibility, and assigned Register/Shift access. Operations receives only an
explicit combination of catalog, pricing, inventory, fulfillment, cancellation,
and reporting permissions. Administrator receives `IDENTITY_MANAGE` plus only
separately granted operational permissions. Provider has
`PAYMENT_EVENT_APPLY` as an integration identity only. Mandatory audit writing
is a system obligation, not an actor permission. Customer payment initiation
uses ownership plus `PAYMENT_INITIATE`; it never grants or reuses provider
authority.

Pickup-fulfillment creation and starting picking require persisted
`FULFILL_PICKUP` and an active assignment to the exact enabled Location derived
by the server from the Order/fulfillment.
A Branch-only assignment, another Location in the same Branch, or an assignment
in another Branch is insufficient. Disabled Branch/Location scope is denied;
relocation is a separate business decision.

`OPEN DECISION`: Exact role bundles, assignment rules, cross-branch grants,
thresholds, maker-checker actions, emergency access, and break-glass auditing.

## Browser authentication baseline

- The single-instance MVP uses the standard server-side servlet session in a
  `Secure`, `HttpOnly` cookie with appropriate `SameSite` policy and CSRF
  protection for unsafe browser requests.
- Session identifiers are random, rotated after authentication/privilege change,
  revocable, and never stored in browser-readable persistent storage.
- Authentication and privilege change rotate the session ID; logout invalidates
  the current session.
- Each authenticated request validates account/profile/assignment status,
  expiry, session `authVersion`, current grants, ownership, Branch, and Location.
- Spring Session JDBC is `DEFERRED` until multi-instance deployment, durable
  sessions, restart persistence, or remote-session management requires it.
  Redis is not part of the MVP baseline.
- `OPEN DECISION`: Absolute/idle duration, concurrent-session policy, MFA,
  trusted devices, and step-up authentication.

If a future non-browser/mobile client requires bearer tokens, it needs a later
ADR covering issuance, rotation, revocation, storage, and client threat model;
the current Blueprint does not silently select JWT.

## Forgot-password lifecycle — optional follow-on

Password reset is `SHOULD / OPTIONAL`, not core MVP. If admitted, the accepted
security boundary below applies.

### Request

1. Accept an identifier and return the same public response whether an account
   exists or not.
2. Apply rate limits by safe combinations of IP/device/account signal and record
   an audit/security event without logging the identifier unnecessarily.
3. For an eligible account with a verified recovery channel, generate a
   cryptographically random token, store only its hash, purpose, account,
   creation/expiry, and unused state, then queue notification after commit.
4. Creating a new token revokes every older active token for the same account
   and purpose in the issuance transaction.

### Complete

1. Hash the presented token and lock the matching token/account records.
2. Verify purpose, expiry, unused state, and eligible account atomically.
3. Apply password policy, store a modern adaptive password hash, mark the token
   consumed, revoke other reset tokens, increment `authVersion`, and record the
   event in one database transaction.
4. Notify the owner after commit. Never auto-login solely from a reset token.

Concurrent completion attempts must yield one success. Tokens and reset URLs
must never appear in logs, audit payloads, analytics, or referrers.

`OPEN DECISION`: Recovery channel, token TTL, rate-limit thresholds, password
policy, whether to retain the current session, and recovery for users without a
verified channel.

## Credential and sensitive-data rules

- MVP passwords use BCrypt with configurable cost and encoded algorithm prefix.
  Plaintext, reversible encryption, security questions, and permanent legacy
  fallback are forbidden.
- Secrets come from approved runtime configuration, not repository files.
- Payment card data is delegated to the provider where possible; store only the
  minimal approved references and display metadata.
- Mask sensitive customer/payment data according to actor and use case.
- Define retention/deletion policies before storing foot images, measurements,
  prompts, provider payloads, or detailed security signals.

## API, provider, and job security

- Validate transport DTOs, content type/size, pagination limits, and identifiers
  at the boundary; validate business invariants again in the use case.
- VNPAY initiation is authenticated, CSRF-protected and owner-scoped. The client
  cannot submit amount, currency, merchant reference or a success result.
- VNPAY IPN is the only public mutation endpoint. It has an exact CSRF exemption
  and validates HMAC-SHA512, merchant, reference, exact amount/currency,
  transaction status and bounded provider fields before application logic.
- Browser Return only maps verified context to a fixed SPA route. It never trusts
  success-looking query parameters and never changes financial state.
- Provider secrets come only from runtime configuration; URLs, responses, logs
  and audit evidence exclude the signing secret and raw customer credentials.
- Authenticate provider callbacks, preserve raw evidence as allowed, and reject
  replay through a unique provider-event key.
- The implemented Slice 5 endpoint accepts an authenticated integration account
  and rechecks persisted `PAYMENT_EVENT_APPLY`; Provider role text is not an
  authorization bypass and no Branch/Location grant is required.
- Its minimal payload contains only provider event ID, PaymentAttempt public ID,
  and outcome. Amount, currency, Order, owner, and inventory are server-resolved.
- Verify event-to-merchant/payment/amount/currency association before changing
  financial state; contradictory or out-of-order events enter reconciliation.
- CORS is an explicit allowlist; CSRF protects cookie-authenticated mutations.
- Scheduled workers and integrations use least-privileged service identities.
- Rate limiting is defense-in-depth, not a substitute for idempotency or database
  constraints.

## Audit and notification boundary

At minimum audit account/grant changes, authentication/recovery events,
inventory adjustments/transfers, price/promotion activation, order overrides,
payment/refund actions, shift reconciliation, AI/content publication, and
sensitive administration. Audit payloads exclude credentials, tokens, secrets,
and unnecessary personal/provider data. Audit reads require permission.

Security notifications are queued only after committed state changes and are
deduplicated. Delivery failure cannot roll back a completed password reset or
payment; it creates an operational retry/alert.

## Threats explicitly blocked

- IDOR and client-selected cross-branch/location access.
- Frontend-only authorization or localized-role parsing.
- A login session being treated as a POS cashier shift.
- Plaintext/reversible passwords or reusable reset tokens.
- Session survival after account revocation through stale authorization data.
- Duplicate/replayed payment, refund, voucher, or webhook processing.
- AI output invoking privileged mutations or publishing itself.
- Audit deletion through parent cascade or secret leakage through logs.

## Verification gates

- Unit tests for authorization policies and security state transitions.
- Integration tests for ownership/scope filters, CSRF/session rotation,
  `authVersion` revocation, reset-token single use, provider replay, and
  database uniqueness.
- Negative API tests for customer ownership and cross-branch/location access.
- Security review before authentication, payment/refund, uploads, or AI-provider
  integration is released.

## Open decisions

- Exact role bundles, assignment rules, thresholds, and branch/location
  inheritance beyond the approved minimum permission vocabulary.
- MFA, step-up authentication, session durations, concurrent-session policy,
  and device policy.
- Recovery channel/TTL/rate limits and verified-contact ownership.
- CORS origins, encryption/key management, secrets platform, log retention, and
  payment compliance boundary.
