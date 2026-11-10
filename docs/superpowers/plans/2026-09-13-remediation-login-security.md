# P4 — Login throttling Implementation Plan

> **For agentic workers:** Use `superpowers:executing-plans` và TDD. Policy D5 cần được chấp nhận trước thay đổi hành vi authentication; không triển khai nhầm các thông số đề xuất thành yêu cầu đã duyệt.

**Goal:** Đáp ứng yêu cầu giới hạn tần suất đăng nhập mà vẫn giữ CSRF, session, lỗi chung và khả năng đăng nhập hợp lệ.

**Architecture:** Dùng một limiter nhỏ trong ứng dụng single-instance, trước bước password verification của Spring Security, dựa trên `Clock` và bộ nhớ có giới hạn. Account normalization dùng lại `UserAccount.normalizeLogin`. Không tạo security service riêng hoặc Redis.

**Tech Stack:** Spring Security, Java stdlib, Clock, JUnit hiện có, Vue và API error contract hiện có.

**Spec:** [Audit F5](../../SESSION_AUDIT_2026_09_13.md), [master D5](2026-09-13-remediation-master.md), `docs/SECURITY_MODEL.md`, AGENTS mục 10. Policy sau đây là PROPOSED.

## Global Constraints

- Áp dụng Global Constraints của master; không thêm authentication flow/password reset/MFA.
- Không log password, raw login attempt payload hoặc bật lưu credentials.
- Không query xem account có tồn tại trước khi quyết định throttle.
- Không thay permission, account status, authVersion hoặc session policy để thực hiện throttle.
- IP do peer kết nối xác định; không tin `X-Forwarded-For` do client gửi khi chưa có trusted proxy config.
- Không tổ chức cuộc thử mật khẩu vào account thật; dùng tài khoản/fixture test riêng.

## 1. Policy đề xuất để duyệt tại P1

Chọn fixed window, tính **mọi request đăng nhập được chấp nhận vào password verification** thay vì chỉ đếm failure. Cách này có thể kiểm tra/ghi nhận nguyên tử trước BCrypt và không có lỗ hổng nhiều request đồng thời cùng vượt qua khi failure handler chưa chạy.

| Thuộc tính | Giá trị khởi đầu đề xuất | Lý do/giới hạn |
|---|---|---|
| Theo `(normalizedLogin, sourceAddress)` | 5 attempts / 15 phút | Không tạo global account lock dễ bị người khác khóa hộ |
| Theo sourceAddress | 30 attempts / 60 giây | Giảm password spray nhiều username cùng nguồn; cần xem xét NAT cửa hàng |
| Window start | Request đầu được ghi nhận, reset khi `now >= expiresAt` | Clock testable; request bị chặn không kéo dài thời hạn |
| Capacity bộ nhớ | 10.000 bucket tổng, tính cả source và pair | Hữu hạn; dọn expired trước thêm |
| Capacity đầy | Không evict bucket còn hiệu lực; reject request cần bucket mới, Retry-After đến thời điểm hết hạn sớm nhất | Tránh xoay username để xóa lịch sử; có thể gây từ chối rộng dưới abuse, phải test và ghi giới hạn |
| Success | Không reset source hoặc pair counter | Không dùng một tài khoản hợp lệ để xóa hạn mức tấn công |
| Lỗi | HTTP 429, code `AUTH_RATE_LIMITED`, `Retry-After` giây nguyên dương | Cùng semantics cho login tồn tại/không tồn tại |
| Restart | Counter mất sau restart | Chỉ chấp nhận single-instance MVP; không phải chống abuse distributed |

Nếu policy này không phù hợp dùng chung NAT/nhân viên, chỉnh thông số ở quyết định D5 trước test acceptance. Không thêm khóa vĩnh viễn. Không thêm global per-account lock nếu chưa đánh giá denial-of-service.

## 2. Files và contract đề xuất

**Create:**

