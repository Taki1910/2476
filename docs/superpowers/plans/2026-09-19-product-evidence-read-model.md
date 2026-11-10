# Product Evidence Read Model Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Expose truthful price, ordered existing media, and complete existing fit guidance on the PDP without adding persistence, authored claims, or policy promises.

**Architecture:** Extend the existing `StorefrontCatalogService.ProductDetail` read model additively. Derive pricing from the exact `VariantView` result set, adapt media from current Product references, and project only validated visible-size fit ranges. The Vue PDP consumes those fields below the existing purchase hierarchy while legacy fields remain intact.

**Tech Stack:** Java 25, Spring Boot 4.1, JDBC, SQL Server/Flyway validation, Vue 3, TypeScript 5.9, Vitest, Vite.

**Spec:** `docs/ADR/0037-product-evidence-read-model.md`

## Global Constraints

- Do not add or edit a Flyway migration.
- Do not add ProductPresentation, Product Media persistence, authoring UI, policy, promotion, delivery, pickup or marketing claims.
- Pricing summary and `variants[].amount` must come from one effective-price result set at one read instant.
- Media references are trimmed, ordered primary then distinct hero, and compared case-sensitively after trimming.
- Canonical Product name is the only fallback alt.
- A visible size without exactly one valid persisted range makes `fitGuidance` null; extra unpublished-size ranges are ignored and omitted.
- New response fields are additive; legacy fields remain serialized.
- Preserve the existing dirty working tree. Do not commit, stage, reset or clean.

## Review Focus

- Whitespace-equal media refs deduplicate while case-different refs remain distinct; Task 1 integration tests pin both behavior classes.
- A price-version transition cannot make summary values disagree with variant values; Task 1 derives both from one list and tests equality.
- Mixed numeric and non-numeric size labels retain deterministic numeric-then-label ordering; Task 1 covers ordering through the SQL-backed projection.
- Extra fit ranges remain usable by Fit Assistant but are absent from public guidance; missing visible ranges return null; Task 1 covers both.
- A late Product response or route change must not retain the previous media selection; Task 3 extends the existing stale-response regression.

---

### Task 1: SQL-backed ProductDetail evidence projection

**Files:**
- Modify: `backend/src/test/java/com/shoecommerce/catalog/VerticalSlice2CustomerCatalogExternalIT.java`
- Modify: `backend/src/main/java/com/shoecommerce/catalog/StorefrontCatalogService.java`

**Interfaces:**
- Consumes: current `detail(UUID)` variant query, `catalog_product.primary_image`, `catalog_product.hero_image`, `catalog_shoe_fit_profile`, and `catalog_shoe_fit_size_range`.
- Produces: `PriceSummary`, `MediaView`, `FitGuidance`, and `FitRangeView` nested records added to `ProductDetail`.
- Preserves: every existing `ProductDetail`, `VariantView`, `ProductSummary`, and hero field.

- [ ] **Step 1: Add failing SQL Server integration tests for price and media**

Add tests that update the fixture Product directly, then call `storefront.detail`:

```java
@Test
void derivesOneConsistentPriceSummaryFromReturnedVariants() {
    Fixture fixture = fixture("evidence-price");
    var single = storefront.detail(fixture.product());
    assertThat(single.pricing()).isEqualTo(new StorefrontCatalogService.PriceSummary("SINGLE", 125_000, 125_000, "VND"));
    assertThat(single.variants()).allSatisfy(variant -> assertThat(variant.amount()).isEqualTo(single.pricing().minimumAmount()));

    catalog.setPrice(fixture.operations(), fixture.unavailableVariant(), 175_000);
    var range = storefront.detail(fixture.product());
    assertThat(range.pricing()).isEqualTo(new StorefrontCatalogService.PriceSummary("RANGE", 125_000, 175_000, "VND"));
    assertThat(range.variants()).extracting(StorefrontCatalogService.VariantView::amount)
            .containsExactly(125_000L, 175_000L);
}

@Test
void projectsTrimmedOrderedMediaWithoutInventingEntries() {
    Fixture fixture = fixture("evidence-media");
    jdbc.update("UPDATE catalog_product SET primary_image = ?, hero_image = ? WHERE public_id = ?",
            " /products/pair.png ", "/products/pair.png", fixture.product());
    assertThat(storefront.detail(fixture.product()).media())
            .containsExactly(new StorefrontCatalogService.MediaView("/products/pair.png", 0, "Court Runner evidence-media"));

    jdbc.update("UPDATE catalog_product SET hero_image = ? WHERE public_id = ?", "/products/PAIR.png", fixture.product());
    assertThat(storefront.detail(fixture.product()).media()).extracting(StorefrontCatalogService.MediaView::url)
            .containsExactly("/products/pair.png", "/products/PAIR.png");

    jdbc.update("UPDATE catalog_product SET primary_image = NULL, hero_image = '   ' WHERE public_id = ?", fixture.product());
    assertThat(storefront.detail(fixture.product()).media()).isEmpty();
}
```

