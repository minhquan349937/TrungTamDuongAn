# Kiểm thử MySQL — thiết kế 1.1

**Luồng kế hoạch 5 trang — 27/10/2026:** Maven trên DB tạm có 35 bài đạt, 1 bài đọc DB thật bỏ qua có chủ đích. Bài đọc thật được chạy riêng trên Docker 3308 và đạt, bao gồm API mới thống kê học sinh/chương trình, workspace và nhóm lĩnh vực. Playwright 8/8, Vitest 19/19, build frontend đạt. Backend kiểm tra preview chưa ghi, lưu một kế hoạch với nhiều mục tiêu, rollback cả hai bảng khi lỗi giữa lượt, idempotency/đồng thời, snapshot, token và phân quyền. Không tạo kế hoạch fixture trong DB nghiệp vụ, không cần migration sau V7. Xem [luồng mới](INTERVENTION_WORKFLOW.md).

**Sau áp dụng V7 — 27/10/2026:** đã thêm `student_goal_bank` trên local 3306 và Docker 3308 sau backup. Docker giữ dữ liệu người dùng đã COMMIT: ESDM 445 mục tiêu chính, VB 170 mục tiêu chính/738 mục tiêu con. Maven V1–V7 trên DB tạm: 29 bài đạt, 1 bài đọc DB thật bỏ qua có chủ đích; bài đó được chạy riêng trên mỗi server và đều đạt 1/1. Vitest 18/18, Playwright 8/8, build frontend đạt. Bao phủ quy tắc Denver/VB, xem lại chưa ghi, lưu cả lượt, snapshot, token cũ, chống trùng/đồng thời và phân quyền. Xem [báo cáo V7](GOAL_BANK_FLOW.md) để biết API và backup. Kết quả bên dưới thuộc các giai đoạn trước V7.

**Sau áp dụng V6 — 27/10/2026:** MySQL local 9.1.0 (`3306`) và Docker 8.4.13 (`3308`) đã được nâng cấp thực tế lên V6. Mỗi server đạt 11/11 kiểm tra trong `verify_goal_bank_v6.sql`, giữ số bản ghi cũ và hash tên trung tâm. `CatalogReadOnlySmokeTests` chạy trực tiếp trên từng kết nối đều đạt 1/1, không tạo fixture hoặc tài khoản. Docker đang `healthy`; backup đầy đủ nằm trong `database/.backups/`. Các kết quả dưới đây là lịch sử trước khi áp dụng thực tế.

**Trước áp dụng V6 — 27/10/2026:** 20/20 bài kiểm thử Maven đạt trên MySQL Docker 8.4.13; 0 lỗi, 0 bỏ qua. Đã kiểm tra cấp ID đồng thời, phạm vi mã, quan hệ cha/con, nội dung TEXT, snapshot và tiến độ. Đã chạy V1–V6 trên database tạm; đã phục hồi backup V4 vào database tạm và nâng cấp V5 → V6, xác nhận ánh xạ ID và dữ liệu lịch sử còn nguyên. Xem [báo cáo V6](GOAL_BANK_V6.md).

**Bổ sung 27/10/2026:** [V5 ngân hàng mục tiêu](GOAL_BANK_V5.md) đã được kiểm tra trên database tạm bằng `database/mysql/test_goal_bank.ps1`: 15/15 kiểm thử backend đạt. Volume MySQL hiện hữu vẫn ở V4. Các kết quả V1–V4 bên dưới là lịch sử kiểm thử trước V5.

Ngày kiểm tra: **26/10/2026**. Môi trường: Windows, **MySQL Community Server 9.1.0**, InnoDB, `utf8mb4_0900_ai_ci`, thời gian UTC. Server kiểm thử riêng chỉ bind `127.0.0.1:55440`, data directory nằm trong `database/.test-runtime/` đã được Git bỏ qua. Không sử dụng database nghiệp vụ đang chạy.

## Kết quả

| Kiểm tra | Kết quả |
| --- | --- |
| Import V1 rồi V2 lên database rỗng | Thành công, CLI exit code 0 |
| Số bảng, loại bỏ cấu trúc chi nhánh/children | Đúng 28 bảng và 1 view |
| Docker Compose MySQL 8.4.13: nạp V1–V4 và kiểm tra chương trình/lĩnh vực | **Đạt**: container `healthy`, 29 bảng, 1 view, FK `domains.program_id` → `programs.id` |
| `tests/constraints.sql` | **47/47 assertions đạt** |
| `tests/concurrency.ps1` | **Đạt**: hai kết nối tranh lịch cùng giáo viên, chỉ một buổi và một audit được lưu; yêu cầu còn lại nhận lỗi overlap 1644 |
| Kiểm tra quyền bằng tài khoản API thử riêng | **Đạt**: chỉ cấp EXECUTE/SELECT vẫn gọi được cả hai procedure; UPDATE trực tiếp lịch và lương đều bị từ chối với lỗi 1142 |

