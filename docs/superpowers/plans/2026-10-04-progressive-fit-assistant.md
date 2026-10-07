# Progressive Fit Assistant Implementation Plan

## Scope

Frontend, frontend tests, i18n, and CSS only. Reuse the current Fit Assistant,
Product fit-guidance response, variants, emit contract, and photo API.

## Test-first sequence

1. Extend Fit Assistant entry tests for both paths and VI/EN copy.
2. Add PDP integration tests for tight/about-right/loose input, authored Product
   evidence, real-size-only choices, unavailable selected-color recovery, and
   explicit shopper application with no cart side effect.
3. Update photo tests for direct Photo → Review → Result, HIGH/MEDIUM wording,
   and every `RETAKE` recovery action with no fabricated selection.
4. Add focus-return/focus-transition assertions while retaining existing guide,
   deep-link, Color → Size, availability, and cart regression tests.
5. Implement the smallest local-state/template change in `FitAssistant.vue`,
   pass existing `fitGuidance` from `ProductView.vue`, then add VI/EN copy and
   responsive CSS.
6. Run focused tests, full frontend tests, typecheck, build, backend regression,
   migration-continuity and diff-integrity checks.
7. Execute real-browser acceptance on the existing Spring + SQL Server stack at
   desktop and approximately 390px. Automated success alone is not a pass.

## Acceptance matrix

- Quick: PDP → assistant → usual EU size → tight/normal/loose → evidence →
  explicit size application.
- Availability: selected-color unavailable and available-other-color paths use
  only real variants.
- Photo: A4 requirement, file validation, review, loading, HIGH, MEDIUM, and all
  `RETAKE` reasons.
- Safety: no automatic size/cart action, no size arithmetic, no brand mapping,
  no A4-free measurement claim, no fake confidence percentage.
- Regression: Color → Size, same-product `?variant=` resynchronization, direct
  PDP, disabled sizes, cart and duplicate protection, VI/EN, keyboard/focus,
  desktop and ~390px.

