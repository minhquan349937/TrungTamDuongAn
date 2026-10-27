# Giao diện Trung tâm Dương An

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

Các màn nghiệp vụ khác vẫn sử dụng LocalStorage để chờ API tương ứng. Kho dữ liệu demo cũ trong trình duyệt được xóa một lần khi tải ứng dụng và không tự tạo lại hồ sơ giả. Màn kế hoạch can thiệp không dùng LocalStorage.

```powershell
npm test
npm run build
```

Các kiểm thử Vitest gồm hợp đồng gọi API mục tiêu và kiểm tra nghiệp vụ LocalStorage cũ. Playwright nạp tài khoản/hồ sơ mẫu trong fixture kiểm thử riêng; dữ liệu này không được tạo trong ứng dụng thực. Chạy toàn bộ kiểm thử giao diện bằng `npm run test:e2e`.
