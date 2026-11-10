# Shopping Experience Upgrade Implementation Plan

> **For agentic workers:** Use `superpowers:executing-plans` to implement task-by-task in the current checkout. Do not spawn workers or change Git state by default. Steps use checkboxes; a checked planning step is not implementation evidence.

**Goal:** Giúp khách khám phá, so sánh, chọn size và hoàn tất mua giày tự tin hơn; giúp nhân viên tìm đúng việc cần xử lý.

**Architecture:** Giữ Vue SPA và REST hiện tại. Ưu tiên trình bày lại dữ liệu có thật, state cục bộ và component hiện có; không chuyển giá, tồn kho, phân quyền hoặc gợi ý size thành quy tắc frontend.

**Tech Stack:** Vue 3, TypeScript, Vue Router, CSS, Vitest; không thêm dependency mặc định.

**Spec:** Brief đã được người dùng đồng ý trong hội thoại ngày 2026-10-13, được lưu đầy đủ trong mục “Design brief” dưới đây. Đây là tài liệu thiết kế/kế hoạch duy nhất cho chương trình này; PRODUCT.md, DESIGN.md và các ADR vẫn chi phối nghiệp vụ.

**Status:** PLANNED — hướng trải nghiệm được đồng ý; chưa triển khai. Các lựa chọn chi tiết dưới đây là PROPOSED cho bản đầu, không phải nghiệp vụ đã được chứng nhận. Requirement: REQ-UX-003. Không mở lại các yêu cầu đã cancelled/deferred.

## Design brief

Khách cần tìm đôi giày phù hợp mà không phải tự ghi nhớ nhiều trang sản phẩm. Nhân viên cần thấy việc tiếp theo cùng bằng chứng đủ để quyết định. Giữ nhận diện nền giấy ấm, chữ Archivo đậm, đường kẻ rõ và điểm nhấn hồng của Stockroom Proof Sheet; tăng chất lượng hình ảnh và tương tác theo mục đích.

Bảy kết quả đã được đồng ý về định hướng:
1. Khám phá “Hôm nay bạn đi đâu?” bằng nhu cầu, màu và ngân sách.
2. Bàn chọn giày so sánh hai hoặc ba sản phẩm.
3. Tư vấn chọn size ngắn, giải thích rõ và do khách xác nhận.
4. Trang sản phẩm có nhịp kể chuyện lookbook và chọn mua thuận tiện trên mobile.
5. Giỏ hàng chỉ rõ vấn đề và cho phép tiếp tục sau lỗi.
6. Hành trình đơn hàng giải thích đã xảy ra gì, đang chờ ai, cần làm gì.
7. Bàn làm việc theo nhiệm vụ cho thu ngân, kho và quản lý.

Đây không phải chương trình thay toàn bộ giao diện hoặc thêm AI vào mọi bước. Gemini chỉ là lựa chọn hỗ trợ phác thảo sau này; không phải dependency, không gửi dữ liệu dự án/khách hàng ra ngoài trong kế hoạch này.

## Global Constraints

- Giá, khuyến mãi, khả dụng, thanh toán, quyền và trạng thái lấy từ backend; không tự tính lại quy tắc thương mại.
- Không tự chọn size, thêm giỏ, tạo đơn, giữ hàng, thanh toán hoặc hoàn tiền thay người dùng.
- Không bịa ảnh thật, đánh giá, độ êm, công năng, chứng nhận, ETA hoặc lượng hàng nội bộ.
- Giữ VI/EN, keyboard, visible focus, screen-reader labels và reduced-motion; kiểm tra 320, 390, 768 và 1440 px.
- Không thêm dependency, migration, provider hoặc endpoint để tiện dựng giao diện. Thiếu contract thì tách proposal cụ thể, chỉ chặn phần phụ thuộc.
- Giữ quy tắc quick-size không tự tăng/giảm size; A4 vẫn bắt buộc với đường đo ảnh.
- POS tiếp tục một dòng, số lượng một, exact cash và scope hiện hành.
- Không stage/commit/reset/stash/switch/push; bảo toàn dirty worktree và SQL hiện có.
- Mỗi đợt sao lưu đúng file trước sửa, lưu receipt/hash và hướng dẫn hoàn tác; không phục hồi toàn bộ checkout.

