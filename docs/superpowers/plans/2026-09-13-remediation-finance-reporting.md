# P2–P3 — Paid allocation, void và reporting Implementation Plan

> **For agentic workers:** Use `superpowers:executing-plans`; thực hiện checkbox tuần tự, RED → GREEN cho từng task. TDD, review tài chính và verification là bắt buộc trước tuyên bố hoàn tất.

**Goal:** Đưa full void về đúng paid component và báo cáo merchandise/shipping đúng, kể cả retry và dữ liệu trước nâng cấp.

**Architecture:** Order sở hữu immutable price/adjustment facts; Payment lưu reversal allocations dương theo component; Reporting chỉ đọc các fact này. Không gọi lại evaluator promotion để tính tiền quá khứ, không thêm một ledger song song.

**Tech Stack:** Java/Spring/JPA/JdbcTemplate, SQL Server/Flyway, Vue/TypeScript/Vitest hiện có.

**Spec:** [Audit F1/F2](../../SESSION_AUDIT_2026_09_13.md), [master plan D1–D3](2026-09-13-remediation-master.md). Trước code phải có ADR paid-component/reversal và REPORTING contract ở P1 được chấp nhận.

## Global Constraints

Áp dụng toàn bộ Global Constraints trong master. Giữ total payment/void, stock semantics và scope hiện có. Không sửa V14/V23/V24/V25 tại chỗ. Những kiểu/field/schema đề xuất dưới đây là thiết kế dự kiến, cần ghi vào ADR trước triển khai.

## 1. Contract nội bộ đề xuất

Một kiểu khóa component dùng chung cho kiểm tra capacity và validation kết quả:

```java
public enum PaidComponentType { ORDER_ITEM, SHIPPING }
public record PaidComponentKey(PaidComponentType type, UUID publicId) {}
public record PaidComponent(PaidComponentKey key, BigDecimal amount) {}
```

Đặt các record trong `order/OrderPaidComponents.java`, một file nhỏ; không tạo interface/factory. `ORDER_ITEM.publicId` là OrderItem public ID; `SHIPPING.publicId` là Order public ID. `OrderPaidComponents.read(UUID orderId)` trả `List<PaidComponent>` từ Order/OrderItem/immutable adjustments; chỉ component > 0 và tổng bằng Order.totalAmount. Caller thực hiện authorization và giữ Order/Payment fences như workflow hiện có. Service này không có API công khai và không tự tạo transaction tài chính riêng.

Nếu xác minh thấy module Order đã có read model đáp ứng đúng input/output, mở rộng nó thay vì tạo file mới; phải cập nhật contract trong plan/ADR trước bàn giao.

```text
paidItem(i) = grossLine(i)
              - ITEM(i)
              - ORDER_ALLOCATION(i)
              - VOUCHER_ALLOCATION(i)
paidShipping = shippingFee - shippingDiscount
sum(paidItem) + paidShipping = totalAmount
```

Trước triển khai, đọc layer thực tế V23/V24 và PromotionService.storeOrder để tránh trừ cả parent order adjustment lẫn child allocation. Thiếu/sai tổng snapshot phải fail closed bằng conflict có code đã chốt; không fallback chia gross.

## 2. Ma trận bắt buộc

Mọi số dưới đây là VND. Với “net sau full void = 0”, capture và void cùng khoảng báo cáo.

| ID | Fixture/đường xử lý | Kỳ vọng |
|---|---|---|
| M1 | A 100.000 giảm ITEM 50.000; B 100.000; Pickup | Capture 150.000; allocation A=50.000/B=100.000; full void product net=0 từng dòng |
| M2 | Một hàng 200.000, discount 50.000 | Capture/void 150.000; gross=200.000, discount=50.000, merchandise net sau void=0 |
| M3 | Hàng 100.000, ship 30.000 | ORDER_ITEM=100.000; SHIPPING=30.000; tổng 130.000; không product net âm do shipping |
| M4 | Hàng 100.000, ship 30.000 và FREE_SHIPPING 30.000 | Chỉ allocation hàng 100.000; shipping component 0 không tạo row |
| M5 | A miễn phí hoàn toàn, B trả 100.000 | Full void chỉ allocation B; không bị chặn bởi component A=0; reservation cả hai vẫn theo luồng bình thường |
| M6 | Ba dòng gross=1, order discount=2 | Allocation discount tie-break theo policy đã chốt; paid components tổng=1; full void không tạo row 0 hoặc chia lại |
| M7 | ITEM + automatic order + CODE/CLAIM voucher + shipping | Các child allocation cộng đúng parent; full void đúng tổng; voucher REDEEMED giữ nguyên sau void |
| M8 | Replay, UNKNOWN, definitive failure→retry, stale result | Không duplicate provider/movement; UNKNOWN giữ capacity; failure release đúng attempt; stale result không sửa attempt mới |
| M9 | Capture ngày 1, void ngày 2; report từng ngày và cả hai | Ngày 2 net có thể âm hợp lệ; gộp hai ngày=0; half-open ngày VN giữ nguyên |
| M10 | Cross-location/customer/cashier và legacy upgrade | 401/403/ownership như contract; legacy row bất biến; nhãn legacy không bị giả định là allocation chuẩn |

