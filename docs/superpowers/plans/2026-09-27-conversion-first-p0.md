# Conversion-first P0 Implementation Plan

**Goal:** Make delivery checkout capture a complete demo-resolved address and
make payment/address failures semantically correct without changing commerce
money, inventory, promotion or fulfillment lifecycles.

**Architecture:** Extend the existing ProblemDetail contract; place one
replaceable AddressResolver boundary in the shipping module; preserve district
shipping selection; snapshot the resolved address on the existing fulfillment
aggregate; map VNPAY configuration failure to a safe 503.

**Spec:** `docs/ADR/0040-semantic-errors-and-demo-address-resolution.md`

## Global constraints

- The dirty `codex/remediation-audit-20260913` working tree is the accepted,
  user-owned baseline. Do not reset, clean, stage or commit unrelated work.
- Work in the current checkout because an isolated worktree would omit the
  accepted uncommitted baseline.
- No external address/geocoding dependency and no shipping-rule, promotion,
  inventory or payment-state change.
- Every behavior change follows observed RED → minimal GREEN → regression.

### Task 1: Stabilize boundary and VNPAY errors

**Files:** `ApiExceptionHandler`, `InvalidRequestException`, new safe provider
exception, `VnPayPaymentProvider`, `VnPayCrypto`, frontend `api`/`format`, and
their focused tests.

- [x] RED: validation exposes field identifiers; VNPAY configuration maps to
  `PAYMENT_PROVIDER_UNAVAILABLE`/503 with no secret or configuration detail.
- [x] GREEN: extend ProblemDetail parsing/mapping and raise the safe exception at
  the provider boundary.
- [x] Verify payment idempotency remains unchanged and no failed initiation is
  persisted.

### Task 2: Resolve and persist a complete delivery snapshot

**Files:** Flyway V30, shipping resolver/controller/service, cart quote/order/
fulfillment models and checkout integration tests.

- [x] RED: list wards; resolve valid hierarchy; reject unknown province,
  district and ward distinctly; preserve unsupported-area distinction.
- [x] RED: checkout matches ward with quote and retains receiver, hierarchy
  labels/codes, address line, canonical address and note.
- [x] GREEN: add the SQL-backed demo resolver, ward quote evidence and immutable
  fulfillment snapshot using the existing transaction.
- [ ] Prove district-based fee selection remains deterministic on SQL Server.

### Task 3: Complete the customer flow

**Files:** frontend API types, Cart view, localization/copy, CSS only if needed,
and focused frontend tests.

- [x] RED: field errors attach to phone/location fields; supported address shows
  fee; unknown ward differs from network failure; unsupported delivery offers
  pickup; VNPAY unavailable never blames entered information.
- [x] GREEN: add dependent ward selection, inline semantic errors, pickup
  fallback and retryable VNPAY copy while preserving cart/address state.
- [ ] Run frontend suite/build, backend unit suite, affected SQL integration,
  typecheck and `git diff --check`; perform the five practical browser scenarios
  when a runnable SQL-backed environment is available.

The affected SQL tests were selected and compiled but skipped by their existing
environment guard because this host has no configured SQL Server datasource or
Docker. The same limitation prevented a meaningful browser checkout pass.

## Review focus

- No credentials/configuration values in API bodies.
- Quote and checkout cannot disagree on ward identity.
- Unknown location, unsupported service area and infrastructure failure remain
  distinct.
- Provider failure creates no PaymentAttempt and does not weaken retry identity.
- Pickup fallback never clears the cart or entered delivery fields.
