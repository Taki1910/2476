# Rà soát các phiên làm việc — 13/09/2026

Trạng thái: **REVIEW COMPLETED WITH FINDINGS — chưa đủ căn cứ nghiệm thu lại toàn bộ hiện trạng.**

Đây là báo cáo đối chiếu, không phải quyết định phê duyệt nghiệp vụ mới. Các chứng nhận cũ vẫn là bằng chứng của thời điểm và phạm vi đã ghi; không tự động chứng nhận cây mã hiện tại.

## 1. Phạm vi và cách đọc kết quả

- Kiểm kê 62 tài liệu Markdown trong `docs/` trước khi thêm báo cáo này, gồm 33 ADR, chỉ mục ADR, tài liệu nền tảng, chứng nhận các phase và hướng dẫn demo.
- “Phiên” được phục dựng theo milestone/ADR/chứng nhận trong tài liệu. Đây không phải bản đọc lại toàn bộ hội thoại Codex; không suy diễn những phiên không có hồ sơ.
- Đối chiếu tài liệu với mã backend/frontend, migration V1–V26, các bộ test hiện có và những đường xử lý trọng yếu. Đây không phải chứng minh hình thức mọi dòng mã hoặc tái chạy toàn bộ E2E.
- HEAD: `9bdb87df476eea48d937dd7947bce950be361417`. Nhiều thay đổi A–C3/S1 đã tồn tại dưới dạng modified/untracked trước audit; không reset, stage, commit hoặc push.
- Thứ tự thẩm quyền: yêu cầu hiện tại → ADR được chấp nhận → business rules → domain model → lifecycle → hợp đồng API → test → legacy → suy luận. Chứng nhận PASS không tự thay thế quy tắc nghiệp vụ.
- `Có triển khai`: tìm thấy code và test tương ứng. `Lịch sử PASS`: tài liệu ghi đã nghiệm thu. `Kiểm chứng mới`: thực sự chạy trong lần này. Ba khái niệm không đồng nghĩa.

## 2. Tóm tắt công việc các phiên, trước khi sửa sản phẩm