- [ ] **Step 2: Add failing SQL Server integration tests for fit completeness**

Add these SQL Server fixture helpers:

```java
private long insertFitProfile(UUID productId, String tendency, String width) {
    UUID profileId = UUID.randomUUID();
    Timestamp now = Timestamp.from(Instant.now());
    jdbc.update("""
            INSERT INTO catalog_shoe_fit_profile(public_id, product_id, size_system,
                fit_tendency, width_profile, created_at)
            SELECT ?, id, 'EU', ?, ?, ? FROM catalog_product WHERE public_id = ?
            """, profileId, tendency, width, now, productId);
    return jdbc.queryForObject("SELECT id FROM catalog_shoe_fit_profile WHERE public_id = ?",
            Long.class, profileId);
}

private void insertFitRange(long profileId, String size, double minLength, double maxLength,
        double minWidth, double maxWidth) {
    jdbc.update("""
            INSERT INTO catalog_shoe_fit_size_range(profile_id, size_label,
                min_foot_length_mm, max_foot_length_mm, min_foot_width_mm, max_foot_width_mm)
            VALUES (?, ?, ?, ?, ?, ?)
            """, profileId, size, minLength, maxLength, minWidth, maxWidth);
}
```

Then cover:

```java
@Test
void returnsOnlyCompleteVisibleSizeGuidanceAndIgnoresExtraRanges() {
    Fixture fixture = fixture("evidence-fit");
    long profileId = insertFitProfile(fixture.product(), "TRUE_TO_SIZE", "REGULAR");
    insertFitRange(profileId, "41", 250, 257, 88, 99);
    insertFitRange(profileId, "42", 257, 264, 90, 101);
    insertFitRange(profileId, "99", 300, 307, 110, 120);

    var guidance = storefront.detail(fixture.product()).fitGuidance();
    assertThat(guidance).isNotNull();
    assertThat(guidance.fitAssistantSupported()).isTrue();
    assertThat(guidance.ranges()).extracting(StorefrontCatalogService.FitRangeView::size)
            .containsExactly("41", "42");
}

@Test
void omitsFitGuidanceWhenAVisibleSizeHasNoRange() {
    Fixture fixture = fixture("evidence-fit-missing");
    long profileId = insertFitProfile(fixture.product(), "TRUE_TO_SIZE", "REGULAR");
    insertFitRange(profileId, "41", 250, 257, 88, 99);
    assertThat(storefront.detail(fixture.product()).fitGuidance()).isNull();
}
```

Also create a Product with published visible labels `"9"`, `"10"`, `"M"`, and
`"XS"`, inserting variants and ranges deliberately as `"XS", "10", "M", "9"`.
Assert both variant and guidance order `"9", "10", "M", "XS"`: numeric labels
are numeric-first and numeric-sorted, while labels follow deterministically.
Assert that none is selected, parsed into a different label, or replaced with a
default. Assert a second
`insertFitRange(profileId, "41", ...)` fails with
`DataIntegrityViolationException`, documenting the database's stronger
duplicate-range invariant.

- [ ] **Step 3: Run the targeted integration test and observe RED**

Run from `backend/` with the existing isolated SQL Server acceptance environment variables:

