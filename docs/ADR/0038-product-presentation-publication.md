# ADR-0038: Versioned ProductPresentation publication pilot

Status: Accepted for pilot implementation

Date: 2026-09-20

## Context

The storefront can expose authoritative identity, price, availability, media and
fit evidence, but shoppers still struggle to explain why nearby products are
meaningfully different. Catalog data alone collapses many cards into image,
price and size. Three conservative Vietnamese/English summaries are now approved
for a bounded pilot; three comparison products intentionally have no approved
merchandising content.

Catalog remains the authority for Product facts. HOME merchandising remains the
authority for curated page composition. Neither boundary should own localized
editorial copy attached to one Product. The pilot therefore needs a small,
independently publishable ProductPresentation boundary without becoming a PIM,
CMS, media library or generic attribute system.

## Decision

Create a versioned `ProductPresentation` aggregate in the merchandising module.
It owns only an approved localized plain-text summary and its publication
lifecycle. It references the authoritative Catalog Product but does not copy or
reinterpret Product facts.

The alternatives were rejected as follows:

- Adding localized copy directly to Product would mix editorial lifecycle,
  localization and permissions into Catalog.
- Reusing HOME page sections would couple product-owned content to page layout
  publication and locks.
- A generic content/attribute model would exceed the evidence available for this
  pilot and create unsupported flexibility.

The pilot deliberately omits highlights, intended-use fields, taxonomy aliases,
rich text, media management, scheduling and revision browsing.

## Ownership and authorization

- Catalog owns Product identity, category, collection, variants and fit facts.
- ProductPresentation owns only `summaryVi`, `summaryEn` and revision state.
- HOME merchandising remains separate.
- `STOREFRONT_MANAGE` authorizes every management read and command, including
  draft creation, draft editing and publication.
- `CATALOG_MANAGE` does not grant ProductPresentation authority and is not
  required in addition to `STOREFRONT_MANAGE`.
- Authorization is enforced by the application service for every management use
  case. Vue control visibility is not authorization.

No new role or permission is introduced.

## Persistence

Flyway adds one SQL Server table. The actual schema mechanism is:

```sql
CREATE TABLE product_presentation_revision (
    id BIGINT IDENTITY(1,1) NOT NULL
        CONSTRAINT PK_product_presentation_revision PRIMARY KEY,
    public_id UNIQUEIDENTIFIER NOT NULL
        CONSTRAINT UQ_product_presentation_revision_public UNIQUE,
    product_id BIGINT NOT NULL,
    revision_number INT NOT NULL,
    status VARCHAR(16) NOT NULL,
    summary_vi NVARCHAR(500) NULL,
    summary_en NVARCHAR(500) NULL,
    entity_version BIGINT NOT NULL
        CONSTRAINT DF_product_presentation_revision_version DEFAULT 0,
    created_by_account_public_id UNIQUEIDENTIFIER NOT NULL,
    created_at DATETIME2(6) NOT NULL,
    published_at DATETIME2(6) NULL,
    retired_at DATETIME2(6) NULL,
    CONSTRAINT FK_product_presentation_product
        FOREIGN KEY(product_id) REFERENCES catalog_product(id),
    CONSTRAINT FK_product_presentation_creator
        FOREIGN KEY(created_by_account_public_id)
        REFERENCES iam_user_account(public_id),
    CONSTRAINT UQ_product_presentation_revision_number
        UNIQUE(product_id, revision_number),
    CONSTRAINT CK_product_presentation_revision_number
        CHECK(revision_number > 0),
    CONSTRAINT CK_product_presentation_version
        CHECK(entity_version >= 0),
    CONSTRAINT CK_product_presentation_status
        CHECK(status IN ('DRAFT','PUBLISHED','RETIRED')),
    CONSTRAINT CK_product_presentation_summary_vi
        CHECK(summary_vi IS NULL OR LEN(LTRIM(RTRIM(summary_vi))) BETWEEN 1 AND 500),
    CONSTRAINT CK_product_presentation_summary_en
        CHECK(summary_en IS NULL OR LEN(LTRIM(RTRIM(summary_en))) BETWEEN 1 AND 500),
    CONSTRAINT CK_product_presentation_lifecycle CHECK(
        (status='DRAFT'
            AND published_at IS NULL
            AND retired_at IS NULL)
        OR (status='PUBLISHED'
            AND published_at IS NOT NULL
            AND retired_at IS NULL
            AND summary_vi IS NOT NULL
            AND summary_en IS NOT NULL)
        OR (status='RETIRED'
            AND published_at IS NOT NULL
            AND retired_at IS NOT NULL
            AND summary_vi IS NOT NULL
            AND summary_en IS NOT NULL)
    )
);

CREATE UNIQUE INDEX UX_product_presentation_one_draft
    ON product_presentation_revision(product_id)
    WHERE status='DRAFT';

CREATE UNIQUE INDEX UX_product_presentation_one_published
    ON product_presentation_revision(product_id)
    WHERE status='PUBLISHED';
```

