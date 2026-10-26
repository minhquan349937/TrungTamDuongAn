# Kết quả kiểm thử — 15/10/2026

Đã chạy thực tế trên Windows, Node.js 22.14.0 và Chrome headless.

| Kiểm tra | Kết quả |
| --- | --- |
| `npm run build` — TypeScript và Vite production | Đạt |
| `npm test` — Vitest | 9/9 đạt |
| `npm run test:e2e` — Playwright | 6/6 đạt |

Các kịch bản trình duyệt đã thực hiện:

1. Dashboard, đúng 8 nhóm menu, mở/thu sidebar, tìm kiếm không dấu, trạng thái, phân trang, trạng thái không có kết quả, màn hình 1440×1100 và 390×844, không tràn ngang trang.
2. Thêm học sinh, tải lại kiểm tra LocalStorage, sửa tên, xem liên kết phụ huynh, xác nhận xóa.
3. Thêm nhân viên, tạo lớp nhóm, chọn học sinh và xem chi tiết phân lớp.
4. Tạo lịch, chặn trùng lịch, đổi giờ, lưu điểm danh; giáo viên gửi đơn, Admin duyệt; ghi nhận thanh toán học phí.
5. Dashboard giáo viên, chấm công, chặn vào học phí; dashboard phụ huynh, chọn hai hồ sơ con, ẩn quyền thêm học sinh, chặn vào nhân sự.
6. Tất cả đường dẫn trong 8 nhóm menu Admin, hai biểu đồ có dữ liệu hiển thị, tạo khoản thu, xác nhận khôi phục dữ liệu.

Kiểm thử dịch vụ bao gồm phạm vi dữ liệu, kiểm tra quyền ghi, liên kết phụ huynh, xóa dữ liệu liên quan, sức chứa lớp và xung đột giáo viên/phòng/lớp/học sinh. Các kịch bản trình duyệt bắt lỗi JavaScript phát sinh.

Ảnh chụp từ bộ kiểm thử nằm ở `test-results/dashboard-desktop.png` và `test-results/dashboard-mobile.png` (không đưa vào kho mã nguồn).

Phạm vi xác minh: frontend và dịch vụ giả lập. Chưa kết nối hoặc kiểm thử backend Java/database/API thật. Các màn hình đánh dấu giai đoạn 2 chưa triển khai nghiệp vụ.
