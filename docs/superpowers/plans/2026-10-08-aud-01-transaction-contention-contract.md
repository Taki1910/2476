# AUD-01 Transaction Contention Contract Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Turn SQL Server deadlock victim (1205) and lock-timeout (1222) failures into one safe, deterministic HTTP 503 contract whose caller retries the exact command with the same idempotency key.

**Architecture:** Keep transaction ownership in the existing application services and add no retry framework. A small cause-chain classifier recognizes only SQL Server error numbers 1205 and 1222; the shared API advice emits the stable problem contract after Spring has rolled the transaction back. The Vue client parses the machine fields, displays safe VI/EN copy, and preserves each unresolved command identity until an explicit user retry succeeds or the payload changes.

**Tech Stack:** Java 25, Spring Boot 4.1.1 / Spring Framework 7.0.9, Hibernate 7.4.5.Final, Microsoft SQL Server JDBC 13.4.0, JUnit 5, AssertJ, Vue 3, TypeScript 5.9, Vitest 3.

**Spec:** `C:/Users/asus/.codex/attachments/061549a3-84f8-450f-bc81-f014861d8216/Văn bản đã dán.txt`

## Global Constraints

- SQL Server 1205 and 1222 map to HTTP 503 with `code=CONCURRENCY_RETRY_REQUIRED`, `retryable=true`, and `reuseSameIdempotencyKey=true`.
- The response exposes no raw SQL message, table/index name, Hibernate detail, stack trace, or deadlock graph.
- `SERVER AUTOMATIC RETRY = NO`; `CLIENT SAME-KEY RETRY = YES` with the exact payload.
- Do not add Spring Retry, a global interceptor, provider/AI/external-HTTP retry, or a new dependency.
- Do not treat every `CannotAcquireLockException`, `PessimisticLockingFailureException`, `QueryTimeoutException`, or `DataAccessException` as retryable.
- Do not emit `Retry-After`: production wait policy is not approved. Use a test-only bounded `SET LOCK_TIMEOUT` for acceptance reproduction.
- A contention victim must leave no partial business/idempotency/audit effect. Same key plus changed payload remains `IDEMPOTENCY_KEY_CONFLICT`.
- Preserve the user-owned dirty REC-001 worktree. Do not start AUD-02, AUD-03, or REC-002 and do not broaden idempotency to unrelated endpoints.
- Use TDD for every behavior change. Do not weaken an existing assertion to make the change pass.
- Do not commit a task checkpoint when a touched file already contains inseparable user-owned changes; record the exact diff and test receipt instead.

## Review Focus

- An exact 1205/1222 nested under JDBC, Hibernate, JPA, or Spring wrappers must classify; Task 1 tests nested cause and `SQLException#getNextException()` paths.
- SQLState `40001` or a broad lock exception without error number 1205/1222 must not classify; Task 1 pins both negatives.
- A real deadlock/timeout victim must roll back its effect row before the same-key retry; Task 2 asserts row counts and replay identity.
- Editing the payload after a failed attempt must not silently reuse its key; Tasks 2 and 3 assert backend conflict and frontend new command identity.
- A 503 must not start an automatic loop or duplicate a submission; Task 3 asserts one API call per explicit invocation and stable keys across invocations.

---

## File Map

