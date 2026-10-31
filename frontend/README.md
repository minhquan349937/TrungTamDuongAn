# Giao diện Trung tâm Dương An

Nhận xét kế hoạch hằng tháng (V12): giáo viên/ADMIN viết bản nháp và công bố ngay trong chi tiết kế hoạch; phụ huynh xem báo cáo đã công bố tại `/#/reports`. Xem [thiết kế và API V12](../database/mysql/PLAN_EVALUATIONS_V12.md).

Tab **Đánh giá kế hoạch** trong hồ sơ học sinh cho chọn kế hoạch hiện tại hoặc kế hoạch cũ, xem phần trăm và đánh giá từng mục tiêu. Nút đánh giá chung ở cuối mở form thông tin kế hoạch/học sinh/người đánh giá và nhận xét. Hai tab kế hoạch hiện tại/lịch sử chỉ dùng để xem và quản lý kế hoạch.

Ngân hàng mục tiêu và kế hoạch hiện dùng [schema/API V8 N–N](../database/mysql/GOAL_BANK_V8.md). `/#/library` quản lý thư viện thật; `/#/individual/plan` tạo kế hoạch, sửa kế hoạch và ghi nhận tiến độ. Database Docker đã reset dữ liệu nghiệp vụ, giữ 3 tài khoản đăng nhập; cần nhập lại hai bộ SQL V8 và tạo hồ sơ thật trước khi có dữ liệu hiển thị.

Frontend dùng React, TypeScript, Vite, Ant Design và font Be Vietnam Pro. Cần Node.js 22, MySQL Docker và Spring Boot đang chạy để đăng nhập.

```powershell
cd frontend
npm ci
npm run dev
```

Mở địa chỉ do Vite in ra. Đăng nhập được xác thực bằng session Spring Boot/MySQL; quyền ADMIN, TEACHER, PARENT được lấy từ tài khoản trên máy chủ. Không còn tài khoản hoặc hồ sơ demo được tự tạo. Xem [README gốc](../README.md) để tạo quản trị viên đầu tiên.

Màn **Kế hoạch can thiệp** (`/#/individual/plan`) gồm 5 trang dùng API thật: danh sách học sinh có tuổi/thống kê → hồ sơ với 5 tab → chọn chương trình → chọn tuổi riêng trong từng lĩnh vực và nhiều mục tiêu → xem lại theo mục lục/hoàn thành. Khi hoàn thành, backend lưu `learning_plans` và toàn bộ `plan_goals` trong một transaction. Lựa chọn được giữ khi đổi tuổi/lĩnh vực và khi quay lại từ trang kiểm tra. Denver chọn chính, VB mở con khi cần; VB không có con cho chọn chính. Xem [luồng 5 trang và API](../database/mysql/INTERVENTION_WORKFLOW.md).

Không cần tạo kế hoạch rỗng trước khi chọn mục tiêu. Chỉ ADMIN được quản lý kế hoạch trong phạm vi hiện tại. Complete gửi cặp `goalId/specificGoalId`, metadata, `requestId` và `reviewToken`; gửi lại cùng lượt không tạo trùng. Ngân hàng V7 đã có vẫn được đọc. Tải lại trang giữa lượt chưa lưu sẽ quay về chọn chương trình; kế hoạch đã lưu được đọc lại từ MySQL.

Màn hồ sơ học sinh tạo, xem và sửa học sinh cùng phụ huynh chính qua MySQL. Khi tạo, backend tự cấp mã HS001… và trạng thái Đang học; không cần nhập hai trường này. Màn hồ sơ phụ huynh đọc danh sách phụ huynh chính từ API và cho sửa thông tin liên hệ. Giáo viên được phân công sau qua lớp hoặc buổi học. Các màn nghiệp vụ khác vẫn sử dụng LocalStorage để chờ API tương ứng. Kho dữ liệu demo cũ trong trình duyệt được xóa một lần khi tải ứng dụng và không tự tạo lại hồ sơ giả.

```powershell
npm test
npm run build
```

Các kiểm thử Vitest gồm hợp đồng gọi API mục tiêu và kiểm tra nghiệp vụ LocalStorage cũ. Playwright nạp tài khoản/hồ sơ mẫu trong fixture kiểm thử riêng; dữ liệu này không được tạo trong ứng dụng thực. Chạy toàn bộ kiểm thử giao diện bằng `npm run test:e2e`.

Luồng kế hoạch và đánh giá theo phần trăm (V11): [Tài liệu và kết quả chuyển đổi](../database/mysql/PLAN_EVALUATIONS_V11.md).
