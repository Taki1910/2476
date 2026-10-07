# P5B Manager AI Assistance Design

## Status

`CONFIRMED` by the 2026-10-04 P5B brief. This design extends the certified P5A workspace baseline without changing deterministic merchandising authority.

## Purpose

Managers may request bounded copy suggestions or translate natural-language discovery requests into existing P5A filters. AI output is advisory: copy changes only the local browser draft after explicit acceptance, and interpreted intent is executed by the existing deterministic candidate endpoint.

## Boundaries

- `MerchandisingAssistant` is the only provider-facing interface. It exposes availability, copy rewrite, and intent interpretation.
- `OpenAiMerchandisingAssistant` uses the JDK `HttpClient`; no SDK, AI framework, provider registry, retry subsystem, conversation, tool, or persistence is introduced.
- `MerchandisingAssistantService` owns authorization, request validation, authoritative evidence resolution, and provider-error mapping.
- All assistant routes require `STOREFRONT_MANAGE` at the application boundary.
- P5A remains authoritative for eligibility, ranking, sales, availability, promotion, readiness, placement, and ordering.

## Provider contract

Each call sends `POST /v1/responses` with configurable base URL/model/key, `store=false`, `reasoning.effort=none`, `max_output_tokens=700`, a bounded timeout, and strict `text.format` JSON Schema. No `tools`, conversation identifier, previous response, or provider-side state is used.

Copy output is `{ primary, alternatives, notes }`, with at most two alternatives and plain-text length limits. Intent output is `{ intent, query, explanation }`, where `intent` is exactly one of `BEST_SELLERS`, `PROMOTED`, `NEEDS_REVIEW`, `SEARCH`, or `UNSUPPORTED`.

## Data and trust model

The browser supplies selectors and draft text, never trusted Product or Promotion facts. The backend resolves at most eight selected Product IDs through the P5A read model and an optional active public Promotion reference through the existing Promotion service. Only display name, category, collection, summarized availability, 30-day sales, active Product promotion, readiness, placement, and current draft context may enter the provider payload.

Catalog text, campaign names, manager instructions, and draft copy are serialized as untrusted data. Fixed instructions explicitly prohibit treating those values as instructions or inventing unsupported facts. Provider output is untrusted, parsed through the strict schema, length checked, and returned only as a suggestion.

No customer, address, phone, email, account, payment, order history, employee, raw inventory row, SQL, audit, or secret data is sent. Prompts, responses, response IDs, conversations, history, and dismissal state are not persisted.

## API

- `GET /api/v1/operations/storefront/assistant/availability`
- `POST /api/v1/operations/storefront/assistant/copy`
- `POST /api/v1/operations/storefront/assistant/intent`

Availability is configuration-derived and reveals only `enabled`, `available`, and `status`: `AVAILABLE`, `DISABLED`, or `NOT_CONFIGURED`. Runtime timeout, rate limit, refusal, malformed output, and provider failure map to stable application errors without raw payloads.

Intent mapping is fixed: `BEST_SELLERS -> BEST_SELLER`, `PROMOTED -> PROMOTED`, `NEEDS_REVIEW -> NEEDS_REVIEW`, `SEARCH -> ALL + query`. `UNSUPPORTED` triggers no candidate call.

## Manager experience

Assistant controls remain visually secondary to existing deterministic filters and manual fields. A small intent input sits beside Product discovery. Copy actions sit beside the active VI/EN field and open a preview with Use, Try again, and Cancel. Use updates only the selected local field, preserving locale isolation and existing dirty-state, Save, and Publish behavior.

When disabled, unconfigured, or failed, the manager retains the complete P5A and manual-editing experience. Provider failure never becomes a Storefront save or publish failure.

## Configuration

- `MERCHANDISING_AI_ENABLED` defaults to `false`.
- `OPENAI_API_KEY` defaults to empty and remains backend-only.
- `OPENAI_MODEL` defaults to `gpt-6-luna`.
- `OPENAI_BASE_URL` defaults to `https://api.openai.com` and exists for compatible test/dev endpoints.
- `OPENAI_TIMEOUT` defaults to a bounded duration.

## Documentation and retention

ADR-0042 records provider selection and extends ADR-0008. Application code does not opt into training or intentionally persist requests/responses. `store=false` disables response storage for later retrieval but is not documented as absolute zero provider retention; provider abuse-monitoring and account data-control policies may still apply.

## Verification

TDD covers disabled/missing configuration, exact HTTP payload, strict parsing, refusal/failure classes, safe evidence, prompt injection, supported/unsupported intents, permission enforcement, frontend preview/apply/cancel/retry, locale isolation, disabled fallback, keyboard/focus, and responsive source rules. Final verification reruns backend unit, SQL acceptance, frontend suite, typecheck/build, real-stack smoke, migration inventory, `git diff --check`, and staged-file count.

## Explicit exclusions

No generic chat, autonomous mutation/publication, AI ranking, images, media selection, embeddings, history, prompt persistence, provenance persistence, multi-provider routing, new recommendation semantics, or V34 migration.