## Baseline và giới hạn chứng cứ

Observed: HEAD b60b69f cùng các thay đổi chưa commit; không đồng nhất HEAD với bản đã chứng nhận. Certified baseline vẫn là docs/FINAL_FREEZE.md. Đợt gần nhất ghi nhận frontend 333 tests, typecheck/build thành công; browser chưa kiểm tra lại vì CUA lỗi khởi tạo. Đó là chứng cứ lịch sử, cần chạy lại khi triển khai.

Đã kiểm tra ProductSummary/ProductDetail/Variant/ProductFitGuidance trong frontend/src/api.ts; gallery trong ProductView.vue; quick-size trong FitAssistant.vue; discovery trong CatalogView.vue. Gallery và quick-size đã tồn tại: nâng cấp chúng, không xây bản thứ hai.

## Thứ tự và điểm kết thúc từng đợt

| Đợt | Task | Kết quả bàn giao | Phụ thuộc |
|---|---|---|---|
| 1 | UX-01 Bàn chọn giày | Chọn tối đa ba đôi, so sánh và quay lại sản phẩm | Public product API hiện có |
| 2 | UX-02 Lookbook | Gallery rõ hơn, vùng mua mobile, bản ảnh hiện có | Ảnh thật chỉ chặn phần biên tập ảnh thật |
| 3 | UX-03 Chọn size | Quick-size dễ hiểu, trạng thái theo màu, photo path rõ | Quy tắc hiện có; thuật toán mới chưa được duyệt |
| 4 | UX-04 Giỏ hàng phục hồi | Thông báo cạnh vấn đề, giữ lựa chọn hợp lệ | Error contract hiện có |
| 5 | UX-05 Hành trình đơn | Timeline thật và hành động tiếp theo | Order/payment/fulfillment contracts |
| 6 | UX-06 Khám phá nhu cầu | Lối vào trực quan dẫn đến discovery hiện có | Chỉ dùng nhóm/query parser hỗ trợ |
| 7 | UX-07 Bàn làm việc | Tác vụ theo vai trò và location | API được phép của từng vai trò |

Không gộp bảy đợt thành một lần phát hành. UX-06 được giữ trong kế hoạch dù chưa nằm trong bộ ba ưu tiên. Thứ tự này ưu tiên quyết định chọn mua trước; mọi đợt đều phải có demo và kết quả kiểm tra riêng.

## Review Focus

1. Sản phẩm mất xuất bản hoặc request lỗi trong lúc so sánh: đánh dấu không tải được, cho bỏ/thử lại, không coi cache là khả dụng (UX-01).
2. Thiếu ảnh, evidence hoặc hướng dẫn phom: placeholder trung thực, không điền nội dung suy đoán (UX-01/02/03).
3. Đổi màu/locale khi đang chọn size: nhãn và khả dụng cập nhật, không tự áp dụng size (UX-03).
4. Mất mạng hoặc contention sau lệnh giao dịch: giữ cùng payload/key theo hợp đồng, không gửi lại tự động (UX-04/05).
5. Đổi account/location hoặc thiếu quyền: không giữ lộ dữ liệu tác vụ của phiên trước, không gọi API không được phép (UX-07).

## UX-01 — Bàn chọn giày

**Actor/purpose:** Khách hoặc guest so sánh trước khi quyết định; không cần đăng nhập để đọc catalog công khai.
**Preconditions:** Sản phẩm được API public trả về. **State:** danh sách ID cục bộ tối đa 3; bản đầu giữ trong phiên SPA, tải lại trang thì xóa (có chú thích rõ). **Side effects:** chỉ đọc API. **Failure:** lỗi riêng từng sản phẩm, retry rõ ràng; không bỏ lựa chọn còn hợp lệ.

**Files:** tạo frontend/src/comparison.ts, frontend/src/components/ProductComparison.vue, frontend/src/product-comparison.test.ts; sửa CatalogView.vue, ProductView.vue, App.vue, i18n.ts, styles.css trong frontend/src. Không đổi api.ts trừ type import cần thiết.