- `backend/src/main/java/com/shoecommerce/platform/api/SqlServerContentionClassifier.java`: exact, package-local SQL Server error-number classifier.
- `backend/src/main/java/com/shoecommerce/platform/api/ApiExceptionHandler.java`: shared safe 503 problem response; no retry execution.
- `backend/src/test/java/com/shoecommerce/platform/api/ApiExceptionHandlerTest.java`: unit contract and false-positive coverage.
- `backend/src/test/java/com/shoecommerce/platform/api/TransactionContentionExternalIT.java`: deterministic real-SQL-Server deadlock/timeout rollback and same-key acceptance probe, entirely test-scoped.
- `frontend/src/api.ts`: parse the two machine-readable retry fields without automatic retry.
- `frontend/src/retryable-command.ts`: reuse the existing receipt fingerprint/key pattern for unresolved UI commands.
- `frontend/src/retryable-command.test.ts`: exact same/different payload identity behavior and no implicit execution.
- `frontend/src/format.ts`, `frontend/src/format.test.ts`, `frontend/src/i18n.ts`: safe, specific EN/VI contention copy before the generic 5xx branch.
- `frontend/src/views/ProductManagementDetailView.vue`: preserve inventory adjustment identity just as receipt already does.
- `frontend/src/views/PickupDetailView.vue`: preserve handover/dispatch/deliver identity per action and fulfillment.
- `frontend/src/views/OrderStatusView.vue`: preserve confirmed-cancellation and void-retry identity per order/action.
- `frontend/src/manager-catalog.test.ts`, `frontend/src/commerce-render.test.ts`, `frontend/src/checkout.test.ts`, `frontend/src/api.test.ts`: focused client regression and retry assertions.
- `docs/ADR/0044-client-owned-contention-retry.md`: successor clarification for retry ownership.
- `docs/ARCHITECTURE.md`, `docs/BUSINESS_RULES.md`, `docs/API/README.md`: canonical transaction/error contract.
- `.agent/REQUIREMENTS.yaml`, `.agent/TASKS.yaml`, `.agent/ACCEPTANCE_MATRIX.md`, `.agent/CURRENT_STATE.md`, `.agent/HISTORY.md`, `.agent/MASTER_PLAN.md`: AUD-01 evidence and dependency-chain memory after PASS only.

### Task 1: Exact SQL Server classification and stable API problem

**Files:**
- Create: `backend/src/main/java/com/shoecommerce/platform/api/SqlServerContentionClassifier.java`
- Modify: `backend/src/main/java/com/shoecommerce/platform/api/ApiExceptionHandler.java`
- Modify: `backend/src/test/java/com/shoecommerce/platform/api/ApiExceptionHandlerTest.java`

**Interfaces:**
- Consumes: Java `Throwable`/`SQLException`; existing Spring `ProblemDetail` convention.
- Produces: package-local `static boolean SqlServerContentionClassifier.isRetryable(Throwable failure)` and the HTTP 503 fields `code`, `retryable`, `reuseSameIdempotencyKey`.

**Observed translation chain:** With the installed sources, SQL Server 1205 carries SQLState `40001`, which Hibernate converts to `org.hibernate.exception.LockAcquisitionException`; SQL Server 1222 is converted by `SQLServerDialect` to `org.hibernate.exception.LockTimeoutException`, a `LockAcquisitionException` subtype. Spring 7's `HibernateExceptionTranslator` converts either to `CannotAcquireLockException`, retaining the JDBC exception below it. The classifier therefore verifies the nested JDBC error number rather than trusting the broad outer type.

- [ ] **Step 1: Write failing classifier tests**

Add tests named `recognizesOnlySqlServerDeadlockAndLockTimeoutAcrossWrapperChains` and `doesNotPromoteBroadLockOrDatabaseFailures`. Assert error numbers 1205 and 1222 classify through normal causes and `nextException`; 0/2601, SQLState-only `40001`, and broad Spring lock exceptions without those JDBC codes do not.

- [ ] **Step 2: Run the focused unit test and confirm RED**

Run from `backend`: `./mvnw.cmd -Dtest=ApiExceptionHandlerTest test`

Expected: FAIL because the classifier/503 behavior does not exist.

- [ ] **Step 3: Implement the minimum classifier**

Create a non-instantiable package-local class with `static boolean isRetryable(Throwable failure)`. Traverse the cause and `SQLException#getNextException()` graph with an identity-based visited set; return true only when `SQLException#getErrorCode()` is 1205 or 1222. Do not inspect localized/raw messages and do not accept SQLState alone.

- [ ] **Step 4: Write the failing 503 problem test**

Add `mapsExactSqlServerContentionToSafeSameKeyRetryContract`. Pass a wrapped 1205/1222 failure to `handleUnexpected` and assert status 503, exact code/booleans, safe detail, correlation ID, and absence of the private SQL text. Add a 2601 case and assert it is not the contention contract.

- [ ] **Step 5: Add the shared handler branch**

At the start of `handleUnexpected(Exception, WebRequest)`, call the classifier. On true, return `ProblemDetail.forStatusAndDetail(SERVICE_UNAVAILABLE, "This request conflicted with another operation. Try the same action again.")` with the three fixed properties. Keep the existing sanitized 500 path unchanged for false.

