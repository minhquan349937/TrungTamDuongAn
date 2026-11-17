# Số điện thoại đăng nhập và tài khoản phụ huynh

Mục **Tài khoản & phân quyền** (`/#/accounts`) lấy tài khoản thật từ MySQL, có cột số điện thoại, tìm kiếm theo số/email/tên/mã và nút **Sửa liên hệ**. Thông tin liên hệ được đồng bộ với hồ sơ nhân viên/phụ huynh liên kết.

Trang đăng nhập nhận **Số điện thoại hoặc email**. Số điện thoại chuẩn hóa bỏ khoảng trắng, dấu chấm, ngoặc và gạch ngang; `+84 912 345 678`, `84 912 345 678` và `0912345678` cùng chỉ một tài khoản. Email chuẩn hóa chữ thường. Mỗi số điện thoại/email thuộc tối đa một tài khoản; số không hợp lệ hoặc trùng được báo lỗi.

Khi thêm phụ huynh tại **Hồ sơ phụ huynh → Thêm phụ huynh**, hoặc khi nhập phụ huynh cùng hồ sơ học sinh:

- Tự tạo `app_user` role `PARENT`, liên kết bằng `guardian.user_id`.
- Tên hiển thị lấy từ họ tên phụ huynh; số điện thoại là tên đăng nhập; email không bắt buộc, nếu có có thể dùng đăng nhập.
- Mật khẩu ban đầu `123456` được lưu dưới dạng BCrypt, không lưu mật khẩu rõ.
- Hồ sơ và tài khoản nằm trong cùng transaction; lỗi không để lại tài khoản/hồ sơ tạo dở.
- Cùng phụ huynh có nhiều con được dùng chung một hồ sơ/tài khoản nếu tên và thông tin email đã cung cấp khớp. Nếu số đã thuộc người/role khác, trả 409. Việc dùng lại tài khoản không đổi mật khẩu hay trạng thái.
- Sửa hồ sơ phụ huynh đồng bộ tên/số/email với tài khoản, không đổi mật khẩu. Lưu hồ sơ phụ huynh cũ chưa có tài khoản sẽ tạo tài khoản còn thiếu.

Tài khoản giáo viên vẫn tự tạo theo luồng nhân sự hiện có; số điện thoại nhập trong hồ sơ cũng được đồng bộ để đăng nhập. Các tài khoản `LOCKED`/`DISABLED` không đăng nhập được bằng cả số điện thoại lẫn email.

## API

| Method | Đường dẫn | Chức năng |
| --- | --- | --- |
| POST | `/api/auth/login` | Body mới `{"login":"0912345678","password":"123456"}`; vẫn hỗ trợ body cũ dùng thuộc tính `email` |
| GET | `/api/auth/me` | Thông tin phiên đăng nhập, thêm `phone`, `email` có thể NULL |
| GET | `/api/admin/accounts` | Danh sách tài khoản thật, không trả password/hash |
| PUT | `/api/admin/accounts/{id}/contact` | Body `{"email":null,"phone":"0912345678"}`; đồng bộ hồ sơ liên kết |
| GET | `/api/guardians` | Danh sách phụ huynh, gồm hồ sơ chưa liên kết học sinh |
| POST | `/api/guardians` | Tạo phụ huynh và tài khoản PARENT từ tên/số/email/địa chỉ |
| PUT | `/api/guardians/{id}` | Sửa hồ sơ và thông tin đăng nhập liên kết |

Các API quản lý tài khoản/phụ huynh chỉ dành cho ADMIN. Mọi thao tác ghi yêu cầu session và CSRF. Role/mật khẩu/trạng thái không được chỉnh qua API sửa liên hệ. Các quyền xem học sinh, công bố và gửi báo cáo giữ nguyên.

## Database và triển khai

Migration `database/mysql/migrations/V18__phone_login.sql` thêm `app_user.phone` có unique index; cho phép `email` NULL với điều kiện luôn còn số điện thoại hoặc email đăng nhập. Không đổi ID, role, mật khẩu hay liên kết đang có.

Các số hợp lệ đã có trong hồ sơ **đã liên kết tài khoản** được điền vào tài khoản chỉ khi xác định duy nhất cho người dùng và không trùng tài khoản khác. Số mơ hồ/trùng/không hợp lệ được để trống để quản trị viên rà soát tại mục Tài khoản. Migration không tự tạo tài khoản hàng loạt cho hồ sơ cũ.

