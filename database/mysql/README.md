# MySQL — thiết kế Dương An 1.1

**V12 hiện tại:** [Nhận xét kế hoạch hằng tháng](PLAN_EVALUATIONS_V12.md). `plan_evaluations` cho phép giáo viên công bố nhận xét nguyên bản trong cổng phụ huynh. Chạy `apply_plan_evaluations.ps1` để kiểm tra schema hoặc `-Apply` để sao lưu và nâng cấp.

**V13:** thay mục tiêu trong một kế hoạch qua `supersedes_id`, giữ bản ghi và lịch sử đánh giá cũ. Chạy `apply_plan_goal_replacement.ps1` sau V12.

**Hiện tại: [ngân hàng mục tiêu V8 — N–N](GOAL_BANK_V8.md)** đã áp dụng trực tiếp trên Docker `127.0.0.1:3308`, database `duong_an_v11` ngày 27/10/2026. Dữ liệu nghiệp vụ đã reset; giữ 3 tài khoản đăng nhập cùng mật khẩu BCrypt. Denver/VB mới đã chuẩn bị trong `imports/v8/`, chưa nhập lại vào dữ liệu sử dụng. Không chạy lại INSERT 1–N cũ.

V8 thêm `program_level_domains`; lĩnh vực thuộc chương trình và dùng chung giữa các cấp độ, mục tiêu thuộc cặp cấp độ–lĩnh vực. Backend có Entity/Repository JPA, giao diện thư viện và kế hoạch dùng API theo LD. Xem báo cáo V8 để biết cấu trúc, file thay đổi, kiểm thử và cách nhập dữ liệu.

Chạy kiểm thử V8 khi database development vẫn rỗng: `powershell -ExecutionPolicy Bypass -File database/mysql/test_goal_bank_v8.ps1 -DevelopmentEmptyDatabase`. Không tạo hoặc xóa database khác.

## Tài liệu V1–V7 bên dưới được giữ làm lịch sử

**Luồng ứng dụng mới:** [Kế hoạch can thiệp — 5 trang](INTERVENTION_WORKFLOW.md), hoàn thành lưu `learning_plans` + `plan_goals` trong một transaction; không cần migration thêm sau V7.

**Thiết kế hiện tại: [kế hoạch và đánh giá V11](PLAN_EVALUATIONS_V11.md)** — học sinh → kế hoạch → nhiều mục tiêu → nhiều lần đánh giá theo phần trăm. `goal_evaluations` thay `goal_criteria`/`progress_records`; bỏ ngân hàng cá nhân `student_goal_bank`. Catalog Denver/VB giữ nguyên. Dùng `apply_goal_evaluations.ps1` để kiểm tra và `-Apply` để backup/chuyển đổi; không chạy lại V7 trên V11. Các mô tả schema cũ bên dưới là tài liệu lịch sử trước V11.


**Lịch sử V5:** xem [thiết kế trung gian](GOAL_BANK_V5.md). V5 thêm `program_levels`, chuyển cấu trúc Denver/VB về `programs → program_levels → domains → goals → specific_goals`. Các con số 29 bảng và mô tả V4 bên dưới là trạng thái trước V5; local và Docker hiện đã ở V6.

Triển khai [bản điều chỉnh 1.1](../DESIGN_v1.1_chinh_sua.md) thành **29 bảng InnoDB + view `invoice_balance`** sau migration V4, cho **một trung tâm**, ba vai trò `ADMIN`, `TEACHER`, `PARENT`. Theo yêu cầu triển khai mới, sử dụng **MySQL 8.4 trở lên** thay cho định hướng PostgreSQL trong tài liệu gốc.

Đã xác nhận migration V1–V4 trên Docker MySQL **8.4.13** (29 bảng, 1 view); bộ kiểm thử ràng buộc 47 assertions đã chạy riêng trên MySQL **9.1.0**. Đây là schema mới cho **database rỗng**, không phải script chuyển dữ liệu từ PostgreSQL hay nâng cấp database đang dùng V1 cũ. Các file PostgreSQL ở `database/migrations/` được giữ để tham khảo; không chạy lẫn hai bộ migration.

## Các file

