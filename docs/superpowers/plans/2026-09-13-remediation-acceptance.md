# P5 — Nghiệm thu và đóng hồ sơ Implementation Plan

> **For agentic workers:** Use `superpowers:executing-plans` và `superpowers:verification-before-completion`. Bằng chứng phải đến từ đúng snapshot đang nghiệm thu; mọi checkbox là việc cần làm trong phiên thực thi.

**Goal:** Có verdict tái lập được cho từng finding, bao gồm SQL migration/race, API/UI, security và hồ sơ yêu cầu.

**Architecture:** Tái sử dụng Maven acceptance và frontend tests, thêm regression vào harness hiện hữu. Browser-demo và acceptance có database riêng. Không lấy một bộ screenshot hoặc unit suite làm bằng chứng thay cho tính đúng tài chính.

**Tech Stack:** Maven Surefire/Failsafe, SQL Server, Flyway, pnpm/Vitest/Vite, browser thật khi môi trường cho phép.

**Spec:** [master](2026-09-13-remediation-master.md), [P2/P3](2026-09-13-remediation-finance-reporting.md), [P4](2026-09-13-remediation-login-security.md), [audit](../../SESSION_AUDIT_2026_09_13.md).

## Global Constraints

- Giữ các global constraints của master, đặc biệt data preservation và không gọi payment provider thật.
- Full SQL acceptance phải không skip ca bắt buộc. Không cố định “176” hay “321” vì suite hiện tại đã thay đổi.
- Profile `acceptance` chạy `*ExternalIT`; container IT không được tự cộng vào nếu chưa thực sự chạy.
- Lưu logs/summaries từ lần chạy mới; không cộng XML lịch sử còn trong target.
- Các chứng nhận A–S1 cũ là historical evidence, không sửa thành chứng nhận cho bản mới.

## 1. Ma trận bằng chứng đóng finding

| Finding | Bằng chứng bắt buộc | Không đủ để đóng |
|---|---|---|
| F1 | M2/M3/M7/M9 tại reporting SQL/service, API và browser; legacy policy | Chỉ net tổng đúng hoặc đổi nhãn UI |
| F2 | M1/M4/M5/M6/M8; constraints/upgrade/retry/callback; paid component đúng | Chỉ sum(allocation)=capture |
| F3 | ADR C3 cùng mapping lifecycle/rules/contract, nguồn approval | Code/test có sẵn, hoặc đổi certificate |
| F4 | D4 đã chốt và branch behavior đã test/document | Chỉ đổi status trong báo cáo |
| F5 | Limiter concurrency/boundary/capacity, HTTP 429, recovery, security regression | Có class limiter nhưng chưa vào chain |
| F6 | Current index/supersedes không mâu thuẫn, runbook đúng migration | Sửa mọi tài liệu lịch sử cho giống nhau |
| F7 | Snapshot/manifest/hash + log/runtimes/DB riêng và kết quả có thể tái tạo | Chỉ cùng HEAD với worktree khác |
| F8 | API index + tài liệu request/response/auth/error/replay/consumer/tests | Danh sách URL không có semantics |

## 2. Task P5.1: Full automated gates

**Files tạo:** `artifacts/remediation-acceptance/` chứa runtime/manifest, logs và summary. **Files đọc:** backend pom, frontend package scripts, migration và tests mới. Không lưu password/env dump.

- [ ] Freeze phạm vi test bằng source hash trước chạy; ghi migration version cao nhất, package-lock thực tế và runtime versions.
- [ ] Xác minh DB acceptance đã mới/disposable bằng đúng tên ghi trong P0; không chạy clean data trên DB browser hoặc DB nghiệp vụ.
- [ ] Chạy fresh migration + full acceptance; upgrade fixtures phải gồm populated trước migration và legacy financial states theo D2.
- [ ] Chạy frontend toàn suite và build; build gồm `vue-tsc -b`. Chạy lint chỉ nếu repo lúc đó có script thực sự, không bịa PASS lint.
- [ ] Tổng hợp đúng reports do lần chạy này tạo, nêu suites/tests/failure/error/skip và command exit code.
- [ ] Nếu fail, phân loại environment/product/fixture; chỉ sửa fixture khi chứng minh fixture sai yêu cầu. Không xóa race test hoặc nới assert để có PASS.
- [ ] Sau sửa phát sinh, chạy lại targeted case và gate rộng bị ảnh hưởng; không tiếp tục dùng log trước thay đổi làm final evidence.