## 3. P2 — sửa reversal end-to-end

### Task P2.1: Regression đỏ và paid snapshot reader

**Files:** tạo `backend/src/main/java/com/shoecommerce/order/OrderPaidComponents.java`; tạo `backend/src/test/java/com/shoecommerce/order/OrderPaidComponentsExternalIT.java`; đọc `CustomerOrder.java`, `OrderItem.java`, `promotion/PromotionService.java` và migrations adjustment.

**Consumes:** orderId, immutable `commerce_order`, `commerce_order_item`, `commerce_order_item_adjustment`; **Produces:** read contract ở mục 1.

- [ ] Viết fixture isolated SQL cho M1/M4/M5/M6, dùng các use case hiện hữu; nếu cần seed snapshot để test integrity thì tách tên test đó khỏi E2E.
- [ ] Chạy test khi reader chưa có/đang trả gross; xác nhận thất bại do thiếu behavior, không do datasource.
- [ ] Thêm aggregate query theo từng OrderItem, trừ từng layer đúng một lần; cộng shipping net riêng; dùng BigDecimal scale 0/exact conversion.
- [ ] Validate mọi amount không âm, total khớp, component ID/type không trùng; zero component bị bỏ qua, zero **order** theo D4.
- [ ] Thêm case snapshot bị thiếu/không khớp tổng → conflict, không tự sửa dữ liệu; current promotion edit không đổi output.
- [ ] Chạy GREEN và review query cardinality; không có join làm nhân bản discount.

Assertion của test dùng trực tiếp output đã định nghĩa:

```java
var amounts = components.read(orderId).stream().collect(java.util.stream.Collectors.toMap(
        OrderPaidComponents.PaidComponent::key, OrderPaidComponents.PaidComponent::amount));
assertThat(amounts.get(new OrderPaidComponents.PaidComponentKey(
        OrderPaidComponents.PaidComponentType.ORDER_ITEM, itemAId)))
        .isEqualByComparingTo("50000");
assertThat(amounts.get(new OrderPaidComponents.PaidComponentKey(
        OrderPaidComponents.PaidComponentType.ORDER_ITEM, itemBId)))
        .isEqualByComparingTo("100000");
assertThat(amounts.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add))
        .isEqualByComparingTo("150000");
```

`components` là bean autowired; `orderId/itemAId/itemBId` là IDs thật do fixture trả về. Không hard-code production IDs hoặc test private method làm bằng chứng duy nhất.

### Task P2.2: Migration và replay tương thích lịch sử

**Files:** dự kiến `V27__paid_component_void_allocations.sql`; `VoidAllocation.java`, `VoidAllocationRepository.java`, `VoidAttempt.java`; test mới `FlywayV27UpgradeExternalIT.java`. Nếu V27 đã tồn tại, dùng số kế tiếp và đổi tên test/plan thống nhất.

**Consumes:** D1/D2, paid component key; **Produces:** lưu/retrieve component có DB integrity, nhận biết allocation version của attempt.

