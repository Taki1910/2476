# Identity and session contract

Source: `identity/AuthController.java`, `SecurityConfiguration.java`,
`LoginRateLimitFilter.java`, `LoginRateLimiter.java`; SPA `api.ts`/`session.ts`.
Paths below start with `/api/v1/auth`.

| Method/path | Request | Response and semantics |
|---|---|---|
| GET `/csrf` | None, public | 200 `{headerName, parameterName, token}`; session/CSRF cookie |
| POST `/login` | URL-encoded `username`, `password`; current CSRF token header | 200 `{accountId, login, roles, permissions}` and session fixation protection; generic 401 `AUTHENTICATION_FAILED` |
| GET `/me` | Authenticated session | 200 current account DTO; 401 when absent/expired/revoked |
| POST `/logout` | Authenticated session and CSRF | 204; invalidates session, clears authentication and cookies |
| POST `/register` | JSON `{login,password}`; login max254; password12–72; nonblank; CSRF | 201 `{accountId,login}`; creates customer identity through application service; no automatic login |

Mutations require CSRF, including public login/register. Generic ProblemDetail
contains `code`, safe `detail`, status and correlation ID. No financial idempotency
header applies to login. Registration uniqueness is enforced; a duplicate is an
identity conflict, not a second account. Passwords and raw login failures are not
returned in error details.

## Bounded login throttle — ADR-0036

After CSRF, before password authentication, admitted attempts count atomically
against both normalized-login/peer-address (5 per15minutes) and peer-address
(30 perminute). Successful authentication also counts; denied requests do not
extend the window. `X-Forwarded-For` is not trusted. At 10,000 live buckets,
expired entries are purged and new keys fail closed without evicting live entries.
State is local to one process and resets on restart; this is not a distributed limit.

Denial is HTTP429 `AUTH_RATE_LIMITED` with positive integer-seconds `Retry-After`.
The SPA validates the header and displays localized recovery copy, falling back
to generic retry-later text when missing/invalid. No account existence distinction.
Windows reopen at their exact expiry; success does not reset counters.

Default configuration is `security.login-rate-limit` in application.yml. Only the
test profile raises limits for unrelated commerce fixtures; LoginRateLimitExternalIT
overrides those with production limits. Verification: LoginRateLimiterTest
(concurrency, expiry, capacity), LoginRateLimitExternalIT (real HTTP, CSRF, spoofed
forwarded address, success/failure, username rotation), Foundation1SecurityExternalIT;
frontend api/format tests and browser locale check.
