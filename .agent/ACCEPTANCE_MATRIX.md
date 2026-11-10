# Acceptance Matrix

This is an audit view of `.agent/REQUIREMENTS.yaml`, not a replacement for it.
Status values here mirror the registry. `Planned` rows may intentionally have no
implementation/evidence; `Completed` rows require evidence.

| Requirement | Area | Status | Implementation | Verification | Evidence |
|---|---|---|---|---|---|
| REQ-GOV-001 | Governance | Completed | Modular monolith, REST SPA, SQL/Flyway | Architecture review | `docs/ARCHITECTURE.md`; ADR-0001/0002/0009 |
| REQ-GOV-002 | Governance | Completed | Source hierarchy and legacy boundary | Documentation audit | `AGENTS.md`; `docs/LEGACY_REUSE.md` |
| REQ-GOV-003 | Governance | Completed | Project Memory ledger | Reconcile + PM-A..PM-J scenarios | `.agent/*`; global `session-continuity` skill |
| REQ-SEC-001 | Identity and Access | Completed | Separate account/customer/employee models | Baseline acceptance | `docs/DOMAIN_MODEL.md`; `docs/FINAL_FREEZE.md` |
| REQ-SEC-002 | Identity and Access | Completed | Permission and scope enforcement | Security/SQL acceptance | `docs/SECURITY_MODEL.md`; ADR-0030 |
| REQ-SEC-003 | Identity and Access | Completed | Credential/reset/session security | Security regression | `docs/SECURITY_MODEL.md`; `docs/FINAL_FREEZE.md` |
| REQ-CAT-001 | Catalog | Completed | Product/Variant ownership | Domain review + baseline | `docs/BUSINESS_RULES.md`; `docs/FINAL_FREEZE.md` |
| REQ-CAT-002 | Catalog | Completed | Variant option/lifecycle | Unit + SQL evidence | ADR-0041; `ProductVariantTest` |
| REQ-CAT-003 | Catalog | Completed | Immutable post-publication barcode | POS SQL tests | BR-CAT-110; `VerticalSlice6PosExternalIT` |
| REQ-CAT-004 | Catalog | Completed | Controlled Product Evidence/presentation | SQL/frontend/browser acceptance | Product Evidence ledgers; `docs/FINAL_FREEZE.md` |
| REQ-INV-001 | Inventory | Completed | Location balances/reservations | SQL/concurrency acceptance | ADR-0004; `docs/FINAL_FREEZE.md` |
| REQ-INV-002 | Inventory | In progress | Reserve/release/commit/adjust/receive and multi-line Transfer implemented; physical-return movement pending REQ-FUL-003 evidence | AUD-03 focused 7/7; SQL regression 68/68; V36 fresh/upgrade; frontend 329/329, typecheck/build | ADR-0046; V36; `InventoryTransferExternalIT`; `InventoryTransfersView.vue` |
| REQ-INV-003 | Inventory | Completed | Idempotent scoped direct receive with RECEIVE movement/audit and admin UI | Domain/controller + fresh/upgrade SQL + concurrency + frontend regression | `InventoryReceiptService`; V34; `FlywayV34UpgradeExternalIT`; `VerticalSlice2ExternalIT`; manager UI tests |
| REQ-CONC-001 | Cross-domain Concurrency | Completed | Exact SQL Server 1205/1222 rollback-to-503 contract and caller-owned same-key retry | Classifier/handler unit + real deadlock/timeout SQL + audited-flow frontend regression | ADR-0044; `ApiExceptionHandlerTest`; `TransactionContentionExternalIT`; `retryable-command.test.ts` |
| REQ-CHECKOUT-001 | Checkout and Order | Superseded | Historical single-item checkout | Supersession review | ADR-0026 |
| REQ-CHECKOUT-002 | Checkout and Order | Completed | Atomic multi-item checkout | Phase 15B + Phase 18 acceptance | ADR-0026; `docs/PHASE_18_FINAL_ACCEPTANCE.md` |
| REQ-CHECKOUT-003 | Checkout and Order | Completed | Immutable quote/revalidation | Unit + SQL acceptance | Business rules; `docs/FINAL_FREEZE.md` |
| REQ-CHECKOUT-004 | Checkout and Order | Completed | Idempotency/fingerprint | Replay/conflict/concurrency tests | ADR-0010/0026; `docs/FINAL_FREEZE.md` |
| REQ-ORD-001 | Checkout and Order | Completed | Shared Order core | Architecture + integration | ADR-0003; `docs/FINAL_FREEZE.md` |
| REQ-ORD-002 | Checkout and Order | Completed | Immutable historical snapshots | Reporting/history regression | Domain/Promotion docs; `docs/FINAL_FREEZE.md` |
| REQ-PAY-001 | Payment and Refund | Completed | Payment attempts/events/idempotency | Unit + SQL provider-result tests | ADR-0005/0022; `docs/FINAL_FREEZE.md` |
| REQ-PAY-002 | Payment and Refund | Completed | Component void/refund capacity | Financial SQL/reporting tests | ADR-0034; `docs/FINAL_FREEZE.md` |
| REQ-PAY-003 | Payment and Refund | Planned / Future scope | Live VNPAY certification; A-E certification plan prepared | Local contract review and focused 17/17 PASS; external sandbox/IPN not run | `docs/REQ-PAY-003-VNPAY-SANDBOX-CERTIFICATION-PLAN.md`; explicit new admission required |
| REQ-PAY-004 | Payment and Refund | Completed | Authorized, location-scoped, idempotent manual QueryDr reconciliation for existing ambiguous VNPAY attempts; no new attempt or automatic provider retry | Provider/service unit 123/123; clean SQL acceptance 50/50 plus migration proof; frontend 327/327, typecheck/build; independent review | ADR-0045; V35; `VnPayQueryProviderTest`; `VnPayPaymentExternalIT`; `reconciliation.test.ts` |
| REQ-FUL-001 | Fulfillment | Completed | Pickup preparation/handover | Browser + SQL evidence | ADR-0027; Phase 18 acceptance |
| REQ-FUL-002 | Fulfillment | Completed | Delivery lifecycle/dispatch | Browser + SQL evidence | ADR-0027; Phase 18 acceptance |
| REQ-FUL-003 | Fulfillment | Planned / Future scope | Return/exchange workflow | Not admitted | Not a graduation blocker; explicit new admission required |
| REQ-POS-001 | POS | Completed | Limited exact-cash POS | Unit + SQL acceptance | `docs/FINAL_FREEZE.md`; POS ExternalIT |
| REQ-POS-002 | POS | Completed | Barcode and bounded search | POS workflow/SQL tests | BR-CASH-105..107; POS tests |
| REQ-PROMO-001 | Promotion and Voucher | Completed | Central deterministic pipeline | Architecture + SQL acceptance | ADR-0007; Promotion Engine |
| REQ-PROMO-002 | Promotion and Voucher | Completed | Automatic item/order/shipping promotions | C2 certification | ADR-0032; `docs/PHASE_C2_CERTIFICATION.md` |
| REQ-PROMO-003 | Promotion and Voucher | Superseded | Historical tie-break | Supersession review | BR-PROMO-103/110 |
| REQ-PROMO-004 | Promotion and Voucher | Completed | Priority/revision-ID tie-break | C2/C3 SQL acceptance | ADR-0032/0035; C3 certification |
| REQ-PROMO-005 | Promotion and Voucher | Completed | Voucher family/claim/redemption | C3 certification | ADR-0035; `docs/PHASE_C3_CERTIFICATION.md` |
| REQ-RPT-001 | Reporting | Completed | Scoped reconciliation from source facts | Reporting SQL/browser acceptance | Reporting glossary; readiness ledger |
| REQ-AUD-001 | Audit | Completed | Transactional immutable audit | SQL acceptance | ADR-0012; `docs/FINAL_FREEZE.md` |
| REQ-NOT-001 | Notification | Planned / Future scope | Notification delivery subsystem | Not admitted | Not a graduation blocker; explicit new admission required |
| REQ-MERCH-001 | Storefront Merchandising | Completed | Versioned HOME drafts/publication | S1 SQL/frontend/browser certification | ADR-0033; S1 certification |
| REQ-MERCH-002 | Storefront Merchandising | Completed | Deterministic P5A candidates | Unit/frontend and later SQL regression | P5A plan/history; `docs/FINAL_FREEZE.md` |
| REQ-AI-001 | AI | Completed | Proposal-only trust boundary | Architecture/security review | ADR-0008; AI Strategy |
| REQ-FIT-001 | Fit Assistant | Completed | A4-calibrated photo guidance | Analyzer/unit/browser acceptance | ADR-0028; AI fitting evaluation |
| REQ-FIT-002 | Fit Assistant | Completed | Usual-size quick path + optional photo path | Frontend tests | Progressive Fit design; `cart-flow.test.ts` |
| REQ-AI-002 | AI | Completed | Optional manager copy/intent preview | 25 focused backend; 10 provider-contract; frontend/full regression | ADR-0042; `docs/FINAL_FREEZE.md` |
| REQ-AI-003 | AI | Planned / Optional release gate | Live OpenAI provider smoke | Not run; key absent | Not a graduation-core blocker; `docs/FINAL_FREEZE.md` |
| REQ-UX-001 | Frontend UX | Completed | Responsive accessible VI/EN surfaces; post-freeze Operations menu and locale-reactive cart toast | Frontend 330/330; typecheck/build; desktop and 390px browser | Frontend Architecture; readiness/freeze evidence; `AUTONOMOUS-UX-20261013` task |
| REQ-UX-002 | Frontend UX | Cancelled | Dark theme | Decision audit | Intentionally light-only; readiness/freeze evidence |