The foreign key uses `catalog_product.id`, following the relational convention.
Public APIs continue to address a Product by `catalog_product.public_id`. The
creator foreign key is valid because migration V2 establishes
`UQ_iam_user_account_public_id` on `iam_user_account(public_id)`. There is no
cascade delete. Existing Product lifecycle rules and the Product foreign key
prevent silent orphaning.

The table stores no Catalog facts, derived fit statements, card copy, search
vocabulary or presentation layout.

## Revision allocation and locking

Every ProductPresentation mutation begins a `SERIALIZABLE` transaction and
acquires the authoritative Product key lock with this indexed lookup:

```sql
SELECT id
FROM catalog_product WITH (UPDLOCK,HOLDLOCK)
WHERE public_id = ?;
```

It then reads that Product's revision scope under the same transaction:

```sql
SELECT id, public_id, revision_number, status, entity_version
FROM product_presentation_revision WITH (UPDLOCK,HOLDLOCK)
WHERE product_id = ?
ORDER BY id;
```

Draft creation allocates `COALESCE(MAX(revision_number), 0) + 1` only after
those locks are held, then inserts before committing.

The unique Product public-ID lookup takes a transaction-scoped update/key lock on
one Product row. Therefore Product A mutations serialize with Product A while
Product B remains independent. The unique `(product_id, revision_number)`
constraint and filtered indexes remain the final database invariants;
duplicate-key races are translated to the documented conflict response.

No distributed lock or database trigger framework is introduced.

## Lifecycle

### Draft creation

- At most one draft may exist for a Product.
- If a published revision exists, a new draft copies its exact VI and EN summary
  values before returning to the editor.
- If neither a draft nor a publication exists, the first draft starts with both
  summaries `null`.
- Draft creation never invents or derives content from Catalog facts.
- A copied draft is a new revision with version 0; the published source row is
  not updated.

### Draft editing

- Only a `DRAFT` revision may be updated.
- Every update identifies the draft public ID and expected `entityVersion`.
- The SQL update includes both identity and expected version in its predicate and
  increments `entity_version` atomically. Its predicate is equivalent to
  `WHERE public_id=? AND product_id=? AND status='DRAFT' AND entity_version=?`.
- Zero affected rows is a conflict, never a silent overwrite or switch to a
  newer draft.
- A stale identity/version returns HTTP 409 with
  `PRODUCT_PRESENTATION_DRAFT_CONFLICT`.
- A competing creation that hits the one-draft index returns HTTP 409 with
  `PRODUCT_PRESENTATION_DRAFT_EXISTS`; the client reloads management state.

Drafts may be incomplete so operators can save work in progress. Input is plain
text. On every draft save, the application trims leading and trailing
whitespace, converts blank-after-trim to `null`, preserves internal whitespace,
does not machine-translate, and enforces the 500-character limit on the
canonical value before persistence. Tests assert the stored canonical values,
not only form behavior. There is no rich-text or HTML sanitization path because
rich text is not accepted.

After publication, editorial and creation identity are immutable:
`summary_vi`, `summary_en`, `revision_number`, `product_id`, `public_id`,
`created_by_account_public_id` and `created_at` cannot change. No generic update
method is provided for revision rows. Only the publication service may change
the lifecycle-controlled `status`, `published_at`, `retired_at` and
`entity_version`, including the valid `PUBLISHED -> RETIRED` transition.

