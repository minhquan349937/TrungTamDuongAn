# Trang giáo viên theo phân công

`GET /api/teacher/portal` xác định tài khoản bằng phiên đăng nhập, tìm `staff.user_id` và chỉ trả học sinh/lớp đang được giáo viên đó phụ trách. Không nhận mã giáo viên từ client. Tài khoản và hồ sơ nhân viên phải đang hoạt động.

Lớp phải đang hoạt động và trong khoảng ngày hiệu lực. Ghi danh phải có trạng thái `ENROLLED`, đã bắt đầu và chưa kết thúc (ngày kết thúc là mốc loại trừ). Học sinh tham gia nhiều lớp chỉ xuất hiện một lần trong danh sách. Lớp chưa có học sinh vẫn hiển thị. Lịch và điểm danh chỉ gồm buổi không bị hủy của các lớp này, có cùng giáo viên được phân công; không trả ghi chú nội bộ học sinh, lương hay thông tin phụ huynh.

Frontend dùng dữ liệu API trong bộ nhớ cho tổng quan, học sinh, lớp cá nhân/nhóm/bán trú và lịch dạy, không ghép dữ liệu demo từ localStorage. Tải lại khi focus tab và mỗi 15 giây khi trang hiển thị. API lỗi xóa dữ liệu cũ khỏi màn hình và có nút thử lại. Tài khoản chưa liên kết hồ sơ nhân viên hoạt động có thông báo và danh sách rỗng. Việc viết kế hoạch/đánh giá tiếp tục dùng các API hiện có; các thao tác còn dựa vào `mockService.run` không được ghi giả vào dữ liệu giáo viên.

Quản trị viên tạo/sửa lớp nhóm và bán trú qua `/api/admin/classes` (GET/POST/PUT/DELETE), có kiểm tra quyền ADMIN và CSRF. Dữ liệu lớp và ghi danh được lưu trong một transaction. Đổi giáo viên cập nhật danh sách phụ trách ở tài khoản tương ứng. Xóa ở giao diện đóng lớp, giữ lịch sử. Chưa có tính năng chuyển lớp sang hình thức khác.

Các lớp cũ chỉ nằm trong trình duyệt và có giáo viên/học sinh khớp hồ sơ thật được giữ ở trang quản trị với nhãn **Chỉ lưu trong trình duyệt**. Chọn **Đồng bộ lên hệ thống**, kiểm tra và lưu để chuyển lớp đó lên CSDL; chỉ bỏ bản sao cũ sau khi API lưu thành công. Không tự nhập các lớp demo không khớp hồ sơ thật.

Kiểm thử:

```powershell
$env:TEACHER_PORTAL_TEST_DB_URL='jdbc:mysql://127.0.0.1:3308/duong_an_v11?useUnicode=true&characterEncoding=utf8&serverTimezone=UTC'
mvn -q '-Dtest=TeacherPortalIntegrationTests' test
cd frontend
npx playwright test --config playwright.teacher.config.ts
npm run build
```

Kiểm thử MySQL dùng dữ liệu giả lập trong transaction và rollback. Kiểm thử giao diện giả lập API, gồm luồng quản trị tạo lớp rồi chuyển sang giáo viên để kiểm tra danh sách học sinh.
