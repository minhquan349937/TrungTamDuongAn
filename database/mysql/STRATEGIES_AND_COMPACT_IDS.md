# Chiến lược và ID ngắn — V15/V16

Hai migration dựa trên schema MySQL 8.4.13 thực tế của `duong_an_v11`.
Chưa áp dụng vào database chính. Không thêm API chiến lược.

## V15: chiến lược theo mục tiêu trong kế hoạch

`strategies` là danh mục dùng chung: mỗi dòng là một chiến lược như
“Làm mẫu”, “Chờ đợi”. `description` mô tả chung chiến lược, có thể NULL.

`plan_goal_strategies` liên kết nhiều–nhiều giữa `plan_goals` và `strategies`.
`description` ở bảng này là cách giáo viên áp dụng chiến lược cho một mục tiêu
trong kế hoạch của học sinh; không thay đổi mô tả chiến lược chung.

- `UNIQUE(plan_goal_id, strategy_id)` ngăn gán trùng chiến lược cho cùng mục tiêu.
- `plan_goal_id` dùng đúng `CHAR(36) CHARACTER SET ascii COLLATE ascii_bin` của bảng hiện tại.
- Hai FK dùng `ON DELETE RESTRICT ON UPDATE RESTRICT`, theo quy tắc không cascade của các bảng kế hoạch/đánh giá hiện có.
- Hai trigger mới chuyển mô tả áp dụng rỗng hoặc chỉ có khoảng trắng/tab/xuống dòng thành NULL khi INSERT/UPDATE; văn bản không trống giữ nguyên.
- `display_order` mặc định 0, không âm; `is_active` mặc định 1, chỉ nhận 0/1.
- Không tạo dữ liệu mẫu, entity JPA hoặc API cho hai bảng mới.

## V16: ID có tiền tố

Quy ước: tiền tố riêng + số thứ tự có ít nhất 3 chữ số, ví dụ `CL001`,
`CL999`, `CL1000`; tối đa 999999 ID mỗi loại. Không cấp lại mã đã xóa sau
khi giao dịch tạo bản ghi được commit. Có thể có khoảng trống giữa các mã.

Danh sách đầy đủ 39 tiền tố nằm trong [short_id_prefixes.json](short_id_prefixes.json).
Một số mã chính:

| Bảng | Ví dụ ID |
| --- | --- |
| app_user | TK001 |
| student | HS001 |
| guardian | PH001 |
| staff | NV001 |
| programs | PR001 |
| program_levels | LV001 |
| domains | DM001 |
| program_level_domains | LD001 |
| goals | GL001 |
| specific_goals | SG001 |
| learning_plans | KH001 |
| plan_goals | MTKH001 |
| strategies | CL001 |
| plan_goal_strategies | MTCL001 |
| goal_evaluations | DG001 |
| plan_evaluations | NX001 |
| website_media | MD001 |
| website_publications | XB001 |

Các bảng có khóa chính ghép (`student_guardian`, `notification_recipient`,
`website_publication_media`) giữ cấu trúc khóa ghép; thành phần tham chiếu
được đổi theo ID mới. Khóa cấu hình như `website_sections.section_key`,
`entity_name`, khóa bộ đếm `student_code_sequence.id=1` và khóa lịch sử
migration giữ nguyên ý nghĩa. Chúng không phải ID bản ghi nghiệp vụ.

Hai singleton nghiệp vụ đổi sang `center.id='TT001'` và
`website_settings.id='WCFG001'`. Các cột CHAR(36) đang có giữ nguyên độ dài
để tương thích entity JPA và FK; giá trị lưu và trả ra là mã ngắn. ID số ở
`audit_event`/`website_publications` đổi sang CHAR(36), bỏ AUTO_INCREMENT.

V16 lưu bảng đối chiếu lâu dài `migration_v16_id_map`; cập nhật khóa ngoại
kể cả khóa ghép, giá trị ID trong JSON, đường dẫn media trong JSON công bố
website và tham chiếu lịch sử trong schema hiện tại. Hai bước qua ID tạm
tránh xung đột với ID cũ. Trigger bảo vệ snapshot/đánh giá được tạm tháo và
khôi phục đúng định nghĩa; 69 FK được kiểm tra trước khi commit dữ liệu.
Mốc updated_at giữ nguyên khi chỉ thay ID.

`entity_id_sequences` là bảng kỹ thuật cấp mã. `ShortIds.next(jdbc, table)`
tăng bộ đếm nguyên tử và đọc kết quả trên cùng kết nối MySQL. Trong
transaction, khóa giữ đến commit/rollback. Các chỗ tạo bản ghi hiện có dùng
chung bộ cấp này. Khi viết API chiến lược sau này, gọi bộ cấp trong transaction;
không dùng MAX(id)+1 hay tự nhập ID mà không đồng bộ bộ đếm.

`learning_plans.request_key` giữ UUID chống gửi lặp của frontend, tách khỏi
khóa chính `KH...`. UUID mã yêu cầu và UUID trong tên tệp media là mã kỹ
thuật, không phải ID bản ghi. Không thêm endpoint mới. Bản công bố website
mới nhất được chọn theo thời điểm công bố rồi phần số của ID, tránh so sánh
chuỗi sai giữa XB999 và XB1000. Các bộ đếm mã hồ sơ bị trống sau lần reset
trước được khởi tạo lại từ mã hồ sơ hiện tại, không thay đổi các mã hồ sơ đó.

## Triển khai

1. Dừng các tiến trình có thể ghi database và sao lưu toàn bộ bằng `backup_database.ps1`.
2. Chạy `V15__plan_goal_strategies.sql` rồi `V16__compact_entity_ids.sql` trên database hiện có sau V14. Không chạy lại migration lịch sử.
3. Triển khai mã ứng dụng/frontend đã cập nhật cùng đợt với V16; khởi động lại và đăng nhập lại. Phiên đăng nhập và URL chứa ID cũ cần thay mới; bảng đối chiếu hỗ trợ tra cứu.
4. Kiểm tra bảng đối chiếu, số dòng và bộ đếm sau migration.

DDL MySQL tự commit. Nếu lỗi giữa migration, giữ ứng dụng dừng và khôi
phục toàn bộ backup trước khi chạy lại; không bỏ qua lỗi hoặc dùng --force.
Migration có phạm vi schema cụ thể của dự án hiện tại; cần rà soát lại nếu
thêm bảng, FK hoặc trigger khác trước lúc triển khai.

## Kiểm chứng

`python database/mysql/verify_compact_ids.py` tạo bản sao có tên ngẫu nhiên
`test_compact_ids_*`, chạy nguyên văn V15/V16 rồi xóa bản sao. Không ghi
database nguồn. Cần PyMySQL và MySQL client. Thêm `--keep` để giữ bản sao
cho kiểm thử ứng dụng; công cụ in tên schema để dùng làm URL kiểm thử.

Đã kiểm tra số dòng, 39 tiền tố, 69 FK, JSON snapshot, mô tả trống, mã chống
gửi lặp, trigger bất biến và đường dẫn media công bố website. Kiểm thử Java
kiểm tra cấp mã đồng thời không trùng, rollback bộ đếm và vượt mốc 999.
Frontend đã build thành công.