### Publication

Publication runs in one `SERIALIZABLE` transaction scoped to the target Product.
The command identifies exactly:

- target Product public ID;
- target draft public ID;
- expected draft `entityVersion`;
- expected current published revision public ID, or `null` when the preview
  observed no publication.

The service re-locks Product and its revision set, verifies those expectations,
and validates that both trimmed summaries are non-null. Missing VI or EN returns
the existing structured 400 validation response with
`PRODUCT_PRESENTATION_LOCALES_REQUIRED`.

The transaction order is fixed:

1. acquire the Product and revision-scope locks described above;
2. verify the exact draft identity, draft version and expected current
   publication;
3. validate canonical VI/EN completeness;
4. locate the current `PUBLISHED` revision;
5. change that revision, when present, to `RETIRED`, set
   `retired_at`, and increment its entity version;
6. change the selected unchanged `DRAFT` to `PUBLISHED`, set `published_at`,
   and increment its entity version;
7. append audit evidence;
8. commit atomically.

A changed draft, changed publication expectation or missing draft returns HTTP
409 with `PRODUCT_PRESENTATION_PUBLISH_CONFLICT`. Published content and revision
identity are never updated in place. SQL-backed integration tests must prove the
application rejects such updates. Transaction rollback ensures a failed
replacement leaves the previous publication current; no intermediate lifecycle
state is externally observable.

Publish has bounded replay semantics. If the exact requested draft is already
the current `PUBLISHED` revision and its version is exactly the requested
pre-publish version plus one, the service returns that existing successful state
without another mutation or audit event. If it has been retired/replaced, if a
different publication is current, or if the versions cannot represent that
single completed transition, the replay returns
`PRODUCT_PRESENTATION_PUBLISH_CONFLICT` and current management state is reloaded.
Publish never creates a revision.

Entity versions follow one rule: create starts at 0; each successful draft save
increments by one; publishing the draft increments it by one; retiring the
previous publication increments that row by one. No other path changes a
revision version.

## Audit

The existing `AuditWriter` records business actions only:

- `PRODUCT_PRESENTATION_DRAFT_CREATED`;
- `PRODUCT_PRESENTATION_DRAFT_UPDATED`;
- `PRODUCT_PRESENTATION_PUBLISHED` when no previous publication existed;
- `PRODUCT_PRESENTATION_REPLACED` when publication also retired a previous
  revision, including the previous revision public ID in audit details.

Every event uses the existing audit envelope to identify the actor, Product and
target presentation revision, with revision number and relevant transition in
details. No `updated_by` or `published_by` columns duplicate that history.

Preview and local keystrokes create no audit records.

## Management API

All identifiers in URLs and JSON are public UUIDs. Management DTOs are separate
from customer DTOs.

### Read state

```http
GET /api/v1/operations/product-presentations/{productId}
```

```json
{
  "productId": "uuid",
  "draft": null,
  "published": {
    "id": "uuid",
    "revisionNumber": 1,
    "status": "PUBLISHED",
    "entityVersion": 1,
    "summaryVi": "...",
    "summaryEn": "..."
  }
}
```

Management state may expose revision IDs, lifecycle state and entity versions
because editing and exact publication require them.

### Create or save draft

```http
PUT /api/v1/operations/product-presentations/{productId}/draft
```

```json
{
  "expectedDraftRevisionId": "uuid or null",
  "expectedEntityVersion": "integer or null",
  "summaryVi": "nullable plain text",
  "summaryEn": "nullable plain text"
}
```

The two modes are explicit:

- When no draft exists, both expectations must be `null` and both summary inputs
  must be `null`. The server creates the next revision under the Product lock,
  copied from the current publication or empty when no publication exists, and
  returns it. The operator then edits/saves that identified draft.
- When a draft exists, both expectations are required and must identify its exact
  ID/version. The supplied summaries replace that draft's summaries after
  canonicalization. Null or blank locale values are allowed.

An update request never falls back to another draft. A competing create/update
returns a recoverable 409 and the UI reloads management state.