**Interfaces đề xuất:** comparisonIds: Ref<string[]>; toggleComparison(productId: string): 'added' | 'removed' | 'limit'; clearComparison(): void. Component nhận IDs từ module này, đọc api.product(id): Promise<ProductDetail>; dùng product-media.ts và product-evidence.ts để hiển thị. Đặt component một lần trong shell shopper, không hiện khay trên /operations.

- [ ] Viết test RED: thêm A/B/C thành công, D trả limit và không thay danh sách; chọn lại B bỏ B; clear về rỗng.
- [ ] Chạy `npm.cmd --prefix frontend test -- src/product-comparison.test.ts`, xác nhận fail vì chức năng chưa có.
- [ ] Tạo module state nhỏ; không store framework, persistence hoặc tài khoản wishlist.
- [ ] Viết/render test: 0/1 mục giải thích cần ít nhất 2; 2/3 mục so sánh; lỗi một request không làm mất các mục khác; phản hồi cũ không ghi đè lựa chọn mới.
- [ ] Thêm nút “So sánh” tại catalog/PDP; khay có số mục, xóa từng mục, mở/đóng và thông báo đạt giới hạn. Panel in-page mở bằng button, không thêm modal/focus trap nếu không cần.
- [ ] So sánh tên, ảnh, giá/range có nhãn đúng, màu/size/khả dụng public, evidence và fitSummary; không so sánh lượng tồn nội bộ. Thiếu dữ liệu hiện “Chưa có thông tin”.
- [ ] Toggle “Chỉ xem điểm khác nhau”: so giá trị canonical trước dịch; thiếu và có dữ liệu là khác nhau, cả hai thiếu là giống nhau. Tên/ảnh và đường dẫn luôn giữ.
- [ ] Mobile hiển thị tối đa hai cột so sánh, có bộ chọn hai sản phẩm trong ba; không vuốt ngang cả tài liệu. Nút chuyển tới PDP để chọn màu/size, không thêm giỏ trực tiếp từ bảng.
- [ ] Test VI/EN, keyboard mở/đóng/trả focus hợp lý, không có sản phẩm “được chọn” chỉ bằng màu; chạy full gate bên dưới.

**Acceptance:** So 2 và 3 đôi, xóa/thay đôi, xem khác biệt, quay lại PDP được bằng chuột/bàn phím; không có commercial mutation; không gọi lại cả catalog để đọc 3 sản phẩm; không thêm polling.

## UX-02 — PDP/lookbook

**Files:** sửa frontend/src/views/ProductView.vue, product-media.ts nếu fallback hiện tại chưa đủ, styles.css, i18n.ts; mở rộng product-media.test.ts, cart-flow.test.ts; media được chấp thuận đặt theo pipeline assets hiện hữu, không đổi allowlist tùy tiện.
**Interfaces:** ProductDetail.media ({url, position, alt}), pricing, options; tái dùng selection/add-to-cart hiện tại, không tạo handler mua thứ hai.
**Actor/state:** Khách xem media và chọn variant; chỉ index ảnh và lựa chọn UI đổi. Add-to-cart vẫn là thao tác tường minh theo luồng hiện hành.

- [ ] Viết RED: 0/1/n ảnh, ảnh lỗi, chuyển sản phẩm reset index, không đọc quá cuối mảng; ảnh alt phù hợp và button thumbnail có tên/trạng thái.
- [ ] Nâng gallery hiện có: ảnh chính ổn định tỷ lệ, thumbnail keyboard, ảnh dưới fold lazy-load; không carousel tự chạy.
- [ ] Dàn nhịp ảnh → bằng chứng sản phẩm → chọn màu/size. Chỉ có nhãn “cận đế/chất liệu” nếu asset được mô tả đúng; không đoán từ thứ tự ảnh.
- [ ] Thanh mobile cho biết lựa chọn hiện tại, dẫn tới chọn size nếu còn thiếu; gọi lại cùng handler giỏ. Test không che thông báo lỗi hoặc nút ở 320px và khi zoom 200%.
- [ ] Chạy `npm.cmd --prefix frontend test -- src/product-media.test.ts src/cart-flow.test.ts` và full gate.

