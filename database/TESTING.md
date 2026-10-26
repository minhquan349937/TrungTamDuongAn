# Kết quả kiểm thử thiết kế database

Ngày thực hiện: **22/10/2026**.

Môi trường: PostgreSQL **18.4**, Windows x64, một PostgreSQL cluster kiểm thử riêng tại `database/.test-runtime/pgdata`, chỉ lắng nghe `127.0.0.1:55439`. Không sử dụng database MySQL của dự án hoặc database nghiệp vụ khác.

| Hạng mục | Kết quả |
| --- | --- |
| Áp dụng V1 và V2 vào database mới trong một transaction | Thành công |
| Số bảng vật lý / view công nợ | 30 / 1 |
| Assertions SQL trong `tests/constraints.sql` | **32/32 đạt** |
| Sáu truy vấn mẫu sau khi bind giá trị kiểm thử | Chạy thành công trên schema rỗng |
| Rollback fixture | Thành công; `student` còn 0 dòng |

Các kiểm tra thực hiện gồm:

- Quan hệ nhiều phụ huynh–nhiều học sinh, một liên hệ chính.
- Chỉ nhận ba role, tài khoản guardian đúng vai trò PARENT.
- Capacity=1 cho lớp cá nhân, không trùng khoảng ghi danh cùng lớp/học sinh.
- Buổi liền nhau được chấp nhận; từ chối thời gian sai, phòng khác cơ sở và trùng lớp/phòng/giáo viên/học sinh.
- Khi đổi lịch, hủy và mở lại buổi học, thời gian chiếm chỗ của danh sách giáo viên/học sinh được cập nhật hoặc kiểm tra lại.
- Không điểm danh người ngoài danh sách buổi; không gắn ghi danh sai học sinh/lớp.
- Học phí nhiều dòng và nhiều lần thu không bị nhân tổng tiền; trạng thái một phần/đã đủ; loại phiếu thu hủy khỏi công nợ; chống request thanh toán trùng khóa; giảm trừ không vượt tiền dòng.
- Không xóa vật lý học sinh đã có liên kết/lịch sử.
- Lương có các giai đoạn liền nhau nhưng không chồng lấn.
- Không chấm công hai lần/ngày; giờ ra không trước giờ vào.
- Duyệt đơn nghỉ cần người/thời gian duyệt, người gửi không tự duyệt, khoảng nghỉ đã duyệt không chồng nhau.

Phạm vi chưa kiểm thử/triển khai: ứng dụng Java/API, JWT và query scope qua API, giới hạn sĩ số trong transaction, thu vượt công nợ, stress/concurrency test, hiệu năng trên dữ liệu lớn, PostgreSQL 17. Sáu truy vấn mẫu mới được xác minh cú pháp/kiểu dữ liệu trên schema trống; không thay cho kiểm thử quyền truy cập đầu cuối.

Chi tiết kiểm tra và dữ liệu giả đều có trong `tests/constraints.sql`. Script kết thúc bằng `ROLLBACK`; không có tài khoản đăng nhập sử dụng được tạo ra.
