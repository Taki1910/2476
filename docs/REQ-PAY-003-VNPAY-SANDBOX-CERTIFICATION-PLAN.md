# REQ-PAY-003 — VNPAY Sandbox Certification Plan

- Status: `PREPARED — NOT ADMITTED / NOT EXECUTED`
- Prepared: 2026-10-13
- Applies to: current modular-monolith payment boundary at migration head V36
- External provider calls, public exposure and database promotion authorized: **No**

## 1. Certification objective

Produce external evidence that the existing VNPAY 2.1.0 integration works with
merchant-issued sandbox configuration while preserving the already accepted
Order, PaymentAttempt, reservation, promotion and inventory invariants. This is
a deployment/certification gate, not a payment redesign and not authorization
for refund, physical Return or exchange work.

## 2. Existing contract baseline

- Payment initiation derives amount/currency/reference/deadline server-side and
  creates at most one pending attempt per Payment.
- The payment URL uses VNPAY version `2.1.0`, `pay`, integer VND multiplied by
  100, GMT+7 timestamps and HMAC-SHA512.
- Browser Return verifies signed context and redirects to the fixed SPA result
  route; it never establishes payment state.
- Public IPN is the payment authority. It verifies signature, merchant,
  reference, amount, status and provider transaction facts before entering the
  local transaction.
- Success atomically moves Attempt to `SUCCEEDED`, Order to `PAID` and adopted
  reservations to `COMMITTED`; it does not issue physical stock.
- Late success after cancellation/expiry becomes `REVIEW_REQUIRED` and cannot
  reopen the Order or recreate stock.
- Manual QueryDr is permission/location scoped, actor/key/attempt idempotent,
  performs provider I/O outside the database transaction and applies saved
  verified evidence through the same payment-result boundary.

Primary evidence: ADR-0022, ADR-0034, ADR-0045,
`VnPayPaymentProviderTest`, `VnPayQueryProviderTest`,
`PaymentReconciliationServiceTest` and `VnPayPaymentExternalIT`.

## 3. Official VNPAY 2.1.0 alignment

Authoritative material reviewed on 2026-10-13:

- [PAY integration guide](https://sandbox.vnpayment.vn/apis/docs/thanh-toan-pay/pay.html)
- [QueryDr/refund API guide](https://sandbox.vnpayment.vn/apis/docs/truy-van-hoan-tien/querydr%26refund.html)
- [VNPAY 2.1.0 technical specification](https://sandbox.vnpayment.vn/apis/files/VNPAY%20Payment%20Gateway_Techspec%202.1.0-VN.pdf)
- [Sandbox downloads and SIT material](https://sandbox.vnpayment.vn/apis/downloads/)
- [Official sandbox demo/test-card list](https://sandbox.vnpayment.vn/apis/vnpay-demo/)

Confirmed alignment:

- Sandbox PAY endpoint:
  `https://sandbox.vnpayment.vn/paymentv2/vpcpay.html`.
- Sandbox QueryDr endpoint:
  `https://sandbox.vnpayment.vn/merchant_webapi/api/transaction`.
- PAY v2.1.0 uses HMAC-SHA512 and does not send
  `vnp_SecureHashType` in the initiation request.
- IPN is server-to-server GET, must be HTTPS, validates checksum before other
  processing and returns JSON `RspCode`/`Message`. `00` and `02` end IPN retry.
- Return URL is for checksum validation and customer display only; it must not
  update the transaction.
- QueryDr is JSON POST. Its request and response pipe-delimited checksum orders
  match `VnPayQueryProvider`.
- `vnp_ResponseCode` indicates QueryDr request processing, while
  `vnp_TransactionStatus` is the payment state; the implementation keeps these
  meanings separate.

Compatibility items to resolve before or during the admitted campaign:

1. Official PAY/QueryDr documents constrain `vnp_TransactionNo` to 1–15 digits;
   current parsers accept up to 32. Add a strict representative-payload test and
   decide whether to tighten the bound before live certification.
2. QueryDr documents mark response `vnp_Command` optional; current verification
   requires exact `querydr`. Capture an actual signed sandbox response. If the
   provider omits this optional field, make the smallest verified compatibility
   change before continuing the campaign.
3. The official SIT workbook and merchant-specific onboarding values can differ
   from the public examples. The admitted operator must use the current workbook
   issued for the exact TMN code, not copied historical sample values.

These are certification gaps, not evidence of a current V36 demo regression.

## 4. Security and environment prerequisites

Before any real provider call:

1. Obtain a sandbox `vnp_TmnCode` and `vnp_HashSecret` through VNPAY's merchant
   registration/onboarding channel.
2. Inject secrets only through the existing runtime environment variables
   `VNPAY_TMN_CODE` and `VNPAY_HASH_SECRET`. Never commit them, put them in CLI
   arguments, screenshots, logs, reports or project memory, or paste them into
   chat.
3. Set `VNPAY_PAY_URL` and `VNPAY_API_URL` to the official sandbox HTTPS
   endpoints. Set `VNPAY_SERVER_IP` to the actual outbound server IP expected by
   VNPAY.
4. Provision a reviewed HTTPS origin reachable by VNPAY. Route only the required
   callback path to `/api/v1/payments/vnpay/ipn`; keep operations/admin routes
   private. Configure `VNPAY_RETURN_URL` to the HTTPS Return endpoint and
   `VNPAY_FRONTEND_RESULT_URL` to the controlled SPA result page.
5. Review TLS, reverse-proxy forwarding, rate limits, request-size limits,
   correlation logging and secret redaction. Do not enable a tunnel or public
   listener without separate approval and security review.
6. Use an isolated disposable V36 SQL database with synthetic test identities.
   Never run the campaign against `shoe_commerce`, the protected V35 databases
   or the current demo candidate.
7. Use only the current official sandbox test accounts/cards. No real card,
   customer or production data is permitted.
8. Record outbound connectivity, DNS/TLS and callback reachability without
   logging signed URLs, HashSecret, full card data or OTP values.

Current readiness on 2026-10-13: all six VNPAY runtime variables inspected for
this shell were absent; no approved public HTTPS callback or tunnel exists; no
provider connectivity or mutation call was made.

## 5. Certification matrix

Category key: A = already locally verified; B = additional isolated SQL/runtime
verification; C = actual sandbox call; D = publicly reachable IPN; E = blocked
until merchant configuration exists.

| Cat. | Scenario | Expected behavior and invariants | Required environment | Evidence / pass criteria |
|---|---|---|---|---|
| A | PAY URL canonicalization and tamper detection | Exact server-owned amount ×100, GMT+7 dates, stable sorted HMAC-SHA512; tampering rejected | Unit test only | Known signature fixtures pass; secret never appears |
| A | Return vs IPN authority | Return redirects to SPA without mutation; only verified IPN may resolve Attempt | Local HTTP + disposable SQL | Return leaves Attempt/Order/reservation unchanged; IPN tests pass |
| A | Success, failure, duplicate and replay | Success commits reservation once; failure preserves eligible reservation; exact replay is a no-op | Disposable SQL | One authoritative transition/audit; no duplicate redemption or inventory effect |
| A | Late/out-of-order success | Cancelled/expired Order stays terminal; Attempt becomes `REVIEW_REQUIRED` | Disposable SQL | No Order reopen, re-reserve or stock recreation |
| A | QueryDr signing/result verification | Exact checksum order; merchant/reference/amount/type/known transaction match; invalid evidence changes nothing | Unit test only | Provider/service tests pass with safe errors |
| A | QueryDr idempotency and contention | One provider query per command; same-key replay reuses outcome; saved evidence survives local retry | Unit + disposable SQL | No second query/attempt/business effect; AUD-01 503 contract retained |
| A | Provider unavailable/timeout mapping | Safe 503; no raw HTTP, JDBC or secret detail | Unit test only | `PAYMENT_PROVIDER_UNAVAILABLE`; no secret in body/log assertion |
| B | Release-artifact SQL regression | Re-run payment/reconciliation/expiry/cancellation/concurrency cases on a fresh V36 database | Disposable SQL Server database | All focused tests pass; Flyway 36/36; zero invariant violations |
| B | Exact deployed proxy/client-IP behavior | Initiation records a valid bounded client IP and signed request remains stable behind the reviewed proxy | Isolated deployed runtime | Stored IP and generated field match approved forwarding policy |
| B | Official boundary-length fixtures | Exercise 8-char TMN, 1–15 digit transaction number and optional QueryDr command behavior | Unit/contract fixture | Behavior matches the current merchant SIT specification |
| C/E | Successful NCB sandbox payment | Redirect completes; Return displays pending/known Attempt; verified provider result resolves to paid exactly once | Merchant TMN/secret, official test card, outbound HTTPS | VNPAY transaction reference, redacted request/response evidence, Attempt `SUCCEEDED`, Order `PAID`, reservation `COMMITTED` |
| C/E | Declined/insufficient-funds payment | Definitive failure changes only Attempt; deliberate new-key retry remains possible | Merchant config + official failure card | Attempt `FAILED`; Order/reservation unchanged; no duplicate Attempt for replay |
| C/E | Customer cancel and payment expiry | Non-success result cannot pay Order; expired hold releases according to existing lifecycle | Merchant config + official scenario | Terminal states and inventory/promotion capacity remain coherent |
| C/E | QueryDr paid/failed/unresolved | Signed live response is interpreted separately by response code and transaction status | Merchant config + existing sandbox transaction | Exact target only; no new attempt; unresolved result causes no business mutation |
| C/E | Provider timeout/unavailable | No automatic provider retry; status is queried before any deliberate follow-up | Controlled network fault in sandbox runtime | Safe 503/audit; no duplicate outbound request or charge-capable state |
| D/E | Initial public IPN delivery | HTTPS callback validates and applies the signed result | Approved public HTTPS IPN + merchant config | VNPAY receives JSON `00`; local transition and audit commit atomically |
| D/E | Duplicate IPN retry | Exact repeat returns `02` and creates no duplicate effect | Public HTTPS IPN | Same terminal facts/counts before and after replay |
| D/E | Delayed/out-of-order IPN | Late success becomes review work when local Order/hold is terminal | Public HTTPS IPN + controllable scenario | No reopen/re-reserve; `REVIEW_REQUIRED` and audit evidence present |
| D/E | Invalid signature/reference/amount | Reject with official-compatible response; no local state change | Public HTTPS IPN test fixture agreed with VNPAY | `97`, `01`, `04` or safe `99` as applicable; immutable pre/post facts |

Safe without VNPAY calls: every A scenario and all B scenarios against a newly
created disposable database. Categories C–E require a separately approved live
sandbox campaign.

## 6. Execution order for a separately admitted campaign

1. Approve scope, public callback architecture and isolated database name.
2. Inject merchant secrets out-of-band and verify presence only, never values.
3. Run category B and resolve the two compatibility items above.
4. Prove HTTPS IPN reachability with a non-financial health mechanism agreed in
   the deployment design; do not synthesize a provider success.
5. Execute one official success, one official failure and one cancel/expiry case.
6. Verify duplicate and delayed IPN behavior, then QueryDr against the created
   sandbox references.
7. Reconcile database/audit/inventory evidence, remove public exposure and
   credentials, delete only the explicitly disposable database, and publish a
   redacted certification report.

## 7. Completion rule

REQ-PAY-003 remains `planned` until categories B–E applicable to the admitted
campaign pass with redacted provider and database evidence. Local mocks, unit
tests, a generated payment URL or a browser Return alone cannot complete it.
