# Ngân hàng mục tiêu theo học sinh — V7

**Luồng giao diện hiện tại:** xem [Kế hoạch can thiệp — 5 trang](INTERVENTION_WORKFLOW.md). Theo yêu cầu mới, Hoàn thành lưu trực tiếp `learning_plans` và `plan_goals` trong một transaction. API ngân hàng riêng V7 bên dưới vẫn được giữ để đọc dữ liệu cũ và tương thích, nhưng không còn là điểm hoàn tất của luồng giao diện chính.

Đã triển khai luồng **chọn học sinh → ngân hàng mục tiêu → chọn chương trình → cấp độ/độ tuổi → lĩnh vực → chọn mục tiêu → xem lại → hoàn tất**. Frontend tại `/#/individual/plan` gọi Spring Boot qua session và CSRF, lưu dữ liệu trong MySQL. Không cần tạo kế hoạch trước khi tạo ngân hàng của học sinh.

## Quy tắc Denver và VB

| Chương trình | Chi tiết mục tiêu | Cách chọn |
| --- | --- | --- |
| `ESDM`, `DENVER` | `goals.code`, `name`, `description`; không truy vấn `specific_goals` | Chọn mục tiêu chính; không hiển thị vùng mục tiêu con |
| `VB` có con đang hoạt động | Truy vấn con khi mở chi tiết mục tiêu chính; sắp xếp `display_order,id` | Chọn một hoặc nhiều mục tiêu con A/B/C/D; không chọn đồng thời mục tiêu chính |
| `VB` không có con đang hoạt động | Vẫn hiển thị mục tiêu chính | Cho phép chọn trực tiếp mục tiêu chính |

Mỗi mục tiêu con hiển thị mã, mô tả và tuổi. Nếu một mốc tuổi của con là NULL, API kế thừa riêng mốc đó từ `program_levels`. Danh sách chương trình/cấp độ/lĩnh vực/mục tiêu chỉ lấy dữ liệu đang hoạt động. Client dựa vào `supportsSpecificGoals` và `canSelectGeneralGoal` do backend trả về, không tự suy đoán bằng tên chương trình.

Frontend giữ các lựa chọn khi đổi cấp độ hoặc lĩnh vực trong cùng chương trình. Chuyển chương trình bắt đầu một lượt chọn mới. Ngân hàng đã lưu có thể chứa mục tiêu của nhiều chương trình qua nhiều lượt hoàn tất. Không có trường mức độ ưu tiên.

## API

| Phương thức và đường dẫn | Chức năng |
| --- | --- |
| `GET /api/students` | Chọn học sinh |
| `GET /api/programs` | Chọn chương trình |
| `GET /api/programs/{id}/levels` | Cấp độ, độ tuổi |
| `GET /api/levels/{id}/domains` | Lĩnh vực thuộc cấp độ |
| `GET /api/domains/{id}/goals?q=&page=0&size=20` | Mục tiêu chính; phân trang tối đa 100/bước |
| `GET /api/goals/{id}` | Nội dung chính, khả năng lựa chọn, vị trí trong thư viện và con nếu chương trình hỗ trợ |
| `GET /api/students/{studentId}/goal-bank` | Ngân hàng đã lưu của riêng học sinh |
| `POST /api/students/{studentId}/goal-bank/preview` | Kiểm tra lựa chọn và trả nội dung để xem lại; chưa ghi DB |
| `POST /api/students/{studentId}/goal-bank/complete` | Kiểm tra lại nội dung đã xem và lưu cả lượt trong một transaction |

Ví dụ preview (thay ID bằng ID thật trong database):

```json
{
  "programId": "PR000001",
  "selections": [
    {"goalId": "GL000001", "specificGoalId": null}
  ]
}
```

VB chọn con gửi cả `goalId` và `specificGoalId`. Backend kiểm tra con thuộc đúng cha, đúng chương trình, trạng thái hoạt động và quy tắc lựa chọn. Mỗi lượt nhận 1–1000 mục tiêu, loại bỏ ID trùng trong request. Nội dung/mô tả không lấy từ client.

Preview trả `programId`, `items`, `total`, `reviewToken`. Complete gửi cùng `programId`, các cặp ID đã xem và `reviewToken` nhận được. Token là SHA256 của học sinh và nội dung lựa chọn đã chuẩn hóa; dùng để phát hiện thay đổi, không thay thế xác thực/phân quyền. Nếu nội dung thư viện hoặc lựa chọn thay đổi, trả HTTP 409 và yêu cầu xem lại. Lựa chọn sai trả 400, không tìm thấy trả 404, sai liên kết cha/con trả 409.

Complete trả `addedCount`, `alreadyPresentCount`, `bank`. Gửi lại lượt hoàn tất không tạo bản ghi trùng. Khóa dòng học sinh và unique key bảo vệ khi hai request lưu cùng lúc. Sau khi lưu, GET đọc bản chụp đã lưu, nên sửa thư viện không làm thay đổi nội dung ngân hàng cũ. Ngân hàng không tự kích hoạt kế hoạch; chọn kế hoạch rồi dùng nút **Thêm vào kế hoạch** nếu muốn giao mục tiêu.

