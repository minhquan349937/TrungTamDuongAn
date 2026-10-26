# Trung tâm Dương An

Dự án quản lý trung tâm can thiệp sớm, gồm frontend demo tiếng Việt, thiết kế database PostgreSQL và bộ khung backend Java.

## Thành phần

| Thư mục | Nội dung |
| --- | --- |
| `frontend/` | React, TypeScript, Vite, Tailwind CSS, Ant Design, Recharts; ba vai trò ADMIN, TEACHER, PARENT |
| `database/` | Thiết kế ERD, 30 bảng PostgreSQL, migrations và kiểm thử ràng buộc |
| `src/` | Bộ khung Spring Boot, Java 21 |

Frontend hiện dùng dữ liệu giả lưu trong LocalStorage, chưa nối API/database. Backend hiện còn cấu hình MySQL ban đầu; thiết kế PostgreSQL được chuẩn bị riêng để tích hợp tiếp.

## Chạy frontend

Yêu cầu Node.js 22 và npm.

```shell
cd frontend
npm ci
npm run dev
```

Mở địa chỉ localhost mà Vite hiển thị. Chọn vai trò ở góc trên phải để thử giao diện quản trị viên, giáo viên và phụ huynh. Không cần chạy backend để dùng bản demo.

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
- [Thiết kế database và sơ đồ ERD](database/DESIGN.md)
- [Kết quả kiểm thử database](database/TESTING.md)

Các tính năng kế hoạch can thiệp và báo cáo chuyên sâu thuộc giai đoạn 2. Tất cả hồ sơ trong bản demo là dữ liệu giả. Cấu hình MySQL trong `compose.yaml` là cấu hình phát triển mẫu.