```powershell
.\mvnw.cmd -Pacceptance -Dit.test=VerticalSlice2CustomerCatalogExternalIT verify
```

Expected: compilation/test failure because the new records and `ProductDetail` fields do not exist.

- [ ] **Step 4: Implement the minimal backend projection**

Add records with these exact signatures:

```java
public record PriceSummary(String state, long minimumAmount, long maximumAmount, String currency) { }
public record MediaView(String url, int position, String alt) { }
public record FitRangeView(String size, BigDecimal minimumFootLengthMm, BigDecimal maximumFootLengthMm,
        BigDecimal minimumFootWidthMm, BigDecimal maximumFootWidthMm) { }
public record FitGuidance(String sizeSystem, String fitTendency, String widthProfile,
        boolean fitAssistantSupported, List<FitRangeView> ranges) { }
```

Extend `ProductDetail` additively with `PriceSummary pricing`, `List<MediaView> media`, and nullable `FitGuidance fitGuidance`, leaving all current fields and `variants` unchanged.

After the existing variant query returns, derive pricing from that exact list:

```java
long minimum = variants.stream().mapToLong(VariantView::amount).min().orElseThrow();
long maximum = variants.stream().mapToLong(VariantView::amount).max().orElseThrow();
PriceSummary pricing = new PriceSummary(minimum == maximum ? "SINGLE" : "RANGE", minimum, maximum, "VND");
```

Build media in primary/hero order by trimming non-blank refs and adding the hero only when it is not exactly equal to the trimmed primary ref. Use `metadata.name()` as every entry's alt and assign positions after deduplication.

Load the Product profile and ranges inside the existing read-only transaction. Filter ranges to the distinct size labels in `variants`; require one returned range for every visible size. Return null for no profile, unsupported profile values, an empty visible-size set, or any missing visible range. Preserve SQL Server numeric-first ordering for both the visible variant query and fit ranges:

```sql
ORDER BY
    CASE WHEN TRY_CONVERT(DECIMAL(10,2), size_label) IS NULL THEN 1 ELSE 0 END,
    TRY_CONVERT(DECIMAL(10,2), size_label),
    size_label
```

Set legacy `fitSupported` from the existing authoritative support rule. Because that rule also requires every published size to have a range and tolerates extra ranges, it should agree with non-null `fitGuidance`; keep the rule explicit in code rather than coupling only for DTO convenience.

- [ ] **Step 5: Run the targeted integration test and observe GREEN**

Run the same Maven command. Expected: all `VerticalSlice2CustomerCatalogExternalIT` tests pass against SQL Server.

- [ ] **Step 6: Record a no-commit checkpoint**

Run:

```powershell
git diff -- backend/src/main/java/com/shoecommerce/catalog/StorefrontCatalogService.java backend/src/test/java/com/shoecommerce/catalog/VerticalSlice2CustomerCatalogExternalIT.java
```

Confirm the diff contains no migration, pricing mutation or legacy-field removal. Do not stage or commit.

### Task 2: Frontend contract and truthful media fallback

**Files:**
- Modify: `frontend/src/api.ts`
- Modify: `frontend/src/product-media.ts`
- Modify: `frontend/src/product-media.test.ts`
- Modify: `frontend/src/views/CatalogView.vue`
- Modify: `frontend/src/views/CartView.vue`
- Modify: `frontend/src/components/StorefrontSectionPreview.vue`

**Interfaces:**
- Consumes: Task 1 `pricing`, `media`, and `fitGuidance` JSON fields.
- Produces: exact TypeScript types plus a claim-free `productAlt(name)` fallback used outside the PDP.
- Preserves: legacy `ProductMerchandising`, `ProductSummary`, and flat `ProductDetail` fields.

- [ ] **Step 1: Write failing type/helper tests**

Replace product-specific media expectations with:

```ts
expect(productAlt('City Loafer')).toBe('City Loafer')
expect(productAlt('Unknown Product')).toBe('Unknown Product')
expect(productAlt('City Loafer')).not.toMatch(/leather|da |technical|mesh|suede/i)
```