| Phiên/mốc | Việc phải thực hiện | Hồ sơ điều phối | Đối chiếu hiện trạng |
|---|---|---|---|
| Blueprint v1 | Tách hệ mới khỏi legacy; xác định domain và các khoảng trống kiến trúc | `ARCHITECTURE_REVIEW_V1.md`, `LEGACY_REUSE.md` | Review lịch sử phát hiện 6 blocker; không nên đọc chúng như 6 lỗi còn nguyên hôm nay. Backend mới và Vue tách khỏi `QLCHGiay/`. |
| Blueprint v1.1 | Chốt checkout, tồn kho, tài chính, scope, lifecycle; sửa governance ADR | `BLUEPRINT_V1_1_CHANGELOG.md`, `ARCHITECTURE_REVIEW_V1_1.md` | Có bản sửa và vòng review tiếp theo; kết luận lịch sử là APPROVE WITH CHANGES, không phải tự do triển khai toàn bộ blueprint. |
| Blueprint v1.1.1 và MVP baseline | Khóa thứ tự lock, capacity void/refund, công thức VND, reporting; thu hẹp phạm vi | `BLUEPRINT_V1_1_1_CHANGELOG.md`, `MVP_IMPLEMENTATION_BASELINE.md`, ADR 0001–0012 | Có baseline được ghi nhận phê duyệt. Một số nội dung tổng quan cũ chưa cập nhật theo các slice mới; xem F6. |
| Foundation 0 | Java/Spring/Maven, SQL Server/Flyway, UTC, UUID, lỗi API, audit | Baseline; ADR 0001/0002/0009/0012 | Có `pom.xml`, cấu hình, platform, audit, migration và SQL harness; backend biên dịch/unit PASS mới. SQL toàn bộ chưa chạy lại. |
| Foundation 1 | Account, session/CSRF, BCrypt, authVersion, quyền và scope | ADR 0006/0011; `SECURITY_MODEL.md` | Có filter, policy, IAM và test; không thấy throttle đăng nhập, F5. Không coi password reset tùy chọn là thiếu MVP. |
| Catalog + inventory | Product/Variant/SKU, publish có giá/tồn, nhập/điều chỉnh và reservation rõ ràng | Baseline Stage 2; ADR 0004/0013 | Có CatalogService, InventoryAdjustmentService, InventoryReservationService và integration tests. Stock không đặt lên Product. |
| Pricing + quote | Giá VND từ server, version, quote có chủ sở hữu và hạn dùng | Baseline Stage 3; ADR 0019 | Có PriceQuote/CartQuote, price history; quote không phải reservation. Các quote mới có thể thực hiện lazy expiry theo ADR 0021. |
| Order và checkout ban đầu | Adopt reservation; quote/order snapshot; idempotency; giữ hàng nguyên tử | ADR 0014/0020/0021 | Có CustomerOrderService và test checkout. ADR 0020 đã được thay thế về deadline; không áp dụng lại deadline cũ. |
| Payment | Attempt, callback đáng tin cậy, replay, lỗi/đến muộn | ADR 0015/0016 → 0022 | Có PaymentAttemptService, VerifiedPaymentResultService, VNPAY adapters. ADR 0015/0016 đã SUPERSEDED cho online payment. Sandbox merchant thực chưa được chứng nhận. |
| Pickup và cancellation/void | Picking → prepared → handover; chặn hủy sau xuất hàng; hoàn trước xuất hàng | ADR 0017/0018/0023 | Có các service/lock/test tương ứng. Mở lại phân bổ void sau khi mở rộng tiền, F2. |
| POS giới hạn | Một đôi, giá server, tiền mặt đúng số tiền, shift, giao ngay, cạnh tranh tồn với online | ADR 0024 | Có PosService và 10 ca trong suite POS. Không yêu cầu split tender, tiền thừa, nhiều dòng hoặc offline. |
| Reporting MVP | Capture + cash − successful void, scope/ngày VN, ngoại lệ đối soát | ADR 0025; glossary | Có projection SQL và 5 ca reporting. Công thức theo sản phẩm chưa theo kịp shipping/discount, F1. |
| Phase 15A/15B | Search, giỏ lưu lại, My Orders; N dòng trong một quote/order/payment, một location | Roadmap; ADR 0026 | Có triển khai frontend/backend và regression. Cart hiện là local browser theo chủ sở hữu, không phải cart đồng bộ nhiều thiết bị. |
| Phase 16 | Pickup/Delivery, địa chỉ snapshot, queue nhân viên, dispatch/delivered, hủy trước xuất | ADR 0027 | Có fulfillment type/lifecycle/queue và test. Phí 0 là phạm vi lịch sử Phase 16; C1 đã mở rộng. |
| Phase 17 | Ảnh bàn chân trên A4 → đo → range theo sản phẩm → đề xuất size có giới hạn | ADR 0028; `AI_FITTING_EVALUATION.md` | Có CV Java deterministic và metadata V20; 9 test fitting PASS mới. Không có mô hình ML được huấn luyện; thiếu đánh giá camera thực đã được công khai. |
| Phase 18 | Nghiệm thu luồng Pickup, Delivery, hủy/void, POS, reporting, tích hợp fitting | `PHASE_18_FINAL_ACCEPTANCE.md`, roadmap | PASS lịch sử gắn `439d94c…`; không áp dụng nguyên trạng cho thay đổi C1–C3 sau đó. |
| Phase A | Cô lập cart theo account, merge guest, UX giỏ/fitting | ADR 0029; chứng nhận A | Có cart/session guards và test chạy mới. Không chạy lại SQL tại A là giới hạn đã khai báo, không phải chứng cứ giả. |
| Phase B | Staff access theo location, quyền trực tiếp, không tự nâng quyền; fulfillment recovery | ADR 0030; chứng nhận B | Có StaffAccessService/Controller, PeopleAccessView, V21; khác mô hình EmployeeProfile đầy đủ là quyết định giới hạn đã ghi trong ADR. |
| Phase C1 | Quote phí ship có revision, origin cố định, province/district code, admin scope | ADR 0031; chứng nhận C1 | Có ShippingService, V22, ShippingRulesView; tác động reporting chưa đầy đủ, F1/F2. GSO-2024 là snapshot demo được chọn, không tuyên bố dữ liệu hành chính hiện hành. |
| Phase C2 | Automatic ITEM/ORDER/SHIPPING, rounding/allocation, usage, stale quote, quyền admin | ADR 0032; chứng nhận C2 | Có PromotionService, V23, tích hợp quote/payment/expiry. Báo cáo/phân bổ reversal và zero-payable cần xử lý, F1/F2/F4. |
| Phase C3 | CODE/CLAIMABLE, một voucher được chọn, claim/usage, privacy, stale/idempotency | Chứng nhận C3; V24/V25 | Có VoucherService, family/claim/redemption, các màn offers/vouchers, test. Thiếu đồng bộ ADR/domain/lifecycle cho quyết định mới, F3; khẳng định reporting PASS rộng hơn bằng chứng, F1. |
| S1.1 | HOME draft/publication, permission, concurrency và V26 | ADR 0033; chứng nhận S1 | Có service/controller/migration và test fresh/upgrade. Publication SERIALIZABLE và audit; khách chỉ đọc published. |
| S1.2 | Editor, validate, preview, conflict, publish rõ ràng | Chứng nhận S1; frontend architecture | Có StorefrontManagementView và test; không tự sinh HTML hoặc endpoint tùy ý. |
| S1.3 | Shared renderer, HOME cho khách, CTA, VI/EN, stale references | ADR 0033; chứng nhận S1 | Có StorefrontHomepage/StorefrontSectionPreview; test frontend PASS mới. |
| S1.4 | Bốn asset, fixture chuẩn, publish qua UI, demo, cleanup | Chứng nhận S1; `DEMO.md`; setup script | Có assets/script/test; native zoom 200% vẫn chưa có bằng chứng, là ngoại lệ được chứng nhận ghi nhận. Không gọi là đã kiểm thử 200%. |

