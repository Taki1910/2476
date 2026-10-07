# ADR-0037: Read-only Product Evidence customer projection

Status: Accepted for bounded read-only Product Evidence slice

Date: 2026-09-19

## Context

The Product Detail Page already exposes authoritative product identity, published
variants, current base prices and customer-safe availability. The Catalog also
stores optional primary/hero asset references and complete shoe-fit profiles for
some products. The current customer response does not package those facts around
the questions a shopper asks, so the SPA recomputes a price range, renders only
one image, and hides useful fit metadata behind the photo-analysis flow.

The repository has no authoritative localized product description, material or
care content, no approved secondary photography, and no confirmed return or
exchange policy. Product-specific image descriptions are currently hard-coded in
the SPA and include factual claims that are not backed by Catalog data.

This slice must improve product evidence using existing sources only. It must not
create ProductPresentation or Product Media persistence, introduce authoring,
change pricing or inventory rules, or turn cart-time fulfillment calculations
into PDP promises.

## Decision

`GET /api/v1/storefront/products/{productId}` remains the customer PDP read
endpoint. Its response is extended additively with three customer-oriented
projections:

1. `pricing`: a server-derived summary over the same effective published variant
   prices used by the detail query;
2. `media`: an ordered, deduplicated projection over the existing Product
   `primary_image` and `hero_image` references;
3. `fitGuidance`: the existing complete Product fit profile and its size ranges,
   or `null` when the profile is missing, unsupported or inconsistent.

Existing flat Product merchandising fields and per-variant amounts remain in the
wire response for backward compatibility. `campaignEligible` and
`merchandisingRank` are legacy fields, not evidence or promotion claims. New PDP
code must not use them. Their removal requires a later coordinated contract
migration.

`presentation` is intentionally absent from this first-slice response. A null or
absent presentation is valid in the future contract, but no empty DTO or database
infrastructure is created before authoritative editorial content and an
authoring owner exist.

## Ownership

- Catalog owns Product and ProductVariant identity, existing image references,
  and Product shoe-fit profiles.
- Pricing owns effective variant prices. The detail service only projects their
  minimum and maximum at one authoritative read instant.
- Inventory owns stock balances and reservations. The existing storefront query
  continues to expose only `AVAILABLE` or `UNAVAILABLE`.
- AI/Fitting consumes Catalog fit data and returns advisory analysis. It does not
  own or modify the fit profile.
- ProductPresentation remains the approved future merchandising boundary for
  localized subtitle, story and highlights. It is outside this slice.
- Store Policy, Fulfillment and Promotion remain the only future authorities for
  policy, delivery/pickup and promotion claims. None are added to this response.

## Customer response additions

The additive response shape is:

```text
pricing
  state: SINGLE | RANGE
  minimumAmount: integer VND
  maximumAmount: integer VND
  currency: VND

media[]
  url: existing controlled asset reference
  position: zero-based display order
  alt: canonical Product name

fitGuidance: null | object
  sizeSystem: EU
  fitTendency: RUNS_SMALL | TRUE_TO_SIZE | RUNS_LARGE
  widthProfile: NARROW | REGULAR | WIDE
  fitAssistantSupported: true
  ranges[]
    size
    minimumFootLengthMm
    maximumFootLengthMm
    minimumFootWidthMm
    maximumFootWidthMm
```

The existing `variants[]` contract remains unchanged.

### Pricing rules

The summary is calculated from the effective prices of the published variants
returned by the Product detail query at the same server instant.

- `SINGLE` means minimum and maximum are equal.
- `RANGE` means they differ.
- Amounts are not persisted on Product or merchandising records.
- The summary does not include unconfirmed promotion, shipping or voucher
  effects.

### Media rules

Media ordering is deterministic: non-blank `primary_image` first, followed by a
distinct non-blank `hero_image`.

- References are trimmed and then compared exactly. Equal references produce one
  entry only; URL case is not folded because an asset path may be case-sensitive.
