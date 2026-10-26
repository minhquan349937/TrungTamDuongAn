# Database Dương An

Thiết kế **PostgreSQL 17/18** cho hệ thống quản lý trung tâm can thiệp sớm, phục vụ một trung tâm với nhiều cơ sở, ba vai trò ADMIN / TEACHER / PARENT.

- [Thiết kế chi tiết và sơ đồ ERD](DESIGN.md)
- [V1: 30 bảng, khóa ngoại, chỉ mục và view công nợ](migrations/V1__core_schema.sql)
- [V2: đồng bộ thời gian chống trùng lịch và updated_at](migrations/V2__schedule_integrity.sql)
- [Kiểm thử ràng buộc SQL](tests/constraints.sql)
- [Truy vấn mẫu cho backend](queries.sql)
- [Kết quả kiểm thử PostgreSQL thực tế](TESTING.md) — 32/32 kiểm tra đạt trên PostgreSQL 18.4.

## Những phần đã thiết kế

Tài khoản, cơ sở/phòng, học sinh, phụ huynh nhiều–nhiều, nhân viên/chứng chỉ/lương, lịch sử phụ trách, lớp/ghi danh/phân công, lịch từng buổi, danh sách từng buổi, điểm danh, chấm công, nghỉ phép, học phí nhiều dòng/thanh toán nhiều lần, thông báo/người nhận, tài liệu, ngày nghỉ, chuyển cơ sở, sinh hoạt bán trú và audit.

Tài liệu thiết kế chỉ rõ ràng buộc DB đã thực hiện và nghiệp vụ service còn cần triển khai. Kế hoạch/báo cáo chuyên sâu và các chức năng giai đoạn 2 chưa có bảng thực thi trong migration.

## Khởi tạo trên database mới

Chạy **một lần trên database rỗng**, theo thứ tự V1 → V2. Không trỏ vào database demo hoặc production đang có dữ liệu. Cần quyền tạo schema và cài extension `btree_gist`. PostgreSQL dùng `psql` trong PATH hoặc gọi đầy đủ đường dẫn tới `bin/psql.exe`.

Ví dụ sau yêu cầu database `duong_an_dev` đã được tạo và người dùng `duong_an_owner` đã có quyền quản lý database đó; `-W` hỏi mật khẩu tương tác, không ghi mật khẩu vào file.

```powershell
psql -X -h localhost -p 5432 -U duong_an_owner -d duong_an_dev -W -v ON_ERROR_STOP=1 -1 -f database/migrations/V1__core_schema.sql -f database/migrations/V2__schedule_integrity.sql
```

`-1` chạy cả hai file trong một transaction: có lỗi sẽ rollback toàn bộ. Không chạy lại V1 trên database đã có schema; khi thay đổi, tạo migration V3 trở đi. Không có tài khoản/mật khẩu hoặc dữ liệu trẻ thật được tạo bởi migrations.

## Tích hợp Spring Boot sau này

Cấu hình dự án hiện có vẫn là MySQL; bộ thiết kế này chưa thay đổi `pom.xml`, `compose.yaml`, `application.properties` hoặc dịch vụ mock của frontend.

Khi triển khai PostgreSQL:

1. Thay driver MySQL bằng PostgreSQL; thêm Spring Data JPA và Flyway cùng module PostgreSQL phù hợp phiên bản Spring Boot đang dùng.
2. Chép migrations vào `src/main/resources/db/migration/`. Chọn đúng một cách quản lý migration; nếu đã áp dụng thủ công thì xử lý baseline Flyway theo hiện trạng, không chạy V1 lần nữa.
3. JDBC URL dùng `jdbc:postgresql://localhost:5432/duong_an_dev?currentSchema=duong_an,public`; tài khoản migration và tài khoản chạy API tách biệt.
4. Đặt Hibernate `ddl-auto=validate`, schema mặc định `duong_an`; không để Hibernate tự tạo lại các bảng có trigger/exclusion.
5. Triển khai transaction, quyền truy cập và các quy tắc service ở mục 6–9 của `DESIGN.md`.

## Kiểm thử SQL

Chạy **chỉ trên database kiểm thử riêng**, sau hai migrations, khi chưa có dữ liệu nghiệp vụ:

```powershell
psql -X -h localhost -p 5432 -U duong_an_test_owner -d duong_an_test -W -v ON_ERROR_STOP=1 -f database/tests/constraints.sql
```

Script tạo fixture giả, kiểm tra bằng assertions và `ROLLBACK` toàn bộ fixture ở cuối. Dùng kiểm tra SQLSTATE để phân biệt lỗi đúng loại với lỗi cú pháp. Kiểm tra điều kiện cần thất bại sẽ bị đánh dấu FAIL nếu câu SQL bất ngờ thành công.

Bộ này kiểm tra ràng buộc/schema, không kiểm thử API, phân quyền Spring Security hay transaction service chưa được viết. Exclusion constraints đã được kiểm tra bằng các thao tác SQL; chưa thực hiện stress test tải đồng thời.

Các file runtime của PostgreSQL kiểm thử được bỏ qua bằng `database/.gitignore`; không đưa data directory, log, token hoặc mật khẩu lên Git.