### Publish the previewed draft

```http
POST /api/v1/operations/product-presentations/{productId}/publish
```

```json
{
  "expectedDraftRevisionId": "uuid",
  "expectedEntityVersion": 3,
  "expectedPublishedRevisionId": "uuid or null"
}
```

The response is the newly published management state. Existing project error
serialization is reused for 400, 403, 404 and 409 responses.

## Customer contract and query composition

The current published presentation is composed into the existing product browse
and detail responses. The SPA does not issue per-card or PDP content requests.

The additive field is either:

```json
"presentation": null
```

or:

```json
"presentation": {
  "summary": {
    "vi": "...",
    "en": "..."
  }
}
```

The customer projection deliberately omits revision number, management ID,
status, author, entity version and timestamps because the customer UI has no
cache or debugging need for them.

The browse SQL left-joins the filtered current `PUBLISHED` presentation once as
part of the existing catalog query. It does not query per Product. The detail
metadata query uses the same filtered join. The one-published filtered index
makes the join cardinality deterministic. Existing response fields remain
unchanged.

Draft and retired content never enter customer queries. A missing publication is
a successful response with `presentation: null`.

## Demo pilot dataset

Demo bootstrap uses deterministic public IDs derived through the existing
`shoe-commerce-demo:<key>` UUID convention. It inserts one published revision
only when no presentation history exists for the Product. It does not overwrite
operator content on restart and does not create fake historical drafts. It
resolves the internal Product foreign key from the stable Product public ID and
uses the existing `manager.demo` account as creator.

The project does not reset business data on demo startup: repeated bootstrap
runs preserve existing records. Therefore a matching deterministic pilot row is
a no-op, while any existing presentation history also prevents the bootstrap
from treating published editorial content as mutable. Restoring the canonical
pilot dataset uses the documented fresh disposable demo database flow, not an
in-place rewrite.

The exact approved published summaries are:

### Court Classic

VI:

> Court Classic thuộc danh mục Court và bộ sưu tập Court Originals. Hồ sơ kích
> cỡ hiện được ghi nhận là chuẩn kích cỡ, với độ rộng tiêu chuẩn.

EN:

> Court Classic is in the Court category and the Court Originals collection. Its
> current sizing profile is recorded as true to size with a regular width
> profile.

### Metro Runner

VI:

> Metro Runner thuộc danh mục Running và bộ sưu tập Metro Motion. Hồ sơ kích cỡ
> hiện được ghi nhận là chuẩn kích cỡ, với độ rộng được ghi nhận là rộng.

EN:

> Metro Runner is in the Running category and the Metro Motion collection. Its
> current sizing profile is recorded as true to size with a wide width profile.

### Trail Form

VI:

> Trail Form thuộc danh mục Trail và bộ sưu tập Trail Series. Hồ sơ kích cỡ hiện
> được ghi nhận là chuẩn kích cỡ, với độ rộng tiêu chuẩn.

EN:

> Trail Form is in the Trail category and the Trail Series collection. Its
> current sizing profile is recorded as true to size with a regular width
> profile.

No ProductPresentation row is created for Court High, Pace Knit or Trail Edge.
They intentionally project `presentation: null`. Literal category, color, price,
availability and size listings are not copied into merchandising summaries.

## Management UI and preview

The operations UI is limited to:

```text
Product selection
  -> current published/draft state
  -> VI/EN plain-text editor
  -> local preview
  -> save draft
  -> publish exact previewed draft
```

The existing storefront product list supplies the selector for this pilot; no
Product CRUD or presentation list API is added. Direct management reads still
validate the Product ID and permission server-side.

Preview is local and never publishes. A small shared summary-rendering component
is used by preview and storefront consumption so copy selection and empty-state
behavior cannot drift. The component renders nothing for null/blank content.

The screen shows only the current draft and current publication. It does not add
revision history, scheduling, rich text, highlights, media, arbitrary blocks or
drag-and-drop behavior.

## Storefront rendering

### Product cards

