# ADR-0047: Banner-linked public voucher codes

Status: Accepted — 2026-11-13 by explicit product-owner confirmation.
Decision class: Confirmed business requirement.

## Context

The storefront already links a published promotion family from a banner and
validates voucher codes through the authoritative cart quote. The prior public
offer detail contract excluded CODE families and exposed only CLAIMABLE offers.
The product owner requested that a customer opening a banner can see and copy
the code attached to that banner.

## Decision

- A CODE family may be attached to a public storefront banner only when its
  promotion is published and the family is marked publicly discoverable.
- The existing public family-detail route may return the normalized code for
  that linked, publicly discoverable CODE family.
- Generic offer listing continues to return CLAIMABLE offers only. Private CODE
  families and all non-public codes remain unavailable through storefront
  discovery and detail.
- The storefront renders the code with a copy action. Cart quote and checkout
  continue to enforce usage limits, time, minimum spend, and all promotion
  eligibility server-side; seeing a code is not a promise of eligibility.
- No code issuance, customer ownership, or redemption is created by viewing or
  copying the code.

## Consequences

Public banners and explicitly linked offer details can disclose their
publicly-discoverable code. Managers must treat such codes as public marketing
content. The API response gains an optional `code` field; consumers should
ignore it when absent. Private code secrecy and customer-owned CLAIMABLE flows
remain unchanged.

## Rejected alternative

Returning every CODE family in the generic offer list would disclose campaigns
outside their intended storefront placements and is not required by the
banner-driven request.
