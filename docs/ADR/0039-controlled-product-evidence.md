# ADR-0039: Controlled Product evidence on Catalog Product

Status: Accepted

Date: 2026-09-24

## Context

ADR-0037 exposes existing price, media and fit evidence. ADR-0038 publishes
localized ProductPresentation summaries. Human review found that those sources do
not give shoppers enough factual differentiation: category, collection and three
conservative summaries largely repeat existing taxonomy and fit.

The business has approved five controlled facts and concrete demo values for six
comparison products. These facts are Product master data, not editorial copy,
variant data or AI output.

## Decision

Catalog Product owns five nullable controlled facts:

- `intendedUse`
- `primarySurface`
- `upperConstruction`
- `cutProfile`
- `primarySoleProfile`

They are stored as nullable columns on `catalog_product`. `NULL` means
`UNKNOWN / NOT PROVIDED`. There is no generic attribute table and no persisted
`NOT_APPLICABLE` state in this slice.

Fit tendency and width remain owned by `catalog_shoe_fit_profile`; storefront
projections may expose them but must not copy them into Product. Variant color
remains Variant-owned. Category and collection remain taxonomy. ProductPresentation
remains localized editorial content and does not become a factual source.

### Controlled values

```text
intendedUse:
  CASUAL_DAILY | ROAD_RUNNING | TRAIL_WALKING | TRAIL_RUNNING

primarySurface:
  SMOOTH_HARD_SURFACE | ROAD_TRACK | MIXED_TRAIL

upperConstruction:
  SYNTHETIC_LEATHER | CANVAS_TEXTILE | MESH_TEXTILE | KNIT_TEXTILE |
  WOVEN_TEXTILE | MESH_TEXTILE_WITH_SYNTHETIC_OVERLAYS

cutProfile:
  LOW_TOP | HIGH_TOP

primarySoleProfile:
  CUPSOLE | FLEX_GROOVED | LUGGED
```

The sole profile is descriptive only. It does not assert grip, cushioning,
durability, comfort or performance.

### Approved demo matrix

| Product | Intended use | Primary surface | Upper construction | Cut | Primary sole profile |
|---|---|---|---|---|---|
| Court Classic | CASUAL_DAILY | SMOOTH_HARD_SURFACE | SYNTHETIC_LEATHER | LOW_TOP | CUPSOLE |
| Court High | CASUAL_DAILY | SMOOTH_HARD_SURFACE | CANVAS_TEXTILE | HIGH_TOP | CUPSOLE |
| Metro Runner | ROAD_RUNNING | ROAD_TRACK | MESH_TEXTILE | LOW_TOP | FLEX_GROOVED |
| Pace Knit | ROAD_RUNNING | ROAD_TRACK | KNIT_TEXTILE | LOW_TOP | FLEX_GROOVED |
| Trail Form | TRAIL_WALKING | MIXED_TRAIL | WOVEN_TEXTILE | LOW_TOP | LUGGED |
| Trail Edge | TRAIL_RUNNING | MIXED_TRAIL | MESH_TEXTILE_WITH_SYNTHETIC_OVERLAYS | LOW_TOP | LUGGED |

Existing fit remains authoritative for Court Classic (`TRUE_TO_SIZE / REGULAR`),
Metro Runner (`TRUE_TO_SIZE / WIDE`) and Trail Form
(`TRUE_TO_SIZE / REGULAR`). Fit remains unknown for Court High, Pace Knit and
Trail Edge.

## Application and API behavior

`CATALOG_MANAGE` authorizes management reads and replacement commands. A command
replaces all five facts atomically, requires the expected Product entity version,
and returns `409` when the Product changed. Existing `AuditWriter` records the
change; no audit subsystem is introduced.

Customer list and detail responses add `evidence`. When all five fields are null,
`evidence` is null. Otherwise it is an object whose individual fields may be null.
No layer infers missing values from taxonomy, names, images or another Product.
Catalog list responses may project a small fit summary from a complete existing
FitProfile; Product detail retains the ADR-0037 full fit guidance.

Codes are not localized persistence. Vue maps them to localized labels.

## Management and presentation

The SPA currently has no Catalog/Product management surface. Add a general
`/operations/products` surface rather than a narrowly named Product-evidence
screen. It shows existing Product context and controlled selects, including an
“Unknown / not provided” option.

Catalog cards expose only a compact, high-signal subset. PDP placement remains
responsive and evidence-led: a compact fact summary may sit near the purchase
decision while the full fact set remains progressively available. Size selection,
availability and Add to Cart must not be displaced on mobile. ProductPresentation
remains secondary editorial context and is removed from catalog-card comparison.

## Persistence and seed behavior

Flyway adds only nullable columns and SQL Server check constraints. Old migrations
are not changed. The demo bootstrap writes exactly the six approved rows
idempotently. Other Products remain null. Re-running demo bootstrap must preserve
the matrix.

## Excluded

Cushioning, weight, weather capability, heel-to-toe drop, durability, comfort,
breathability, support, closure, outsole material, lining, care, gender target and
detailed construction remain outside this decision.

## Consequences

The model stays small and strongly validated, and shoppers gain factual comparison
without a PIM or new publication subsystem. Catalog fact changes become visible
after an authorized atomic save; optimistic locking and existing audit history
protect against silent overwrite. Taxonomy, fit and editorial copy can still drift
semantically, so the management surface presents them together but never derives
one from another.