Không tìm thấy hồ sơ riêng đủ để gán chính xác mọi số “Phase 1–14” cho từng phiên. Bảng dùng tên Foundation/Stage/slice/ADR thực có, tránh bịa lịch sử. “Optional A/B/C” trong baseline cũng không phải “Phase A/B/C1/C2/C3” đã triển khai sau Phase 18.

## 3. Phát hiện cần xử lý

### F1 — P1: Product Sales sai khi đơn có giảm giá hoặc phí vận chuyển

**Đã tái hiện bằng truy vấn sản phẩm hiện tại trên SQL Server**, dùng bảng tạm và dữ liệu tổng hợp, không sửa database ứng dụng.

`ReportingService.java:64` lấy `PaymentAttempt.amount` cho báo cáo tổng. `:129` lấy `unit_price_amount * quantity` cho doanh thu sản phẩm; `:158` trừ toàn bộ ORDER_ITEM void, không trừ discount snapshot hay tách shipping. Đây là hai cơ sở tính khác nhau sau C1/C2/C3.

| Đơn đã thanh toán rồi void toàn bộ | Net tổng thực chạy | Net sản phẩm thực chạy | Mong đợi phần hàng |
|---|---:|---:|---:|
| Hàng 200.000, giảm 50.000, trả/hoàn 150.000 | 0 | **50.000** | 0 |
| Hàng 100.000, ship 30.000, trả/hoàn 130.000 | 0 | **−30.000** | 0; ship được trình bày riêng |

Không có bằng chứng ở đây rằng khách bị hoàn sai **tổng** tiền. Lỗi xác nhận là số liệu theo sản phẩm và phân loại tiền. Tuy vậy đây là chức năng reporting đã được tuyên bố hoàn thành.