Các assertions bao gồm: một trung tâm; ba vai trò; email chuẩn hóa; loại tài khoản nhân sự; trạng thái nghỉ học; một người giám hộ chính còn hiệu lực; sức chứa lớp cá nhân; chồng lịch giáo viên/lớp khi tạo, sửa, kích hoạt lại; hai buổi sát nhau; rollback cập nhật lỗi; quyền ADMIN ở procedure; điểm danh duy nhất; FK bảo toàn lịch sử; khoảng lương liền nhau/chồng nhau; cấm tự duyệt nghỉ phép; công nợ nhiều dòng/nhiều khoản thanh toán không nhân đôi; idempotency và hủy thu; mã cấp độ theo lĩnh vực; tiêu chí tùy chọn/duy nhất; số cơ hội/tỷ lệ; snapshot bất biến khi sửa mẫu; phiên bản tiêu chí mới giữ quan sát cũ; không tự đánh dấu đạt từ một quan sát; audit chỉ ghi giao dịch thành công.

Kiểm tra quyền được chạy bổ sung trên server kiểm thử bằng một MySQL user tạm, theo mẫu GRANT trong README. User này đã được xóa sau kiểm tra; không nằm trong migration. Server kiểm thử được dừng sau khi xác minh xong.

## Chạy lại

Tạo **database kiểm thử mới, riêng biệt**, ví dụ `duong_an_v11_test`, rồi import V1 → V2. **Không INSERT center thủ công trước bộ test**; test tự tạo center và fixture. Trong Command Prompt tại thư mục gốc:

```bat
mysql --host=127.0.0.1 --port=3306 --user=duong_an_test_owner -p --default-character-set=utf8mb4 duong_an_v11_test < database/mysql/tests/constraints.sql
```

Cuối output phải có `passed_assertions = 47`; exit code phải bằng 0. Lỗi SQL ngoài dự kiến dừng script. Các ca cố tình thất bại đối chiếu MySQL errno (`1062`, `1451`, `1452`, `3819`, `1644`), không coi một lỗi bất kỳ là thành công. Helpers được xóa sau khi test đạt.

Sau đó chạy test đồng thời bằng PowerShell, sử dụng `mysql_config_editor` để lưu login path cục bộ nếu cần mật khẩu:

```powershell
& 'C:/Program Files/MySQL/MySQL Server 9.1/bin/mysql_config_editor.exe' set --login-path=duong_an_test --host=127.0.0.1 --port=3306 --user=duong_an_test_owner --password
& ./database/mysql/tests/concurrency.ps1 `
    -MySqlExe 'C:/Program Files/MySQL/MySQL Server 9.1/bin/mysql.exe' `
    -Port 3306 -User duong_an_test_owner -LoginPath duong_an_test -Database duong_an_v11_test
```

Script giữ khóa center trong một kết nối rồi khởi chạy hai client ghi lịch để kiểm tra cạnh tranh. Output thành công bắt đầu bằng `PASS`. Log nằm trong `database/.test-runtime/mysql-concurrency/`. Chạy một lần mỗi database fixture; lần tiếp theo tạo database kiểm thử mới.

**Fixture được giữ lại**, vì hai procedure chủ động COMMIT; không dùng ROLLBACK cuối script để giả vờ đã dọn dữ liệu. Tất cả tên, học sinh, email `.invalid`, tài khoản và khóa trong bộ test là dữ liệu giả; chuỗi password hash test không dùng đăng nhập ứng dụng.

## Giới hạn

- Migration V1–V4 đã khởi tạo và truy vấn smoke-test trên Docker MySQL **8.4.13**. Bộ 47 assertions, concurrency và quyền vẫn được kiểm tra riêng trên MySQL **9.1.0**; chưa chạy toàn bộ bộ test trên 8.4.
- Kiểm tra đồng thời là một kịch bản xung đột với hai người ghi, không phải stress test tải lớn.
- Bộ test SQL kiểm tra tầng DB. Chưa kiểm thử API, xác thực người dùng, phân quyền trẻ/lớp, chống trùng lịch học sinh qua ghi danh, khóa giao dịch thu phí hay thuật toán đạt mục tiêu ở service.
- Việc bắt buộc dùng procedure phụ thuộc quyền tài khoản API; kiểm tra overlap không áp dụng cho SQL trực tiếp bằng owner. Cần triển khai quyền theo [README](README.md#chống-chồng-lịch-và-khoảng-lương).
- Không thực hiện chuyển dữ liệu PostgreSQL, triển khai production hay nối dữ liệu frontend trong công việc này.
