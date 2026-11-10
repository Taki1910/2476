# Final freeze preparation

Project status: `FINAL PROJECT READINESS: PASSED FOR CORE SYSTEM — AI PROVIDER LIVE VERIFICATION PENDING`

P5B status: `P5B IMPLEMENTED — CORE/SQL REGRESSION PASSED, LIVE AI PROVIDER SMOKE PENDING`

This is the authoritative readiness and freeze-preparation record for the
graduation project. It is not a live-provider certification and it does not
create a commit, tag, package or release. Historical audits, phase certificates
and execution ledgers remain evidence for their dates; they do not override
this baseline.

## Baseline

| Item | Current value |
|---|---|
| Branch | `codex/remediation-audit-20260913` |
| HEAD | `2302354b6dc01d58239d98924ec5ce7afca50cdd` |
| Working tree | Intentionally dirty; accepted uncommitted implementation is part of the baseline |
| Schema | SQL Server, Flyway V1 through V33; no V34 |
| Backend | Java 25.0.4, Spring Boot modular monolith |
| Frontend | Vue SPA, TypeScript, Vite |
| Theme | Intentionally light-only; no dark-theme support claimed |
| AI provider | Optional OpenAI Responses API integration; disabled by default |
| Live AI gate | Pending because `OPENAI_API_KEY` is not configured locally |

HEAD alone does not reproduce the accepted product. Do not reset, clean,
revert, stash or reconstruct this baseline from the commit without first
preserving the working-tree state.

## Accepted capability map

- **P0 — semantic checkout/address/payment:** authoritative quotes, resolved
  delivery addresses, immutable order pricing, reservations, verified payment
  results, cancellation and financial void behavior.
- **P1 — discovery and Product experience:** catalog search, Product detail,
  Product Evidence, controlled media, presentation copy and advisory fit flow.
- **P2 — catalog/inventory/lifecycle:** Product/Variant administration,
  location-aware stock, explicit inventory operations and variant sale
  lifecycle.
- **P3 — promotions:** automatic Product/order/shipping promotion evaluation,
  voucher family issuance/claim/redemption and immutable allocations.
- **P4 — barcode/POS:** unique variant barcode identity and the accepted limited
  exact-cash, single-line POS workflow.
- **P5A — deterministic merchandising:** authoritative candidate eligibility,
  bestseller evidence, Product-promotion evidence, readiness, placement,
  versioned Storefront drafts and atomic publication.
- **P5B — optional manager AI assistance:** bounded VI/EN copy proposals and
  strict manager-intent interpretation into existing P5A filters. AI cannot
  rank Products, choose Product IDs, mutate drafts, save or publish.
- **Human UX/visual polish:** accepted responsive behavior, accessibility
  basics, state correctness and browser verification at desktop and 390 px.

The architecture map is [ADR/README.md](ADR/README.md), the broader design is
[ARCHITECTURE.md](ARCHITECTURE.md), and the API contract index is
[API/README.md](API/README.md).

## Optional-AI failure boundary

P5B is optional. When it is disabled, unconfigured, timed out, rate limited,
refused or returns malformed structured output:

- deterministic P5A recommendations remain available;
- Storefront management and manual editing remain available;
- Save Draft and Publish remain available;
- no provider request or response is intentionally persisted;
- no provider failure can grant business authority or change ranking rules.

Provider calls use the Responses API with strict `text.format`, `store=false`,
`reasoning.effort=none`, bounded output and no tools or conversation state.
Local provider-contract tests prove the request shape, but only a successful
call with deployment credentials can close the live-provider gate.

## Current verification evidence

The accepted P5B implementation and final regression produced:

| Gate | Result |
|---|---|
| Focused P5B backend | 25/25 passed, including 10/10 provider HTTP contract tests |
| Backend unit | 27 suites, 99/99 passed; 0 failures/errors/skips |
| SQL Server integration | 37 suites, 232/232 passed; 0 failures/errors/skips |
| Frontend | 23/23 files, 286/286 tests passed |
| TypeScript | `vue-tsc -b` passed |
| Production build | Passed, 98 modules |
| Real-stack browser | Passed at 1440×900 and 390×844 with AI disabled |
| Disabled-AI isolation | Manual discovery, editing, Save Draft and Publish passed |
| Migration | Fresh V1→V33 passed; no V34 |
| Live OpenAI provider | Not run: `OPENAI_API_KEY` not configured |

The live-provider verdict remains:

`LIVE AI PROVIDER SMOKE NOT RUN — OPENAI_API_KEY NOT CONFIGURED`

Required deployment verification is exactly one safe Vietnamese copy request,
one supported `BEST_SELLERS` intent, and one `UNSUPPORTED` intent using demo
merchandising data. Results must remain previews only and must not be applied or
published automatically. A rejected model/request/schema must be reported; the
model must not be silently changed and strict output must not be weakened.

## Migration and data freeze

The migration directory contains exactly 33 ordered files, V1 through V33,
with no missing version and no V34. P5B adds no migration and does not persist
prompts, completions, chats, response IDs, assistant sessions or recommendation
decisions.

## Demo and handoff

Use [DEMO.md](DEMO.md) with a fresh disposable database and the explicit
`demo` profile. Supply provider credentials only through the process
environment. Never place `OPENAI_API_KEY` in source, repository files, fixtures,
screenshots, logs or documentation.

The recommended presentation sequence is:

1. shopper discovery, Product Evidence, Product detail and Cart;
2. promotion, Pickup/Delivery checkout and deterministic payment;
3. My Orders and fulfillment;
4. catalog, inventory, promotion and reporting administration;
5. barcode lookup and optional exact-cash POS;
6. P5A Storefront discovery/edit/save/publish;
7. optional P5B copy/intent preview only when provider configuration is valid.

## Deferred debt

- Generic AI chat, autonomous actions and autonomous publishing.
- AI Product ranking, generated media and embeddings/vector search.
- Persistent AI history/provenance and multi-provider routing.
- Media library and Product publication timestamp.
- Recommendation dismissal/history.
- Expanded POS features beyond the accepted limited workflow.
- Real VNPAY sandbox certification, carrier integration, returns/exchanges and
  additional payment providers.
- P2 staff-navigation discoverability at narrow widths and P3 long mobile
  Product-management scrolling remain accepted UX debt.

## Source-control and release boundary

- No Git stage, commit, tag, branch, push, reset, clean, stash, rebase or release
  is authorized by this preparation.
- A final package/freeze must wait for the live OpenAI provider smoke or must be
  explicitly released as core-ready with the provider prerequisite documented.
- The working tree remains the source of truth until separately authorized
  source-control packaging is performed.

The next valid final verdict after a successful live-provider smoke and fresh
minimal regression check is:

`FINAL PROJECT READINESS: PASSED — READY TO FREEZE`