`PHASE_C3_CERTIFICATION.md:81` nói reporting đối soát discount và historical allocations. Suite `VerticalSlice7ReportingExternalIT` hiện có 5 test, chủ yếu base-price/multi-item/void/exceptions/scope; không có kịch bản discount/shipping này. PASS suite cũ chưa chứng minh yêu cầu mới.

**PROPOSED:** mở lại acceptance reporting C1–C3; tách gross merchandise, các loại discount, merchandise net, shipping net và reversal đúng component. Chỉnh API/UI/glossary cùng một lần theo hợp đồng được duyệt; không chỉ đổi nhãn hoặc ép hai tổng bằng nhau. Khi shipping khác 0, tổng net sản phẩm không nhất thiết bằng net toàn đơn; phương trình đúng phải cộng phần shipping net sau reversal.

### F2 — P1: Void tái chia tổng tiền theo giá gốc, mất paid allocation từng dòng

**Đã tái hiện bằng phương thức production đã biên dịch** `VoidService.componentAmounts` (`VoidService.java:197`).

- A và B đều giá gốc 100.000; ITEM discount 50.000 chỉ cho A. Paid snapshot phải tương ứng A=50.000, B=100.000.
- Hàm hiện trả A=75.000, B=75.000 do phân bổ tổng 150.000 theo tỷ trọng giá gốc.
- Đơn một dòng giá 100.000 + ship 30.000 được gán một component ORDER_ITEM=130.000.
- `CustomerOrder.PaymentFacts` không mang item discount allocation hoặc shipping discount; `VoidResultService` kiểm tra lại bằng chính cách phân bổ đó. Tổng đúng không chứng minh component đúng.

Điều này không khớp paid-component intent trong `REPORTING_GLOSSARY.md` và `PROMOTION_ENGINE.md`, và trực tiếp góp phần vào F1. ADR 0025 từng cho phép chỉ có ORDER_ITEM khi chưa có shipping/voucher; không đủ để chứng nhận mở rộng sau này.

**PROPOSED:** dùng immutable order item adjustments để xác định số tiền thực trả từng dòng và `shippingNet` riêng; không tính lại từ promotion hiện tại, không phân bổ lại toàn bộ capture theo gross. Bổ sung regression gồm giảm riêng một dòng, free-shipping, dòng miễn phí nhưng order còn phải trả, làm tròn và full void. Đánh giá migration/backfill lịch sử trước khi thay đổi component đã ghi; không viết lại financial history trong audit.

### F3 — P2: C3 có triển khai và chứng nhận nhưng thiếu đặc tả chuẩn được cập nhật

Đối chiếu toàn bộ chỉ mục ADR: ADR 0032 chỉ chấp nhận automatic C2 và loại voucher ra ngoài C2; ADR 0033 nói về HOME. Không tìm thấy ADR C3 riêng hoặc phần mở rộng C3 trong ADR 0032.

`DOMAIN_MODEL.md` và `LIFECYCLES.md` vẫn mô tả VoucherIssuance `ISSUED → RESERVED → REDEEMED`, trong khi `VoucherClaim.java:19` dùng `CLAIMED/REVOKED` và việc sử dụng nằm ở `promotion_redemption`. `PROMOTION_ENGINE.md` còn để định dạng public code là OPEN DECISION; chứng nhận C3 ghi NFKC/uppercase, family identity, claim và giới hạn cụ thể.

Đây không phải bằng chứng C3 chưa từng được người dùng duyệt trong hội thoại. Đây là **thiếu bằng chứng thẩm quyền nhất quán trong bộ hồ sơ đang được rà soát**. Không được dùng test hoặc certificate để âm thầm ghi đè ADR/rule.

**PROPOSED:** một ADR C3 ngắn, dẫn quyết định đã phê duyệt nếu truy xuất được; map Definition→Family/Revision, Issuance→Claim khi CLAIMABLE, Redemption→usage; ghi rõ CODE unlimited, thất bại payment giữ RESERVED, hủy unpaid mới RELEASED, void không reissue. Đồng bộ domain/lifecycle/rules/API mà không tạo các bảng trùng chỉ để giống tên blueprint.