- `backend/src/main/java/com/shoecommerce/identity/LoginRateLimiter.java` — ghi nhận atomically, cửa sổ và capacity, không biết HTTP hay repository.
- `backend/src/main/java/com/shoecommerce/identity/LoginRateLimitFilter.java` — chỉ áp dụng POST `/api/v1/auth/login`, map kết quả sang Problem Details.
- `backend/src/test/java/com/shoecommerce/identity/LoginRateLimiterTest.java`.
- `backend/src/test/java/com/shoecommerce/identity/LoginRateLimitExternalIT.java` — test HTTP/Security chain và JDBC profile đã có.
- `docs/ADR/0036-single-instance-login-throttling.md` — số dự kiến; chọn số còn trống khi thực thi.

**Modify:** `backend/src/main/java/com/shoecommerce/identity/SecurityConfiguration.java`, `backend/src/main/resources/application.yml`, `docs/SECURITY_MODEL.md`, `docs/API/README.md`, `frontend/src/api.ts`, `frontend/src/format.ts`, `frontend/src/i18n.ts`, `frontend/src/views/LoginView.vue`; test client trong `frontend/src/api.test.ts` và copy trong `frontend/src/format.test.ts`. Không tạo một màn đăng nhập thứ hai.

Contract dự kiến, tất cả kiểu ở trong `LoginRateLimiter`:

```java
public record Policy(int pairLimit, java.time.Duration pairWindow,
        int sourceLimit, java.time.Duration sourceWindow, int maxBuckets) {}
public record Decision(boolean allowed, long retryAfterSeconds) {}
public LoginRateLimiter(java.time.Clock clock, Policy policy);
public synchronized Decision acquire(String normalizedLogin, String sourceAddress);
```

`acquire` hoặc ghi nhận cả hai counters và trả allowed, hoặc không ghi nhận counter nào và trả denied. Không giữ monitor trong BCrypt/SQL. Bounded scan dọn expired chỉ thực hiện khi cần capacity; có `ponytail:` comment nếu chọn scan O(n) với trần 10.000 và nêu hướng thay khi có đo đạc.

## 3. Các task

### Task P4.1: Policy và limiter có test Clock

**Consumes:** D5 đã chốt. **Produces:** limiter và unit tests, không tạo security side effect ngoài bộ nhớ.

- [ ] Ghi ADR/spec, config keys, giới hạn input và nguồn IP; trạng thái được chấp nhận trước bước integration.
- [ ] Viết RED boundary test: 5 allowed, lần 6 denied; tại đúng thời điểm window hết hạn cho request mới.
- [ ] Viết RED source limit, pair isolation, simultaneous requests, capacity/full, request bị chặn không gia hạn window; username khác vẫn chịu source limit.
- [ ] Dùng Clock inject; không dùng sleep trong unit test. Instant/time tăng test kiểm soát được.
- [ ] Implement fixed-window bằng stdlib map có bounded capacity; một critical section bao cả kiểm tra và tăng cả hai counters.
- [ ] Validate config limit/window/capacity dương ngay startup; key login đã normalized và bounded như input contract hiện tại; không giữ raw password.
- [ ] GREEN, review trường hợp clock boundary và cleanup không loại bucket đang hiệu lực.

Unit test tối thiểu, với contract đề xuất ở trên:

```java
var policy = new LoginRateLimiter.Policy(5, Duration.ofMinutes(15),
        30, Duration.ofMinutes(1), 10_000);
var limiter = new LoginRateLimiter(Clock.fixed(Instant.EPOCH, ZoneOffset.UTC), policy);
for (int i = 0; i < 5; i++) {
    assertThat(limiter.acquire("audit.user", "127.0.0.1").allowed()).isTrue();
}
var denied = limiter.acquire("audit.user", "127.0.0.1");
assertThat(denied.allowed()).isFalse();
assertThat(denied.retryAfterSeconds()).isEqualTo(900);
```

