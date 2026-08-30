# Product

<!-- impeccable:product-schema 1 -->

## Platform

web

## Stack

Vue 3, TypeScript, Vite, and Vue Router in one SPA backed by the Spring REST API.

## Users

Registered shoe customers browsing and checking out, plus location-scoped staff completing pickup, limited cashier operations, and manager reconciliation. Staff workflows remain separate operational surfaces with server-enforced permissions.

## Product Purpose

Let customers discover and reserve currently sellable shoes, and let authorized
staff complete location-scoped fulfillment and exact-cash POS work against the
same commercial authority. Success preserves immutable order evidence and
authoritative inventory cannot oversell across channels. Authorized managers
can trace reported sales and stock back to those same financial and inventory facts.

## Positioning

The storefront makes commercial truth visible: publication, availability, and price come from their owning backend domains rather than frontend inference or stale catalog fields.

## Operating Context

Customers use a responsive browser flow: sign in, browse products, choose an
available variant, request a quote, reserve the pair, and receive an explicit
pending-payment Order confirmation. The same system also supports
location-scoped staff operations outside this customer flow. Cashiers use a
focused responsive workbench for one exact-cash, quantity-one sale at a time.

## Capabilities and Constraints

- Catalog publication, location-aware inventory, and exact integer VND pricing are server-owned.
- A price quote is evidence, not a reservation, order, payment, promotion, or stock guarantee.
- Checkout revalidates that evidence and stock; success creates a time-bounded
  reservation and an unpaid Order, never a Payment.
- Customer availability is intentionally coarse and must not expose internal location quantities.
- Customer catalog access requires current authenticated authority and must not reuse staff operations permissions.
- Limited POS accepts only an active authorized Shift, one server-priced variant, quantity one and exact cash; it never accepts client-authored money.
- POS and Customer Web compete on the same Location InventoryBalance, and a completed POS receipt is immutable historical evidence.
- Reporting is read-only and location-scoped. It reconciles successful online capture and POS cash against successful item void allocations, while unresolved provider outcomes remain visible exceptions.
- Product media is absent from the current domain; the UI must not fabricate it.

## Evidence on Hand

Real product names, SKU-level sizes/colors, publication state, effective price versions, and location inventory balances exist in SQL Server. No approved product photography, marketing claims, reviews, or brand assets exist in the new application.

## Product Principles

- Server truth over client calculation.
- Honest product evidence over invented richness.
- Product, variant, availability, then price: preserve that decision hierarchy.
- Explicit failure and expiry states are part of the product, not edge-case decoration.
- Browse/quote never mutate inventory; checkout reservation does, atomically.

## Accessibility & Inclusion

The storefront requires semantic controls, keyboard operation, visible focus, announced asynchronous errors, non-color availability cues, responsive layouts, and reduced-motion support.