Add compile-visible fixture coverage for `ProductDetail` with `pricing`, `media`, and nullable `fitGuidance`.

- [ ] **Step 2: Run focused tests and observe RED**

Run from `frontend/`:

```powershell
pnpm test -- product-media.test.ts cart-flow.test.ts
```

Expected: helper/type fixtures fail because the new contract and claim-free helper are absent.

- [ ] **Step 3: Define the additive TypeScript contract**

Add exact types:

```ts
export type ProductPricing = { state: 'SINGLE' | 'RANGE'; minimumAmount: number; maximumAmount: number; currency: 'VND' }
export type ProductEvidenceMedia = { url: string; position: number; alt: string }
export type ProductFitRange = { size: string; minimumFootLengthMm: number; maximumFootLengthMm: number; minimumFootWidthMm: number; maximumFootWidthMm: number }
export type ProductFitGuidance = { sizeSystem: 'EU'; fitTendency: 'RUNS_SMALL' | 'TRUE_TO_SIZE' | 'RUNS_LARGE'; widthProfile: 'NARROW' | 'REGULAR' | 'WIDE'; fitAssistantSupported: boolean; ranges: ProductFitRange[] }
```

Extend `ProductDetail` with required `pricing`, `media`, and `fitGuidance: ProductFitGuidance | null`. Keep every legacy property.

- [ ] **Step 4: Remove frontend product-name authority**

Reduce `product-media.ts` to the neutral placeholder and canonical fallback:

```ts
export const PRODUCT_IMAGE_PLACEHOLDER = '/products/placeholder.svg'
export const productAlt = (name: string) => name
```

Update Catalog, Cart and Storefront section rendering to use server/cart image refs or the placeholder, and `productAlt(name)`. Remove unused `locale` and `productMedia` imports. Do not move any old claim into `i18n.ts`.

- [ ] **Step 5: Run focused tests and observe GREEN**

Run the same Vitest command. Expected: helper and existing catalog/cart regressions pass.

- [ ] **Step 6: Record a no-commit checkpoint**

Review only the six Task 2 files with `git diff -- <paths>` and confirm no new asset or product claim was added.

### Task 3: PDP evidence and progressive disclosure

**Files:**
- Modify: `frontend/src/cart-flow.test.ts`
- Modify: `frontend/src/views/ProductView.vue`
- Modify: `frontend/src/i18n.ts`
- Modify: `frontend/src/styles.css`

**Interfaces:**
- Consumes: Task 2 `ProductDetail` evidence types.
- Produces: accessible main-image/gallery selection and conditional Size and fit guide.
- Preserves: explicit size selection, deep links, disabled CTA messaging, optional Fit Assistant, and mobile purchase hierarchy.

- [ ] **Step 1: Add failing four-state PDP rendering tests**

Create fixtures from one common ProductDetail and assert:

```ts
it.each([
  ['media only', { media: twoMedia, fitGuidance: null }, true, false],
  ['fit only', { media: [], fitGuidance }, false, true],
  ['neither', { media: [], fitGuidance: null }, false, false],
  ['both', { media: twoMedia, fitGuidance }, true, true],
])('renders %s without empty evidence sections', async (_label, evidence, hasGallery, hasGuide) => {
  vi.spyOn(api, 'product').mockResolvedValue({ ...fitProduct, ...evidence })
  await mount(productComponent, '/products/fit-product')
  expect(Boolean(find(root, node => String(node.props.class).includes('product-media-picker')))).toBe(hasGallery)
  expect(Boolean(find(root, node => node.tag === 'details' && String(node.props.class).includes('product-fit-evidence')))).toBe(hasGuide)
  expect(text(root)).not.toContain('N/A')
})
```

Add assertions that `pricing.state === 'RANGE'` uses server minimum/maximum, a selected variant still shows its amount, gallery selection changes the main image, gallery controls use generic localized image-position/navigation labels rather than invented image descriptions, and a route change resets to the next Product's first media item even if an older request resolves late.

- [ ] **Step 2: Run PDP tests and observe RED**

Run:

```powershell
pnpm test -- cart-flow.test.ts
```

Expected: new evidence selectors/content are absent and old price computation remains.

