# Product Roadmap

Status: authoritative product roadmap for the new Shoe Commerce project.
Last updated: 2026-09-05

## Phase 15 — Customer Commerce Completion

Status: COMPLETE

Phase 15A established customer search, navigation, persisted cart continuity,
owner-scoped My Orders, discoverable tracking, and complete loading/empty/error
states. Phase 15B completed the domain evolution from a single sellable line to
one atomic multi-item commerce transaction.

Delivered and accepted:

- backend-authoritative product search and size/variant selection;
- a persisted cart with merged duplicate variants, quantities 1–10, and at most
  50 distinct lines;
- one immutable, expiring server quote for every cart line and the full total;
- one Order with N immutable OrderItems and N explicit reservations at one common
  enabled location, with deterministic batch locking and no partial checkout;
- one idempotent checkout command and one full-amount payment flow;
- all-line payment commit, expiry, cancellation/restore, pickup handover, void
  allocation, reporting, and historical price snapshots;
- owner-scoped order history/detail with every item visible after refresh;
- VI/EN browser acceptance at 1440, 1024, 768, and 390 on real SQL-backed data,
  including changed-price review, insufficient-stock rollback, account isolation,
  logout/session guarding, and payment continuation;
- clean SQL Server acceptance: 111 integration tests and 9 unit tests, with no
  failures, errors, or skips.

The governing decision is `docs/ADR/0026-multi-item-online-commerce.md` and the
append-only schema evolution is Flyway V18. Phase 15B did not add split-location
fulfillment, delivery, or AI fitting.

## Phase 16 — Fulfillment Expansion

Status: COMPLETE

Delivered and accepted:

- one checkout command snapshots either Pickup location or Delivery receiver,
  phone, address, note, and the Phase 16 zero delivery fee;
- one generalized fulfillment aggregate with explicit Pickup and Delivery
  lifecycles, server-side transitions, immutable snapshots, audit, and
  idempotency;
- location-scoped employee work queue and detail flows for accept, prepare,
  handover, dispatch, and delivery;
- all-line reservation consumption at Pickup handover or Delivery dispatch,
  with exactly one immutable stock movement per order line;
- cancellation and successful payment void before physical issue, with
  cancellation forbidden after handover or dispatch;
- owner-scoped customer tracking and VI/EN responsive employee/customer UI;
- browser acceptance at 1440, 1024, 768, and 390 pixels on real SQL-backed
  multi-item Pickup and Delivery orders, including cancellation, account
  isolation, authorization, and inventory evidence;
- clean acceptance: 114 SQL Server integration tests, 9 backend unit tests,
  and 92 frontend tests, with no failures, errors, or skips.

The governing decision is `docs/ADR/0027-pickup-and-delivery-fulfillment.md` and
the append-only schema evolution is Flyway V19. Phase 16 uses store-managed
delivery; carrier assignment, GPS/ETA, notifications, returns, and AI fitting
remain outside this phase.

## Phase 17 — AI-Assisted Shoe Fitting MVP

Status: COMPLETE

AI remains a commerce-integrated product feature, not a generic chatbot.

Customer flow:

Product detail → Find my size with AI → guided foot photo → calibration/reference
object → analysis → recommended EU size → analysis confidence → explanation → select
recommended size → add to cart.

Technical direction:

Image → foot segmentation / landmarks → perspective/reference calibration → foot
length/width estimate → shoe-fit metadata → size recommendation →
confidence/explanation.

Required shoe-fit metadata:

- Size
- Recommended foot-length range
- Width profile
- Narrow / regular / wide
- Runs-small / true-to-size / runs-large

Deferred feedback loop:

- Too small
- Perfect
- Too large

A single uncalibrated foot photo must not be treated as reliably producing
real-world centimeter measurements.

## Phase 18 — Final Product Integration & Thesis Acceptance

Status: FINAL ACCEPTANCE PASSED / SOL FINAL CERTIFICATION PASSED / GRADUATION PROJECT MVP CERTIFIED

Terra technical/integration acceptance and independent Sol final product
certification have passed for the transaction, customer commerce, fulfillment,
POS, reporting, and AI-fitting slices on a clean, reproducible local
environment. The certified application baseline is
`439d94c5b4f820d1088f82979570aa023f56d07b`; this is graduation-project MVP
certification, not production certification.

## Product capability map

### DONE

- Security / RBAC / session
- Catalog / variant / inventory / pricing
- Authoritative multi-line quote
- Atomic multi-item checkout/payment/cancellation core
- Customer search/cart/orders/tracking experience
- Pickup/Delivery fulfillment product experience
- POS
- Reporting/reconciliation

### NEXT

- Optional tag or push after human approval

### LATER

- Carrier/driver integration, live delivery tracking, and notifications

## Product completion rule

`PRODUCT MVP: COMPLETE` may be declared only when the fulfillment experience
and required product differentiators have passed their relevant acceptance
gates. Those gates have passed through independent Sol final certification.

Current verdicts:

- `TRANSACTION CORE: COMPLETE`
- `CUSTOMER COMMERCE: COMPLETE`
- `FULFILLMENT PRODUCT: COMPLETE`
- `AI FITTING: COMPLETE`
- `PRODUCT MVP: COMPLETE`

Phase 18 — Final Product Integration & Thesis Acceptance: `PASSED`

Graduation Project MVP: `CERTIFIED`

Frozen certified baseline:
`439d94c5b4f820d1088f82979570aa023f56d07b`
