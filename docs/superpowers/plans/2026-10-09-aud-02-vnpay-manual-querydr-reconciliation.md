# AUD-02 VNPAY Manual QueryDr Reconciliation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Let an authorized, location-scoped operator manually reconcile an existing ambiguous VNPAY payment attempt through a verified QueryDr request without creating another payment attempt or automatically retrying the provider call.

**Architecture:** A VNPAY QueryDr adapter builds and verifies the official 2.1.0 pipe-signed contract. A durable reconciliation command records actor-scoped idempotency and verified evidence in short transactions around one external call; the existing payment-result service remains the only business-state transition path. The reporting UI invokes the command explicitly and reuses the AUD-01 retry identity only after a retryable local contention response.

**Tech Stack:** Java 21, Spring Boot/JPA/JdbcTemplate, SQL Server/Flyway, JUnit 5/AssertJ, Vue 3/TypeScript/Vitest.

**Spec:** `C:/Users/asus/.codex/attachments/798b3c5f-0a13-4758-b8e9-4b9b1e2d9950/Văn bản đã dán.txt`

## Global Constraints

- Manual QueryDr only; no scheduler, background poller, new payment provider, refund workflow, or live VNPAY certification.
- Never create a new PaymentAttempt; use the stored merchant reference, original attempt timestamp, amount, currency and client/server identity.
- VNPAY network I/O stays outside DB transactions and is never automatically repeated by AUD-01 handling.
- Same actor/key/attempt replays; the same actor/key with another attempt returns `IDEMPOTENCY_KEY_CONFLICT`.
- Invalid signature, reference, merchant, amount, command or transaction relationship never mutates payment/order state and never exposes raw provider data.
- Only `PENDING` (the current UNKNOWN equivalent) and `REVIEW_REQUIRED` are queryable; confirmed terminal states only support safe completed-command replay.
- QueryDr cannot bypass order, inventory, promotion, payment or late-capture invariants already enforced by `VerifiedPaymentResultService`.
- Keep all REC-001/V34 and AUD-01 dirty changes; no stage, commit, reset, checkout, stash, clean, merge, rebase or push.

## Review Focus

- A signed HTTP 200 response whose amount/reference/merchant differs must produce a stable mismatch and leave both payment and order unchanged (Task 1 and Task 2).
- A local SQL 1205/1222 after verified evidence is saved must return AUD-01 503 and explicit same-key retry must apply saved evidence without another QueryDr call (Task 2 and Task 3).
- Two actors racing the same attempt may make read-only provider queries, but at most one local transition and audit fact may be committed (Task 3).
- A `REVIEW_REQUIRED` late-capture attempt must remain review-required when existing local invariants forbid reopening the order (Task 2).
- UI retry identity must survive only retryable contention for the exact attempt; provider outage, mismatch, success and unresolved completion must not auto-loop (Task 4).

---

### Task 1: Official QueryDr adapter

**Files:**
- Create: `backend/src/main/java/com/shoecommerce/payment/PaymentQueryProvider.java`
- Create: `backend/src/main/java/com/shoecommerce/payment/VnPayQueryProvider.java`
- Create: `backend/src/main/java/com/shoecommerce/payment/TestPaymentQueryProvider.java`
- Test: `backend/src/test/java/com/shoecommerce/payment/VnPayQueryProviderTest.java`

**Interfaces:**
- Consumes: `VnPayCrypto`, `VnPayPaymentProvider.VNPAY_ZONE`, configured VNPAY transaction API URL and server IP.
- Produces: `PaymentQueryProvider.query(Request) -> Result`, where `Request` contains request ID, original merchant reference, optional provider transaction number, original transaction time, expected VND amount and request time; `Result` distinguishes `CONFIRMED_PAID`, `CONFIRMED_FAILED`, `UNRESOLVED`, and `REVIEW_REQUIRED` with verified safe evidence fields.

- [ ] Write tests proving exact `querydr` fields, original reference/date, VND×100 comparison, official request/response checksum order including promotion fields, and request ID constraints.
- [ ] Run `./mvnw -q -Dtest=VnPayQueryProviderTest test` from `backend`; expect RED because the provider does not exist.
- [ ] Implement the minimum HttpClient adapter with 5s connect/20s request timeout, signature-first verification, strict merchant/command/reference/amount/type checks and typed safe exceptions.
- [ ] Add tests for success, failed, processing/unknown, reversal/fraud review, invalid signature, mismatched fields and network failure; run the focused test and expect PASS.

### Task 2: Durable manual reconciliation command and API

**Files:**
- Create: `backend/src/main/resources/db/migration/V35__manual_vnpay_reconciliation.sql`
- Create: `backend/src/main/java/com/shoecommerce/payment/PaymentReconciliationCommand.java`
- Create: `backend/src/main/java/com/shoecommerce/payment/PaymentReconciliationCommandRepository.java`
- Create: `backend/src/main/java/com/shoecommerce/payment/PaymentReconciliationService.java`
- Create: `backend/src/main/java/com/shoecommerce/payment/PaymentReconciliationController.java`
- Modify: `backend/src/main/java/com/shoecommerce/payment/PaymentAttempt.java`
- Modify: `backend/src/main/java/com/shoecommerce/payment/PaymentAttemptRepository.java`
- Modify: `backend/src/main/java/com/shoecommerce/payment/VerifiedPaymentResultService.java`
- Modify: `backend/src/main/java/com/shoecommerce/identity/PermissionCode.java`
- Modify: `backend/src/main/java/com/shoecommerce/platform/api/ApiExceptionHandler.java`
- Test: `backend/src/test/java/com/shoecommerce/payment/PaymentReconciliationServiceTest.java`
- Test: `backend/src/test/java/com/shoecommerce/payment/PaymentReconciliationControllerTest.java`

