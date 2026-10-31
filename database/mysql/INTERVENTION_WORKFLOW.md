> Tài liệu lịch sử trước V11. Luồng đang dùng được mô tả tại [PLAN_EVALUATIONS_V11.md](PLAN_EVALUATIONS_V11.md); không còn ngân hàng mục tiêu cá nhân.

# Kế hoạch can thiệp — luồng 5 trang

## Sau khi nhập catalog V8

Các truy vấn ứng dụng đã đối chiếu với dữ liệu Docker `duong_an_v11`: Denver 10 lĩnh vực/445 mục tiêu/4 cấp độ (12–48 tháng), VB 16 lĩnh vực/170 mục tiêu chính/738 mục tiêu con/3 cấp độ (0–48 tháng). Không reset hoặc nhập lại catalog.

- Thống kê chương trình tổng hợp riêng từng bảng trước khi JOIN, không nhân số lĩnh vực theo số cấp độ hoặc số mục tiêu con.
- API nhóm lĩnh vực trả `id` DM ổn định; mỗi lựa chọn độ tuổi trả LD riêng. FE lưu lựa chọn theo DM và lấy mục tiêu theo LD, không nhóm hoặc giữ trạng thái theo tên lĩnh vực.
- Mục tiêu được lọc `is_active` và sắp theo `display_order,id`. Denver không đọc bảng con; VB đọc con khi mở mục tiêu chính.
- Trang xem lại và thứ tự lưu theo thư viện: lĩnh vực → cấp độ → mục tiêu → mục tiêu con, không phụ thuộc thứ tự click checkbox. Kế hoạch đã lưu đọc snapshot.
- Danh sách học sinh dùng LEFT JOIN để vẫn trả học sinh chưa có kế hoạch; đếm riêng mục tiêu đang học/hoàn thành, bỏ kế hoạch bị hủy và phiên bản đã được thay thế.
- Hồ sơ vẫn có đúng 5 tab: kế hoạch hiện tại, lịch sử, ngân hàng, hoàn thành, tạm dừng. Hoàn thành lưu kế hoạch và các mục tiêu trong một transaction, chống gửi lặp.

Truy vấn SQL có thể xem trong [intervention_queries.sql](intervention_queries.sql); đây là các SELECT mẫu cho Workbench, không thay thế bước kiểm tra và lưu của backend.

`ImportedCatalogWorkflowTests` kiểm tra toàn bộ các cặp LD của dữ liệu đã nhập và chạy luồng Denver/VB với hồ sơ/kế hoạch tạm trong transaction **luôn rollback**. Không chạy `test_goal_bank_v8.ps1` trên database đã nhập catalog vì runner đó dành riêng cho database rỗng.

Kiểm tra sau chỉnh truy vấn: 6 bài backend (2 bài trên catalog đã nhập, 1 smoke chỉ đọc, 3 bài quy tắc Denver/VB), 19 bài Vitest, 2 bài Playwright luồng kế hoạch đều đạt. Backend package và frontend build thành công. Sau test vẫn có 2 chương trình, 26 lĩnh vực, 615 mục tiêu chính, 738 con, 3 tài khoản; không để lại học sinh hoặc kế hoạch kiểm thử.

Hiện database chưa có hồ sơ học sinh (`student = 0`), vì vậy trang danh sách sẽ hiện thông báo chưa có hồ sơ. Import thư viện không tự tạo học sinh.

## Mô tả phiên bản trước và chi tiết luồng

**Cập nhật V8:** xem [GOAL_BANK_V8.md](GOAL_BANK_V8.md). API mục tiêu chuyển sang `level-domains/{LD}/goals` và `intervention/level-domains/{LD}/targets`; lĩnh vực dùng chung giữa các cấp độ. Khi đổi tháng tuổi, xóa mục tiêu không còn thuộc độ tuổi đang chọn; quay lại từ trang xem lại vẫn giữ lựa chọn. Các mô tả giữ mục tiêu khi đổi tuổi và API DM bên dưới là hành vi trước V8.

Luồng hiện tại thay thế màn chọn mục tiêu trong một trang. Giữ Spring Boot MVC với Controller → Service → Repository/JDBC và React làm giao diện; dữ liệu lấy từ MySQL Docker 3308.

