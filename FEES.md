# Học phí lưu MySQL và hiển thị cho phụ huynh

Màn hình `/fees` và `/payments` dùng dữ liệu từ MySQL thay cho localStorage.
Không cần tạo bảng hoặc chạy migration mới: sử dụng `invoice`, `invoice_line`,
`payment`, view `invoice_balance` và liên kết `student_guardian` hiện có.

## API

- `GET /api/fees`: ADMIN xem các khoản thu đã phát hành; PARENT chỉ xem khoản thu
  của học sinh có liên kết gia đình còn hiệu lực với chính tài khoản đăng nhập.
- `GET /api/fees/students`: danh sách học sinh theo cùng phạm vi quyền, bao gồm
  học sinh chưa có khoản thu.
- `POST /api/fees`: ADMIN tạo hóa đơn ISSUED và một dòng khoản thu trong cùng giao dịch.
  Body: `{ "studentId": "...", "title": "Học phí", "month": "2026-11", "amount": 2500000 }`.
  `dueOn` tùy chọn; mặc định cuối tháng hoặc ngày tạo nếu tháng học phí đã qua.
- `POST /api/fees/{id}/payment`: ADMIN ghi nhận tiền mặt cho số tiền còn thiếu.
  Khóa hóa đơn trước khi tính số dư, gọi lại sau khi thu đủ không tạo thanh toán trùng.

Các thao tác ghi yêu cầu CSRF. TEACHER không có quyền truy cập API học phí.
Server lấy người tạo/người thu từ phiên đăng nhập; không nhận từ frontend.
Không hiển thị hóa đơn DRAFT/VOID. Hỗ trợ đọc số dư của hóa đơn thanh toán một phần.

## Quyền phụ huynh

Phạm vi tài chính dựa trên `guardian.user_id`, `student_guardian.student_id`,
`revoked_at IS NULL` và tài khoản PARENT còn ACTIVE. Phụ huynh có nhiều con
xem được khoản thu của các con có liên kết; chưa có liên kết thì nhận danh sách rỗng.
Quyền xem khoản thu được triển khai riêng theo yêu cầu, không phụ thuộc cờ
`can_view_portal` đang dùng cho báo cáo học tập. Không cập nhật cờ này, không
công bố hoặc thay đổi quyền xem nhận xét kế hoạch.

Trang học phí tự tải lại mỗi 15 giây khi đang hiển thị, khi quay lại tab/cửa sổ,
hoặc khi nhấn Cập nhật. Trang tổng quan phụ huynh cũng lấy tổng khoản chưa thanh
toán từ API học phí theo cùng chu kỳ. Không gửi email/thông báo tự động.
Khoản thu demo đã lưu trong localStorage không được tự nhập vào database.

## Kiểm thử

- `FeeIntegrationTests`: MySQL thật, dữ liệu giả lập, giao dịch rollback;
  bật bằng `FEE_TEST_DB_URL` giống URL MySQL cục bộ. Kiểm tra lưu hóa đơn/dòng thu,
  phân quyền HTTP/CSRF, phụ huynh khác, nhiều con, thu hồi liên kết, tài khoản
  vô hiệu, chưa liên kết, dữ liệu sai, DRAFT/VOID, thanh toán một phần và chống thu trùng.
- `cd frontend; npx playwright test --config playwright.fees.config.ts`:
  dùng API giả lập, hai phiên ADMIN/PARENT độc lập; kiểm tra tạo khoản thu,
  tải lại trang, tự cập nhật, thanh toán và giữ form khi lưu lỗi.
- `npm run build` kiểm tra TypeScript và build frontend.

File triển khai: `FeeController.java`, `FeeService.java`, `SecurityConfig.java`,
`frontend/src/services/feeApi.ts`, `frontend/src/pages/Fees.tsx`,
`frontend/src/pages/Dashboard.tsx`.
