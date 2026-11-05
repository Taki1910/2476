# Đối soát tính năng và cổng nghiên cứu — 2026-10-15

Phạm vi: kiểm tra nguồn, tài liệu và chứng cứ sẵn có; không sửa ứng dụng, Git hay SQL Server. `ACCEPTED` dưới đây chỉ có nghĩa trong phạm vi chứng nhận lịch sử được dẫn; không đồng nghĩa đã chứng nhận bản checkout hiện tại. Nhánh quan sát `codex/remediation-audit-20260913`, HEAD `b60b69f15273e96b73242ff6be13f5e4d89939ff`, working tree có thay đổi của người dùng; nguồn migration V1–V36 (36 file). Baseline được chứng nhận vẫn là `docs/FINAL_FREEZE.md`: HEAD cũ `2302354...`, V1–V33, core đạt, live AI provider chưa kiểm tra. Demo/candidate V36 không được tự động nâng thành baseline.

## A. Tích hợp skill

| Thành phần | Trách nhiệm và quyết định |
| --- | --- |
| `shoe-commerce-skill-router` (`F0B91A40`) | Chọn bộ skill tối thiểu, giữ một nơi ghi trạng thái. KEEP_EXISTING. |
| `reuse-first-research` trong `.codex/skills` (`B89EE711`) | Nghiên cứu nội bộ/nguồn chính thống, so REUSE/ADAPT/BUILD trước thay đổi đáng kể. ADD_SCOPED: nội dung hiện có đã liên kết với router bằng hướng dẫn, không có khóa ghi kỹ thuật. |
| `session-continuity` (`54DC6217`) | Khôi phục yêu cầu/quyết định/chứng cứ và next action từ `.agent`. KEEP_EXISTING. |
| `hybrid-engineering-router` (`814B2F75`) | Điều phối kỹ thuật sau quyết định nghiên cứu; tránh chạy hai workflow cho một trách nhiệm. KEEP_EXISTING. |
| Debugging/TDD/review/UI skills hiện có | Chỉ kích hoạt cho đợt triển khai phù hợp, không nhận quyền đổi requirement hoặc bypass router. KEEP_EXISTING. |

Không tìm thấy `INSTALL_REUSE_FIRST_RESEARCH.md` trong project, Desktop, Downloads, Documents hoặc attachments hiện có. Cũng không có `AGENTS_GLOBAL_RESEARCH_SNIPPET.md` hay `AGENTS_SHOE_COMMERCE_SNIPPET.md` trong package skill hiện hữu. Skill hiện hữu chỉ có `SKILL.md`, `references/evaluation-cases.md`, `references/research-gate-template.md`. Đã đọc và SHA-256 cả ba file: `B89EE711...`, `3E2122CA...`, `651B918E...`. Cả ba là Markdown thường; không có script/hook/binary/dependency hay điểm gửi dữ liệu. Các chuỗi `curl | sh`, `.env`, upload trong nội dung là cảnh báo chống hành vi đó, không phải lệnh phải chạy. Nguồn gốc, release/license của package định cài chưa xác minh được vì thiếu installer/package. Verdict cho **package yêu cầu**: `REVIEW_REQUIRED`; không cài vào `.agents/skills`, không sửa hai AGENTS, không tạo before-copy giả. Skill `.codex` đã tồn tại và có thể dùng như incumbent trong phạm vi nghiên cứu. Chưa chạy thử kích hoạt trong Codex session mới; chỉ kiểm tra tĩnh: frontmatter, trigger, Research Decision gate và ví dụ đánh giá đều có. Không gọi đây là enforcement kỹ thuật.

## B. Kiểm kê tính năng