**Asset handoff:** Mong muốn 3 sản phẩm đại diện, mỗi đôi 4 góc: tổng thể, trên chân, upper, đế; nguồn/quyền sử dụng, ID sản phẩm/màu và mô tả đi kèm. Không cần ảnh để bắt đầu layout; chưa có ảnh thật thì chỉ nghiệm thu layout với asset hiện hữu, không gọi là bộ lookbook ảnh thật hoàn tất. Việc nhập media mới phải tuân thủ contract/catalog validation hiện hành.

## UX-03 — Tư vấn size liền mạch

**Files:** frontend/src/components/FitAssistant.vue, views/ProductView.vue, i18n.ts, styles.css; tests fit-assistant.test.ts, cart-flow.test.ts.
**Interfaces:** tái dùng props/emits hiện tại của FitAssistant, ProductFitGuidance, Variant.availability và api.fitAnalysis; không thêm API trong bản đầu.
**Actor/state:** Khách tự chọn đường quick/photo và xác nhận size; không giữ ảnh hoặc số đo qua phiên. Lỗi ảnh theo RETAKE/UNSUPPORTED_PRODUCT hiện hữu.

- [ ] Test RED cho nhãn phân biệt “size thường mang”, “điểm bắt đầu tham khảo”, “size đã chọn”; đổi màu cập nhật khả dụng, không tự thêm giỏ.
- [ ] Rút ngắn bố cục của quick path hiện có, đưa giải thích cạnh lựa chọn; giữ nguyên thuật toán và quyền xác nhận của khách.
- [ ] Kiểm tra thiếu fitGuidance, hết size ở màu đang chọn, có màu khác, không hỗ trợ ảnh; ảnh chỉ gửi khi khách chủ động yêu cầu.
- [ ] Giữ A4, giới hạn upload/retake và thông báo độ tin cậy hiện có; test đổi VI/EN giữa các bước.
- [ ] Chạy `npm.cmd --prefix frontend test -- src/fit-assistant.test.ts src/cart-flow.test.ts` và full gate.

**OPEN DECISION:** “Thích ôm/thoải mái” chưa có mapping được duyệt thành tăng/giảm size. Bản này không dùng câu trả lời để đổi gợi ý. Nếu muốn thuật toán mới: cần policy, dữ liệu kiểm chứng, API/domain review và plan riêng; không chặn UI hiện tại.

## UX-04 — Giỏ hàng dễ phục hồi

**Files:** frontend/src/views/CartView.vue, checkout.ts, cart.ts (chỉ khi cần), i18n.ts, styles.css; cart-flow.test.ts, checkout.test.ts, retryable-command.test.ts.
**Interfaces:** api.cartQuote/api.cartCheckout, CartQuote, ApiError, retryable-command.ts; giữ fingerprint/idempotency và cách query recovery hiện tại.
**Actor/state:** Khách đã có cart; quote mới có thể thay quote cũ khi hợp lệ, không tự thay cart demand. Tạo order chỉ ở hành động checkout hiện hữu.

- [ ] Lập bảng error code hiện có → thông báo/hành động trước sửa. Nếu API không xác định item lỗi thì hiển thị lỗi cấp giỏ, không suy đoán dòng bị lỗi.
- [ ] Test RED: quote hết hạn, giá đổi, stock conflict, mất mạng, 503 contention; giữ các lựa chọn hợp lệ và same-key retry.
- [ ] Đưa lỗi có định danh xuống đúng dòng; thêm bước xem thay đổi giá dựa trên quote backend; focus/announce vấn đề, không xóa cả giỏ.
- [ ] Test double-click không phát sinh lệnh trùng, checkout chỉ dùng quote hợp lệ, tổng tiền chỉ lấy từ quote.
- [ ] Chạy `npm.cmd --prefix frontend test -- src/cart-flow.test.ts src/checkout.test.ts src/retryable-command.test.ts` và full gate. Backend thay đổi nếu có phải tách contract + SQL regression riêng.

## UX-05 — Hành trình đơn hàng

