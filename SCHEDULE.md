# Lịch học lưu trong MySQL

Trang lịch ngày, thời khóa biểu, điều phối lịch và điểm danh sử dụng `/api/schedule`, không lấy danh sách lớp hay lịch từ localStorage và không gọi `mockService` để lưu. Lỗi API hiển thị thông báo/thử lại; không thay bằng dữ liệu mẫu.

Danh sách lớp lấy từ `learning_class`, gồm cá nhân, nhóm và bán trú. Giáo viên đi theo phân công của lớp. Khi tạo/sửa lịch, backend kiểm tra lớp, tài khoản giáo viên, thời hạn lớp, giờ bắt đầu/kết thúc và trùng lịch lớp/giáo viên/phòng/học sinh. Lưu trong một transaction vào `class_session`, thời gian UTC; giao diện dùng giờ Việt Nam. Nhật ký ghi vào `audit_event` và hỗ trợ cả schema audit BIGINT cũ lẫn ID ngắn V16.

Danh sách điểm danh lấy từ lịch sử ghi danh có hiệu lực trong ngày của buổi học. Bản ghi `ENDED` vẫn được dùng cho buổi học trước ngày kết thúc; bản `CANCELLED` không được dùng. Điểm danh ghi thật vào `student_attendance`. Không đổi ngày, giờ hoặc giáo viên sau khi buổi học đã có điểm danh.

Quyền: ADMIN tạo/sửa lịch và điểm danh; TEACHER chỉ xem và điểm danh buổi mình được phân công; PARENT chỉ xem lịch và điểm danh của con đang liên kết, không nhận tên hay trạng thái điểm danh của các học sinh khác. Các thao tác ghi có CSRF. Phạm vi đọc từ ngày/đến ngày tối đa 63 ngày, hỗ trợ lịch ngày/tuần. Giao diện làm mới khi focus và mỗi 15 giây khi trang hiển thị.

API:

- `GET /api/schedule?from=YYYY-MM-DD&to=YYYY-MM-DD`
- `POST /api/schedule/sessions`
- `PUT /api/schedule/sessions/{id}`
- `PUT /api/schedule/sessions/{id}/attendance`

Body tạo/sửa nhận `classId`, `date`, `start`, `end`, `room` và `changeReason` tùy chọn. Không nhận giáo viên tùy ý từ client. Mỗi buổi nằm trong một ngày theo giờ Việt Nam.

Migration **V19__schedule_write_lock.sql** thêm bảng khóa kỹ thuật để các yêu cầu lưu cùng lúc không bỏ lọt lịch trùng, kể cả khi lịch chưa có buổi nào. Không tạo dữ liệu lớp, học sinh hoặc buổi học mẫu. Service đồng thời khóa dòng `center` nếu đã có để phối hợp với procedure lịch cũ; không tạo hồ sơ cơ sở giả khi bảng `center` còn trống. V19 đã được áp dụng trên MySQL cục bộ khi triển khai tính năng.

Kiểm thử (dữ liệu kiểm thử MySQL nằm trong transaction và rollback):

```powershell
$env:SCHEDULE_TEST_DB_URL='jdbc:mysql://127.0.0.1:3308/duong_an_v11?useUnicode=true&characterEncoding=utf8&serverTimezone=UTC'
mvn -q '-Duser.timezone=Asia/Ho_Chi_Minh' '-Dtest=ScheduleIntegrationTests' test
cd frontend
npx playwright test --config playwright.schedule.config.ts
npx playwright test --config playwright.parent.config.ts
npm run build
```
