# ADR-0021: Independent Checkout Reservation Expiry

- Status: `ACCEPTED`
- Accepted: 2026-08-26
- Scope: Vertical Slice 3 reservation-expiry hardening
- Decision class: Inventory transaction semantics
- Supersedes: ADR-0020 only where it reused `PriceQuote.expiresAt` as the hold deadline

## Context

A quote deadline proves price validity at checkout. Reusing it as the new stock
hold deadline can leave only seconds of reservation time when checkout starts
near quote expiry.

## Decision

- Keep quote validation unchanged: checkout requires server `now <
  PriceQuote.expiresAt` and does not refresh an expired quote.
- Give each successful quote-checkout Reservation a fresh deadline from the
  existing UTC `Clock`: `createdAt + commerce.checkout.reservation-ttl`. The
  server default is 15 minutes and the client cannot submit this value.
- Expose `reservationExpiresAt` in the customer-safe Order response so post-
  checkout UI never presents the quote deadline as the stock-hold deadline.
- Keep lazy expiry. Quote and checkout paths normalize the requested variant.
  Catalog detail limits discovery to the requested product; catalog browse
  selects only published variants with an `ADOPTED`, deadline-passed checkout
  hold. Discovery uses the V12 status/deadline index, then each affected variant
  uses the existing short Order -> Reservation -> Balance transaction.
- Only `PENDING_PAYMENT` quote-checkout Orders with `ADOPTED` reservations are
  eligible. Terminal Reservation/Order state makes repeated evaluation a no-op.

## Consequences

- A quote may expire after successful checkout without cancelling its Order or
  Reservation. The hold remains until its own deadline.
- Expired holds do not indefinitely reduce customer-visible availability, while
  no scheduler, JVM lock, broker, or broad unfiltered reservation cleanup is
  introduced.
- The SQL Server pessimistic Balance lock and existing lock order remain the
  authority for no-overselling and release-versus-checkout races.
- V12 already supplies the deadline columns, lifecycle constraints, and expiry
  index; no migration is required.

## Rejected alternatives

- Reusing quote expiry, accepting a client-authored deadline, or deriving it
  from browser time.
- A scheduler, Redis, Kafka, or a scan of every Reservation on catalog reads.
