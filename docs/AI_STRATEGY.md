# AI Strategy — Blueprint v1.1

> Architecture boundary: **ACCEPTED**
>
> MVP implementation scope: **DEFERRED**

## Principle and release priority

AI assists; it never owns deterministic transactional truth. Every AI result is
a `Recommendation` or `Draft` until schema validation, deterministic business
validation, authorized preview/approval where required, and an ordinary
application use case.

| Capability | Feasibility | Authority |
|---|---|---|
| Size recommendation from declared measurements | `OPTIONAL ADVANCED` | Recommendation only |
| Image-assisted foot analysis | `DEFERRED` pending privacy/quality evidence | Recommendation only |
| Product recommendation/personalization | `OPTIONAL ADVANCED` | Ranked suggestions only |
| Promotion/campaign recommendation | `OPTIONAL ADVANCED` | Draft; no benefit grant |
| Banner/marketing copy or image generation | `OPTIONAL ADVANCED` | Draft content |
| Structured homepage/page configuration | `OPTIONAL ADVANCED` | Draft configuration |
| Virtual try-on | `DEFERRED` | No architecture selected |

No AI capability is required for the first transactional release. Checkout,
catalog browsing, size selection, and content management must work without AI.

## Execution boundary

```text
Input -> Application use case -> AI Adapter -> Structured output
      -> Schema validation -> Deterministic business validation
      -> Preview/consent -> Authorized ordinary use case -> Stored result
```

The adapter may receive the minimum approved data for one bounded task. It does
not receive repositories, SQL, broad admin tools, inventory/payment/refund
commands, credentials, or permission-management capability.

## Customer recommendation rules

- Clearly identify fit/size output as a recommendation, not a guarantee.
- State supported size systems and confidence/limitations; never invent missing
  measurements or stock facts.
- Validate recommended variants against current published catalog data; current
  stock/price is fetched from deterministic services at display/checkout time.
- Provide a non-AI path to choose size and products.
- Obtain explicit approved consent before collecting measurements/images; define
  purpose, retention, deletion, and human support paths first.
- Do not use sensitive/protected attributes for targeting without explicit lawful
  policy and review.
- Recommendation failure or provider outage cannot block checkout.

`OPEN DECISION`: Accuracy targets, calibration dataset, supported shoe/size
systems, confidence UX, image-processing location, retention/deletion policy,
and whether images are stored at all.

## Admin content and campaign rules

- AI produces data conforming to a versioned `PageConfiguration` or campaign
  draft schema, not executable markup or scripts.
- Deterministic validation checks component allowlist, lengths, accessibility
  metadata, referenced product/campaign IDs, publication windows, and scope.
- Preview and explicit authorized approval are required before publication.
- Publication creates a versioned immutable record; rollback republishes a known
  valid version.
- A draft promotion must pass the same promotion engine validation and approval
  as a human-authored definition. AI cannot activate, issue, redeem, or target a
  discount by itself.

## Data and security boundary

- Classify every input field before provider transmission; minimize and redact
  personal, employee, order, and security data.
- Never send credentials, reset tokens, raw payment data, authorization grants,
  secrets, or unrestricted audit records.
- Treat prompt and model output as untrusted input; validate, escape, and apply
  normal authorization at the final use case.
- Store provider/model/config/version provenance only as needed for evaluation,
  support, and audit; do not retain prompts by default.
- Apply tenant/branch/customer ownership checks before retrieving context and
  before persisting any approved result.

## Evaluation and failure behavior

- A confirmed use case, baseline without AI, representative evaluation set, and
  acceptance threshold are required before customer-facing claims.
- Test performance across supported sizes/product families and inspect harmful
  or systematically poor recommendations.
- Invalid, unsafe, unavailable, or low-confidence output fails closed to the
  ordinary non-AI experience.
- Observe quality, latency, cost, provider errors, approval/rejection rate, and
  privacy/security incidents without logging sensitive raw inputs.

## Provider decision gate

No provider is selected. A later ADR must cover business value, evaluation
evidence, data categories and consent, regional/compliance constraints,
availability/cost, fallback, abuse controls, security, retention, observability,
and exit strategy.

Vector databases, dedicated inference services, brokers, GPU services, and
fine-tuning remain `DEFERRED`. Add them only after a confirmed use case and
measured limitation show that the existing application/SQL boundary cannot meet
the requirement.

## Phase 17 resolved fitting slice

Phase 17 implements the approved local path from [ADR-0028](ADR/0028-ai-assisted-shoe-fitting.md):
constrained deterministic Java CV calibrated by a visible A4 sheet. It is not a
trained model and does not create a probability claim. The output remains a
validated customer proposal; it cannot change stock, payment, authorization or
publishable business state. Raw images are transient request data and are not
stored. Learned segmentation, provider selection, history and feedback remain
deferred.