### F4 — P2 / OPEN DECISION: Quy tắc đơn 0 đồng khác implementation

`BUSINESS_RULES.md:49` (BR-MON-107) yêu cầu zero-payable confirmation không tạo external PaymentAttempt. `PromotionService.java:163` từ chối `total <= 0`; V23 cũng ràng buộc `total_amount > 0`. Vì C2 cho phép phần trăm đến 100 và cap theo base, khả năng này không còn thuần lý thuyết.

**Cần quyết định trước khi sửa:** cho phép đơn 0 đồng với use case confirmation/redemption/reservation đầy đủ; hoặc xác nhận loại khỏi phạm vi hiện tại và sửa rule/admin validation/thông báo tương ứng. Không chỉ bỏ CHECK hoặc đổi điều kiện Java vì có ảnh hưởng payment và fulfillment. Audit chưa tự chọn chính sách.

### F5 — P2: Chưa thấy giới hạn tần suất đăng nhập theo yêu cầu bảo mật

AGENTS mục 10 có rate limiting. Đường đăng nhập `SecurityConfiguration.java:55` dùng formLogin; failure handler trả 401, không có throttle/account-attempt policy. Tìm trong backend main không thấy rate limiter/login-attempt storage/filter; Semaphore ở fitting chỉ giới hạn xử lý ảnh đồng thời.

Có BCrypt, session rotation, CSRF, authVersion và scope; chúng không thay thế chống thử mật khẩu hàng loạt. Chưa có bằng chứng reverse proxy bên ngoài đảm nhiệm phần này; không kết luận hệ thống đã bị khai thác.

**PROPOSED:** xác định nguồn enforcement và ngưỡng/window/backoff theo account và nguồn yêu cầu, lỗi an toàn, test vượt ngưỡng/hết thời gian/không khóa oan tài khoản. Giữ giải pháp phù hợp một instance; chưa có lý do thêm Redis. Đây là thiếu triển khai/chứng cứ của yêu cầu đã nêu, không phải yêu cầu thêm MFA hoặc reset password ngoài scope.

### F6 — P2: Tài liệu hiện hành lẫn baseline lịch sử và quyết định đã bị thay thế

Ví dụ có thể kiểm tra:

- Roadmap dừng ở 05/09 và baseline `439d94c…`, chưa có A/B/C1/C2/C3/S1; code hiện tại ở HEAD `9bdb87d…` cộng worktree.
- `BUSINESS_RULES.md:262` vẫn CONFIRMED quantity=1/no customer Location; ADR 0026/0027 cho multi-line/selected Pickup.
- `BUSINESS_RULES.md:273` và ADR C1 có tổng trước khuyến mãi; V23/PromotionService đã dùng công thức sau discount.
- BR-PROMO-103 xuất hiện hai lần (`:161`, `:278`) với diễn đạt tie-break khác nhau.
- `PROJECT_OVERVIEW.md` và changelog v1.1.1 vẫn đặt CashierShift sau InventoryBalance; ADR 0024 và rules mới đặt Shift đầu cho POS.
- `PRODUCT.md` còn nói catalog phải authenticated và browse/quote không thay đổi tồn; SecurityConfiguration cho public catalog, ADR 0021 cho lazy expiry giải phóng reserved.
- `DEMO.md` mở đầu V1–V20 dù migrations hiện tại V1–V26. Phần bổ sung phía dưới không sửa câu đầu.

Không nên sửa lại lịch sử chứng nhận cho trông đồng nhất. **PROPOSED:** thêm chỉ mục CURRENT với bảng supersedes; đánh dấu đúng các đoạn slice lịch sử, nối tới ADR mới; cập nhật đoạn hướng dẫn vận hành hiện tại. Với xung đột nghiệp vụ F3/F4, giữ OPEN DECISION cho tới khi xác nhận, không tự nâng thành CONFIRMED.

