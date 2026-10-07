# ADR-0036: Bounded single-instance login throttling

Status: Accepted — 2026-09-13 through approval of remediation D5.

Before authentication, atomically admit a login request against both fixed windows:
normalized username + remote address: 5 requests / 15 minutes; remote address:
30 requests / 60 seconds. Count all admitted requests, including successful login.
Success does not reset either bucket; denial does not increment or extend them.
Use the account normalization function and `request.getRemoteAddr()`, not untrusted
forwarding headers. Existing/nonexistent accounts have the same throttle behavior.

Use injected Clock and at most 10,000 buckets across both key kinds. Remove expired
buckets before inserting; do not evict live buckets. If capacity is insufficient,
deny creation until an expiry frees capacity. Atomic admission must never consume
one bucket when the other rejects. Synchronize only in-memory admission, never
password hashing or database access. No additional dependency is required.

The filter runs after CSRF and before UsernamePasswordAuthenticationFilter, once
inside the security chain. Reject with HTTP 429, `AUTH_RATE_LIMITED`, the existing
ProblemDetail contract and positive integer Retry-After seconds. UI renders safe
VI/EN recovery instructions. No username/password is logged by the limiter.

This policy supports one application instance. Restart clears windows; shared
source addresses share a limit; exhausting new keys can temporarily deny new
sources. Multi-instance deployment requires an approved shared enforcement design.
Do not add Redis speculatively. Commerce fixture suites may configure higher limits;
dedicated auth acceptance must test the strict policy and default/demo stay strict.

Acceptance includes normalization, both limits, exact expiry, denial atomicity,
bounded memory, concurrent last slot, success/nonexistent-account behavior, CSRF,
Retry-After and frontend recovery. Acceptance of policy is not proof it is deployed.