## Các trang

1. `/#/individual/plan`: tất cả học sinh, tìm theo tên/mã, tuổi theo tháng, số mục tiêu đang học và đã hoàn thành. Không còn giới hạn 100 học sinh của API chọn nhanh cũ.
2. `/#/individual/plan/{studentId}`: hồ sơ với các mục Kế hoạch hiện tại, Lịch sử kế hoạch, Ngân hàng mục tiêu, Mục tiêu đã hoàn thành, Mục tiêu đã dừng. Bấm Ngân hàng mục tiêu → Gợi ý mục tiêu.
3. `/#/individual/plan/{studentId}/new/program`: chương trình đang hoạt động từ `programs`, số lĩnh vực, số mục tiêu chính và khoảng tuổi từ các cấp độ.
4. `/#/individual/plan/{studentId}/new/targets`: mỗi lĩnh vực có một lựa chọn tháng tuổi riêng; chọn tuổi sẽ tải mục tiêu ngay bên dưới. Có thể chọn nhiều mục tiêu và nhiều lĩnh vực. Lựa chọn được giữ khi đổi tuổi hoặc quay lại từ trang xem lại. Không có mức độ ưu tiên. Có tên kế hoạch và ngày bắt đầu để người dùng kiểm tra.
5. `/#/individual/plan/{studentId}/new/review`: mục lục lĩnh vực → mục tiêu chính → các mục tiêu con đã chọn nếu có. Bấm Hoàn thành để lưu kế hoạch và quay về Kế hoạch hiện tại.

Tên lĩnh vực trong dữ liệu nhập có thể lặp lại ở nhiều cấp độ với các mã khác nhau. API gom theo tên đã bỏ khoảng trắng hai đầu và không phân biệt hoa/thường; mỗi tuổi vẫn giữ đúng `domain_id` và `program_level_id` gốc. Các tên khác nhau không được tự suy đoán là cùng lĩnh vực. Số lĩnh vực trong bảng chương trình đếm các tên lĩnh vực khác nhau; số mục tiêu đếm mục tiêu chính đang hoạt động qua tất cả cấp độ.

Denver (`ESDM`/`DENVER`) chọn trực tiếp mục tiêu chính, hiển thị mã/tên/mô tả và không truy vấn bảng mục tiêu con khi tải lĩnh vực. VB chỉ tải danh sách con khi người dùng mở mục tiêu chính; con theo `display_order,id`. VB không có con đang hoạt động cho phép chọn mục tiêu chính. Backend kiểm tra lại quy tắc ở bước xem lại và hoàn tất.

## API mới

Tất cả API `/api/intervention/**` hiện yêu cầu ADMIN. Session và CSRF áp dụng như các API kế hoạch trước đó.

| API | Nội dung |
| --- | --- |
| `GET /api/intervention/students?q=` | Học sinh, tuổi, thống kê mục tiêu |
| `GET /api/intervention/students/{id}` | Hồ sơ, các kế hoạch và snapshot mục tiêu, ngân hàng V7 đã có |
| `GET /api/intervention/programs` | Chương trình và thống kê |
| `GET /api/intervention/programs/{id}/domains` | Lĩnh vực và các khoảng tuổi được chọn |
| `GET /api/intervention/domains/{id}/targets` | Toàn bộ mục tiêu chính trong lĩnh vực/tuổi, khả năng chọn chính/con |
| `POST /api/intervention/students/{id}/preview` | Xác minh, trả nội dung mục tiêu và token; không ghi DB |
| `POST /api/intervention/students/{id}/complete` | Lưu kế hoạch và mục tiêu trong một transaction |

Preview gửi các trường:

```json
{
  "requestId": "UUID-moi-cho-moi-luot-lap-ke-hoach",
  "programId": "PR0001",
  "name": "Kế hoạch Denver",
  "startDate": "2026-10-27",
  "selections": [{"goalId": "GL000001", "specificGoalId": null}]
}
```

Thay ID ví dụ bằng ID thật. `requestId` phải là UUID chuẩn; frontend tạo bằng `crypto.randomUUID()`. Preview trả các trường trên cùng `items`, `total`, `reviewToken`. Complete gửi metadata, các cặp ID và `reviewToken`; không nhận mô tả mục tiêu từ client.