### F7 — P2: “Frozen” chưa có định danh đầy đủ cho cây mã được chứng nhận

C3/S1 ghi HEAD cùng một mã nhưng công việc ở worktree modified/untracked. Điều này được tài liệu thừa nhận; không phải lỗi vì không commit khi chưa được yêu cầu. Tuy nhiên chỉ checkout HEAD không tái tạo C3/S1. Kết quả frontend mới là 160 tests, khác S1 ghi 147, càng cho thấy không thể coi cùng HEAD là cùng bộ bằng chứng.

**PROPOSED:** mỗi lần nghiệm thu lưu commit/tree hoặc manifest hash kèm phạm vi worktree, lệnh, runtime, migration count và test report của đúng lần đó. Commit/tag chỉ là bước tích hợp riêng khi được yêu cầu. Không dùng lại XML cũ còn trong target để cộng số test.

### F8 — P2: Hồ sơ API chưa có chỉ mục hợp đồng như thiết kế yêu cầu

`FRONTEND_ARCHITECTURE.md` và blueprint changelog quy định `docs/API/`, nhưng thư mục này không tồn tại. Một số ADR có endpoint/contract; controller và `frontend/src/api.ts` cũng chứa DTO. Vì vậy không kết luận mọi API đều thiếu thiết kế, nhưng chưa có một bản chỉ mục kiểm tra request/response, quyền, lỗi, idempotency, version và consumer xuyên suốt.

**PROPOSED:** lập contract index từ endpoint hiện có, dẫn đến ADR/DTO/test; ưu tiên quote/checkout/voucher/shipping/reporting. Chỉ bổ sung schema chính thức nơi thiếu; không tạo bộ sinh client hoặc dependency chỉ để có tài liệu.

## 4. Phần khác thiết kế ban đầu nhưng có căn cứ giữ lại

| Thay đổi | Căn cứ | Kết luận |
|---|---|---|
| N dòng thay một dòng checkout | ADR 0026 | Giữ; không “sửa” về quantity=1 vì đọc baseline cũ. |
| Delivery bên cạnh Pickup; phí ship có revision | ADR 0027/0031 | Giữ; hoàn thiện ảnh hưởng kế toán/reporting. |
| Payment commit giữ onHand/reserved, chỉ xuất vật lý khi handover/dispatch | ADR 0022/0027 | Giữ; không khôi phục giảm onHand ngay lúc trả tiền. |
| VNPAY boundary thay provider-result mô phỏng ban đầu | ADR 0022 | Giữ; demo adapter và merchant certification phải phân biệt. |
| Browser cart theo account, chưa có server Cart | ADR 0029 | Giữ đúng phạm vi; multi-device là deferred. |
| Staff capability + exact Location, chưa xây EmployeeProfile đầy đủ | ADR 0030 | Giữ slice đã chấp nhận, không thêm HR subsystem. |
| CV Java A4 thay giả định dịch vụ AI/ML bên ngoài | ADR 0028 | Giữ; báo cáo đồ án phải gọi đúng classical CV, confidence heuristic, synthetic evaluation. |
| HOME typed sections/shared renderer thay arbitrary HTML | ADR 0033 | Giữ; S1 là quản trị thủ công, không phải AI tự xuất bản. |
| Family/claim cho voucher | Có implementation/certificate, thiếu đồng bộ ADR | Đề xuất hợp thức hóa hồ sơ F3; không tự tuyên bố đã thay thế toàn bộ voucher blueprint. |

Không mở rộng sang returns sau giao hàng, partial refund, split POS, carrier/GPS, notifications, password reset, AI tạo campaign, microservices hoặc hạ tầng mới. Các mục này là deferred/optional; thiếu chúng không tự khiến MVP lịch sử thất bại.

## 5. Kiểm chứng mới và giới hạn