| REQ-UX-003 | Frontend UX | In progress | UX01 cache revalidation and UX05 stale refresh correction implemented; technical PASS reported for UX01, UX05 366/366 technical PASS | Current UX01 source/mounted regressions present; UX05 isolated Edge behavior PASS. UX01 visual and UX05 final no-clipping/browser/real-stack acceptance PENDING; latest UX05 tool retry ENVIRONMENT_BLOCKED. No completed/release acceptance promotion. | DEC-0013; `frontend/src/components/ProductComparison.vue`; `frontend/src/product-comparison.test.ts`; docs/UX_UPGRADE_VERIFICATION_20261015.md |

### 2026-10-20 incremental milestone evidence (no new registry IDs)

Guest Cart ADR-0029/BR-CHK-109: cross-tab merge and stale login source plus regression cases present; user reports two RED→GREEN technical PASS outcomes. Backend race remains INCONCLUSIVE. SEARCH-01: typo-suggestion source/tests present and functional/performance remediation TECHNICAL_PASS reported within measured scope; benchmark receipt absent here. SEARCH-02: autocomplete source/mounted tests present and frontend TECHNICAL_PASS reported; endpoint/browser ACCEPTANCE_PENDING. Exact recent run receipts for these slices were not located locally; no release acceptance or completed requirement is inferred.

## Audit rules

- Every registry ID must appear exactly once above.
- A `completed` registry entry must have non-empty evidence and a matrix row.
- A matrix ID missing from the registry is an inconsistency.
- Superseded/cancelled rows remain visible and are excluded from active backlog.
