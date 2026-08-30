# ADR-0019: Customer Catalog Read Model and Versioned Price Quotes

- Status: `ACCEPTED`
- Accepted: 2026-08-26
- Scope: Vertical Slice 2 customer browse, availability, and base-price quote
- Decision class: Pricing history, customer authorization, and public API boundary

## Context

The published catalog already combined Product, ProductVariant, one mutable base-price row, and location inventory for staff workflows. A customer browse flow needs a separate permission and read contract, customer-safe availability, and a price result that remains explainable after staff change the base price. Browsing or quoting must not reserve stock or create an Order.

## Decision

- Add `CATALOG_BROWSE` to the Customer role bundle. Staff permissions do not imply customer storefront access.
- Keep catalog identity and publication in Catalog, balances in Inventory, and price evidence in Pricing. A storefront application read model may join those tables without moving ownership.
- A storefront product is visible only when it has at least one `PUBLISHED` variant with an effective base-price version.
- Availability is `AVAILABLE` when any enabled Location in an enabled Branch has `onHand - reserved > 0`; otherwise it is `UNAVAILABLE`. The customer API exposes no quantity or Location detail.
- Convert `pricing_variant_price` from one mutable row per variant to non-overlapping effective versions. Price changes lock the ProductVariant, close the current version, and append a new one. A filtered unique index permits at most one open version.
- A `PriceQuote` snapshots the effective version, exact integer VND amount, owner, quote time, and expiry. The Slice 2 validity window is 15 minutes. A quote is immutable evidence and is not a stock promise, reservation, Order, Payment, Promotion, or Voucher.
- Unpublished or unknown variants are concealed as `404`; a published but unavailable variant returns `409 VARIANT_UNAVAILABLE`.

## Transaction and concurrency behavior

Price-version transitions serialize on the ProductVariant row and rely on the filtered unique database index as the final invariant. Quote creation reads the effective published version and current aggregate availability in one transaction, then inserts only pricing evidence. It deliberately does not lock or mutate Inventory because later stock changes are allowed and checkout must revalidate/reserve separately.

## Consequences

- Later price changes cannot rewrite prior quote amounts or source-version identity.
- Customer catalog reads stay independent from operational Location scope and do not leak internal quantities.
- Existing reservation/order workflows continue selecting the single open current price until a future checkout slice explicitly adopts quote consumption.
- The 15-minute quote window can become configurable only when a confirmed channel/provider requirement needs different validity.

## Rejected alternatives

- A mutable `variant.price` field or client-calculated price.
- Treating unpaid Orders as availability.
- Granting anonymous catalog access without an approved acquisition/SEO decision.
- Reusing `CATALOG_MANAGE`, `INVENTORY_VIEW`, or `CHECKOUT_RESERVE` for customer browsing.
- Locking stock during quote creation, which would incorrectly turn preview into reservation.