**Files:** frontend/src/views/OrderStatusView.vue, OrdersView.vue, PaymentResultView.vue, i18n.ts, styles.css; commerce-render.test.ts, checkout.test.ts và cart-flow.test.ts nếu chứa case liên quan.
**Interfaces:** Order, FulfillmentStatus, VoidStatus và payment reads đang dùng tại các view; không thêm state domain.
**Actor/security:** Chủ đơn đã xác thực; giữ ownership check backend. UI đọc bằng chứng và liên kết tới hành động hiện hữu, không tự giao dịch.

- [ ] Lập mapping enum thật → nội dung “đã xảy ra / đang chờ / bước tiếp”; test paid, pending/unknown, cancelled, pickup, delivery, reversal review/failure.
- [ ] Render timeline chỉ từ state/timestamp thật; thiếu timestamp bỏ thời gian, không tạo mốc tương lai giả.
- [ ] Hiện rõ địa điểm nhận từ dữ liệu đơn; không bịa địa chỉ khi API chỉ trả location name/code. Trường hợp cần địa chỉ mới tách contract.
- [ ] Test đơn của tài khoản khác/401/403, refresh lỗi và chuyển ID nhanh; không lộ dữ liệu đơn cũ hoặc retry payment tự động.
- [ ] Chạy `npm.cmd --prefix frontend test -- src/commerce-render.test.ts src/checkout.test.ts src/cart-flow.test.ts` và full gate.

## UX-06 — “Hôm nay bạn đi đâu?”

**Files:** frontend/src/views/CatalogView.vue, components/StorefrontIntentLinks.vue, i18n.ts, styles.css; storefront-homepage.test.ts, product-evidence.test.ts.
**Interfaces:** api.discovery(query), InterpretedQuery, ProductEvidence; URL q vẫn là nguồn truy vấn; giữ lối xem tất cả.
**Actor/state:** Guest/customer chọn nhu cầu/màu/ngân sách; chỉ thay query/read kết quả, không lưu hồ sơ cá nhân.

- [ ] Xác minh query grammar/backend discovery tests: chỉ hiển thị lựa chọn ánh xạ chính xác với category/color/maximumPrice đã hỗ trợ.
- [ ] Test RED đường chọn → URL query → request → kết quả; back/forward, xóa lọc, zero results và lỗi mạng.
- [ ] Triển khai nhóm chọn ngắn, không bắt buộc onboarding. Phân biệt kết quả khớp với suggestions; không gọi suggestion là nằm trong ngân sách nếu không có bằng chứng.
- [ ] Lý do đề xuất từ evidence/giá/category trả về; không tự gán “êm”, “chống nước” hoặc “phù hợp đi làm” chỉ vì kiểu dáng.
- [ ] Chạy `npm.cmd --prefix frontend test -- src/storefront-homepage.test.ts src/product-evidence.test.ts` và full gate.

**Conditional:** Nhãn đời sống “đi học/đi làm” chỉ là copy mở đầu nếu chưa có mapping approved; dùng nhóm dữ liệu thực như Everyday/Running cho bản đầu. Query mới cần contract riêng.

## UX-07 — Bàn làm việc theo nhiệm vụ

**Files:** frontend/src/views/PosView.vue, PickupQueueView.vue, ReportsView.vue, InventoryTransfersView.vue, App.vue, session.ts nếu cần đọc quyền hiện hữu, i18n.ts, styles.css; pos-workflow.test.ts, app-navigation.test.ts, session-route.test.ts, inventory-transfer.test.ts, reconciliation.test.ts.
**Interfaces:** api.pickupQueue(), api.reportScope(), scoped report/transfer reads và shift reads hiện có; không tạo endpoint dashboard tổng hợp mặc định.
**Actor/state:** Staff có permission và location phù hợp; UI tổ chức read model theo công việc, mutation vẫn nằm tại use case hiện hữu.

