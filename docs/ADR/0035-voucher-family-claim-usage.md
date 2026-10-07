# ADR-0035: Voucher family, claim and usage are separate concepts

Status: Accepted — 2026-09-13 through approval of remediation D6. This records
current approval, not an inferred approval preceding the C3 certificate.

The C3 implementation extends ADR-0032. `promotion_family` owns stable identity,
immutable acquisition mode (AUTOMATIC, CODE, CLAIMABLE) and normalized CODE.
`promotion` owns immutable published revisions. Voucher Definition maps to this
family/revision pair. Issuance maps to a customer-owned `voucher_claim` for
CLAIMABLE; CODE and AUTOMATIC do not require issuance rows. Redemption maps to
`promotion_redemption`, shared with automatic promotions; no parallel ledger is
required.

| Actor / action | Preconditions | State and side effects | Failure / replay |
|---|---|---|---|
| Guest discovers | Public, published, in valid window | Read only; private codes absent | Unavailable detail returns safe 404 |
| Customer saves | Public CLAIMABLE with valid published revision | One CLAIMED row per customer/family; audit; no usage | Existing claim replays, including REVOKED; no replacement claim |
| Promotion manager revokes | PROMOTION_MANAGE and existing claim | CLAIMED → REVOKED, actor/time/audit | Terminal replay; no customer self-revocation |
| Customer selects voucher | Own active claim or valid CODE; eligibility, limits and minimum spend | One optional selected voucher in authoritative quote; no usage | Explicit error; never silently substitute another voucher |
| Customer checks out | Accepted quote/evidence still valid; stock available | Order snapshots; RESERVED usage for CLAIMABLE and limited CODE | Stale quote fails without partial order/stock/usage; idempotent replay |
| Verified payment succeeds | Accepted provider result | RESERVED → REDEEMED atomically | Replay does not consume again |
| Payment fails | Existing payment attempt | Keep RESERVED hold | Retry within current lifecycle |
| Unpaid cancel / expiry | Unpaid eligible Order | RESERVED → RELEASED; preserve evidence | Existing idempotent cancellation/expiry rules |
| Paid financial void | Eligible capture | Keep REDEEMED; no reissue | Financial reversal rules in ADR-0034 |

Claim state is CLAIMED/REVOKED; it is not USED or EXPIRED. Offer availability and
usage eligibility are separate. CLAIMABLE lifetime usage derives from RESERVED
or REDEEMED rows across the family. Numeric usage limits remain revision-scoped;
unlimited CODE need not create a redemption row. The claimed revision is evidence;
a fresh quote resolves the family's published revision. RETIRED accepted evidence
remains authoritative, INVALIDATED evidence is stale.

CODE normalization is NFKC, trim, Locale.ROOT uppercase and
`[A-Z0-9][A-Z0-9_-]{2,31}`. Customer snapshots expose masking, never raw private
codes. Customer ownership is enforced server-side; authentication as an employee
does not confer customer voucher ownership.

Pricing order is ITEM, ORDER_AUTOMATIC, selected merchandise voucher, then shipping
with at most one shipping benefit. Merchandise voucher bases exclude shipping.
Selected FREE_SHIPPING wins shipping and rejects pickup/zero shipping/ineligibility.
Published prices and discounts are snapshotted; reporting and voids do not rerun
current promotion rules. Zero-total MVP policy is governed by ADR-0034.

Blueprint VoucherIssuance lifecycle names are conceptual future design, superseded
for this implemented C3 scope by the explicit states above. Other deferred campaign,
reissue and multi-voucher features remain outside this decision.

Verification sources: PromotionC3FoundationExternalIT, PromotionC3AdministrationExternalIT,
PromotionC3DiscoveryClaimExternalIT, PromotionC3VoucherEvaluationExternalIT and
VnPayPaymentExternalIT. Historical C3 certification is evidence to recheck, not a
new acceptance verdict.
