# ADR-0042: Use Stateless OpenAI Assistance for Manager Merchandising

- Status: `ACCEPTED`
- Accepted: 2026-10-04
- Decision class: Confirmed optional feature slice extending ADR-0008
- Extends: ADR-0008 proposal and approval boundary; ADR-0033 Storefront publication

## Context

P5A already supplies deterministic Product eligibility, ranking, promotion,
availability, sales, readiness and HOME-placement evidence. Managers need
optional help rewriting merchandising copy and translating natural language
into those existing filters. The ordinary editor and deterministic discovery
must remain available without an AI provider.

## Decision

Use OpenAI's Responses API behind the domain-specific `MerchandisingAssistant`
boundary. The default configurable model is `gpt-6-luna`. The adapter uses the
JDK `HttpClient`, because the repository already uses it and the bounded text
request does not justify an SDK, Spring AI, LangChain or an agent framework.

Every request is stateless: `store=false`, `reasoning.effort=none`,
`max_output_tokens=700`, strict `text.format` JSON Schema, no conversation and
no tools. Copy output is a bounded plain-text proposal. Intent output is a
closed enum that the existing P5A endpoint executes deterministically.

The application resolves at most eight selected Product IDs and an optional
active public Promotion reference. It transmits only the safe merchandising
projection described in the P5B design. Client-supplied facts, customer/order/
payment/employee data, raw database rows, SQL, audit data and secrets are
excluded. Catalog and manager text are untrusted data and cannot override fixed
instructions.

All assistant routes require `STOREFRONT_MANAGE`. Provider output never saves,
publishes, ranks, mutates inventory or changes business state. The manager must
preview and explicitly apply copy to local draft state, then use the existing
Save and Publish commands.

Configuration is backend-only. Disabled, missing-key, timeout, rate-limit,
refusal, invalid-output and provider-unavailable outcomes fail safely without
blocking P5A or Storefront publication. Prompts, responses, response IDs,
conversations and history are not persisted, and P5B adds no migration.

## Data handling

The application does not opt into provider training or intentionally persist
requests/responses. `store=false` prevents later Responses API retrieval, but
does not claim absolute zero provider retention; applicable abuse-monitoring,
prompt-caching and account data-control policies still govern provider-side
processing.

## Consequences

- Manager copy and intent assistance can fail independently of merchandising.
- Provider/model/base URL remain runtime configuration, but there is no
  speculative multi-provider abstraction.
- Exact request serialization and response parsing require contract tests.
- Live-provider smoke is conditional on a locally configured API key.

## Rejected alternatives

- Frontend provider calls or frontend-held credentials.
- Chat Completions, Conversations, built-in tools or persistent AI state.
- Generic chat, autonomous ranking/mutation/publication and free-form action parsing.
- OpenAI SDK, Spring AI, LangChain, retry subsystem or provider registry.

## Deferred

AI images/media selection, embeddings/vector search, persistent history or
provenance, feedback analytics, multi-provider routing and new recommendation
semantics remain deferred.