- [ ] **Step 6: Run unit tests and inspect the response surface**

Run: `./mvnw.cmd -Dtest=ApiExceptionHandlerTest test`

Expected: PASS; no response assertion contains SQL/JDBC/Hibernate detail.

- [ ] **Step 7: Checkpoint the exact Task 1 diff**

Run: `git diff --check -- backend/src/main/java/com/shoecommerce/platform/api backend/src/test/java/com/shoecommerce/platform/api/ApiExceptionHandlerTest.java`

Expected: no whitespace errors. Do not commit over pre-existing user changes.

### Task 2: Real SQL Server deadlock, lock-timeout, rollback, and idempotency acceptance

**Files:**
- Create: `backend/src/test/java/com/shoecommerce/platform/api/TransactionContentionExternalIT.java`

**Interfaces:**
- Consumes: Task 1 shared handler; existing `BusinessConflictException`; externally configured isolated SQL Server acceptance database.
- Produces: deterministic evidence for 1205/1222 translation, rollback, retry, replay, mismatch conflict, and non-contention rejection.

- [ ] **Step 1: Write the test-scoped transaction probe**

Inside the ExternalIT only, define a test controller and proxied `@Transactional` service accepting `(String idempotencyKey, String payload, int firstRow, int secondRow, boolean lowDeadlockPriority)`. The test schema has two lock rows and an effect table with unique key plus payload. Existing same key/same payload replays; same key/different payload throws `BusinessConflictException`; a new attempt inserts its effect inside the same transaction before requesting the second lock. Apply only fixed `LOW`/`HIGH` priority values and restore `NORMAL` before returning the pooled connection.

- [ ] **Step 2: Write the failing real-deadlock test**

Create two concurrent MockMvc requests with opposite row order, coordinate both first locks with latches, and set one transaction as the deterministic low-priority victim. Assert exactly one initial 2xx and one 503 response, exact contract fields, no victim effect after rollback, victim retry with the same key/payload succeeds, another replay creates nothing, and changed payload with that key returns `IDEMPOTENCY_KEY_CONFLICT`.

- [ ] **Step 3: Write the failing real-lock-timeout test**

Hold one probe row with a separate JDBC transaction. Use `SET LOCK_TIMEOUT 1000` only in the contender session, restore `-1` before returning its pooled connection, assert 503/contract and zero contender effect, release the holder, then assert same-key retry succeeds once and replay does not duplicate it.

- [ ] **Step 4: Write the non-contention database failure test**

Force an unrelated SQL Server constraint/duplicate-key failure and assert it does not return `CONCURRENCY_RETRY_REQUIRED`, `retryable=true`, or `reuseSameIdempotencyKey=true`.

- [ ] **Step 5: Run the acceptance class and confirm RED before Task 1, then GREEN after Task 1**

Run from `backend` with the existing isolated SQL Server environment:

`./mvnw.cmd -Pacceptance -Dit.test=TransactionContentionExternalIT verify`

Expected: PASS with a real 1205 victim and real 1222 timeout. The test creates and drops only its `aud01_*` probe tables; it adds no production migration or endpoint.

- [ ] **Step 6: Cross-check real application idempotency services**

Run focused existing SQL tests for inventory receipt/adjustment/reservation, checkout, POS, promotion/voucher, and payment local transactions. Confirm their success/idempotency rows are transaction-local; list any critical missing command identity as `AUD-01 BLOCKING GAP` rather than adding new endpoints.

- [ ] **Step 7: Checkpoint the exact Task 2 evidence**

Record test command, timestamp, exit code, and passed test names. Run `git diff --check -- backend/src/test/java/com/shoecommerce/platform/api/TransactionContentionExternalIT.java`.

### Task 3: Frontend machine fields, safe copy, and same-key explicit retry

**Files:**
- Create: `frontend/src/retryable-command.ts`
- Create: `frontend/src/retryable-command.test.ts`
- Modify: `frontend/src/api.ts`
- Modify: `frontend/src/api.test.ts`
- Modify: `frontend/src/format.ts`
- Modify: `frontend/src/format.test.ts`
- Modify: `frontend/src/i18n.ts`
- Modify: `frontend/src/views/ProductManagementDetailView.vue`
- Modify: `frontend/src/views/PickupDetailView.vue`
- Modify: `frontend/src/views/OrderStatusView.vue`
- Modify: `frontend/src/manager-catalog.test.ts`
- Modify: `frontend/src/commerce-render.test.ts`
- Modify: `frontend/src/checkout.test.ts`

