# Progressive Fit Assistant Design

## Status

`CONFIRMED` by the user on 2026-10-04.

## Purpose and boundary

Reduce shopper effort when choosing a size without changing catalog, variant,
inventory, cart, or image-analysis business behavior. This is a frontend-only
presentation and discovery change. No backend, migration, dependency, computer
vision, brand conversion, or size-arithmetic work is permitted.

## Shopper flow

The existing PDP entry remains visually secondary to the purchase controls.
Opening it presents two explicit paths:

1. **I know my usual size** — the shopper selects an EU size that actually
   exists for the product and says whether their current footwear feels tight,
   about right, or loose.
2. **Measure with a photo** — the shopper is told that a fully visible A4 sheet
   is required, then follows Photo → Review → Result using the existing API.

The quick result calls the usual EU size a starting point. Fit perception and
the product's authored fit tendency/width profile are shown as evidence only;
they never produce `+/-` size arithmetic. The shopper must explicitly apply the
size. Availability and alternative-color actions use only current product
variants.

The photo path keeps existing validation, privacy copy, A4 calibration, API,
availability handling, and explicit size acceptance. `HIGH` is a strong photo-
quality result. `MEDIUM` is the only supported reduced-confidence result and is
described as a usable image-based estimate, not a fit probability. A `RETAKE`
never exposes a size action and always offers both retry-photo and switch-to-
quick-guidance recovery.

## Interaction and accessibility

- Native buttons, radio inputs, and a native EU-size select are used.
- Opening the assistant, choosing a path, reaching a result, and reaching a
  retake state move focus to the new heading; closing returns focus to entry.
- Status/error content retains live-region semantics.
- Controls keep 44px minimum targets and stack cleanly near 390px.
- VI and EN copy describe guidance, evidence, availability, and confidence
  without guarantees or fake percentages.

## Preserved behavior

PDP Color → Size order, deep-linked variant synchronization, unavailable-state
rules, manual size selection, selected-color resolution, add-to-cart behavior,
duplicate protection, and the existing size/fit guide remain unchanged.