API học sinh/ngân hàng/kế hoạch hiện yêu cầu **ADMIN**, phù hợp phạm vi API hiện hữu. TEACHER và PARENT chưa được mở truy cập ngân hàng khi chưa có kiểm tra phân công học sinh. Catalog GET cho phép các tài khoản đã đăng nhập; thao tác ghi catalog yêu cầu ADMIN.

## Cấu trúc và MVC

Controller → Service → Repository dùng JDBC, theo kiến trúc MVC hiện tại; React là phần giao diện. Các lớp chính:

- `StudentGoalBankController`, `StudentGoalBankService`, `StudentGoalBankRepository`, `GoalBankDtos`.
- `GoalSelectionPolicy`: quy tắc chương trình; `GoalTargetResolver`: xác minh và đọc nội dung mục tiêu. Một mục tiêu VB được tải một lần trong mỗi lượt xác minh dù chọn nhiều con.
- `GoalCatalogService`, `GoalCatalogRepository`: tách truy vấn mục tiêu chính và mục tiêu con.
- `frontend/src/pages/StudentGoalBank.tsx`, `RealInterventionPlans.tsx`, `services/goalBankApi.ts`.

[V7__student_goal_bank.sql](migrations/V7__student_goal_bank.sql) bổ sung bảng `student_goal_bank`: `id`, `student_id`, `goal_id`, `specific_goal_id`, `target_key` generated, `goal_snapshot` JSON, `selected_by`, `created_at`. FK cha/con kết hợp ngăn gắn con sai cha; unique `(student_id,target_key)` ngăn trùng theo học sinh. Không thay đổi catalog, dữ liệu import, kế hoạch hoặc tiến độ cũ.

## Triển khai đã thực hiện ngày 27/10/2026

V7 đã áp dụng trên schema `duong_an_v11` ở local **3306** và Docker **3308**. Backend mặc định dùng Docker; hai database độc lập. Sau khi người dùng COMMIT import, kiểm tra Docker thấy:

| Chương trình | Cấp độ | Lĩnh vực | Mục tiêu chính | Mục tiêu con |
| --- | ---: | ---: | ---: | ---: |
| ESDM | 4 | 46 | 445 | 0 |
| VB | 3 | 34 | 170 | 738 |

Không chạy lại hoặc chỉnh sửa hai file import của người dùng. Không tạo học sinh hay mục tiêu ngân hàng mẫu trong database nghiệp vụ. Bảng ngân hàng mới có 0 bản ghi sau migration.

Backup trước V7 nằm trong thư mục Git ignore `database/.backups/`:

- Docker sau COMMIT: `duong_an_v11_before_v7_20261027_114634.sql`, SHA256 `B8A7EE9D4A882EE71162C6C02BDA455A962952B6076804F57CB85FA3A34448CD`.
- Local: `duong_an_v11_local3306_before_v7_20261027_114319.sql`.

Compose đã thêm mount V7 cho volume mới. Volume hiện hữu được nâng cấp trực tiếp bằng SQL; không xóa volume hoặc khởi tạo lại dữ liệu. Container không bị khởi động lại để áp dụng V7 trong lúc người dùng nhập dữ liệu.

Với một bản Docker đã có V6 nhưng chưa có V7, kiểm tra trước bằng:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File database/mysql/apply_student_goal_bank.ps1
```

Thêm `-Apply` để tạo backup mới và áp dụng V7. Script dừng nếu có transaction đang sửa dữ liệu chưa kết thúc; hoàn tất COMMIT/ROLLBACK trong đúng tab import trước khi chạy. Nếu bảng đã tồn tại, script không chạy lại migration.

## Kiểm thử

- MySQL V1–V7 trên database tạm: Maven có 29 bài đạt, 1 bài đọc DB thật được bỏ qua có chủ đích. Bao phủ Denver không truy vấn con, VB nhiều con/không con, giữ lựa chọn, xem lại chưa ghi, token cũ, snapshot, sai chương trình/cha, lưu lặp/đồng thời, CSRF và phân quyền.
- `CatalogReadOnlySmokeTests`: chạy riêng trên 3306 và 3308, mỗi server 1 bài đạt, không tạo fixture hoặc tài khoản.
- Frontend: Vitest 18/18, Playwright 8/8, production build đạt. UI test dùng API mock; tích hợp MySQL được kiểm tra riêng bằng Maven.

Chạy lại kiểm thử backend với `database/mysql/test_goal_bank.ps1` và frontend với `npm test`, `npm run build`, `npm run test:e2e` trong `frontend/`. Script backend tạo/xóa database kiểm thử riêng, không ghi fixture vào schema nghiệp vụ.
