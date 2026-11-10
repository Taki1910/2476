# ADR-0033: Storefront merchandising publication

Status: Accepted for Phase S1.1

The `merchandising` module owns the curated HOME layout. Catalog remains the
authority for public products, prices, availability and product media;
Promotion remains the authority for public offer state and terms.

HOME uses one mutable `DRAFT` and one immutable active `PUBLISHED` revision.
Saving replaces the bounded draft after checking its revision ID and entity
version. Publishing runs at `SERIALIZABLE`, locks the HOME revision set,
revalidates all Catalog and Promotion references, retires the previous
publication, publishes the draft and writes audit evidence in one transaction.
Catalog reference validation reads only current published product identities and
effective prices. It does not invoke customer catalog browsing or lazy checkout
hold expiry while the serialized HOME publication lock is held.
Stale write or publication expectations return HTTP 409 with
`STOREFRONT_DRAFT_CONFLICT`.

The supported sections are `HERO_BANNER`, `PRODUCT_COLLECTION` and
`PROMOTION_SPOTLIGHT`. Copy is managed in Vietnamese and English. Media uses a
fixed key mapped to an existing `/products/*.png` asset. CTA destinations are
limited to `PRODUCT_DETAIL`, `CATALOG_SEARCH`, `PROMOTION_DETAIL` and `OFFERS`;
URLs and HTML are not accepted.

Customer reads expose only the active published revision through
`GET /api/v1/storefront/homepage`. No publication returns HTTP 200 with null
publication metadata and an empty section list. The read model resolves current
Catalog and Promotion data. It omits a stale product member, an empty product
collection, a stale promotion spotlight, or any section whose typed CTA target
is no longer public. Other valid sections still render. This never restores
unpublished commerce data from a merchandising snapshot.

Management endpoints are:

- `GET /api/v1/operations/storefront/homepage`
- `PUT /api/v1/operations/storefront/homepage/draft`
- `POST /api/v1/operations/storefront/homepage/publish`

All management use cases require the independent `STOREFRONT_MANAGE`
permission. The permission is not part of the OPERATIONS role bundle.
Unpublication is deferred beyond S1.1.