| Tính năng | Yêu cầu gốc | Hiện trạng/thiếu | Chứng cứ | Trạng thái |
| --- | --- | --- | --- | --- |
| Tìm chính xác, bỏ dấu, nhóm màu/mục đích, giá, SKU | P1 discovery | Có parser/ranking và phân tách match/suggestion; cần đo chất lượng thêm | `CatalogDiscovery.java`, `CatalogDiscoveryTest.java`, P1 spec | ACCEPTED trong phạm vi P1 |
| Tìm một phần từ và chịu lỗi gõ | Mong muốn nêu trong yêu cầu lần này; chưa thấy ID/acceptance lịch sử riêng | `name::contains` hỗ trợ chuỗi con; không thấy edit-distance/typo matching. Cần corpus VI/EN và ngưỡng nhiễu được duyệt | `CatalogDiscovery.java:76-100`, `:121-129` | PARTIAL |
| Gợi ý ngay khi gõ | Mong muốn nêu trong yêu cầu lần này | UI chỉ gửi tìm khi submit; `suggestions` là fallback sau zero exact match, không phải autocomplete | `CatalogView.vue:153-171`, `CatalogDiscovery.java:65-73` | NOT_IMPLEMENTED |
| Bộ lọc khám phá, zero-result recovery | P1, UX06 | Form nhu cầu/màu/ngân sách, URL/API, clear và suggestion có; back/forward, slow/error gate còn mở | `CatalogView.vue`, `cart-flow.test.ts`, UX report | PARTIAL cho UX06 |
| So sánh sản phẩm | REQ-UX-003/UX01 | Panel 2–3 sản phẩm có; mở lại giữ cache cũ dù public product đổi hoặc 404 | `ProductComparison.vue:25,90`, UX report review | PARTIAL |
| Product detail/gallery, Color→Size, Buy Now | P1/UX02 | Luồng cũ và gallery/mobile nâng cấp có; 200% zoom/ảnh đại diện thật chưa xác nhận | `ProductView.vue`, `product-media.test.ts`, UX report | PARTIAL cho UX02; P1 lịch sử ACCEPTED |
| Giỏ guest nhiều dòng, giữ qua đăng nhập | Phase 15, Phase A, ADR-0029 | `activateCart` cộng số lượng và tiêu thụ guest envelope; unit test và chứng nhận Phase A có. Hành trình **đăng ký tài khoản mới rồi merge** chưa có test/browser receipt riêng; lỗi mất giỏ được báo trước đây chưa thể đóng từ sign-out test | `cart.ts:37-56`, `session.ts:42-57`, `RegisterView.vue:18-21`, `cart.test.ts:37-49`, Phase A | IMPLEMENTED_NOT_ACCEPTED cho hành trình đăng ký; merge login lịch sử ACCEPTED |
| Checkout nhiều dòng, quote, địa chỉ/phí, Pickup/Delivery | Phase 15/16, ADR-0026/27/31/43 | Có core và chứng nhận lịch sử; không chạy lại SQL trong audit này | Phase 18, FINAL_FREEZE, API COMMERCE | ACCEPTED trong phạm vi lịch sử |
| Lịch sử/track đơn, UX05 | Phase 15/16, UX05 | Timeline thực có; refresh 403/404 vẫn có thể hiển thị đơn cũ và nút thao tác | `OrderStatusView.vue:61-68,109-145`, UX report review | PARTIAL cho UX05; core lịch sử ACCEPTED |
| Voucher/khuyến mãi khách | C2/C3 | Pipeline, voucher family, claim/redemption và UI có; không mở rộng phân phối email | C3 certificate, `OffersView.vue`, `VouchersView.vue` | ACCEPTED trong phạm vi C3 |
| Fit quick/photo/A4/quality/retry/VI-EN | REQ-FIT-001/002, UX03 | Chức năng và fixture synthetic có; accuracy ảnh camera thật chưa chứng nhận, UX locale/photo visual gate còn | `FitAssistant.vue`, `AI_FITTING_EVALUATION.md`, UX report | PARTIAL cho kiểm chứng thực tế; core lịch sử ACCEPTED |
| HOME, banner/section, sản phẩm/media | S1, P5A, UX02 | HOME versioned, sản phẩm/evidence/media có; lookbook ảnh thật chờ asset có quyền | S1 certificate, FINAL_FREEZE, UX report | PARTIAL cho lookbook; core lịch sử ACCEPTED |
| Bestseller deterministic và manager AI draft | P5A/P5B | Deterministic có; AI optional preview có, live provider smoke chưa chạy | FINAL_FREEZE, ADR-0042 | ACCEPTED core; live gate DEFERRED |
| Catalog/variant, receive/transfer | REQ-CAT-*, REQ-INV-002/003 | Catalog, receive, transfer V36 có chứng cứ candidate; physical return chưa có | ADR-0041/46, V34/V36, acceptance matrix | PARTIAL cho REQ-INV-002 |
| Staff scope, fulfillment, POS/barcode, reporting | Core, UX07 | Core có chứng nhận; UX07 tái dùng màn hình hiện hữu, thêm transfer filter; full role/location/visual gates còn | FINAL_FREEZE, UX report, `InventoryTransfersView.vue` | PARTIAL cho UX07; core lịch sử ACCEPTED |
| Return/exchange/refund mở rộng | REQ-FUL-003, REQ-INV-002 | Return/inspection chưa được admit; financial void hiện hữu không chứng minh Return mới | DEC-0010/12, acceptance matrix | DEFERRED |
| VNPAY sandbox production gate, carrier, notification, expanded POS, advanced reporting, scheduled reconciliation, extended AI/media | Roadmap/REQ-PAY-003/NOT-001/AI-003 | Tùy mục: kế hoạch hoặc capability giới hạn; thiếu admission/provider/evidence. Không tự kích hoạt | FINAL_FREEZE, DEC-0010/11, PAY-003 plan | DEFERRED |