**Interfaces:**
- Consumes: Task 1 `PaymentQueryProvider`; existing `AuthorizationPolicy`, `VerifiedPaymentResultService`, `AuditWriter`, `BusinessConflictException`, and AUD-01 exception handler.
- Produces: `POST /api/v1/operations/payment-attempts/{attemptId}/reconcile` with `Idempotency-Key`; safe result containing attempt/order/reference, previous/current state, outcome, resolved flag and replay flag.

- [ ] Write service/controller tests for permission plus location scope, required key, eligible-state validation, canonical request data, provider call outside the prepare/apply transactions, and stable error codes.
- [ ] Run focused tests; expect RED because the workflow/API do not exist.
- [ ] Add V35 with `PAYMENT_RECONCILE` granted to the accepted OPERATIONS bundle and one durable command/evidence table constrained by actor/key and provider request ID.
- [ ] Implement prepare → external QueryDr → persist verified evidence → apply/replay using short proxied transactions; preserve provider outage/invalid evidence without payment mutation.
- [ ] Route definitive results through the existing verified payment-result invariants; map unresolved/review results without reopening a terminal order and append exactly one operator audit event.
- [ ] Run focused tests; expect PASS.

### Task 3: SQL Server idempotency, concurrency and migration proof

**Files:**
- Create: `backend/src/test/java/com/shoecommerce/FlywayV35UpgradeExternalIT.java`
- Create: `backend/src/test/java/com/shoecommerce/payment/PaymentReconciliationExternalIT.java`
- Modify: `backend/pom.xml` only if the existing external-test include list requires it.

**Interfaces:**
- Consumes: Task 2 endpoint/service/table and AUD-01 exact SQL Server contention classifier.
- Produces: real-SQL evidence for fresh/upgrade migration, same-key replay, changed-target conflict, authorization/scope, rollback/no mutation, saved-evidence retry and concurrent operators.

- [ ] Add migration tests for fresh V1→V35 and V34→V35 upgrade, including constraints/permission grant.
- [ ] Add SQL Server integration tests for pending→paid, pending→failed, unresolved/mismatch/outage no mutation, REVIEW_REQUIRED safe behavior, same-key replay and changed target conflict.
- [ ] Add a two-operator race asserting at most one local transition/reconciliation audit and no duplicate payment effect; add an apply-contention case proving 503 metadata and no second provider call on exact retry.
- [ ] Run the project’s SQL Server external-test command for these classes and relevant AUD-01/payment regressions; expect PASS.

### Task 4: Operations UI and command identity

**Files:**
- Modify: `frontend/src/api.ts`
- Modify: `frontend/src/views/ReportsView.vue`
- Modify: `frontend/src/i18n.ts`
- Modify: `frontend/src/styles.css` only for the minimal action/result layout.
- Create: `frontend/src/reconciliation.test.ts`
- Modify: `frontend/src/api.test.ts`

**Interfaces:**
- Consumes: Task 2 endpoint/response/error codes and existing `retryable-command.ts` plus authenticated session permissions.
- Produces: eligible `PAYMENT_REVIEW` action with explicit outcomes and exact-key retry only for `CONCURRENCY_RETRY_REQUIRED`.

- [ ] Write failing tests for permission/state visibility, one click/one request, disabled in flight, no provider-outage auto retry, same-key contention retry, success refresh, unresolved rendering and VI/EN copy.
- [ ] Run focused Vitest tests; expect RED.
- [ ] Implement the smallest API method and report-row action; keep one retry command per attempt and clear it after every non-contention completion.
- [ ] Run focused tests, full frontend tests, typecheck and build; expect PASS.

### Task 5: Contract documentation, review and final verification

**Files:**
- Create: `docs/ADR/0045-manual-vnpay-querydr-reconciliation.md`
- Modify: `docs/ARCHITECTURE.md`
- Modify: `docs/BUSINESS_RULES.md`
- Modify: `docs/LIFECYCLES.md`
- Modify: `docs/SECURITY_MODEL.md`
- Modify: `docs/API/OPERATIONS.md`
- Modify: `docs/API/README.md`
- Modify after every gate passes: `.agent/REQUIREMENTS.yaml`, `.agent/ACCEPTANCE_MATRIX.md`, `.agent/CURRENT_STATE.md`, `.agent/HISTORY.md`, `.agent/MASTER_PLAN.md`, `.agent/TASKS.yaml`, `.agent/DECISIONS.md`.

**Interfaces:**
- Consumes: completed Tasks 1–4 and official VNPAY QueryDr 2.1.0 documentation.
- Produces: successor ADR, state/provider/API/security contracts, independent financial-code review, verification evidence and truthful DONE memory state.

- [ ] Document the official fields/checksums/status mapping, short-transaction boundary, idempotency, permission/scope, no-new-attempt invariant, and `REVIEW_REQUIRED` non-reopening rule; explicitly retain scheduled worker/live certification as not implemented.
- [ ] Run focused backend provider/service/controller/payment/AUD-01 regression plus SQL acceptance, frontend full test/typecheck/build, Flyway checks and `git diff --check`.
- [ ] Perform security/API review and a fresh independent code review; fix Critical/Important findings with RED→GREEN tests, ledger any rulings/minors.
- [ ] Only after all gates pass, mark AUD-02 DONE in project memory while leaving REQ-PAY-003, scheduled reconciliation and AUD-03 open.