Lệnh từ thư mục tương ứng:

```powershell
# backend; SPRING_DATASOURCE_* đã trỏ đúng DB acceptance mới:
./mvnw.cmd -Pacceptance clean verify
# frontend:
pnpm test
pnpm build
```

Quan sát SQL log phải chứng minh migrations đúng phiên bản và suite không skip do thiếu biến datasource. Matching JDBC native-auth library là runtime prerequisite, không sửa source để bỏ auth.

**Concurrency cần giữ:** checkout last unit và đảo thứ tự cart; payment vs expiry/cancel; voucher global/customer last use; handover/dispatch vs cancel; POS vs online; sale vs shift close; void retry/replay/capacity; limiter simultaneous requests.

## 3. Task P5.2: Browser acceptance theo người dùng

**Files:** `docs/DEMO.md`, test case evidence mới trong artifact. Dùng manual human steps nếu tool không điều khiển được browser; ghi rõ người thực hiện và kết quả quan sát, không tự đánh PASS.

| Case | Bước thực hiện | Kết quả và bằng chứng |
|---|---|---|
| B1 discounted Pickup | Customer tạo 2 dòng M1, quote, checkout, demo payment; staff hủy trước handover | UI/SQL amount 150.000; allocation 50.000/100.000; report cùng kỳ zero sau void |
| B2 paid shipping | Customer Delivery M3, demo payment, hủy trước dispatch | Hàng 100.000, ship 30.000; void 130.000; report hàng/ship riêng về zero |
| B3 free shipping/voucher | CODE hoặc CLAIMABLE + shipping benefit theo policy được chọn; payment và void | Không double discount; no row tiền 0; claim usage sau paid void vẫn REDEEMED |
| B4 physical issue protection | Paid order handover/dispatch rồi thử cancel | Guard từ server từ chối; không movement/reversal mới |
| B5 account/scope | Customer A→logout→B, manager/cashier, foreign location | Không lộ cart/voucher/order trước; report/management deny đúng quyền |
| B6 auth throttling | Fixture login sai tới ngưỡng, xem 429, đợi/test clock theo phương pháp chấp nhận; login hợp lệ sau window | Thông báo VI/EN chung, Retry-After hợp lệ; không session sai |
| B7 reporting date | Report riêng ngày capture, ngày void và cả hai | Ngày void âm nếu đúng nghiệp vụ, gộp về zero; không ép âm thành 0 |
| B8 S1 regression | Manager preview/publish HOME, customer CTA/locale | Chỉ published, stale references an toàn, không đổi promotion authority |
| B9 fitting regression | Valid A4 và missing-reference, chọn size thủ công | Recommendation không đổi cart/stock tự động; retake không bịa size |
| B10 POS regression | Cashier open shift, bán đúng một SKU/cash, refresh | Một sale/tender/movement; report POS không lẫn online/shipping |

- [ ] Chạy B1–B10 với fixture mô tả được; log IDs cần thiết và read-only SQL correlation, không lưu password hoặc raw ảnh cá nhân.
- [ ] VI/EN tại desktop và 390px cho các màn thay đổi; smoke 1024/768 cho reporting/login nếu layout đổi.
- [ ] Keyboard/focus/labels/error announcements/table containment; native browser zoom 200% với chứng cứ thực.
- [ ] Nếu zoom không điều khiển được, ghi user-manual outstanding; chỉ cho ngoại lệ nếu quyết định nghiệm thu hiện tại cho phép, không tự kế thừa ngoại lệ S1 vào mọi màn mới.
- [ ] Console/network kiểm tra lỗi không mong đợi; không log secret/code voucher.
- [ ] Không sử dụng response mock để gọi là end-to-end SQL-backed; mock UI tests là tầng khác.