- [ ] Trước migration, viết fresh/upgrade test trên V26 có cả legacy ACTIVE, UNKNOWN, SUCCEEDED, RELEASED; snapshot amount/id/status/timestamps trước upgrade.
- [ ] Tái hiện giới hạn hiện tại: V14 FK `component_public_id` chỉ tới OrderItem và CHECK chỉ ORDER_ITEM; SHIPPING insert phải thất bại trên schema cũ.
- [ ] Theo D1 đã chốt, mở rộng bảng hiện có bằng shipping reference có FK thật; giữ item reference cho ORDER_ITEM. Không bỏ FK rồi chỉ tin Java.
- [ ] Hướng nhỏ đề xuất: item reference nullable + shipping-order reference nullable; CHECK đúng một reference theo type; unique attempt/item và attempt/shipping; amount > 0. ORM ánh xạ lại component key có type.
- [ ] Thêm version marker ở attempt: các row có trước migration được nhận dạng LEGACY_V1, attempt mới do ứng dụng ghi SNAPSHOT_V2. Không đổi amount/status của legacy để “sửa” lịch sử.
- [ ] `usedCapacity` lọc theo component type + identity và chỉ ACTIVE/SUCCEEDED; không dùng UUID trần làm ý nghĩa domain. Giữ Payment fence để serialize capacity.
- [ ] Với old ACTIVE/UNKNOWN: callback/replay phải dựa trên allocation/version đã lưu; không ép khớp thuật toán V2. Definitive retry dùng chính sách D2 đã chốt, không đổi version ngầm giữa các attempt.
- [ ] Kiểm tra fresh→latest và populated V26→latest, duplicate component, wrong FK, wrong reference combination, zero/negative amount; Hibernate validate.
- [ ] Review fail/rollback của migration, định nghĩa ứng dụng nào được phép chạy trên schema mới. Không coi chạy lại binary cũ là rollback an toàn sau khi đã ghi SHIPPING.

**Gate:** migration không thay financial facts cũ; negative SQL assertions thực sự fail; policy lịch sử và deployment order được ghi rõ.

### Task P2.3: Reserve → provider → apply/retry dùng đúng component

**Modify:** `payment/VoidService.java`, `VoidResultService.java`, allocation classes; `fulfillment/PickupCancellationTransactionService.java` chỉ khi truyền/read facts yêu cầu. **Tests:** `payment/VnPayPaymentExternalIT.java` và paid-components test.

- [ ] Thêm M1–M8 vào service flow bằng production cancellation + deterministic test provider; RED phải cho allocation sai hoặc lỗi schema trước khi sửa.
- [ ] Trong reserve/retry, lấy paid components từ reader sau Order/Payment fences; ghi tất cả ACTIVE allocations cùng operation/attempt trong transaction hiện có.
- [ ] Bỏ đường tính tỷ lệ gross cho SNAPSHOT_V2; giữ legacy handling chỉ theo D2/version rõ, không heuristic dựa ngày tùy ý.
- [ ] Apply xác minh đúng operation/attempt/generation, type+ID+amount, sum và snapshot authority; terminal result không bị overwrite.
- [ ] External reverse chỉ sau commit. Failure giữa allocation writes hoặc audit phải rollback cả operation/attempt/capacity/cancellation liên quan.
- [ ] Test đồng thời hai cancellation/retry, UNKNOWN giữ capacity, replay không gọi provider lại, failed release đúng một lần; inventory restoration mỗi dòng đúng một lần.
- [ ] Test order có item paid=0 nhưng total>0 hủy được và restore mọi reservation dù không có allocation tiền cho dòng 0.
- [ ] Chạy targeted SQL suites; review của người/agent có trách nhiệm tài chính trước khi chuyển P3.

```powershell
# Trong backend, datasource đã trỏ DB acceptance riêng:
./mvnw.cmd -Pacceptance '-Dit.test=OrderPaidComponentsExternalIT,FlywayV27UpgradeExternalIT,VnPayPaymentExternalIT' verify
```

Tên V27 trong lệnh phải đi cùng số migration thực đã chọn. Mong đợi: không failures/errors/skips; log còn rõ các case mới đã chạy.

## 4. P3 — reporting, contract và giao diện

### Task P3.1: Projection có breakdown và lịch sử rõ ràng

**Modify:** `reporting/ReportingService.java`, `ReportingController.java`; `payment/VerticalSlice7ReportingExternalIT.java`. **Docs:** `docs/API/REPORTING.md`, glossary.

**Consumes:** paid snapshot + successful allocation có type/version; **Produces:** DTO bổ sung được chốt D3. Giữ URL/scope/date filter hiện tại nếu không có consumer bắt buộc version mới.

Đề xuất field string-VND cho report tổng: `merchandiseGross`, `itemDiscount`, `orderDiscount`, `voucherDiscount`, `merchandiseNetBeforeReversal`, `shippingGross`, `shippingDiscount`, `shippingNetBeforeReversal`, `merchandiseVoids`, `shippingVoids`, `unallocatedLegacyVoids`, `netSales`. Product row thêm discount breakdown và `merchandiseNetBeforeReversal`; không hiển thị phí ship thành doanh thu SKU. Chỉ thêm field thật sự hiển thị/đối soát; bỏ field dư khi chốt contract, cập nhật mọi test liên quan.

