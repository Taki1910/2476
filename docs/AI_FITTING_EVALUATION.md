# Phase 17 AI-assisted fitting evaluation

Status: Terra-audited controlled evaluation; not a certification of product or
camera accuracy.

## Architecture

The MVP uses constrained, deterministic classical computer vision in the Spring
backend. `FitImageAnalyzer` uses Java ImageIO/AWT, neutral-sheet segmentation,
connected components, a projective transform and principal-axis geometry. There
is no trained ML model, LLM inference, paid vision provider, Python sidecar or
OpenCV dependency. The customer-facing feature is therefore “AI-assisted” in
product language, but its measurement is a reproducible CV heuristic, not a
learned model or a probability of correct fit.

The pipeline is:

```text
PNG/JPEG bytes -> decode limits -> A4 quadrilateral -> homography
-> foot component -> principal-axis length/width -> guardrails
-> Product fit profile -> deterministic recommendation + availability facts
```

## Calibration

A4 is the mandatory reference because its physical dimensions are fixed at
210 x 297 mm. The analyzer detects the largest connected neutral-bright sheet
region, verifies its area, aspect ratio, corner visibility and perspective, and
maps the four detected corners to the known A4 coordinate system. All subsequent
measurements use those transformed millimetres. If the sheet is missing,
clipped, too distorted or cannot produce a stable transform, the result is
`RETAKE`; no scale is guessed.

## Controlled fixture classification

The committed PNGs and the in-memory stress fixtures are **B — useful but
idealized synthetic validation**. They use known A4 geometry, high-contrast
silhouettes and deterministic renderer inputs. The stress renderer independently
maps known A4 coordinates into image pixels while production performs the
inverse detected-pixel-to-millimetre transform, but the assets still are not
camera captures. No consented/project-owned real camera assets were found, so:

`REAL CAMERA ROBUSTNESS: LIMITED / NOT FULLY VALIDATED`

No personal images were sourced to fill that gap.

## Measurement results

Current `FitImageAnalyzerTest` output:

| Fixture | Expected L/W | Measured L/W | Length error | Width error | Result |
|---|---:|---:|---:|---:|---|
| `valid-a4-foot.png` | 251.0 / 98.0 mm | 250.6 / 97.8 mm | 0.4 mm | 0.2 mm | SUCCESS |
| `clean-overhead` | 251.0 / 98.0 mm | 250.9 / 97.7 mm | 0.1 mm | 0.3 mm | SUCCESS |
| `off-center-shorter-foot` | 235.0 / 90.0 mm | 234.9 / 89.4 mm | 0.1 mm | 0.6 mm | SUCCESS |
| `near-edge-longer-foot` | 270.0 / 105.0 mm | 270.1 / 104.7 mm | 0.1 mm | 0.3 mm | SUCCESS |
| `moderate-rotation` | 251.0 / 98.0 mm | 251.4 / 98.4 mm | 0.4 mm | 0.4 mm | SUCCESS |
| `moderate-projective` | 270.0 / 105.0 mm | 270.5 / 105.5 mm | 0.5 mm | 0.5 mm | SUCCESS |
| `shadow-and-noise` | 235.0 / 90.0 mm | 234.9 / 90.0 mm | 0.1 mm | 0.0 mm | SUCCESS |
| `missing-reference` | — | — | — | — | RETAKE / `REFERENCE_NOT_FOUND` |
| `clipped-reference` | — | — | — | — | RETAKE / `REFERENCE_CLIPPED` |
| `partial-foot` | — | — | — | — | RETAKE / `FOOT_PARTIAL` |
| `blur` | — | — | — | — | RETAKE / `IMAGE_TOO_BLURRY` |

Across the seven successful idealized synthetic fixtures, median absolute length
error is 0.1 mm (maximum 0.5 mm) and median absolute width error is 0.3 mm
(maximum 0.6 mm). The valid-fixture retake/failure rate is 0/7; the four
invalid stress fixtures return safe retakes 4/4. The separately committed demo
invalid fixtures also return their documented retake reasons. These figures are
synthetic self-consistency results, not a population, camera, medical or
footwear-laboratory accuracy estimate.

## Recommendation

Each EU profile owns a per-size length and width range. Those product-authored
ranges are the real fit mapping. Fit tendency and width profile are explanatory
metadata for that mapping, so the engine deliberately does not add another
numeric tendency or width-profile offset. This avoids double-counting a model
whose ranges already encode that it runs small or large.

| Measurement | Product / profile | Expected | Actual | Width behavior | Result |
|---|---|---|---|---|---|
| 250.6 x 97.8 mm | Court Classic / true-to-size, regular | EU 40 | EU 40 | within profile | PASS |
| 250 x 92 mm | After Dark / runs-small, narrow | EU 40 | EU 40 | within authored range | PASS |
| 250 x 94 mm | City Loafer / runs-large, wide | EU 39 | EU 39 | within authored range | PASS |
| 252 x 105 mm | Court Classic standard profile | EU 40 | EU 40 | `WIDTH_MAY_NOT_MATCH`; next length range does not fit | PASS |
| 252 x 105 mm | controlled overlapping next-size profile | EU 41 | EU 41 | `WIDTH_SIZE_UP`; next range fits both dimensions | PASS |

The recommendation is a size proposal only. Variant selection, color choice,
availability and add-to-cart remain the existing storefront flow. Stock never
rewrites the best-fit size.

## Confidence

The image-analysis score is a 0–100 quality heuristic: A4 ratio (30%),
perspective severity (20%), Laplacian sharpness (25%) and component segmentation
ratio (25%). The recommendation score is `78% image quality + 22% selected
range-boundary margin`, capped at 100. A score below 58 returns `RETAKE`;
80–100 is shown as High and 58–79 as Medium. It is an analysis-confidence
label, not a calibrated probability that a shoe will fit. Profile completeness
is a prerequisite for support, not a confidence boost.

## Limitations

The heuristic assumes one foot, a mostly overhead camera, a neutral A4 sheet,
reasonable lighting and a visually separable foot region. It is sensitive to
dark sheet patterns, strong shadows, socks that blend into the sheet, occlusion,
multiple feet, curled toes and extreme perspective. It supports only profiled EU
products. It does not establish medical, orthotic, biometric or footwear-lab
accuracy. Real-camera validation remains a required future evidence set.

## Privacy

The browser keeps the selected `File` and preview URL in memory while the flow
is open; reset, close and unmount revoke the preview and clear the file. The
backend reads multipart bytes directly, decodes them in memory and returns
derived measurements. It writes no image, result, audit payload, event or
analytics record. The processing permit is released in a `finally` block on
success and every failure path. The 5 MB multipart/decoded-dimension limits and
bounded Tomcat swallow limit reject oversized requests before unbounded work.

## Non-medical scope

This is commercial shoe-fit guidance for selecting a product size. It is not a
medical measurement, diagnosis, orthotic assessment or guarantee of comfort.
