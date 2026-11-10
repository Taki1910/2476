# P5B Manager AI Assistance Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add optional, protected OpenAI copy and intent assistance without changing P5A deterministic behavior or persistence.

**Architecture:** A domain-specific `MerchandisingAssistant` isolates stateless Responses API I/O. An authorized application service resolves bounded P5A evidence, validates untrusted input/output, and exposes three Storefront-specific endpoints; the Vue manager UI previews suggestions and maps supported intents into existing candidate calls.

**Tech Stack:** Java 25, Spring Boot 4.1, JDK `HttpClient`, Jackson, Vue 3, TypeScript, Vitest, SQL Server.

**Spec:** `docs/superpowers/specs/2026-10-04-p5b-manager-ai-assistance-design.md`

## Global Constraints

- Work in the current dirty checkout; do not stage, commit, reset, clean, stash, switch, merge, rebase, cherry-pick, push, or restore unrelated files.
- Keep P5A deterministic and authoritative; AI output is advisory only.
- Use `/v1/responses`, `store=false`, `reasoning.effort=none`, strict `text.format`, no tools, `max_output_tokens=700`, and one bounded request without retries.
- Add no dependency and no migration; preserve V1-V33 and no V34.
- Keep the API key backend-only and exclude sensitive data, raw prompts, and raw provider responses from logs.

## Review Focus

- Catalog or manager prompt-injection text must remain serialized data and gain no instruction or mutation authority.
- Client Product/Promotion claims must be ignored; only backend-resolved P5A facts may reach OpenAI.
- Provider refusal, timeout, 429, 5xx, malformed JSON, and strict-output mismatch must fail without losing local edits or blocking Save/Publish.
- Locale-specific apply must modify exactly one selected VI/EN draft field.
- Unsupported deferred intent must not invoke the candidate endpoint.

---

### Task 1: Provider boundary and exact Responses contract

**Files:**
- Create: `backend/src/main/java/com/shoecommerce/merchandising/MerchandisingAssistant.java`
- Create: `backend/src/main/java/com/shoecommerce/merchandising/OpenAiMerchandisingAssistant.java`
- Create: `backend/src/test/java/com/shoecommerce/merchandising/OpenAiMerchandisingAssistantTest.java`
- Modify: `backend/src/main/resources/application.yml`

**Interfaces:**
- Produces: availability, `rewriteCopy(CopyPrompt)`, and `interpretIntent(String)` with domain-specific records/enums.

- [ ] Write provider contract tests using JDK `HttpServer` for disabled/missing/configured availability, endpoint, headers, model, no tools, storage/reasoning/token settings, schemas, safe payload, parsing, refusal, malformed output, timeout, 429, and 5xx.
- [ ] Run focused tests and confirm RED.
- [ ] Implement the smallest direct JDK HTTP adapter and configuration.
- [ ] Run focused tests and confirm GREEN.

### Task 2: Authorized application service and safe evidence

**Files:**
- Create: `backend/src/main/java/com/shoecommerce/merchandising/MerchandisingAssistantService.java`
- Create: `backend/src/test/java/com/shoecommerce/merchandising/MerchandisingAssistantServiceTest.java`
- Modify: `backend/src/main/java/com/shoecommerce/merchandising/MerchandisingRecommendationService.java`
- Modify: `backend/src/test/java/com/shoecommerce/merchandising/MerchandisingRecommendationServiceTest.java`

**Interfaces:**
- Consumes: Task 1 assistant and current P5A candidate evidence.
- Produces: authorized availability/copy/intent use cases and selected authoritative Product evidence.

- [ ] Write failing tests for validation, permission, Product ordering/bounds/missing IDs, active Promotion resolution, locale behavior, injection-as-data, supported intent, and unsupported intent without candidate execution.
- [ ] Run focused tests and confirm RED.
- [ ] Add a behavior-preserving selected-evidence seam to P5A and implement the application service.
- [ ] Run focused tests and confirm GREEN.

### Task 3: Protected API, errors, ADR, and contracts

**Files:**
- Create: `backend/src/main/java/com/shoecommerce/merchandising/MerchandisingAssistantController.java`
- Create: `backend/src/main/java/com/shoecommerce/merchandising/MerchandisingAssistantUnavailableException.java`
- Create/modify: focused merchandising controller/external tests
- Modify: `backend/src/main/java/com/shoecommerce/platform/api/ApiExceptionHandler.java`
- Create: `docs/ADR/0042-openai-manager-merchandising-assistance.md`
- Modify: `docs/ARCHITECTURE.md`
- Modify: `docs/AI_STRATEGY.md`
- Modify: `docs/API/OPERATIONS.md`

**Interfaces:**
- Consumes: Task 2 use cases.
- Produces: protected availability/copy/intent endpoints and stable Problem Details codes.

- [ ] Write failing controller/security/error-contract tests.
- [ ] Run focused tests and confirm RED.
- [ ] Implement minimal endpoints/error mapping and document the accepted provider boundary.
- [ ] Run focused tests and confirm GREEN.

### Task 4: Secondary manager UX

**Files:**
- Modify: `frontend/src/api.ts`
- Modify: `frontend/src/merchandising.ts`
- Modify: `frontend/src/views/StorefrontManagementView.vue`
- Modify: `frontend/src/storefront-management.test.ts`
- Modify: `frontend/src/i18n.ts`
- Modify: `frontend/src/i18n.test.ts`
- Modify: `frontend/src/styles.css`

**Interfaces:**
- Consumes: Task 3 API and existing P5A candidate filters.
- Produces: availability-aware intent input, copy preview/apply/retry/cancel, and locale-isolated local draft updates.

- [ ] Write failing API/helper/view tests for availability, copy lifecycle, draft preservation, exact field apply, supported intent mapping, unsupported behavior, deterministic fallback, focus, and 390px source rules.
- [ ] Run focused tests and confirm RED.
- [ ] Implement typed API/helpers and incumbent-design UI without a chatbot.
- [ ] Run focused tests, typecheck, and build; confirm GREEN.

### Task 5: Regression, security review, and completion evidence

**Files:**
- Modify only files required by P5B-attributable failures.

**Interfaces:**
- Consumes: Tasks 1-4.
- Produces: fresh P5B/P5A/full-stack verification and the requested completion report.

- [ ] Run focused backend and provider-contract tests.
- [ ] Run full backend unit suite and real SQL acceptance suite.
- [ ] Run focused/full frontend tests, typecheck, and production build.
- [ ] Run real-stack browser smoke with AI disabled; conditionally run safe live-provider copy/intent smoke when a key exists.
- [ ] Verify V1-V33/no V34, migration checksums, `git diff --check`, staged-file count zero, and no secret/reference leakage.
- [ ] Perform a fixed-baseline correctness/security/quality self-review because subagents were not explicitly requested.