- [ ] Kiểm tra từng quyền/API và task list đã có. Bản đầu nâng trang vào từng vai trò, không thêm dashboard thứ hai trùng dữ liệu.
- [ ] Test RED guest/customer/cashier/manager, scope đổi giữa lúc request chạy, 403 và lỗi riêng từng khu vực.
- [ ] POS ưu tiên vào ca/tìm sản phẩm/hoàn tất; kho ưu tiên trạng thái queue và transfer thật; quản lý có đường tới các ngoại lệ reconciliation đang được API hỗ trợ.
- [ ] Không tự đặt ngưỡng “sắp hết hàng”, độ ưu tiên tài chính hoặc “quá hạn” khi chưa có chính sách. Không tính KPI toàn hệ thống từ một trang dữ liệu.
- [ ] Chạy `npm.cmd --prefix frontend test -- src/pos-workflow.test.ts src/app-navigation.test.ts src/session-route.test.ts src/inventory-transfer.test.ts src/reconciliation.test.ts` và full gate.

## Full gate cho mỗi đợt

- [ ] Lưu before-copy + danh sách file/hash của đợt; xác nhận không trộn thay đổi người dùng.
- [ ] Có RED rồi GREEN cho logic mới; `npm.cmd --prefix frontend test` exit 0, không bỏ qua test để xanh.
- [ ] `npm.cmd --prefix frontend run typecheck` và `npm.cmd --prefix frontend run build` exit 0; `git diff --check` không có lỗi whitespace.
- [ ] Ghi kích thước bundle trước/sau cùng cấu hình; giữ route splitting, không tăng chunk warning threshold để giấu regression.
- [ ] Browser check 320/390/768/1440, VI/EN, keyboard, 200% zoom, reduced-motion, mạng chậm/lỗi. Một vòng kiểm tra gom lỗi và một vòng xác nhận sau sửa; lỗi critical vẫn phải đóng.
- [ ] Nếu công cụ browser vẫn hỏng, ghi UNVERIFIED và giữ task chưa visual-accepted; không thay chứng cứ browser bằng unit tests.
- [ ] Rà soát spec, permissions, failure recovery; financial/security/inventory change nếu phát sinh cần review chuyên biệt và SQL integration thích hợp.
- [ ] Lưu receipt command/exit/time/hash/log và cập nhật cùng .agent ledger. Chỉ đánh dấu requirement hoàn thành khi đủ bảy đợt và điều kiện chấp nhận, hoặc có quyết định giảm scope rõ ràng.

## Đo hiệu quả và nghiệm thu chương trình

Chạy cùng kịch bản trước/sau: tìm 2 đôi → so khác biệt → chọn màu/size → giỏ → đọc trạng thái đơn; staff tìm đúng tác vụ được giao. Ghi thời gian, số lần đi lùi và lỗi thao tác từ phiên kiểm tra có quan sát. Chưa có baseline thì không tuyên bố tăng conversion hoặc phần trăm cải thiện. Không thêm analytics/tracking cá nhân mặc định.

Hoàn tất từng đợt khi acceptance/test/browser/review của đợt có chứng cứ. Hoàn tất chương trình khi đủ 7/7 và các dependency đã giải quyết hoặc được người dùng loại khỏi scope tường minh. Live VNPAY, provider AI, Return/notification và dark theme không nằm trong chương trình.

## Plan review và bước tiếp theo

- [x] Đủ bảy ý tưởng, không bỏ hướng khám phá nhu cầu.
- [x] Tách gallery/quick-size đã có khỏi phần cần xây mới.
- [x] Mỗi task có actor, state/side effects, contract, failure, file và acceptance/checks.
- [x] Không biến preference size/nhãn công năng/ETA/ngưỡng stock thành policy ngầm.
- [x] Có rollback và phân biệt automated verification với visual acceptance.
- [ ] Thực thi UX-01: đọc toàn bộ caller/test liên quan, sao lưu touched files, viết regression RED trước khi sửa code.

Phương thức đề xuất: một agent triển khai tuần tự từng đợt, review tại điểm kết thúc. Không cần Gemini hay thêm nhân công để bắt đầu. Kế hoạch này chỉ bàn giao tài liệu; chưa chạy các bước triển khai.

## Execution checkpoint — 2026-10-15
The planning-only statement above is historical. UX01..07 implementation is now present; 351 frontend tests and production build/typecheck pass. Full visual acceptance remains pending, with local SQL Server service recovery required. See `docs/UX_UPGRADE_VERIFICATION_20261015.md` for per-slice delivery, browser evidence, exact remaining gates and rollback locations. Checklist items are intentionally not blanket-checked where evidence is incomplete.