Cards retain current Product identity, taxonomy, price and availability. When a
presentation exists, the locale-selected summary is rendered and visually
clamped to two lines. A null presentation produces no summary element, empty
container, placeholder or invented fallback. Browser validation decides whether
mixed card heights are acceptable before any fixed-height treatment is
considered.

### Product detail

The locale-selected summary appears outside and after the purchase Decision
Cluster. It cannot precede or displace price, explicit size selection,
availability or Add to cart. Null presentation produces no heading or empty
region. Existing price, media, fit and Fit Assistant behavior remains unchanged.

## Search and media boundaries

The `chạy -> Running` vocabulary defect remains
`SEARCH VOCABULARY DEFECT — DEFERRED`. No frontend synonym or localized taxonomy
table is introduced. The separate ProductPresentation boundary does not prevent
a future controlled localized taxonomy/search projection, but this pilot adds no
speculative schema for it.

No product media is reassigned, generated or persisted by this change. The six
pilot Products retain their existing approved Product media.

## Failure behavior

- Unknown Product or presentation revision: existing 404 contract.
- Missing `STOREFRONT_MANAGE`: existing 403 contract.
- Invalid lengths or incomplete publication locales: structured 400 response.
- Stale draft, changed publication expectation, duplicate draft or publish
  conflict: structured 409 response with the codes defined above.
- Missing customer publication: successful `presentation: null`.
- A failure during replacement rolls back retirement, publication and audit.

## Verification and acceptance

Backend TDD and SQL Server integration must prove:

- the foreign key and lifecycle/length CHECK constraints;
- filtered indexes enforce one draft and one publication per Product;
- revision numbering is unique under protected creation, including concurrent
  draft-creation attempts for the same Product;
- first draft is empty and a later draft copies the current publication;
- incomplete drafts save, but cannot publish;
- trim and blank-to-null behavior;
- stale updates conflict without overwriting;
- published revisions cannot be edited through the application service;
- exact draft/version/publication expectations control publish;
- an exact post-success publish replay returns the existing publication without
  a second mutation/audit, while a replaced revision conflicts;
- replacement atomically retires the old publication;
- Product A and Product B remain isolated;
- management permissions and audit evidence;
- browse performs one composed projection rather than N+1 reads;
- detail and browse expose only current published content;
- the pilot state is exactly three published and three null presentations;
- demo bootstrap is idempotent.

Frontend TDD must prove:

- management create/edit/preview/publish and 400/409 recovery;
- VI and EN select the matching authoritative summary;
- cards and PDP render presentation when present;
- null cards/PDPs render no placeholder, empty heading or fallback copy;
- existing PDP Decision Cluster order and interactions remain intact.

Verification includes targeted and full backend tests, the SQL Server external
integration suite, frontend tests, typecheck, production build, and real-browser
desktop and approximately 390 px mobile review.

Independent reviewers are not told which Products contain presentations, which
are controls, or what outcome is expected. They compare recall, perceived
differentiation, reason to open and understanding of differences across:

- Court Classic, Metro Runner and Trail Form;
- Court High, Pace Knit and Trail Edge.

This is not treated as a clean presentation-versus-control experiment: the three
Products with presentations also have authoritative fit profiles, while the
three null Products do not. Every recalled distinction is therefore attributed
to `IMAGE`, `PRICE`, `CATEGORY / COLLECTION`, `FIT GUIDANCE`,
`PRODUCT PRESENTATION SUMMARY`, or `OTHER OBSERVED EVIDENCE`. Presentation
success requires reviewers to notice or remember meaningful value contributed
by the summary itself beyond image, price and existing fit evidence.

Review does not trigger creative rewriting. If engineering succeeds but the
approved summaries do not materially improve differentiation, the final status
is `ENGINEERING PASSED — CONTENT DEPTH INSUFFICIENT`, with missing authoritative
facts reported separately. Green tests alone never establish merchandising
success.

## Consequences

The pilot gains an explicit owner, bilingual authoring, immutable publication,
efficient customer composition and an honest mixed-content experiment. It also
adds one table, one management workflow and publication concurrency rules.

Future highlights, controlled intended use, localized taxonomy or richer Product
facts require separate authoritative content and a new approved decision. They
are not latent fields in this schema.