### Task P4.2: Gắn vào Security chain và UI

**Consumes:** `acquire`, `RestSecurityErrorWriter`; **Produces:** 429 contract, không chạm business authorization.

- [ ] Viết HTTP RED gửi sai password vào fixture login; tới request vượt ngưỡng expect 429 và `Retry-After`, lần trước đó 401.
- [ ] Thêm filter sau CSRF validation và trước `UsernamePasswordAuthenticationFilter`; chỉ một registration. Nếu là bean Spring Boot Filter, disable servlet-container auto registration để không chạy hai lần.
- [ ] Chỉ xử lý đúng method/path; lấy tham số form đúng contract login hiện có, normalize bằng hàm shared. Input malformed được xử lý có giới hạn, không throw 500.
- [ ] Request allowed đi tiếp chain; denied trả Problem Details bằng writer hiện có cùng correlationId, Retry-After; không chạy BCrypt hoặc load account khi bị chặn.
- [ ] Giữ CSRF: request thiếu token vẫn bị kiểm soát, không tạo bypass authentication. Login success vẫn rotate session; logout vẫn invalidate.
- [ ] Update API/client mapping cho 429, copy VI/EN ngắn chỉ thời gian thử lại; không phân biệt “tài khoản không tồn tại”. UI không tự gửi lại sau khi đếm ngược nếu user chưa chủ động.
- [ ] Trong parser response, truyền `Retry-After` hợp lệ thành `ApiError.retryAfterSeconds?: number`; giữ constructor/callers cũ tương thích bằng thuộc tính tùy chọn. Header thiếu hoặc sai định dạng dùng thông báo chung, không hiển thị NaN hoặc tự suy đoán account state. Test 429 có/không có header và non-429 giữ nguyên.
- [ ] Test header X-Forwarded-For giả không đổi key limiter ở cấu hình không trusted proxy; test source/pair trả kết quả đồng nhất cho account có/không có.
- [ ] Chạy integration security và frontend tests; kiểm tra chỉ một lần acquire/request.
- [ ] Tách fixture load khỏi policy acceptance: suite login riêng dùng chính thresholds đã duyệt với Clock/context cô lập. Các suite commerce có nhiều lần login chung loopback phải có test-profile limits được ghi rõ hoặc reset state fixture giữa test; không tắt limiter trong ứng dụng demo/default và không lấy limits nới cho commerce làm bằng chứng throttle production.

```powershell
# backend, DB acceptance riêng đã cấu hình:
./mvnw.cmd '-Dtest=LoginRateLimiterTest' test
./mvnw.cmd -Pacceptance '-Dit.test=LoginRateLimitExternalIT,Foundation1SecurityExternalIT,VerticalSlice6ExternalIT,VerticalSlice7ExternalIT' verify
# frontend:
pnpm test
pnpm build
```

### Task P4.3: Review và giới hạn vận hành

- [ ] Review concurrent admission không vượt limits; memory luôn dưới capacity; expiry không làm overflow Retry-After hoặc trả giá trị âm.
- [ ] Kiểm chứng account hợp lệ đăng nhập sau window; một pair không khóa account từ mọi source.
- [ ] Ghi capacity-full behavior, NAT tradeoff, restart/multi-instance limit và trusted-proxy requirement trong runbook.
- [ ] Không tạo metric/audit từng request chứa login hoặc IP thô nếu chưa có policy retention. Log aggregate counters chỉ khi cần chẩn đoán, không xây observability stack mới.
- [ ] Security review xác nhận flow register/public fitting không được ngụ ý đã có rate limiting bởi task login này; nếu cần mở rộng, ghi scope riêng.

**Exit P4:** policy approved, unit + HTTP gates qua, 429/Retry-After VI/EN rõ ràng, session/CSRF/RBAC không regression. F5 chỉ đóng cho login và phạm vi được ghi; không tuyên bố production abuse protection hoàn chỉnh.
