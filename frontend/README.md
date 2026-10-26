# Dương An — Demo quản lý trung tâm can thiệp sớm

Frontend độc lập với dự án Java ở thư mục cha. React 19, TypeScript, Vite, Tailwind CSS 4, Ant Design, Lucide React, Recharts và React Router. Giao diện tiếng Việt, font Be Vietnam Pro được đóng gói tại chỗ.

## Chạy trên máy

Yêu cầu Node.js 22 và npm.

```powershell
cd frontend
npm ci
npm run dev
```

Mở http://127.0.0.1:5173. Không cần chạy Spring Boot, Docker hay database.

```powershell
npm run build
npm run preview
```

Build tạo `dist/`. Điều hướng dùng HashRouter để hoạt động trên static hosting mà không cần cấu hình rewrite.

## Trải nghiệm demo

- Chọn **Quản trị viên / Giáo viên / Phụ huynh** ở góc trên phải; không dùng mật khẩu thật.
- Quản trị viên: 8 nhóm menu chính; dashboard; thêm/sửa/xóa hồ sơ học sinh và nhân viên; hồ sơ chi tiết; tạo và phân công lớp; tạo/đổi lịch; điểm danh; tạo/duyệt/từ chối nghỉ phép; tạo khoản thu, ghi nhận thanh toán, xem biên nhận.
- Giáo viên minh họa `NV001`: học sinh và lớp được phân công, lịch dạy, điểm danh, hồ sơ cá nhân, chấm công mỗi ngày, gửi đơn nghỉ phép.
- Phụ huynh minh họa `PH001`: chọn một trong hai hồ sơ con liên kết; xem hồ sơ, lịch, điểm danh, học phí, lịch sử thanh toán và thông báo. Không có thao tác quản trị.
- Tìm kiếm không phân biệt dấu trong các bảng, lọc trạng thái, chọn số dòng và phân trang.
- **Khôi phục dữ liệu demo** ở cuối sidebar quản trị viên đặt lại toàn bộ dữ liệu sau xác nhận.
- Kế hoạch, báo cáo chuyên sâu, quản lý tài khoản thật và các nghiệp vụ phụ được ghi rõ **Chức năng sẽ phát triển ở giai đoạn 2**.

## Dữ liệu

1 trung tâm, 2 quản trị viên, 10 nhân viên, 30 học sinh, 30 tài khoản phụ huynh, 8 lớp, các buổi học ngày trong tuần của tháng hiện tại theo múi giờ Việt Nam, 30 khoản học phí và các đơn nghỉ phép mẫu. Tên/ngày sinh được tạo cho minh họa; email dùng miền `example.invalid`, số điện thoại không dùng để liên hệ. Không có ảnh người thật.

Dữ liệu lưu ở khóa LocalStorage `duongan.demo.v1`. Các vai trò trong cùng trình duyệt sử dụng chung dữ liệu; dữ liệu không đồng bộ giữa thiết bị hay người dùng. Chỉ báo tăng/giảm và đường biểu đồ năm trước là minh họa, có chú thích trên giao diện. Số tổng và lượt ghi danh được tính từ dữ liệu hiện tại. Một học sinh có thể thuộc nhiều hình thức.

## Cấu trúc mã nguồn

```text
src/
  components/  Thành phần bảng, phân trang, avatar, trạng thái
  layouts/     Sidebar, header, chọn vai trò, tìm nhanh
  pages/       Dashboard, lớp, lịch, học phí, công việc
  features/    Biểu mẫu và hồ sơ học sinh/nhân viên
  routes/      8 nhóm menu, điều hướng và kiểm tra quyền trang
  services/    Mock CRUD, kiểm tra quyền, xung đột lịch, lưu dữ liệu
  mocks/       Sinh dữ liệu mẫu
  hooks/       Trạng thái ứng dụng và thông báo kết quả
  types/       Mô hình TypeScript
  utils/       Ngày, tiền tệ, tìm kiếm tiếng Việt
tests/         Kiểm thử trình duyệt Playwright
```

## Nối Spring Boot sau này

`services/mockService.ts` là ranh giới nghiệp vụ để thay bằng REST API; `services/authorization.ts` mô phỏng quyền trang, quyền ghi và phạm vi dữ liệu theo tài khoản. Backend thật cần kiểm tra JWT, vai trò và quan hệ sở hữu trên mỗi request; cơ chế demo phía trình duyệt không thay thế bảo mật máy chủ.

Các tài nguyên gợi ý: `/api/students`, `/api/staff`, `/api/parents`, `/api/classes`, `/api/sessions`, `/api/sessions/{id}/attendance`, `/api/fees`, `/api/fees/{id}/payments`, `/api/leaves`, `/api/checkins`. Kiểm tra trùng lịch và ghi nhận thanh toán cần transaction khi có nhiều người dùng. Kế hoạch dự kiến Java 21, Spring Boot 3, PostgreSQL; dự án Java hiện có không được thay đổi bởi bản demo này.

## Kiểm thử

```powershell
npm test
npm run test:e2e
```

Vitest kiểm tra dữ liệu, phân quyền dịch vụ, phạm vi hồ sơ, quan hệ phụ huynh, xóa liên kết, sĩ số, điểm danh, nghỉ phép, thanh toán và xung đột lịch.

Playwright tự mở server localhost và dùng Chrome đã cài tại `C:/Program Files/Google/Chrome/Application/chrome.exe`. Nếu chạy trên máy khác, chỉnh `executablePath` trong `playwright.config.ts` hoặc bỏ thuộc tính đó và cài Chromium của Playwright. Ảnh desktop/mobile được ghi vào `test-results/` khi chạy.

## Triển khai

Chỉ triển khai riêng tư trên ChatGPT Sites. Không đổi sang public nếu chưa được chủ dự án xác nhận. Thư mục `dist/` không được đưa lên Git; workflow Sites đóng gói cùng bản mã nguồn đã xác minh. File `.openai/hosting.json` giữ định danh Site và cấu hình static hosting, không chứa token.