- [ ] Thêm RED M2/M3/M7/M9 tại reporting service; assert snapshot arithmetic, không chỉ assert số dòng hoặc tổng capture.
- [ ] Aggregate adjustments trước join để mỗi Order/OrderItem/capture chỉ tính một lần. Không lấy current promotion/price để dựng tiền lịch sử.
- [ ] Product net = paid merchandise recognized trong kỳ − merchandise reversal recognized trong kỳ; shipping query tách riêng theo Order responsible branch + location.
- [ ] Net tổng vẫn là tiền recognized toàn đơn − successful reversals, không double count shipping; reconciliation hỗ trợ SHIPPING và legacy classification.
- [ ] Nếu legacy không có phân loại được duyệt: expose unallocated và reason; không phân vào SKU giả. Tổng cashflow vẫn đúng. Không gọi F1 lịch sử “đã sửa đầy đủ” nếu chỉ mới phân loại exception.
- [ ] Giữ ý nghĩa các field v1 cũ như D3 quyết định; không lặng lẽ đổi `grossSales` từ settled receipts thành merchandise gross.
- [ ] M9: capture ngày trước/void ngày sau giữ event-time; negative ngày hoàn có thể hợp lệ. M10: same branch/foreign location, customer/cashier deny giữ nguyên.
- [ ] Test unknown/released/retry tránh đếm exception vừa operation vừa allocation thành một số tiền hai lần; chốt granularity ở contract.

Assertion tối thiểu cho M2, sau full void cùng kỳ:

```java
var total = reports.netSales(manager, fromDate, toDate, locationId);
var products = reports.productSales(manager, fromDate, toDate, locationId);
assertThat(total.netSales()).isEqualTo("0");
assertThat(products.netSales()).isEqualTo("0");
assertThat(products.rows()).allSatisfy(row ->
        assertThat(new BigDecimal(row.netSales())).isEqualByComparingTo("0"));
```

M3 phải assert thêm shippingVoids=30.000 và merchandiseVoids=100.000 theo DTO đã chốt, không chỉ hai số net=0.

### Task P3.2: SPA và error/labels dùng server breakdown

**Modify:** `frontend/src/api.ts`, `views/ReportsView.vue`, `i18n.ts`; test `commerce-render.test.ts` và tạo `reports.test.ts` nếu cần test behavior riêng. Không đổi toàn design system.

- [ ] Viết test fixture report M2/M3: giảm giá và ship có nhãn riêng, full void về 0 merchandise; legacy/unallocated hiện explanation.
- [ ] Thêm types khớp contract; giữ money string hiện có, không đổi sang phép toán Number để cân tổng client-side.
- [ ] Render gross/discount/paid/void/net từ API; nhãn online capture không gọi nhầm thành gross merchandise.
- [ ] Khi scope/date đổi, dữ liệu cả nhóm phản ánh cùng bộ filter; stale response không ghi đè kết quả mới. Lỗi một request không trộn số liệu kỳ cũ với kỳ mới.
- [ ] Test VI/EN, loading/empty/error, 401/403 và phiên hết hạn; màn hẹp có scroll bảng nội bộ, focus/heading/labels giữ đúng.
- [ ] Chạy `pnpm test` và `pnpm build`; typecheck chạy trong build. Không thêm lint stack chỉ để có một lệnh nữa.

### Task P3.3: Chứng minh E2E và đóng F1/F2 đúng phạm vi

- [ ] Trên DB demo riêng, tạo đơn nhiều dòng có discount và delivery; trả tiền bằng demo adapter rồi hủy trước dispatch qua UI.
- [ ] Đối chiếu quote → immutable Order → PaymentAttempt → VoidAllocation → report tổng/sản phẩm/shipping; lưu IDs và số tiền, không lưu credentials.
- [ ] Refresh/replay không tạo duplicate reversal/movement; kiểm tra fulfillment terminal không còn action sai.
- [ ] Chạy lại full bộ reporting/payment/shipping/promotion regression và migration gate trước P5.
- [ ] Ghi legacy behavior chính xác và quyết định D2; chỉ kết luận đã sửa dữ liệu lịch sử nếu có giải pháp/case chứng minh, không dựa vào DB demo trống.

**Exit P2/P3:** M1–M10 có evidence phù hợp, total capture không thay, các component đúng, UI không tự tính tiền, không silent history rewrite và không có finding tài chính nghiêm trọng chưa xử lý.