- [ ] **Step 3: Implement server-owned price rendering and media selection**

Remove the variant-array min/max computation. Format `product.pricing.minimumAmount` and `maximumAmount`; keep selected-variant amount behavior unchanged.

Track only the selected media URL/index. On each successful `loadProduct`, reset it to the first returned media item. Use the neutral placeholder when `media` is empty. Render thumbnail controls only when `media.length > 1`, with `aria-pressed`, canonical Product-name alt, deterministic order, and generic localized image-position/navigation labels that avoid repetitive invented descriptions.

- [ ] **Step 4: Implement conditional fit disclosure**

Render a `<details class="product-fit-evidence">` only for non-null guidance, below the purchase decision region and before Fit Assistant. Show interface-localized labels for size system, tendency, width profile, millimetre ranges and the advisory disclaimer. Render only `guidance.ranges`; do not calculate or interpolate measurements.

Keep passing the legacy `product.fitSupported` support result to Fit Assistant,
independently of public guidance rendering. The additive
`guidance.fitAssistantSupported` field documents that rule when guidance is
complete but does not redefine the existing assistant support boundary.
Preserve explicit acceptance and no automatic cart action.

- [ ] **Step 5: Add interface translations and bounded styling**

Add only generic labels such as `Size and fit guide`, `Sizing system`, `Fit tendency`, `Width profile`, `Foot length`, `Foot width`, and enum labels. Add styles for the gallery controls and collapsed fit table without moving price, selection or CTA below the evidence section. At approximately 390 px, keep the purchase decision first and make the range table horizontally safe or stacked.

- [ ] **Step 6: Run PDP tests and observe GREEN**

Run `pnpm test -- cart-flow.test.ts product-media.test.ts`. Expected: all new and existing PDP/fit/cart cases pass in VI and EN.

- [ ] **Step 7: Record a no-commit checkpoint**

Review the Task 3 diff and verify there is no material, care, policy, promotion, pickup, delivery or authored merchandising claim.

### Task 4: Full verification and human browser acceptance

**Files:**
- Verify only; fix failures only in files already listed above unless a concrete dependency is demonstrated.

**Interfaces:**
- Consumes: completed backend and frontend slice.
- Produces: fresh automated and real-browser evidence.

- [ ] **Step 1: Run complete relevant backend verification**

With the existing isolated SQL Server acceptance variables, run from `backend/`:

```powershell
.\mvnw.cmd test
.\mvnw.cmd -Pacceptance '-Dit.test=VerticalSlice2CustomerCatalogExternalIT,FitAnalysisExternalIT' clean verify
```

Expected: unit tests and both SQL-backed integration suites pass with no skips caused by missing datasource configuration.

- [ ] **Step 2: Run complete frontend verification**

From `frontend/`:

```powershell
pnpm test
pnpm typecheck
pnpm build
```

Expected: all tests, TypeScript checks and Vite production build pass.

- [ ] **Step 3: Start the existing demo stack without changing data contracts**

Use the already configured disposable SQL Server database and `demo` profile documented in `docs/DEMO.md`; start backend with `backend/.\mvnw.cmd spring-boot:run` and frontend with `pnpm dev`. Do not add manual business-data SQL outside test fixtures.

- [ ] **Step 4: Validate desktop and approximately 390 px mobile in a real browser**

Inspect at least:

- a profiled Product with one deduplicated image;
- a Product without fit guidance;
- a controlled test/demo state with distinct existing primary/hero refs if one already exists, otherwise verify that no gallery is invented;
- VI and EN;
- price before size selection and selected price after explicit choice;
- collapsed/expanded fit guidance, Fit Assistant optionality, and unchanged Add to cart behavior.

Answer with observed evidence: “Does the PDP now provide more trustworthy evidence about price, imagery and fit without claiming anything the system does not actually know?”

- [ ] **Step 5: Final scope and worktree audit**

Run `git status --short` and `git diff --stat`, compare against the captured baseline, and report exactly which files changed for this slice. Confirm no migration, asset, ProductPresentation, policy or promotion file was introduced, and do not commit.
