# Compact Frontend Review Package

Gói này là bản rút gọn để các agent khác review nhanh frontend/UI/UX. Tổng cộng **49 file**.

Đây là gói review-only, không phải bản build đầy đủ: các route phụ, lockfile, product assets, backend và test không trọng tâm đã được lược bỏ có chủ đích.

## Có gì trong gói

- `frontend/src/`: app shell, router, CSS, API boundary, session/cart/checkout/i18n, các component dùng chung và các màn hình customer/staff quan trọng.
- `frontend/src/*.test.ts`: 4 test tiêu biểu cho flow, rendering, storefront và localization.
- `api-context/README.md`: bối cảnh API/frontend boundary.
- `design-system/MASTER.md`: design intent.
- `screenshots/`: 5 screenshot đại diện cho storefront desktop/mobile, product, cart mobile và POS.
- `00_README.md`–`03_FILE_MANIFEST.md`: hướng dẫn review và manifest.

## Cách dùng cho agent review

Đọc theo thứ tự: `01_PROJECT_CONTEXT.md` → `02_REVIEW_SCOPE.md` → `03_FILE_MANIFEST.md` → `frontend/src/main.ts` → `frontend/src/App.vue`/`styles.css` → state/API → views/components.

Không redesign, refactor, fix bug, hoặc suy diễn backend từ frontend. Phân loại finding thành confirmed defect, likely UX issue, visual weakness, implementation risk, optional improvement, hoặc feature request.