**Interfaces:**
- Consumes: Task 1 problem fields and current API methods that already accept `idempotencyKey`.
- Produces: `RetryableCommand={fingerprint:string,key:string}` and `sameCommand(current, fingerprint)`; `ApiError.retryable`; `ApiError.reuseSameIdempotencyKey`.

- [ ] **Step 1: Write failing API parsing/copy tests**

Assert a 503 problem preserves both booleans on `ApiError`, performs exactly one fetch, and `errorCopy` selects specific safe English and Vietnamese copy before the generic `status >= 500` branch. Assert an ordinary 503 without the code remains generic.

- [ ] **Step 2: Run the focused frontend tests and confirm RED**

Run from `frontend`: `npm test -- src/api.test.ts src/format.test.ts`

- [ ] **Step 3: Extend the minimal error model**

Add optional booleans to `Problem` and defaulted boolean fields at the end of the `ApiError` constructor, then pass only strict `=== true` values from `request`. Add no fetch retry, timer, backoff, or `Retry-After` parsing for this contract.

- [ ] **Step 4: Add safe VI/EN copy**

Handle `CONCURRENCY_RETRY_REQUIRED` before generic 5xx copy. English: `This request conflicted with another operation. Try the same action again.` Vietnamese: `Yêu cầu đang xung đột với một thao tác khác. Hãy thử lại thao tác này.`

- [ ] **Step 5: Write failing command-identity tests**

Assert `sameCommand(undefined, fingerprint)` creates one UUID, repeated calls with the same fingerprint return the same key, and a changed fingerprint creates a new key. Assert the helper invokes no API and contains no loop/timer.

- [ ] **Step 6: Implement and adopt `sameCommand`**

Use the existing receipt fingerprint/key approach, factored into the tiny helper. Inventory adjustment fingerprints variant/location/target/reason/expected version. Fulfillment fingerprints action/fulfillment. Order cancellation/void fingerprints action/order. Preserve the command after 503/unknown failure, clear it only after success or an explicit payload/action change, and leave the existing submit button as the explicit retry action.

- [ ] **Step 7: Pin every audited flow**

Extend tests to prove: checkout and payment retain their already-durable exact key/payload after `CONCURRENCY_RETRY_REQUIRED`; receipt/adjustment, confirmed cancellation/void retry, handover/dispatch/deliver, and POS reuse their in-progress key on a second explicit call; no flow calls its API twice from one click. POS needs no product-code change if its existing `saleKey` tests pass.

- [ ] **Step 8: Run focused and full frontend verification**

Run:

- `npm test -- src/api.test.ts src/format.test.ts src/retryable-command.test.ts src/checkout.test.ts src/manager-catalog.test.ts src/commerce-render.test.ts src/pos-workflow.test.ts`
- `npm test`
- `npm run typecheck`
- `npm run build`

Expected: all PASS; no automatic retry loop and no raw backend detail in rendered copy.

- [ ] **Step 9: Checkpoint the exact Task 3 diff**

Run: `git diff --check -- frontend/src`

Expected: no whitespace errors; preserve REC-001 edits already present in Product Management.

### Task 4: Canonical ADR, API, architecture, and business rule

**Files:**
- Create: `docs/ADR/0044-client-owned-contention-retry.md`
- Modify: `docs/ARCHITECTURE.md`
- Modify: `docs/BUSINESS_RULES.md`
- Modify: `docs/API/README.md`
- Modify: `docs/ADR/0026-multi-item-online-commerce.md`
- Modify: `docs/ADR/0032-automatic-promotion-engine.md`

**Interfaces:**
- Consumes: verified Task 1–3 behavior and authoritative AUD-01 policy.
- Produces: one successor decision referenced by ADR-0026/0032 and BR-LOCK-105.

- [ ] **Step 1: Write ADR-0044 as a clarification, not a history rewrite**

Record context, decision, consequences, and exclusions. Canonical sequence: deadlock/lock-timeout -> full local rollback -> stable retryable 503 -> caller retries exact operation with same key -> no implicit server/provider retry. State exact 1205/1222 classification and no approved production lock-wait duration.

