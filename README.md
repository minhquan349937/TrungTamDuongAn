# Trung tâm Dương An

Dự án quản lý trung tâm can thiệp sớm, gồm frontend tiếng Việt, database MySQL theo bản chỉnh sửa 1.1 và backend Spring Boot.

## Thành phần

| Thư mục | Nội dung |
| --- | --- |
| `frontend/` | React, TypeScript, Vite, Tailwind CSS, Ant Design, Recharts; ba vai trò ADMIN, TEACHER, PARENT |
| `database/` | MySQL, migrations V1–V7, snapshot mục tiêu và kiểm thử; MySQL Docker ở cổng 3308; giữ bản PostgreSQL 1.0 để tham khảo |
| `src/` | Spring Boot, Java 21; đăng nhập, phân quyền và API ngân hàng mục tiêu |

Đăng nhập lấy tài khoản và vai trò từ MySQL qua Spring Boot. Màn kế hoạch can thiệp gọi API thật; các màn nghiệp vụ còn lại đang dùng LocalStorage rỗng ban đầu.

## Chạy backend và đăng nhập

Khởi động MySQL Docker rồi chạy Spring Boot và frontend:

```powershell
docker compose up -d
mvn spring-boot:run
```

Ở terminal khác:

```powershell
cd frontend
npm ci
npm run dev
```

Database hiện không có tài khoản demo. Để tạo quản trị viên đầu tiên, thêm `SETUP_ADMIN_EMAIL` và `SETUP_ADMIN_PASSWORD` do bạn tự chọn vào `.env`, khởi động backend một lần, sau đó xóa hai dòng này khỏi `.env`. Mật khẩu được băm bằng BCrypt và tài khoản được tạo nếu email chưa tồn tại. Không bật lại bootstrap tài khoản mẫu.

Backend dùng session cookie và CSRF. Catalog theo [thiết kế V6](database/mysql/GOAL_BANK_V6.md) dùng ID ngắn; [V7 ngân hàng theo học sinh](database/mysql/GOAL_BANK_FLOW.md) bổ sung chọn nhiều mục tiêu, xem lại và hoàn tất. Denver chọn chính, VB chọn con hoặc chính khi không có con. Ngày 27/10/2026 đã nâng cấp `duong_an_v11` lên V7 trên MySQL local 3306 và Docker 3308, có full backup và kiểm tra dữ liệu sau nâng cấp. Backend mặc định dùng Docker 3308; hai database độc lập. Các màn nghiệp vụ khác ngoài kế hoạch can thiệp vẫn còn dùng LocalStorage cho tới khi có API tương ứng.
## Chạy MySQL bằng Docker

Docker Desktop cần mở. Từ PowerShell tại thư mục dự án:

```powershell
Copy-Item .env.example .env
docker compose up -d
docker compose ps
```

Database `duong_an_v11` nạp V1–V7 khi khởi tạo volume mới và lưu bền vững trong volume Docker `duong-an-mysql-data`; volume hiện hữu không tự nâng cấp. Kết nối Workbench: `127.0.0.1:3308`, user `root`, mật khẩu ở `.env`. Hướng dẫn và lệnh truy vấn tại [database/mysql/README.md](database/mysql/README.md). Không dùng `docker compose down -v` nếu cần giữ dữ liệu.

## Chạy frontend

Yêu cầu Node.js 22 và npm.

```shell
cd frontend
npm ci
npm run dev
```

Mở địa chỉ localhost mà Vite hiển thị. Backend phải chạy để đăng nhập và dùng màn kế hoạch can thiệp.

```shell
npm run build
npm test
npm run test:e2e
```

Playwright hiện cấu hình Chrome trên Windows; xem hướng dẫn frontend để thay đường dẫn trình duyệt khi chạy trên máy khác.

## Tài liệu

- [Hướng dẫn frontend](frontend/README.md)
- [Kết quả kiểm thử frontend](frontend/TESTING.md)
- [Hướng dẫn database](database/README.md)
- [Thiết kế database 1.1 và sơ đồ ERD](database/DESIGN_v1.1_chinh_sua.md)
- [Hướng dẫn triển khai MySQL 1.1](database/mysql/README.md)
- [Kết quả kiểm thử MySQL](database/mysql/TESTING.md)

Kế hoạch can thiệp dùng API Spring Boot/MySQL với [luồng 5 trang](database/mysql/INTERVENTION_WORKFLOW.md). Hoàn thành lưu `learning_plans` và `plan_goals` cùng transaction; ngân hàng riêng V7 vẫn giữ dữ liệu đã có. Dữ liệu Denver/VB do người dùng nhập được đọc trực tiếp từ Docker.
