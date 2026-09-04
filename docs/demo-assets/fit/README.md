# Controlled fit fixtures

These PNGs are deterministic, idealized synthetic fixtures for the Phase 17
local classical-CV pipeline. They contain a dark background and a neutral A4
sheet; the valid fixture has a rounded dark foot silhouette with target geometry
of 251 mm by 98 mm.

- `valid-a4-foot.png` returns `SUCCESS` near 251 mm length and 98 mm width.
- `invalid-no-reference.png` is expected to return `RETAKE / REFERENCE_NOT_FOUND`.
- `invalid-clipped-sheet.png` is expected to return `RETAKE / REFERENCE_CLIPPED`.
- `invalid-blurred.png` is expected to return `RETAKE / IMAGE_TOO_BLURRY`.

`FitImageAnalyzerTest` additionally generates clean, rotated, projective,
off-centre, near-edge, shadow/noise and partial-foot cases in memory. The whole
set is useful but idealized synthetic validation, not real-camera evidence. It
contains no customer photographs and proves no medical, clinical, scanner, or
population-level accuracy claim.