- Missing or blank references are omitted; both missing produces `media: []`.
- No asset, gallery slot, media ID or variant association is invented.
- The canonical Product name is the only fallback alt in this slice. The SPA no
  longer maps product names to factual material, construction or use-case claims.

### Fit completeness rules

Fit guidance is returned only when all of these conditions hold:

- exactly one Product profile exists and uses the supported `EU` size system;
- at least one published storefront variant size exists;
- every distinct visible variant size has exactly one persisted range;
- every range passes the persisted length and width constraints;
- tendency and width values are supported enums.

Ranges are ordered with an explicit SQL Server numeric-first discriminator:
numeric labels first by `TRY_CONVERT(DECIMAL(10,2), size_label)`, then
non-numeric labels by their original label. The discriminator is required
because ascending SQL Server ordering places `NULL` conversions before numeric
values. Extra persisted ranges for sizes that are not
currently published do not invalidate the profile and are not returned to the
customer. A missing or duplicate range for any visible size, or other
inconsistent data, returns
`fitGuidance: null`; the service never supplies default measurements or a
generic chart. `fitAssistantSupported` is true only inside a non-null complete
guidance object.

The existing Fit Assistant uses the same support gate for published sizes, and
the database uniqueness constraint prevents two persisted ranges for the same
profile and size label. Its recommendation engine may retain extra authored
ranges for sizes that are not currently published; the public `fitGuidance`
projection deliberately filters those ranges out. This preserves the fitting
service's authoritative rule without exposing unavailable size rows in the PDP
guide.

## Frontend behavior

- The PDP renders the server `pricing` summary before selection and continues to
  show the selected variant amount after explicit selection.
- The first media item is the main image. Additional distinct items are reachable
  through an accessible ordered gallery control. Generic localized image
  position/navigation labels distinguish controls without inventing
  image-specific descriptions. No gallery control appears for zero or one item.
- With `media: []`, the existing neutral placeholder is used and its alt is the
  canonical Product name.
- A non-null `fitGuidance` appears below the purchase decision region in a
  collapsed, progressively disclosed Size and fit guide. It labels the EU system,
  tendency, width profile and authored measurement ranges without promising fit.
- The optional Fit Assistant remains visually secondary and retains its explicit
  acceptance step.
- No heading is rendered for absent evidence. The PDP never prints `N/A`, empty
  fields or replacement marketing copy.
- Interface labels remain in frontend i18n. Product-specific content is never
  moved into translation keys.

## Compatibility and failure behavior

The endpoint keeps its current visibility rule: a Product without an effective
published variant is not a storefront detail. Therefore a valid detail always has
at least one authoritative amount for its pricing summary.

New fields are additive. Existing clients may ignore them, and legacy fields are
not removed. Missing media and fit content are successful partial responses, not
errors. A missing Product retains the current not-found behavior.

No migration is required. No old Flyway migration is edited.

## Verification

Backend regression coverage must prove:

- identical primary/hero references deduplicate;
- distinct references preserve primary-then-hero order;
- missing references return an empty list;
- equal prices produce `SINGLE`; differing prices produce the correct `RANGE`;
- missing and inconsistent profiles return null;
- complete profiles return only their persisted ranges in deterministic order;
- legacy fields and variants remain serialized compatibly.

Frontend coverage must prove media-only, fit-only, neither and both states; no
unsupported product claim remains in fallback alt text; and absent evidence does
not render an empty section.

Verification includes the relevant SQL Server integration tests, frontend unit
tests, typecheck, production build, and real-browser PDP review at desktop and
approximately 390 px mobile. The review asks whether price, imagery and fit feel
more trustworthy without implying unsupported product or policy facts.

## Deferred

- ProductPresentation persistence, revisions, publication and authoring;
- `catalog_product_media` persistence and variant-specific media;
- new product assets or product claims;
- material, care and product-characteristic fields;
- return/exchange, delivery ETA, shipping estimate, pickup or promotion claims;
- removal of legacy internal merchandising fields from the public DTO.

Future ProductPresentation and Product Media work requires approved content,
localized authoring responsibility, publication authorization and a separate
implementation decision.