| File | Nội dung |
| --- | --- |
| [V1__design_v1_1.sql](migrations/V1__design_v1_1.sql) | Toàn bộ cột, kiểu dữ liệu, PK/FK, CHECK, UNIQUE, chỉ mục và view công nợ |
| [V2__business_guards.sql](migrations/V2__business_guards.sql) | Chụp/khóa snapshot; procedure lưu buổi học và lịch sử lương |
| [V3__seed_center.sql](migrations/V3__seed_center.sql) | Tạo bản ghi trung tâm ban đầu |
| [V4__programs_and_domains.sql](migrations/V4__programs_and_domains.sql) | Tạo chương trình can thiệp và liên kết lĩnh vực với chương trình |
| [V5__goal_bank_hierarchy.sql](migrations/V5__goal_bank_hierarchy.sql) | Đổi cấu trúc ngân hàng mục tiêu và snapshot giao mục tiêu; có guard từ chối dữ liệu cũ |
| [V6__catalog_short_ids.sql](migrations/V6__catalog_short_ids.sql) | Ánh xạ ID ngắn, FK cha/con, bộ đếm ID và snapshot v3 |
| [V7__student_goal_bank.sql](migrations/V7__student_goal_bank.sql) | Ngân hàng riêng từng học sinh, snapshot và khóa chống trùng |
| [V9__student_profile_codes_and_relationships.sql](migrations/V9__student_profile_codes_and_relationships.sql) | Bộ đếm mã HS001… và quan hệ Ông/Bà; áp dụng sau V8 trên database hiện hữu |
| [V10__staff_guardian_codes.sql](migrations/V10__staff_guardian_codes.sql) | Khởi tạo bộ đếm tuần tự cho NV và PH từ các mã số hiện có |
| [GOAL_BANK_FLOW.md](GOAL_BANK_FLOW.md) | Luồng chọn Denver/VB, API preview/complete và kết quả triển khai V7 |
| [apply_student_goal_bank.ps1](apply_student_goal_bank.ps1) | Kiểm tra, backup và áp dụng V7 lên Docker V6 hiện hữu |
| [GOAL_BANK_V6.md](GOAL_BANK_V6.md) | Báo cáo hiện tại: bảng trước/sau, ERD, API, file thay đổi, backup và khôi phục |
| [GOAL_BANK_V5.md](GOAL_BANK_V5.md) | ERD, tác động, API, cách kiểm thử và giới hạn import |
| [test_goal_bank.ps1](test_goal_bank.ps1) | Kiểm thử V1–V7 hoặc backup V4 rồi nâng cấp trên database tạm |
| [queries.sql](queries.sql) | Truy vấn mẫu: danh sách lớp, phụ huynh, mục tiêu, tiến độ, công nợ |
| [constraints.sql](tests/constraints.sql) | 47 kiểm tra với dữ liệu giả trên database kiểm thử |
| [concurrency.ps1](tests/concurrency.ps1) | Kiểm tra hai kết nối cùng xếp lịch chồng giờ |
| [TESTING.md](TESTING.md) | Môi trường, kết quả và giới hạn kiểm thử |

## Khởi tạo

### Chạy trọn bộ MySQL bằng Docker

Yêu cầu Docker Desktop đang chạy. Từ thư mục gốc dự án, chạy PowerShell:

```powershell
Copy-Item .env.example .env
docker compose up -d
docker compose ps
```

Compose tải và chạy MySQL **8.4.13**, tự nạp V1–V7 khi khởi tạo volume trống lần đầu, rồi lưu dữ liệu trong volume `duong-an-mysql-data`. V3 thêm `center.id=1`; V4 tạo `programs`; V5 cấu trúc lại ngân hàng mục tiêu; V6 chuyển ID ngắn và FK cha/con; V7 tạo ngân hàng riêng của học sinh. Volume đã có từ trước không tự chạy migration mới.

Khi healthcheck hiển thị `healthy`, kết nối MySQL Workbench/ứng dụng bằng:

| Thiết lập | Giá trị mặc định |
| --- | --- |
| Host | `127.0.0.1` |
| Port | `3308` |
| Schema | `duong_an_v11` |
| Username | `root` |
| Password | `MYSQL_ROOT_PASSWORD` trong file `.env` |

Kiểm tra bảng và dữ liệu:

```powershell
docker compose exec mysql mysql -uroot -p duong_an_v11 -e "SELECT VERSION(); SHOW FULL TABLES; SELECT * FROM center;"
```

Lệnh sẽ hỏi mật khẩu ở dấu nhắc; nhập giá trị trong `.env`. Dừng container nhưng giữ nguyên dữ liệu bằng `docker compose down`; chạy lại với `docker compose up -d`. **Không dùng `docker compose down -v` nếu muốn giữ database**. Port mặc định 3308 tránh MySQL đang dùng cổng 3306 và 3307 trên máy; đổi bằng `MYSQL_PORT` trong `.env` nếu cần.

Các SQL được mount chỉ đọc, không đưa file database vào repository. V1–V7 chỉ tự chạy khi MySQL khởi tạo data directory trống; sửa file migration rồi khởi động lại container **không** thay đổi database đã có. Không xóa volume để nạp lại; xem [hướng dẫn V6](GOAL_BANK_V6.md) và [bổ sung V7](GOAL_BANK_FLOW.md) trước khi nâng cấp database hiện hữu.

`.env` có mật khẩu local, đã được Git ignore. Giá trị ví dụ chỉ dùng trên máy cá nhân; thay bằng mật khẩu riêng nếu DB được mở khỏi `127.0.0.1`. Backend Spring Boot hiện kết nối schema này qua JDBC; môi trường triển khai nên dùng tài khoản MySQL riêng với quyền tối thiểu thay cho root.

### Khởi tạo thủ công/Workbench

Trong MySQL Workbench, kết nối bằng tài khoản quản lý migration và chạy:

```sql
CREATE DATABASE duong_an_v11 CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
USE duong_an_v11;
```

Chọn `duong_an_v11` làm schema mặc định. Trên database mới, chạy lần lượt **V1 → V2 → V3 → V4 → V5 → V6**, dừng thực thi ngay khi có lỗi. V3 tự tạo thông tin trung tâm. Với database đang dùng V4, xem [hướng dẫn V6](GOAL_BANK_V6.md) trước khi áp dụng.

Có thể dùng MySQL CLI trong **Command Prompt** (thay đường dẫn `mysql` nếu chưa có PATH), từ thư mục gốc dự án:

```bat
mysql --host=127.0.0.1 --port=3306 --user=duong_an_owner -p --default-character-set=utf8mb4 duong_an_v11 < database/mysql/migrations/V1__design_v1_1.sql
mysql --host=127.0.0.1 --port=3306 --user=duong_an_owner -p --default-character-set=utf8mb4 duong_an_v11 < database/mysql/migrations/V2__business_guards.sql
mysql --host=127.0.0.1 --port=3306 --user=duong_an_owner -p --default-character-set=utf8mb4 duong_an_v11 < database/mysql/migrations/V3__seed_center.sql
mysql --host=127.0.0.1 --port=3306 --user=duong_an_owner -p --default-character-set=utf8mb4 duong_an_v11 < database/mysql/migrations/V4__programs_and_domains.sql
mysql --host=127.0.0.1 --port=3306 --user=duong_an_owner -p --default-character-set=utf8mb4 duong_an_v11 < database/mysql/migrations/V5__goal_bank_hierarchy.sql
mysql --host=127.0.0.1 --port=3306 --user=duong_an_owner -p --default-character-set=utf8mb4 duong_an_v11 < database/mysql/migrations/V6__catalog_short_ids.sql
```

Chỉ chạy lệnh sau khi lệnh trước thành công. Cú pháp `<` trên dành cho Command Prompt/Bash, không dùng trực tiếp trong Windows PowerShell. SQL không tạo mật khẩu hay tài khoản đăng nhập ứng dụng. Backend phải tạo tài khoản quản trị bằng mật khẩu đã hash.