## C. Lệch yêu cầu/chứng cứ

Registry có **47 ID**, matrix có **47 hàng**, khớp mỗi ID một lần: 38 completed, 2 in_progress, 4 planned, 2 superseded, 1 cancelled. Hai in_progress là REQ-INV-002 và REQ-UX-003; bốn planned là REQ-PAY-003, REQ-FUL-003, REQ-NOT-001, REQ-AI-003. Hai superseded và dark theme cancelled được giữ trong thống kê nhưng loại khỏi backlog. Không thấy orphan ID khi so danh sách.

Độ phủ báo cáo: `requirements_count=47`, `reported_requirements_count=47` theo nhóm tính năng ở B và bảng định danh đầy đủ `.agent/ACCEPTANCE_MATRIX.md`. Loại khỏi backlog triển khai hiện tại: `REQ-CHECKOUT-001`/`REQ-PROMO-003` (superseded), `REQ-UX-002` (cancelled), `REQ-PAY-003`/`REQ-FUL-003`/`REQ-NOT-001` (future scope), `REQ-AI-003` (optional release gate). Các ID completed là phạm vi chứng cứ lịch sử, không phải 38 kết quả test mới của audit này.

- `docs/PRODUCT_ROADMAP.md` là chứng nhận Phase 18 lịch sử; `docs/FINAL_FREEZE.md` là baseline core V33; candidate V36 và UX dirty tree không kế thừa chứng nhận tự động.
- Kế hoạch UX vẫn có dòng `PLANNED/chưa triển khai` và checkbox trống dù báo cáo 2026-10-15 nói có triển khai cả bảy khu vực. Trạng thái canonical `REQ-UX-003=in_progress` vẫn đúng; cần đối chiếu từng checkbox với receipt trước khi chấp nhận.
- Báo cáo UX có đoạn SQL stopped/App retry chưa kiểm chứng đã được phần “Live follow-up” phía sau giải quyết. Đọc riêng đoạn cũ gây hiểu nhầm; giữ provenance nhưng thêm chú giải superseded ở lần sửa tài liệu kế tiếp.
- Phase A nói browser đã kiểm tra guest-to-customer merge; tài liệu không chứng minh riêng hành trình guest→register→auto-login→merge. `cart.test.ts` chỉ chứng minh `activateCart` đơn vị. Không gọi defect cũ đã hết.
- Typo tolerance và live autocomplete không có ID riêng trong registry hoặc acceptance plan P1; yêu cầu lần này nhắc lại để đối soát. Cần chốt ví dụ truy vấn, ngưỡng nhiễu và scope trước khi phát triển thành requirement mới.
- Không phát hiện trong audit này một tính năng commercial/financial mới nằm ngoài scope đã duyệt. Audit này không đủ để xác nhận toàn bộ dependency vulnerability hay API exception mapping đã sạch; đây vẫn là nợ rà soát, không phải lỗi mới đã chứng minh.