- [ ] **Step 2: Reconcile the existing authorities**

Change the ambiguous Architecture sentence that currently allows application retry. Keep ADR-0026/0032 historical text but add a current clarification reference to ADR-0044. Rewrite BR-LOCK-105 to name caller ownership and exact-key/payload reuse unambiguously.

- [ ] **Step 3: Document the public problem contract**

Add the HTTP 503 JSON fields and safe retry semantics to `docs/API/README.md`. Explicitly say clients must not create a new operation/key and the server does not automatically replay the transaction.

- [ ] **Step 4: Run documentation consistency checks**

Run:

- `rg -n "bounded retry|automatic retry|CONCURRENCY_RETRY_REQUIRED|BR-LOCK-105|ADR-0044|1205|1222" docs`
- `git diff --check -- docs`

Expected: no surviving authoritative sentence assigns this retry to the server; ADR-0026, ADR-0032, BR-LOCK-105, Architecture, and API README converge on one meaning.

### Task 5: Focused regression, review, and project-memory closure

**Files:**
- Modify after all verification is green: `.agent/REQUIREMENTS.yaml`
- Modify after all verification is green: `.agent/TASKS.yaml`
- Modify after all verification is green: `.agent/ACCEPTANCE_MATRIX.md`
- Modify after all verification is green: `.agent/CURRENT_STATE.md`
- Modify after all verification is green: `.agent/HISTORY.md`
- Modify after all verification is green: `.agent/MASTER_PLAN.md`

**Interfaces:**
- Consumes: observed test receipts from Tasks 1–4; no remembered or inferred PASS values.
- Produces: `REQ-CONC-001` completed evidence, `AUD-01` DONE task record, and exact remaining dependency chain.

- [ ] **Step 1: Run backend unit and focused domain regression**

Run from `backend`:

- `./mvnw.cmd test`
- `./mvnw.cmd -Pacceptance -Dit.test=TransactionContentionExternalIT,VerticalSlice3CheckoutExternalIT,VerticalSlice2ExternalIT,PromotionC2ExternalIT,PromotionC3VoucherEvaluationExternalIT,VerticalSlice6PosExternalIT,VerticalSlice5ExternalIT verify`

Expected: PASS for the shared handler plus checkout, inventory receive/adjust/reservation, promotion/voucher, POS, and payment local transaction handling. Do not claim full-project certification.

- [ ] **Step 2: Verify migrations without adding one**

Run the existing fresh-install and V33-to-V34 upgrade checks already used by REC-001. Expected: PASS and no V35/AUD-01 production schema migration.

- [ ] **Step 3: Perform security/API/code review**

Review for false-positive exception mapping, raw-detail leakage, transaction escape, automatic retry, new-key generation, branch authorization regressions, and accidental external/provider calls. Confirm no new dependency and no unrelated refactor.

- [ ] **Step 4: Update memory only from observed evidence**

Add `REQ-CONC-001` with source `AUD-01 user specification 2026-10-08`, exact implementation/test evidence, and completed status only if all mandatory tests passed. Add `AUD-01` DONE in Tasks, one Acceptance Matrix row, current/history/master-plan summaries, and retain AUD-02/AUD-03/REC-002 as not started.

- [ ] **Step 5: Run ledger and diff integrity checks**

Run the repository's existing memory consistency check if present, then:

- `git status --short`
- `git diff --check`
- `rg -n "AUD-01|REQ-CONC-001|AUD-02|AUD-03|REC-002" .agent`

Expected: AUD-01 alone is completed; pre-existing dirty changes remain present and identified.

- [ ] **Step 6: Obtain final independent review**

Use the requested code-review workflow on the complete AUD-01 diff. Resolve only correctness/security/scope findings, rerun affected tests, and preserve unrelated work.

- [ ] **Step 7: Produce the required A–L report and stop**

Report sections A through L exactly as requested, including exact JDBC/Hibernate/Spring chain, test commands/counts, files changed, memory updates, any `AUD-01 BLOCKING GAP`, and:

```text
AUD-01 — DONE
↓
AUD-02 VNPAY reconciliation
AUD-03 REC-002 Transfer
```

Do not start either remaining item.