| Kiểm tra ngày 13/09/2026 | Kết quả |
|---|---|
| `frontend: pnpm test` | **16 files, 160 tests PASS** |
| `frontend: pnpm build` | **PASS**, gồm `vue-tsc -b` và Vite; 84 modules |
| `backend: .\mvnw.cmd -o test` | **20 unit tests, 0 failure/error/skip, BUILD SUCCESS**; biên dịch 128 source và 36 test source |
| VoidAllocationProbe gọi production compiled method | **Tái hiện F2**, A/B=75.000/75.000; shipping gộp ORDER_ITEM |
| SQL probe chạy SELECT thực lấy từ ReportingService | **Tái hiện F1**, product net +50.000 và −30.000 sau full void |
| SQL Server connectivity | Kết nối Windows integrated qua sqlcmd thành công, chỉ kiểm tra metadata và bảng tạm |
| Full `-Pacceptance clean verify` | **CHƯA CHẠY LẠI**: chưa thiết lập database acceptance riêng và matching JDBC native-auth library; không lấy unit/probe thay cho integration suite |
| Browser E2E, responsive/keyboard, native zoom 200% | **CHƯA CHẠY MỚI**; S1 vẫn ghi zoom là user-manual outstanding |
| Real VNPAY merchant sandbox/payment/refund | **CHƯA CHẠY**, nằm ngoài demo certification |

Các lần chạy đầu trong sandbox bị lỗi quyền đọc của esbuild/Maven; chạy cùng lệnh ngoài sandbox thành công. Đây là giới hạn môi trường, không được ghi thành lỗi sản phẩm. Không thay quyền SQL, tạo login, gọi provider, sửa dữ liệu doanh nghiệp hoặc dựng database acceptance trong lần này.

Probe độc lập nằm ở `artifacts/session-audit-20260913/`. Chúng chứng minh phản ví dụ có giới hạn: Java gọi thuật toán thật, SQL chạy truy vấn thật với fixture tối thiểu. Chúng không chạy toàn bộ HTTP/checkout/payment/migration, cũng không thay thế regression integration cần viết khi sửa.

```powershell
# Từ root repository, sau khi đã chạy backend unit tests:
[xml]$auditReport = Get-Content 'backend/target/surefire-reports/TEST-com.shoecommerce.payment.VnPayVoidProviderTest.xml'
$auditClasspath = ($auditReport.testsuite.properties.property | Where-Object name -eq 'java.class.path').value
java --class-path $auditClasspath 'artifacts/session-audit-20260913/VoidAllocationProbe.java'
./artifacts/session-audit-20260913/reporting-probe.ps1 -Server 'localhost\AN'
```

Probe Java kiểm tra rằng phản ví dụ vẫn tái hiện; exit 0 của probe **không** nghĩa thuật toán sản phẩm đúng. Khi sửa xong cần thay bằng regression kỳ vọng paid components chính xác.

## 6. Đề xuất các phiên tiếp theo, đã điều chỉnh thứ tự theo phát hiện

| Thứ tự | Công việc | Phụ thuộc/quyết định | Điều kiện hoàn tất |
|---|---|---|---|
| R1 — chuẩn hóa quyết định | Contract tiền/reporting, ADR C3, zero-payable, current index | Chốt F3/F4 và nghĩa component tài chính trước khi sửa | Một nguồn quy tắc rõ ràng; proposal không bị ghi thành approved; map đầy đủ tới code/test |
| R2 — sửa tài chính/reporting | Sửa F1/F2 theo snapshot, review lịch sử/migration và consumer | Sau quyết định component ở R1; nếu thay contract/migration cần ADR thích hợp | Hai phản ví dụ về 0 đúng theo merchandise/shipping; item allocations đúng; không đổi total/capture hoặc viết lại history tùy tiện |
| R3 — bảo vệ đăng nhập | Enforcement, thresholds, recovery, abuse tests | Chốt policy nhỏ phù hợp single-instance | Chặn burst đúng policy, không lộ account, không khóa oan; CSRF/session/RBAC regression PASS |
| R4 — nghiệm thu hiện trạng | Full SQL V1→V26 và upgrade; race/replay/rollback; frontend; E2E | Sau R2/R3, database test/demo riêng và runtime đúng | Báo cáo mới không skip bắt buộc; case discount/shipping/voucher/void xuyên suốt; chứng cứ native zoom hoặc ngoại lệ còn mở rõ ràng |
| R5 — đóng hồ sơ | Update roadmap/current pointers, contract index, manifest/logs | Sau acceptance thực sự đạt | Reproducible snapshot; verdict chỉ cho phạm vi đã chứng minh; commit/tag là bước riêng |

