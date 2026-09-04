# ADR-0028: AI-assisted shoe fitting MVP

- Status: `ACCEPTED` for Phase 17
- Date: 2026-09-02
- Extends: ADR-0009, ADR-0015 and ADR-0026
- Decision class: safe to freeze for the Phase 17 product slice

## Context

The customer product page needs an evidence-based size recommendation without
turning AI output into transactional authority. A photograph has no physical
scale by itself, shoe fit differs by model, and variant availability can change
independently from anatomical fit. The MVP therefore needs a measurable local
pipeline, explicit product-owned fit metadata and honest failure states. It must
not upload customer foot images to a paid provider or retain them after the
request.

The project has no approved CV service or machine-learning runtime. Adding one
for a bounded reference-sheet measurement would create deployment, privacy and
operational cost without improving the confirmed MVP acceptance criteria.

## Decision

Phase 17 uses an in-process, deterministic Java image-analysis pipeline behind
one public storefront multipart endpoint. The image must show one bare or
sock-covered foot placed on a fully visible A4 sheet. The known 210 x 297 mm
sheet is the mandatory physical reference.

The pipeline:

1. validates the real uploaded format, byte size and decoded pixel dimensions;
2. detects the neutral A4 quadrilateral and rejects missing, clipped or
   excessively distorted references;
3. maps image coordinates to millimetres with a projective transform;
4. isolates the largest plausible foot component inside the sheet;
5. derives foot length and width from its principal axes;
6. emits a recommendation only when measurement quality and product profile
   coverage pass their thresholds.

This is classical, constrained computer vision and deterministic recommendation
logic. It is described as AI-assisted in the customer experience, but is not a
learned model and must not be represented as one.

Raw image bytes exist only for the duration of the HTTP request. They are not
written to SQL Server, logs, files, events, audit payloads or analytics. Requests
are bounded by HTTP and application limits, decoded dimensions and a small
concurrency permit. The endpoint supports guest customers and remains subject to
the existing CSRF policy.

## Fit data ownership and recommendation rules

`Product` owns one optional shoe fit profile. A profile declares its supported
size system, model fit tendency and width profile. It owns per-size foot length
and width ranges keyed by the existing variant size label. Only products with a
complete approved profile are measurable in this MVP; the current supported
system is EU.

Recommendation is a proposal, not a cart command. Product-authored per-size
length and width ranges are the authoritative mapping of a customer's foot to
that model's fit. `RUNS_SMALL`, `TRUE_TO_SIZE`, `RUNS_LARGE`, and the width
profile describe the model and its authored ranges; they do not apply a second
numeric offset. This prevents double-counting a tendency already represented in
the ranges.

The engine selects the range containing the measured length and checks its
per-size width range. It may move exactly one size up for width only when the
immediate next size contains both the observed length and width. Otherwise it
retains the length-based recommendation and emits a width-mismatch warning. An
adjacent size is advisory only, never an inventory substitution.

Confidence is an analysis-quality score, not a fit probability. Image quality
combines reference geometry, perspective, sharpness and segmentation; the
recommendation blends that score with distance from the selected range boundary.
Profile completeness is a support gate, not an optimistic confidence input. Low
confidence returns a specific retake reason instead of a size.

Fit and stock remain separate facts. An unavailable recommended variant does not
change the anatomical recommendation. The response reports availability and
available colours separately, and the customer must explicitly select a variant
and use the existing add-to-cart flow. Manual size choice always remains
available.

## Persistence and migration

Flyway V20 appends normalized product fit-profile and per-size range tables with
foreign keys, uniqueness and range checks. It stores neither photos nor analysis
results. Seeded demo profiles cover deliberately different model tendencies and
width profiles so the recommendation engine is not a universal lookup table.

The migration must validate on a fresh V1-to-V20 database and on a populated V19
database upgraded to V20. Hibernate remains `ddl-auto=validate`.

## Quality, failure and evaluation

Stable retake reasons cover at least missing reference, clipped reference,
excessive perspective, blur/weak segmentation, implausible measurement and low
confidence. Unsupported products are explicit and do not receive a fabricated
default size. Malformed, oversized and unsupported files fail safely.

Evaluation uses committed controlled fixtures plus in-memory stress fixtures
with known physical dimensions. The report records measured error, rejection
behavior, their idealized-synthetic classification, and the absence of
real-camera validation. Real browser acceptance must exercise the Vue product
page, Spring endpoint, SQL-backed product profile and local image pipeline
together.

## Consequences and risks

- The design is private, offline, reproducible and deploys with the existing
  modular monolith.
- Measurement quality depends on the customer following the capture guide;
  oblique angles, shadows, patterned socks and hidden sheet corners can require a
  retake.
- The controlled-fixture result does not prove clinical or footwear-industry
  scanner accuracy. Copy must present guidance, not a guarantee.
- Calibrating thresholds or supporting additional size systems requires measured
  fixtures and approved product fit data, not a frontend-only change.

## Rejected alternatives

- **Arbitrary photo without a scale reference:** cannot yield defensible
  millimetres.
- **Hard-coded size from image upload success:** deceptive and untestable.
- **LLM or paid vision API:** unnecessary, non-deterministic and expands the
  privacy/provider boundary.
- **Python/OpenCV sidecar or new CV dependency:** no confirmed requirement
  justifies another runtime for the bounded MVP pipeline.
- **Persisting photos for future training:** outside consent, retention and AI
  governance scope.
- **Changing the recommendation to an in-stock size:** conflates fit with
  availability and can recommend a worse fit.

## Deferred

Customer feedback, profile history, multi-foot comparison, learned segmentation,
additional reference objects, non-EU sizing and virtual try-on remain outside
Phase 17. Add them only through an approved data/privacy and model-evaluation
decision.