Token liên kết học sinh, mã lượt tạo, chương trình, tên kế hoạch, ngày bắt đầu và toàn bộ nội dung lựa chọn. Đổi nội dung hoặc metadata sau khi xem lại sẽ nhận 409 và phải xem lại. Các ID trùng trong một lượt được loại bỏ. Mỗi lượt nhận 1–1000 mục tiêu.

## Lưu dữ liệu

Hoàn thành tạo **một bản ghi `learning_plans` trạng thái ACTIVE**, sau đó tạo toàn bộ `plan_goals` trong cùng transaction. `learning_plans.id` dùng UUID của lượt tạo. Trigger V6 chụp nội dung và tiêu chí từ thư viện vào snapshot; sửa thư viện sau này không làm đổi kế hoạch đã lưu. Nếu bất kỳ mục tiêu nào lưu lỗi, cả kế hoạch và các mục tiêu đều rollback.

Khóa dòng học sinh trước khi lưu bảo vệ yêu cầu đồng thời. Gửi lại cùng UUID với đúng học sinh, người tạo, chương trình, tên, ngày và cặp ID sẽ trả kế hoạch đã lưu với `alreadySaved=true`; không tạo thêm. Dùng UUID đã lưu cho lựa chọn khác nhận 409. Retry vẫn trả snapshot cũ nếu thư viện đã sửa sau lần lưu thành công.

Không cần migration mới: dùng schema V7 hiện tại. Không thay đổi các bản ghi chương trình đã nhập, hồ sơ hoặc kế hoạch cũ. Các API ngân hàng V7 vẫn được giữ cho tương thích; luồng 5 trang mới ghi vào `learning_plans` và `plan_goals`, không yêu cầu gọi lưu ngân hàng rồi giao từng mục tiêu.

Tab Ngân hàng mục tiêu hiển thị các snapshot ngân hàng V7 đã có và các mục tiêu đã chọn trong những kế hoạch không bị hủy, loại trùng theo mục tiêu. Tab lịch sử hiển thị mọi kế hoạch với trạng thái thật; mục tiêu đã hoàn thành/dừng lọc theo `ACHIEVED`/`PAUSED`. Không thêm tính năng tự thay đổi trạng thái ở bước này. Các phiên bản mục tiêu đã bị thay thế được loại khỏi thống kê và danh sách hiện tại. Mục tiêu đang học tính `NOT_STARTED`/`IN_PROGRESS` của kế hoạch ACTIVE; kế hoạch CANCELLED không góp vào thống kê.

Draft chỉ giữ trong bộ nhớ của lượt mở trang. Tải lại trực tiếp trang chọn/xem lại sẽ quay về chọn chương trình để bắt đầu lượt mới; kế hoạch đã hoàn thành đọc lại từ MySQL. Các kế hoạch ACTIVE cũ không tự bị đóng khi tạo kế hoạch mới.

## Kiểm tra

`InterventionWorkflowTests` trên database tạm xác minh nhóm lĩnh vực khác mã theo cùng tên, thống kê chương trình, lựa chọn Denver/VB, preview không ghi, snapshot, metadata/token, lỗi giữa lượt rollback cả hai bảng, gửi đồng thời không tạo trùng, trạng thái và quyền/CSRF. Bộ test dùng dữ liệu riêng và dọn sau chạy; không tạo kế hoạch thử trong database nghiệp vụ.

Playwright kiểm tra 5 trang, chọn nhiều mục tiêu ở nhiều lĩnh vực/tuổi, quay lại giữ lựa chọn, preview chưa ghi, hoàn tất/tải lại, VB tải con khi mở và trường hợp hoàn tất thành công nhưng mất phản hồi rồi gửi lại. Ảnh 5 trang nằm trong `frontend/test-results/plan-0*.png` sau kiểm thử.

Kết quả ngày 27/10/2026: Maven 35 bài đạt, 1 bài đọc thật bỏ qua có chủ đích; bài đọc thật chạy riêng trên Docker 3308 đạt 1/1. Vitest 19/19, Playwright 8/8, production build đạt. Sau chỉnh bố cục/chữ, hai ca luồng mới được chạy lại và đều đạt; đã xem ảnh desktop và mobile.