Database cục bộ của workspace đã được áp dụng V18 sau khi sao lưu vào `database/.backups/` (được Git bỏ qua). Khi triển khai sang database khác, dừng ghi từ ứng dụng và chạy:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File database/mysql/apply_phone_login.ps1
powershell -NoProfile -ExecutionPolicy Bypass -File database/mysql/apply_phone_login.ps1 -Apply
```

Script kiểm tra schema, tạo backup trước khi áp dụng và không chạy lại nếu V18 đã tồn tại. `compose.yaml` nạp V18 khi tạo volume database mới. Sau cập nhật cần khởi động lại backend và tải lại frontend.

Luồng sinh mã học sinh cũng được xử lý trường hợp bảng bộ đếm tồn tại nhưng thiếu bản ghi khởi tạo: tự tạo hàng bộ đếm và đối chiếu mã lớn nhất trong dữ liệu trước khi cấp mã, không thay đổi mã cũ.

## File của thay đổi

- Mới: `LoginIdentifiers.java`, `AccountContactService.java`, `AccountManagementService.java`, `AccountManagementController.java`.
- Sửa backend: `AppUser.java`, `UserProfile.java`, `AppUserPrincipal.java`, `AppUserRepository.java`, `DatabaseUserDetailsService.java`, `AuthService.java`, `AuthController.java`, `GuardianProfileService.java`, `GuardianProfileController.java`, `StudentProfileService.java`, `StaffProfileService.java`, `StudentProfileExceptionHandler.java`.
- Database: migration V18, `apply_phone_login.ps1`, `compose.yaml`.
- Mới frontend: `pages/Accounts.tsx`, `services/accountApi.ts`.
- Sửa frontend: `AuthProvider.tsx`, `LoginPage.tsx`, `Supporting.tsx`, `StudentProfiles.tsx`, `DashboardPeopleTables.tsx`, `AppLayout.tsx`, `AppRoutes.tsx`, `studentProfileApi.ts`.
- Test: `PhoneLoginIntegrationTests.java`, `LoginIdentifiersTests.java`; cập nhật test hồ sơ Student/Guardian/Staff theo việc dùng service tài khoản chung; thêm `accounts-phone.spec.ts`, `playwright.accounts.config.ts`; cập nhật helper đăng nhập trong `demo.spec.ts`.
- Tài liệu: `PHONE_LOGIN.md`. Các thay đổi báo cáo/PDF/Gemini đang có trong workspace được giữ nguyên.

## Kết quả kiểm thử 12/11/2026

- `mvnw.cmd -q package`, bật biến DB cho phone login, staff và báo cáo cá nhân: **155 test phát hiện, 85 đạt, 70 bỏ qua**, không có lỗi; JAR build thành công. Các test bỏ qua thuộc nhóm tích hợp khác có điều kiện môi trường riêng.
- Nhóm liên quan tài khoản/hồ sơ: **40/40 đạt**, trong đó có **10 test tích hợp đăng nhập số điện thoại/phụ huynh** và **3 test MySQL nhân sự**; dữ liệu giả lập được rollback.
- Bao phủ: tài khoản phụ huynh không email; đăng nhập HTTP và giữ session; email và số +84/0 trỏ cùng tài khoản; request email cũ; mật khẩu sai; quyền ADMIN/PARENT/TEACHER và CSRF; tạo cùng học sinh; dùng chung phụ huynh cho anh/chị/em; không reset mật khẩu khi sửa; số trùng role khác; đồng bộ liên hệ; số/email sai; tài khoản khóa/vô hiệu hóa; hồ sơ cũ chưa có tài khoản.
- `npm test`: **25/25 đạt**.
- `npm run build`: thành công, còn cảnh báo kích thước bundle của Vite.
- Kiểm thử trình duyệt `npx playwright test --config playwright.accounts.config.ts`: **5/5 đạt**, dùng API giả lập; kiểm tra cột số/tìm kiếm/sửa liên hệ, giữ dữ liệu khi trùng số, thêm phụ huynh không email, nhập phụ huynh cùng học sinh, đăng nhập bằng số và email.

Chạy lại test backend có MySQL:

```powershell
$env:PHONE_LOGIN_TEST_DB_URL='jdbc:mysql://127.0.0.1:3308/duong_an_v11?useUnicode=true&characterEncoding=utf8&serverTimezone=UTC'
$env:STAFF_TEST_DB_URL=$env:PHONE_LOGIN_TEST_DB_URL
$env:INDIVIDUAL_REPORT_TEST_DB_URL=$env:PHONE_LOGIN_TEST_DB_URL
.\mvnw.cmd -q package
```
