# Hồ sơ học sinh trong cổng phụ huynh

## Nguyên nhân và dữ liệu hiện có

Trước thay đổi này, hồ sơ con và bộ chọn con đọc `duongan.demo.v1` trong
localStorage; phân quyền demo dùng cố định `PH001` thay vì tài khoản đăng nhập.
Dữ liệu học sinh tạo qua API nằm trong MySQL nên không xuất hiện trong nguồn demo.
Bản sửa trước chỉ tránh lỗi truy cập phần tử phụ huynh không tồn tại; chưa thay
nguồn dữ liệu của các màn hình phụ huynh.

Kiểm tra database cục bộ ngày 13/11/2026 không phát hiện liên kết
`student_guardian` trỏ đến học sinh/phụ huynh không tồn tại. Có hồ sơ cũ không có
tài khoản và tài khoản chưa liên kết học sinh; không tìm thấy căn cứ liên hệ duy
nhất qua tên/SĐT/email để tự gán các hồ sơ này. Không thay đổi liên kết hiện có.

## Luồng mới

`GET /api/parent/portal` chỉ dành cho PARENT. Server lấy ID người dùng từ phiên
đăng nhập, kiểm tra tài khoản ACTIVE, truy vấn `guardian.user_id` rồi lấy học sinh
qua `student_guardian` còn hiệu lực. Không nhận ID phụ huynh từ frontend.

Response bao gồm:

- Thông tin liên hệ của chính phụ huynh và thông tin cơ bản của các con.
- Lớp đang tham gia và tên giáo viên phụ trách.
- Buổi học không bị hủy trong thời gian ghi danh hiện tại, thời gian UTC.
- Điểm danh của chính các con, không trả danh sách/điểm danh học sinh khác trong lớp.
- Thông báo đã công bố và đã chỉ định chính tài khoản là người nhận.

Không trả ghi chú nội bộ, mục tiêu, đánh giá, mật khẩu, lương, liên hệ của phụ
huynh khác hay thông tin nhân sự ngoài tên giáo viên liên quan.
Hồ sơ cơ bản dùng liên kết gia đình còn hiệu lực theo yêu cầu. Giữ nguyên cờ
`can_view_portal` và quy tắc công bố nhận xét hiện có; không mở quyền xem báo cáo
chưa công bố. Không thay đổi `goal_evaluations` hoặc trạng thái kế hoạch.

`linkStatus` phân biệt `READY`, `NO_GUARDIAN_PROFILE`, `NO_LINKED_STUDENT`.
Phụ huynh chưa liên kết nhận danh sách rỗng và thông báo chưa có hồ sơ con.
Liên kết bị thu hồi hoặc tài khoản bị khóa sẽ không tiếp tục đọc được dữ liệu.

Frontend sử dụng nguồn dữ liệu riêng trong bộ nhớ cho PARENT, không đọc, nhập
hoặc ghi đè dữ liệu demo. Các màn hình hồ sơ, tổng quan, lớp bán trú, lịch học,
điểm danh và thông báo dùng cùng nguồn có phạm vi gia đình. Bộ chọn con dùng ID
thật, giữ lựa chọn khi tự tải lại và xóa trạng thái khi đổi tài khoản.
Dữ liệu tự tải lại mỗi 15 giây khi tab hiện, khi trở lại tab/cửa sổ.
Thời gian buổi học chuyển sang Asia/Ho_Chi_Minh; giữ trạng thái Đi muộn.
Học phí vẫn sử dụng các API riêng đã lưu MySQL.

Nếu máy chủ lỗi, hiển thị Thử lại/Đăng xuất; không lấy dữ liệu demo hoặc giữ
hiển thị hồ sơ cũ để thay thế. Không cần migration hoặc bảng mới.

## File và kiểm thử

- Backend: `ParentPortalService.java`, `ParentPortalController.java`, `SecurityConfig.java`.
- Frontend: `parentPortalApi.ts`, `useDemo.tsx`, `authorization.ts`, kiểu dữ liệu
  Session và các phần hiển thị trong PeopleTables, Schedule, Dashboard, Classes.
- MySQL: `ParentPortalIntegrationTests` (8 trường hợp), bật bằng
  `PARENT_PORTAL_TEST_DB_URL`. Dữ liệu giả lập trong giao dịch rollback. Kiểm tra
  phạm vi gia đình, nhiều con/phụ huynh không chính, thu hồi liên kết, khóa tài
  khoản, chưa liên kết, thời gian ghi danh, buổi hủy, thông báo riêng, HTTP/quyền
  và tham số giả mạo.
- UI: `npx playwright test --config playwright.parent.config.ts` (14 trường hợp
  gồm phụ huynh, đăng nhập/tài khoản và học phí). Kiểm tra nguồn MySQL giả lập
  thay demo PH001, chọn con, hồ sơ, tải lại, lịch/điểm danh, lỗi/retry và đổi tài khoản.
- Unit: `parentPortalApi.test.ts` kiểm tra UTC+7, ID thật và buổi học trước thời
  điểm ghi danh của một trong hai con.
- Đã kiểm tra thêm trên backend/Vite cục bộ bằng tài khoản và hồ sơ giả lập:
  tạo học sinh, đăng nhập phụ huynh bằng SĐT, API chỉ trả đúng một con, mở hồ sơ
  và tải lại thành công; đã xóa các bản ghi giả lập sau khi thử.