**Gate:** mandatory flow có cả hành vi UI và fact backend; “chưa chạy” khác “fail” và khác “pass”. Môi trường chặn thì báo tên case chặn và thiếu prerequisite nào.

## 4. Task P5.3: Rà soát độc lập và đóng hồ sơ

**Create:** `docs/REMEDIATION_ACCEPTANCE.md`. **Modify:** `docs/CURRENT_BASELINE.md`, `docs/PRODUCT_ROADMAP.md`, `docs/DEMO.md`, API index và trạng thái finding.

- [ ] Review financial diff theo D1–D3, đặc biệt legacy ACTIVE/UNKNOWN và shipping FK; reviewer phải xem test và schema, không chỉ screenshot.
- [ ] Review security filter order, duplicate registration, throttle memory bounds, proxy/IP trust, không làm giảm CSRF/session protections.
- [ ] Review source-of-truth: current rules không mâu thuẫn; mọi PROPOSED/OPEN giữ đúng trạng thái; không tự ghi “approved by user” khi không có nguồn.
- [ ] Ghi bảng finding: CLOSED / OPEN / DEFERRED BY EXPLICIT DECISION, lý do, file/test/log dẫn chứng. Deferred không đồng nghĩa fixed.
- [ ] Verdict phải nói phạm vi mới/legacy, DB migrations, tests, manual gaps và provider limitations. Không gọi production certified.
- [ ] Kiểm tra diff scope: chỉ các file task yêu cầu thay đổi; không commit accumulated A–S1 ngoài ý muốn.
- [ ] Lưu manifest cuối và so với đầu: file nào đổi, vì task nào, hash gì. Không lấy commit HEAD làm identity duy nhất khi tree còn dirty.
- [ ] Stop đúng process test đã tạo; cleanup chỉ DB disposable và runtime files được ghi trong allowlist P0. Không wildcard drop/delete mọi database/file theo tiền tố chung.
- [ ] Commit/tag/push chỉ khi được yêu cầu; nếu chưa thì bàn giao patch/manifest/report đủ để người dùng xem xét.

Nội dung tối thiểu certificate mới:

```text
Snapshot: HEAD + manifest SHA-256 + source scope
Decisions: accepted ADRs và D1–D6 disposition
Migrations: source max, fresh run, populated upgrade
Commands: lệnh thực, ngày giờ, runtime, exit codes
Results: unit / SQL / frontend / browser tách riêng
Findings: F1–F8 và evidence links
History: legacy handling thực tế và phần chưa sửa
Limitations: manual zoom/provider/real camera/out-of-scope
Cleanup: DB/process/artifact allowlist đã xử lý
Verdict: chỉ phạm vi được chứng minh
```

## 5. Dừng an toàn và quay lui

- Code-only chưa deploy: khôi phục đúng patch của task, giữ changes người dùng; không whole-tree reset.
- Schema chỉ mới migrate: đánh giá backward compatibility đã ghi ở P2.2 trước rollback binary.
- Đã ghi SNAPSHOT_V2/SHIPPING: không downgrade mù sang code chỉ biết ORDER_ITEM. Chọn forward fix hoặc restore toàn môi trường thử nghiệm disposable.
- Financial history thật: không delete/recompute để né lỗi; ghi reconciliation và quy trình correction đã duyệt.
- Nếu D4 chọn hỗ trợ zero-payable mà slice chưa triển khai, trạng thái chương trình còn OPEN F4; có thể nghiệm thu riêng F1/F2/F5, không đóng toàn bộ.

## 6. Checklist handoff cuối

- [ ] Một entry point current docs và đủ liên kết plan/spec/ADR.
- [ ] Có test RED chứng minh lỗi trước sửa và GREEN đúng behavior sau sửa.
- [ ] Full gates chạy trên đúng snapshot sau thay đổi cuối.
- [ ] Không assertion bị làm yếu để giữ PASS; không probe “lỗi còn tái hiện” bị dùng làm regression GREEN.
- [ ] Không user data/credential leak và không tác động provider thật.
- [ ] User nhận được kết quả ngắn gọn: đã sửa gì, tests nào chạy, finding nào còn mở, bước còn cần quyết định.