Verdict ledger: **INCONSISTENCIES FOUND** ở trạng thái mô tả/receipt UX nêu trên; không thay đổi business status để làm số liệu đẹp hơn.

## D. Research Decisions

| Tính năng | REUSE | ADAPT | BUILD | Khuyến nghị, chứng cứ, rủi ro |
| --- | --- | --- | --- | --- |
| UX05 refresh 403/404 | `api.order`, `ApiError`, guard thế hệ request | Xóa order riêng tư khi quyền/ID mất; giữ lỗi mạng tạm thời theo UI policy hiện hành | Store/cache mới | **ADAPT** `OrderStatusView.vue`; test mounted success→403/404 và ID switch; rủi ro: nhầm 403 với outage, phải giữ thông báo/retry đúng. [Vue watcher cleanup](https://vuejs.org/guide/essentials/watchers) giải thích stale async response. |
| UX01 cache reopen | `api.product`, epoch guard | Revalidate tối đa 3 ID khi reopen/refresh, lỗi từng item và không ghi đè response mới | Polling/cache framework | **ADAPT** `ProductComparison.vue`; test success→404, price change, out-of-order; rủi ro: flicker/request lặp. Vue watcher guidance như trên. |
| Guest→register cart | `activateCart`, `signIn`, ADR-0029 | Nếu flow thực fail, sửa đúng handoff sau khi tái hiện | Server Cart | **REUSE + verify first**: chạy journey thật và test register→signIn→merge, nhiều dòng/số lượng; rủi ro storage failure/multi-tab. [MDN storage event](https://developer.mozilla.org/en-US/docs/Web/API/Window/storage_event) xác nhận tab khác nhận event, tab phát không nhận. |
| Typo/partial search | `CatalogDiscovery` normalization, aliases, rank | Thêm tập ví dụ và matching bị chặn ngưỡng vào rank hiện tại sau khi requirement rõ | Search cluster | **ADAPT có điều kiện**; hiện partial `contains` có, typo chưa. SQL Server [full-text prefix](https://learn.microsoft.com/en-us/sql/t-sql/queries/contains-transact-sql) hỗ trợ prefix nhưng không tự giải quyết typo; cluster không có chứng cứ nhu cầu. Rủi ro: kết quả lạc chủ đề/hiệu năng. |
| Autocomplete | Public discovery API và catalog input | Debounce và gợi ý có nhãn riêng nếu contract/acceptance duyệt; không gọi zero-result suggestions là autocomplete | Endpoint/index mới | **DEFER decision**: cần xác định API gợi ý theo prefix, min chars, keyboard/accessibility và tải; hiện `autocomplete="off"` không phải sản phẩm autocomplete. |
| UX02–07 remaining gates | Existing pages/tests, UX report | Kiểm tra/browser và chỉ sửa defect tái hiện | Redesign | **REUSE/ADAPT**; không xây dashboard/fit algorithm mới. Rủi ro chính là thiếu ảnh thật, role/scope, zoom/motion/network evidence. |

## E. Kế hoạch ưu tiên

| Mức | Việc và điều kiện nghiệm thu | Phụ thuộc |
| --- | --- | --- |
| P1 | UX05: sau 403/404 refresh không còn receiver/address/order actions; mạng tạm lỗi không giả là mất quyền; test mounted và browser account switch. | REQ-UX-003 UX05; owner-only API giữ nguyên. |
| P1 | UX01: reopen/refresh revalidate, unpublished 404 và price change không hiển thị cache cũ; request cũ không ghi đè mới. | REQ-UX-003 UX01. |
| P1 | Guest→register→merge: kiểm chứng sản phẩm và số lượng trước/sau tạo tài khoản, reload, account khác; sửa nếu tái hiện. | ADR-0029/BR-CHK-109; cần fixture/demo account mới. |
| P2 | Hoàn thành UX02–07: 200% zoom, reduced-motion runtime, slow/error, VI/EN, keyboard, 320/390/768/1440 và cashier/manager live; đối chiếu receipt từng slice. | REQ-UX-003, asset thật chỉ cho lookbook ảnh thật. |
| P2 | Chốt corpus search: exact/SKU ưu tiên, tiếng Việt có/bỏ dấu, thiếu chữ/gõ sai, prefix, zero-result, nhiễu; sau đó nghiên cứu matching và contract autocomplete. | Requirement/acceptance mới hoặc clarification được chủ dự án xác nhận. |
| P3 | Đối chiếu abuse registration/fit, error mapping, dependency advisories và sửa mô tả UX stale. | Audit riêng; không tự mở semantic/security change trong UX. |
| DEFERRED | Return/physical return, live VNPAY/AI, notifications, carrier, expanded POS/reporting, scheduled reconciliation, AI media. | Admission riêng và các quyết định/domain/provider/asset tương ứng. |

## F. Chứng cứ xác minh

- **Lịch sử:** FINAL_FREEZE: backend unit 99/99, SQL 232/232, frontend 286/286, browser 1440/390 ở V33; POST-AUD03: V36 candidate SQL 68/68, frontend 329/329; UX report: frontend 351/351 và build/typecheck. Đây là receipt cũ, không chạy lại trong đợt audit này.
- **Nguồn vừa kiểm tra:** `cart.ts`, `session.ts`, `RegisterView.vue`, `CatalogDiscovery.java`, `CatalogView.vue`, `ProductComparison.vue`, `OrderStatusView.vue`, plan/ADR/ledger và danh sách migration. Các kết luận defect UX05/UX01 là suy luận trực tiếp từ flow hiện tại, khớp review 2026-10-15; chưa tái hiện browser mới.
- **Test tự động mới:** không chạy. **Browser mới:** không chạy. **SQL mới:** không chạm. **Giả định còn mở:** registration merge thực tế, chất lượng typo search, độ chính xác ảnh camera, UX visual gate đầy đủ, live providers và vulnerability trạng thái mới nhất.

## G. Next target

`NEXT TARGET: REQ-UX-003 / UX05 — xóa dữ liệu đơn cũ khi refresh bị 403/404`

Lý do: dữ liệu riêng tư và action cũ vẫn có thể hiển thị sau khi quyền truy cập bị từ chối; phạm vi sửa nhỏ và API/domain giữ nguyên. Skill kế tiếp: router → systematic debugging (tái hiện) + reuse-first research (quyết định ADAPT ở trên) → TDD/review/verification. File dự kiến: `frontend/src/views/OrderStatusView.vue`, một test mounted trong `frontend/src/commerce-render.test.ts` hoặc test gần nhất; không cần backend/API/SQL. Acceptance: success→403/404 xóa chi tiết/nút; 401 theo session guard; network error vẫn thông báo và không lộ sai account; fast ID switch bỏ response cũ; VI/EN và keyboard; frontend focused/full/typecheck/build, browser account-switch nếu stack demo sẵn. Không đổi quyền server, payment/refund hoặc data protected. Cần quyết định UI nhỏ về cách hiển thị lỗi mạng tạm thời nếu test/contract hiện tại không xác lập; mặc định ẩn action giao dịch khi status không thể xác minh.

## H. Kết luận

1. **Skill integration:** incumbent research skill dùng được ở mức hướng dẫn; package/installer/snippet yêu cầu thiếu, nên cài vào `.agents` và merge AGENTS **chưa hoàn thành**. Security static review incumbent không thấy khả năng thực thi; provenance package yêu cầu chưa xác minh.
2. **Graduation core:** PASS theo freeze V33 lịch sử, không chứng nhận lại HEAD/dirty tree/V36.
3. **Historical feature plan:** đa phần core có chứng cứ; guest registration merge và yêu cầu search nâng cao chưa có receipt đủ để kết luận.
4. **UX mới:** triển khai hiện diện 7 khu vực, `REQ-UX-003` vẫn IN_PROGRESS; UX01/UX05 có finding cần sửa và gate trực quan còn mở.
5. **Sẵn sàng việc kế:** UX05 đủ căn cứ để mở một đợt sửa riêng sau audit; không thay đổi application trong lần này.