MySQL DDL có implicit commit; không thể rollback toàn bộ hai migration như PostgreSQL. Nếu lỗi giữa chừng, kiểm tra schema đã tạo và khắc phục trên database thử riêng, không chạy lại V1 lên schema đã có bảng. Migrations không có lệnh xóa database/bảng. Xem [tài liệu MySQL về implicit commit](https://dev.mysql.com/doc/refman/8.4/en/implicit-commit.html).

## Danh mục 29 bảng sau V4

| Nhóm | Bảng | Mục đích |
| --- | --- | --- |
| Nền tảng (7) | `center` | Thông tin trung tâm; PK + CHECK chỉ cho phép `id=1` |
| | `app_user` | Tài khoản, vai trò, trạng thái, phiên đăng nhập |
| | `staff` | Nhân sự; tài khoản tùy chọn |
| | `guardian` | Người giám hộ; tài khoản tùy chọn |
| | `student` | Hồ sơ trẻ duy nhất trong hệ thống |
| | `student_guardian` | Quan hệ N–N, quyền xem/đón trẻ, người liên hệ chính |
| | `staff_salary` | Lịch sử mức lương theo khoảng hiệu lực |
| Lớp/lịch (4) | `learning_class` | Lớp và giáo viên chính |
| | `enrollment` | Lịch sử trẻ tham gia lớp |
| | `class_session` | Buổi học, giáo viên thực tế, giờ và ghi chú địa điểm |
| | `student_attendance` | Một kết quả/học sinh/buổi |
| Vận hành (9) | `staff_checkin` | Một bản chấm công/nhân viên/ngày |
| | `leave_request` | Đơn nghỉ, người gửi và người duyệt khác nhau |
| | `invoice`, `invoice_line`, `payment` | Phiếu học phí, các khoản thu, nhiều lần thanh toán |
| | `notification`, `notification_recipient` | Thông báo và người nhận |
| | `document` | Metadata file hoặc đường dẫn, không lưu file nhị phân |
| | `audit_event` | Nhật ký thao tác; API ghi bổ sung ngoài hai procedure |
| Ngân hàng (6) | `programs` | Chương trình can thiệp và khoảng tuổi áp dụng theo tháng |
| | `domains`, `skill_levels`, `goals`, `specific_goals`, `goal_criteria` | Chương trình → lĩnh vực → cấp độ → mục tiêu → mục tiêu cụ thể → tiêu chí tùy chọn |
| Cá nhân (3) | `learning_plans`, `plan_goals`, `progress_records` | Kế hoạch của trẻ → mục tiêu đã chụp → các lần quan sát |

Không có `branch`, `room`, `children` hay bảng nối giáo viên/buổi nâng cao. Sơ đồ quan hệ chi tiết ở [mục 3 của bản thiết kế](../DESIGN_v1.1_chinh_sua.md#3-sơ-đồ-quan-hệ--chia-thành-3-phần-dễ-đọc).

`programs` lưu `id`, `code`, `name`, khoảng tuổi `age_min_months`–`age_max_months` và `description` (cùng trạng thái/thời điểm tạo-sửa). `domains.program_id` là khóa ngoại đến `programs.id`; cột cho phép `NULL` để giữ tương thích với các lĩnh vực cũ chưa được phân vào chương trình. Khi tạo lĩnh vực mới, ứng dụng nên yêu cầu chọn chương trình.

## Các trường của học sinh

| Cột `student` | Kiểu | Ý nghĩa |
| --- | --- | --- |
| `id` | `CHAR(36)` PK | UUID do backend tạo |
| `code` | `VARCHAR(30)` UNIQUE | Mã học sinh |
| `full_name` | `VARCHAR(150)` | Họ tên |
| `birth_date` | `DATE` | Ngày sinh, không sau ngày nhập học |
| `admitted_on` | `DATE` | Ngày nhập học |
| `withdrawn_on` | `DATE`, nullable | Ngày nghỉ; có giá trị khi trạng thái `WITHDRAWN` |
| `status` | `VARCHAR(20)` | `ACTIVE`, `PAUSED`, `WITHDRAWN`; mặc định `ACTIVE` |
| `notes` | `TEXT`, nullable | Ghi chú hồ sơ |
| `created_at`, `updated_at` | `DATETIME(6)` | Thời điểm tạo/sửa theo UTC |

Phụ huynh liên kết qua `student_guardian`; giáo viên lấy từ lớp/buổi học. Không lưu `parent_id` hay `teacher_id` cố định trên học sinh.

## Quy ước MySQL và dữ liệu

- **UUID:** `CHAR(36) CHARACTER SET ascii COLLATE ascii_bin`; backend phát sinh UUID chuẩn. DB kiểm tra tính duy nhất/tham chiếu, không kiểm tra mẫu UUID. `center.id` là số 1, `audit_event.id` tăng tự động.
- **Tiếng Việt:** `utf8mb4`, mặc định so sánh không phân biệt hoa/thường và dấu. Email phải được trim/lowercase trước khi lưu. Mã lĩnh vực/mục tiêu được xem là duy nhất không phân biệt hoa/thường; nên dùng mã ASCII chuẩn hóa. Trạng thái dùng collation phân biệt hoa/thường.
- **Tiền:** `DECIMAL(...,0)`, VND nguyên đồng, không dùng float. `line_total = quantity * unit_price - discount`. Tổng phiếu tính qua view, không lưu tổng trùng lặp. Phiếu VOID có `payment_status='VOID'`; báo cáo doanh thu/công nợ phải loại phiếu VOID/DRAFT theo nghiệp vụ.
- **Thời gian:** PostgreSQL `timestamptz` chuyển thành `DATETIME(6)` theo UTC; `jsonb` chuyển thành MySQL `JSON`. Mọi kết nối ghi phải đặt `time_zone='+00:00'`. DB không tự biết múi giờ của chuỗi `DATETIME` do client gửi; backend đổi từ giờ Việt Nam sang UTC trước khi lưu và đổi ngược khi hiển thị.
- **Khoảng hiệu lực:** lương, ghi danh và thời gian lớp dùng `[bắt đầu, kết thúc)`; `NULL` ở mốc cuối nghĩa là chưa kết thúc. Buổi học `[starts_at, ends_at)`, cho phép buổi sau bắt đầu đúng lúc buổi trước kết thúc. `learning_plans.end_date`, ngày nghỉ phép và `student.withdrawn_on` là ngày lịch nghiệp vụ; ngày cuối kế hoạch/nghỉ phép tính bao gồm.
- **Bảo toàn lịch sử:** không có FK `ON DELETE CASCADE`; ứng dụng dùng trạng thái/vô hiệu hóa, không xóa hồ sơ đã tham chiếu. Không cấp DELETE hàng loạt cho API. FK chặn xóa khi còn bản ghi con, không thay thế toàn bộ chính sách lưu lịch sử.
- **Bổ sung kỹ thuật:** `account_role` ở nhân sự/phụ huynh để FK kiểm tra loại tài khoản; `primary_student_id` là cột generated, không cho người dùng nhập, đảm bảo tối đa một người giám hộ chính chưa bị thu hồi. `created_by`, `assigned_by`, thời điểm tạo/sửa hỗ trợ truy vết.

## Mục tiêu, snapshot và phiên bản

Mã cấp độ duy nhất **trong lĩnh vực**; mã mục tiêu và mục tiêu cụ thể duy nhất toàn hệ thống. Mỗi mục tiêu cụ thể có tối đa một `goal_criteria`. Các danh mục có `is_active` để ngừng dùng mà giữ lịch sử.

Khi INSERT `plan_goals`, truyền hai JSON object rỗng; trigger đọc nội dung mẫu và tiêu chí trong một SELECT, ghi snapshot có `schema_version=1`, bỏ qua JSON do client đưa vào. Ngân hàng chưa có tiêu chí được ghi `criteria_defined=0`; không được coi là tự đạt. Chỉ gán mẫu có cả chuỗi lĩnh vực/cấp độ/mục tiêu đang hoạt động.

```sql
INSERT INTO plan_goals(id,plan_id,specific_goal_id,goal_snapshot,criteria_snapshot,assigned_by)
VALUES(UUID(),@plan_id,@specific_goal_id,JSON_OBJECT(),JSON_OBJECT(),@authenticated_user_id);
```

UPDATE không được thay snapshot, đối tượng, người/thời điểm giao hoặc `supersedes_id`. Khi chủ động áp dụng tiêu chí mới, service mở transaction, khóa kế hoạch và bản giao cũ bằng `SELECT ... FOR UPDATE`, đổi bản cũ sang `PAUSED`, rồi INSERT bản mới với `supersedes_id` bằng ID cũ. Trigger yêu cầu cùng kế hoạch/mục tiêu mẫu và một bản cũ chỉ được thay thế trực tiếp một lần. Quan sát cũ vẫn trỏ vào bản cũ; không chuyển sang tiêu chí mới. Bản đã bị thay thế phải giữ trạng thái `PAUSED`. API không cho thêm quan sát vào phiên bản đã bị thay thế.

`learning_plans.status`: `DRAFT`, `ACTIVE`, `COMPLETED`, `PAUSED`, `CANCELLED`. `plan_goals.status` từ V11: `NOT_STARTED`, `NOT_ACHIEVED`, `ACHIEVED`, được đồng bộ từ lần đánh giá mới nhất.

`measurement_type`: `COUNT`, `OPPORTUNITIES`, `PERCENTAGE`, `DURATION`. Backend kiểm tra trường bắt buộc theo từng kiểu. `support_level`: `INDEPENDENT`, `VERBAL`, `GESTURAL`, `MODEL`, `PARTIAL_PHYSICAL`, `FULL_PHYSICAL`. Đây là danh mục triển khai ban đầu, không ngầm xác định thuật toán xếp hạng hỗ trợ.

`period_unit`: `SESSION`, `DAY`, `WEEK`, `MONTH`; `setting`: `HOME`, `CLASSROOM`, `COMMUNITY`, `OTHER`. `partner_reference` phải dùng một mã ổn định cho mỗi người lớn, không nhập tên tự do. `session_key` là mã buổi quan sát nghiệp vụ, **không phải FK** tới lịch học; backend phải quy định cách nhóm các lần quan sát nếu đánh giá theo SESSION. `setting_count` đếm loại bối cảnh theo danh mục hiện tại.

Không có trigger tự đánh dấu `ACHIEVED`. Service đối chiếu đúng snapshot, gộp theo ngày địa phương/buổi/tuần, kiểm tra chuỗi liên tiếp, số người lớn và số bối cảnh trước khi cập nhật trạng thái. Khi chưa có thuật toán này, trạng thái chỉ nên cập nhật qua thao tác đánh giá được kiểm soát và ghi audit.

## Chống chồng lịch và khoảng lương

MySQL không có exclusion constraint tương đương thiết kế PostgreSQL cũ. V2 cung cấp:

```sql
CALL save_class_session(@id,@class_id,@teacher_id,@starts_at_utc,@ends_at_utc,
                        'SCHEDULED',@location_note,@change_reason,@authenticated_admin_id);
CALL save_staff_salary(@salary_id,@staff_id,@base_salary,@effective_from,
                       @effective_to,@authenticated_admin_id);
```

`save_class_session` khóa dòng `center.id=1` để tuần tự hóa các lần ghi lịch; đọc khóa kiểm tra trùng **lớp hoặc giáo viên**, bỏ qua buổi CANCELLED, kiểm tra lại khi sửa/kích hoạt lại. Một trung tâm quy mô nhỏ chấp nhận khóa chung này. `save_staff_salary` khóa nhân viên, từ chối khoảng lương giao nhau của người đó. Cả hai ghi audit cùng transaction và rollback khi lỗi. Xem [MySQL locking reads](https://dev.mysql.com/doc/refman/8.4/en/innodb-locking-reads.html).

**Điều kiện bắt buộc:** tài khoản API chỉ được EXECUTE hai procedure và SELECT bảng liên quan; **không được INSERT/UPDATE/DELETE trực tiếp** `class_session` và `staff_salary`, không nhận quyền `ALL ON database.*` hoặc quyền bảng từ role khác. Tài khoản migration quản lý schema riêng. Ví dụ cấp quyền, sau khi DBA đã tạo tài khoản phù hợp:

```sql
GRANT EXECUTE ON PROCEDURE duong_an_v11.save_class_session TO 'duong_an_api'@'localhost';
GRANT EXECUTE ON PROCEDURE duong_an_v11.save_staff_salary TO 'duong_an_api'@'localhost';
GRANT SELECT ON duong_an_v11.class_session TO 'duong_an_api'@'localhost';
GRANT SELECT ON duong_an_v11.staff_salary TO 'duong_an_api'@'localhost';
```

Cấp quyền các bảng còn lại theo từng bảng và nghiệp vụ; ví dụ audit chỉ SELECT/INSERT. Procedure dùng `SQL SECURITY DEFINER`; giữ tài khoản tạo procedure tồn tại và có quyền cần thiết khi triển khai/restore. Không gán tài khoản MySQL cho từng phụ huynh; người dùng đăng nhập qua API.

**Các procedure tự START TRANSACTION/COMMIT.** Gọi trên connection không có transaction đang mở (ví dụ service không bọc `@Transactional` cho lời gọi này). Không ghép CALL vào một transaction khác rồi kỳ vọng rollback cả nhóm: MySQL sẽ commit transaction trước khi bắt đầu transaction mới. Nếu cần một giao dịch bao gồm nhiều nghiệp vụ, phải thiết kế lại procedure/API cùng biên transaction trước khi tích hợp.

`p_actor_id` phải lấy từ phiên xác thực phía backend. Kiểm tra ADMIN trong procedure không tự xác thực danh tính người gọi. SQL trực tiếp bằng owner có thể vượt cơ chế ghi lịch; đây không phải CHECK tự áp dụng cho mọi INSERT.

## Phần Spring Boot còn phải triển khai

Schema chưa nối frontend/API. Những quy tắc dưới đây **không được bảo đảm chỉ bằng FK/CHECK**:

1. Phân quyền theo trẻ/lớp/buổi; chỉ nhận `recorded_by`, `created_by`, `assigned_by` từ phiên đăng nhập. Phụ huynh chỉ xem trẻ liên kết còn quyền; **chưa mở quyền xem kế hoạch/tiến độ** mặc định.
2. Kiểm tra nhân viên được phân công là giáo viên hợp lệ; buổi nằm trong ngày hoạt động của lớp và thời gian làm việc của giáo viên. Việc đổi giáo viên chính không tự sửa người dạy các buổi đã lập.
3. Ghi danh không chồng khoảng trong cùng lớp/trẻ, sức chứa lớp tại từng ngày, trùng lịch **học sinh** giữa các lớp. Chỉ khóa/check ở service trước khi CALL riêng không bảo đảm chống race với thay đổi ghi danh; cần thiết kế một giao dịch ghi chung nếu yêu cầu tuyệt đối.
4. Điểm danh: enrollment hợp lệ vào ngày buổi học theo múi giờ trung tâm, kể cả bản ghi ENDED còn hiệu lực trong quá khứ; buổi chưa hủy, người ghi đúng quyền. Sửa lịch có điểm danh phải kiểm tra lại ngày/đối tượng và ghi lý do.
5. Thu phí: khóa dòng invoice khi sửa dòng thu, ghi/hủy payment; chỉ thu phiếu ISSUED, không thu vượt nợ nếu chính sách không cho phép. `idempotency_key` chặn request trùng nhưng không ngăn hai request khác key cùng thu vượt. Đối chiếu payload khi retry key. Không sửa tùy ý khoản thu đã phát hành.
6. Kiểm tra cấu trúc tiêu chí theo kiểu đo, dữ liệu quan sát nằm trong thời gian kế hoạch, phiên bản còn dùng, và thuật toán đánh giá nhiều ngày/người/bối cảnh.
7. Ghi audit cho các nghiệp vụ khác; ngăn API sửa/xóa audit và lịch sử. Không sử dụng `CascadeType.REMOVE` từ trẻ tới kế hoạch/điểm danh/học phí.

Backend hiện có MySQL driver, nhưng chưa cấu hình ORM/migration cho bộ này. Khi tích hợp: giữ driver MySQL; thêm JPA/Flyway nếu chọn dùng; chỉ đưa **hai file trong `mysql/migrations/`** vào migration location mới, dùng schema history phù hợp, cấu hình Hibernate `ddl-auto=validate`. Không copy thêm V1/V2 PostgreSQL. Pin phiên bản MySQL trong môi trường triển khai thay vì dựa vào `latest` và chạy lại bộ kiểm thử trên phiên bản đó.

JDBC tham khảo: `jdbc:mysql://localhost:3306/duong_an_v11?connectionTimeZone=UTC&forceConnectionTimeZoneToSession=true`; đặt timezone JDBC/Hibernate theo UTC và kiểm tra conversion bằng test API khi triển khai.