R1 là tài liệu quyết định; R2/R3 mới là thay đổi hành vi. Không gộp mọi thứ thành refactor lớn. Không sửa chứng nhận cũ để xóa dấu vết; báo cáo mới dẫn các phần cần mở lại.

## 7. Bản đồ toàn bộ nhóm tài liệu

| Nhóm | Tài liệu trong phạm vi | Cách sử dụng/kết luận |
|---|---|---|
| Governance/blueprint/history | PROJECT_OVERVIEW; ARCHITECTURE; ARCHITECTURE_REVIEW_V1; ARCHITECTURE_REVIEW_V1_1; BLUEPRINT_V1_1_CHANGELOG; BLUEPRINT_V1_1_1_CHANGELOG; MVP_IMPLEMENTATION_BASELINE | Dùng để phục dựng yêu cầu, approval và scope; lịch sử không tự là trạng thái hiện hành. |
| Domain/behavior | BUSINESS_RULES; DOMAIN_MODEL; LIFECYCLES; ACTORS_AND_USE_CASES | Tìm invariant, actor, transition và phạm vi; các điểm không đồng bộ ghi F3/F4/F6. |
| Chuyên đề | SECURITY_MODEL; PROMOTION_ENGINE; REPORTING_GLOSSARY; FRONTEND_ARCHITECTURE; AI_STRATEGY; AI_FITTING_EVALUATION; LEGACY_REUSE | Đối chiếu module tương ứng, tách capability hiện tại với blueprint/deferred. |
| Tiến độ/nghiệm thu | PRODUCT_ROADMAP; PHASE_18_FINAL_ACCEPTANCE; PHASE_A_CERTIFICATION; PHASE_B_CERTIFICATION; PHASE_C1_CERTIFICATION; PHASE_C2_CERTIFICATION; PHASE_C3_CERTIFICATION; PHASE_S1_CERTIFICATION | Bằng chứng lịch sử; không kế thừa PASS cho phạm vi mới hoặc cây mã chưa định danh. |
| Demo | DEMO; demo-assets/fit/README | Runbook, fixture, giới hạn camera/provider; số migration đầu runbook cần cập nhật. |
| ADR nền tảng | ADR/README; ADR 0001–0012 | Quyết định hệ thống và governance; tránh triển khai hết domain tương lai chỉ vì kiến trúc hỗ trợ. |
| ADR các slice đầu | ADR 0013–0021 | Reservation/order/payment/pickup/quote/expiry; 0015/0016/0020 có supersession cần tôn trọng. |
| ADR sản phẩm | ADR 0022–0028 | VNPAY, void, POS, reporting, multi-item, delivery và fitting; ranh giới cũ cần cập nhật khi money mở rộng. |
| ADR sau MVP | ADR 0029–0033 | Cart ownership, scoped staff, shipping, automatic promotion, HOME; khoảng trống C3 ở F3. |

**Kết luận:** Có bằng chứng triển khai đáng kể cho tất cả milestone được ghi trong tài liệu, và các thay đổi lớn phần nhiều có ADR. Tuy nhiên không thể xác nhận “đúng và đầy đủ toàn bộ”: F1/F2 là phản ví dụ đã chạy, F3–F8 là các khoảng trống quyết định/tài liệu/kiểm soát cần xử lý; full SQL/E2E hiện trạng chưa được tái chứng nhận. Không cần làm lại kiến trúc từ đầu; cần sửa đúng phần tiền/reporting, đồng bộ nguồn yêu cầu và nghiệm thu lại trên snapshot xác định.
