# Kế hoạch học tập và đánh giá mục tiêu — V11

Đã áp dụng trực tiếp ngày 28/10/2026 trên MySQL 8.4.13, Docker `127.0.0.1:3308`, schema `duong_an_v11`.

## Luồng đang dùng

Học sinh → Tạo kế hoạch học tập → Chọn nhiều mục tiêu từ thư viện Denver/VB → Xem lại → Lưu kế hoạch → Đánh giá từng mục tiêu → Đánh giá lại / xem lịch sử.

- Một học sinh có nhiều `learning_plans`; mỗi kế hoạch có nhiều `plan_goals`.
- Có thể chọn mục tiêu từ nhiều chương trình, lĩnh vực và cấp độ trong cùng kế hoạch. Đổi chương trình/cấp độ không xóa các lựa chọn trước đó.
- Denver lưu `goal_id`, `specific_goal_id=NULL`. VB cho phép chọn mục tiêu chính hoặc con; mỗi lựa chọn được đánh giá riêng có một `plan_goals` riêng.
- Tạo kế hoạch và toàn bộ mục tiêu trong một transaction; mã request giữ tính chống gửi lặp. Nội dung chi tiết đọc từ snapshot để giữ chương trình, cấp độ, lĩnh vực và mô tả tại thời điểm tạo.
- Quản trị viên tạo/sửa kế hoạch. Giáo viên được phân công có thể xem kế hoạch và tạo đánh giá; chỉ sửa đánh giá do mình tạo. Quản trị viên có thể sửa các đánh giá.

## Thay đổi database

| Bảng | Kết quả |
|---|---|
| `student_goal_bank` | Đã xóa; lựa chọn cũ, nếu có, được lưu vết chứ không gộp vào `plan_goals` |
| `goal_criteria` | Đã xóa; tiêu chí cũ được lưu vết, không giả lập thành kết quả đánh giá |
| `progress_records` | Đã xóa sau khi chuyển lịch sử và kiểm tra số lượng |
| `goal_evaluations` | Bảng mới: id, plan_goal_id, attempt_no, achievement_percent, result_status, restart_decision, evaluation_notes, evaluated_at, evaluated_by, created_at, updated_at |
| `migration_v11_archive` | Lưu JSON dữ liệu cũ để đối chiếu và xử lý các trường hợp không chuyển đổi được |
| `learning_plans`, `plan_goals` | Giữ nguyên bản ghi và liên kết học sinh/mục tiêu; thay quy tắc trạng thái mục tiêu |

`result_status` là cột generated STORED: NULL → `NOT_STARTED`; dưới 80 → `NOT_ACHIEVED`; từ 80 đến 100 → `ACHIEVED`. CHECK giới hạn phần trăm 0–100. Không có `success_count`, `opportunity_count`, `support_level` trong bảng mới.

Unique `(plan_goal_id,attempt_no)` và trigger bảo đảm số lần đánh giá liên tiếp. Service khóa kế hoạch/mục tiêu trước khi cấp số lần đánh giá. Trigger đồng bộ `plan_goals.status` theo lần mới nhất; sửa lần cũ không đổi trạng thái của lần mới nhất. Không có API xóa lịch sử, nhập trạng thái mục tiêu hoặc nhập người đánh giá từ trình duyệt.

Các cột snapshot cũ trong `plan_goals` được giữ để tránh mất dữ liệu lịch sử. Snapshot tiêu chí chỉ còn là dữ liệu cũ; các mục tiêu mới không còn đọc bảng tiêu chí. `supersedes_id` cũ được giữ để đối chiếu, nhưng không tạo phiên bản mục tiêu mới để đánh giá lại; mọi lần đánh giá lại nằm trong `goal_evaluations`.

## Dữ liệu đã kiểm tra và giữ lại

Trước/sau migration: **3 học sinh, 2 kế hoạch, 60 mục tiêu trong kế hoạch**. Ba bảng cũ đều có 0 dòng, nên bảng đánh giá mới hiện có 0 dòng; không tạo đánh giá giả cho dữ liệu thật.

Có 56 mục tiêu `NOT_STARTED`, 2 `ACHIEVED`, 2 `PAUSED` theo quy tắc cũ. Hai trạng thái “Đạt” và hai trạng thái “Tạm dừng” không có phần trăm hoặc bản ghi đánh giá để chuyển đổi đáng tin cậy. Trạng thái gốc của cả 60 mục tiêu được lưu trong `migration_v11_archive` (`source_table='plan_goals'`). Theo quy tắc mới, cả 60 mục tiêu hiện là `NOT_STARTED`, chờ đánh giá có phần trăm.

Hash trước/sau cho thấy dữ liệu `student`, `learning_plans`, `programs`, `program_levels`, `domains`, `program_level_domains`, `goals`, `specific_goals` không đổi. Giữ nguyên **2 chương trình, 7 cấp độ, 26 lĩnh vực, 70 liên kết cấp độ/lĩnh vực, 615 mục tiêu chính và 738 mục tiêu con**. Không import hoặc sửa thư viện Denver/VB.

Backup ngay trước chuyển đổi:

- `database/.backups/duong_an_v11_before_goal_evaluations_v11_20261028_173533.sql`
- SHA256: `3B4C9FED0B47A7831FBE8AE0C9CBD35165B28315947ECFE84559912A1229FDEF`
- Dấu vân tay trước chuyển đổi: `database/.backups/v11_before_fingerprints.json`.

Đối với database có lịch sử: migration giữ ID, người đánh giá, thời gian, ghi chú và thứ tự; chuyển `success_count/opportunity_count*100` khi có mẫu số lớn hơn 0. Nếu không đủ dữ liệu, phần trăm giữ NULL và báo `NO_PERCENTAGE_AVAILABLE`; toàn bộ metadata nguồn vẫn nằm trong archive. Tiêu chí định nghĩa theo catalog không phải đánh giá của học sinh nên chỉ được lưu vết. Migration kiểm tra FK đến các bảng cũ và số dòng đã sao chép trước khi DROP.

## API và phân quyền

- `GET /api/intervention/students`: giáo viên chỉ thấy học sinh trong phân công hiện hành.
- `GET /api/intervention/students/{id}`: kế hoạch, mục tiêu và snapshot; kiểm tra phạm vi giáo viên.
- `POST .../preview`, `POST .../complete`: quản trị viên tạo kế hoạch trực tiếp từ thư viện, không qua ngân hàng cá nhân.
- `GET /api/plans/{plan}/goals/{goal}/evaluations`: lịch sử theo thứ tự lần đánh giá giảm dần.
- `POST` cùng URL: thêm lần đánh giá, backend cấp `attempt_no` và lấy người đánh giá từ phiên đăng nhập.
- `PUT .../evaluations/{id}`: sửa một lần đánh giá; kiểm tra cả phạm vi học sinh và người tạo.
- `GET/POST /api/admin/individual-assignments`, `GET .../teachers`, `PUT .../{id}`: quản trị viên xem/thêm phân công cá nhân, lấy giáo viên hợp lệ và đổi người phụ trách.
- Body đánh giá: `achievementPercent`, `restartDecision` (`RETRY`, `NO_RETRY`, NULL), `evaluationNotes`.
- API ngân hàng học sinh và API tiến độ/trạng thái mục tiêu cũ đã được gỡ.

Phân công giáo viên được xác định bằng `staff.user_id` → `learning_class.teacher_id` → `enrollment.student_id`, với tài khoản/nhân viên/lớp đang hoạt động và thời hạn ghi danh còn hiệu lực theo ngày Việt Nam. Database hiện chưa có lớp hoạt động; giáo viên chưa được phân công sẽ không thấy hoặc ghi đánh giá cho học sinh. Trang `/individual/assign` đã được nối MySQL: quản trị viên chọn học sinh và giáo viên để tạo lớp cá nhân/ghi danh thật, hoặc đổi giáo viên. Đổi giáo viên cập nhật ngay phạm vi truy cập, giữ nguyên kế hoạch và lịch sử. Chỉ nhân viên đang làm việc có tài khoản TEACHER hoạt động được chọn. Không tạo phân công giả cho dữ liệu thật khi migration.

Các bảng kế hoạch và đánh giá của dự án dùng `JdbcTemplate`; không có JPA entity cho ba bảng bị xóa. Các entity JPA catalog hiện có vẫn được Hibernate `validate` thành công.

## File triển khai

- Database: `migrations/V11__plan_goal_evaluations.sql`, `apply_goal_evaluations.ps1`, `apply_student_goal_bank.ps1` (chặn tái tạo ngân hàng sau V11), `queries.sql`, `intervention_queries.sql`, `compose.yaml`.
- Backend mới: `dto/GoalEvaluationDtos.java`, `dto/GoalSelectionDtos.java`, `repository/GoalEvaluationRepository.java`, `service/GoalEvaluationService.java`, `service/PlanAccessService.java`, `controller/GoalEvaluationController.java`, `service/IndividualAssignmentService.java`, `controller/IndividualAssignmentController.java`.
- Backend cập nhật: `dto/InterventionDtos.java`, `dto/PlanProgressDtos.java`, `repository/InterventionRepository.java`, `repository/PlanGoalRepository.java`, `repository/PlanProgressRepository.java`, `service/InterventionService.java`, `service/GoalTargetResolver.java`, `service/GoalSelectionPolicy.java`, `service/PlanProgressService.java`, `controller/InterventionController.java`, `controller/PlanProgressController.java`, `controller/CatalogExceptionHandler.java`, `security/SecurityConfig.java`.
- Backend đã gỡ: `GoalBankDtos.java`, `StudentGoalBankController.java`, `StudentGoalBankService.java`, `StudentGoalBankRepository.java`.
- React/API cập nhật: `pages/RealInterventionPlans.tsx`, `pages/PlanProgress.tsx`, `services/goalBankApi.ts`, `services/interventionApi.ts`, `features/PeopleTables.tsx`; thêm `services/goalEvaluationApi.ts`, `services/individualAssignmentApi.ts`, `pages/IndividualAssignments.tsx`; cập nhật `routes/AppRoutes.tsx` cho phân công thật.
- Gỡ luồng cá nhân cũ không còn route: `pages/StudentGoalBank.tsx`, `pages/InterventionPlans.tsx`, `services/interventionService.ts` và test demo tương ứng.
- Kiểm thử mới/cập nhật: `GoalEvaluationWorkflowTests`, `GoalSelectionPolicyTests`, `ImportedCatalogWorkflowTests`, `CatalogReadOnlySmokeTests`, `InterventionWorkflowTests`, `GoalCatalogApiTests`, `CatalogWriteApiTests`; gỡ `StudentGoalBankApiTests`; thêm SQL `tests/evaluations_v11_legacy_before.sql`/`after.sql`, Vitest `goalEvaluationApi.test.ts`, cập nhật `goalBankApi.test.ts`, Playwright `intervention.spec.ts`, `intervention-fixture.ts`, `plan-selection.spec.ts`, `assignments.spec.ts`, `playwright.workflow.config.ts`.
- Tài liệu: README chính/frontend/database, tài liệu V7/V8 được đánh dấu lịch sử; báo cáo hiện tại là file này. V1–V10 vẫn được giữ làm chuỗi migration lịch sử; cài mới chạy đến V11.

## Kiểm thử

- Migration thử trên bản sao dữ liệu thật: giữ 2 kế hoạch/60 mục tiêu, đúng trạng thái mới và archive.
- Migration thử có dữ liệu lịch sử: giữ 3 lần đánh giá, chuyển 50%/80% đúng kết quả, giữ lần chưa có phần trăm, lưu đủ 1 lựa chọn ngân hàng và 1 tiêu chí nguồn. Kiểm tra bằng SQL trả PASS.
- Backend: 11 bài đã đạt (5 luồng đánh giá/phân công, 3 quy tắc Denver/VB, 2 trên catalog import, 1 smoke chỉ đọc DB thật). Kiểm tra tạo nhiều kế hoạch, mục tiêu Denver/VB chung kế hoạch, phân cấp snapshot, các ngưỡng NULL/0/79,99/80/100, đánh giá lại, sửa lần cũ/mới, lịch sử, phạm vi giáo viên, quyền sửa, CSRF, cross-plan và ràng buộc DB.
- Frontend: Vitest 19/19; 4 tình huống Playwright đã đạt, bao phủ chọn nhiều mục tiêu/cấp độ, giữ lựa chọn khi đổi Denver/VB, lưu/chống gửi lặp, sửa kế hoạch, đánh giá 79,99% rồi 80%, lịch sử giao diện xem lại trên màn hình nhỏ, thêm phân công và đổi giáo viên.
- Build React/TypeScript và Maven clean/test đã đạt. Spring Boot đã khởi động lại trên schema V11, `/api/auth/csrf` trả HTTP 200; các class API ngân hàng/trạng thái cũ không còn trong output biên dịch. Các test tạo/sửa dữ liệu backend chạy trên schema sao chép và rollback; smoke trên database thật chỉ đọc. Hai schema sao chép chỉ dùng để kiểm thử đã được dọn sau khi hoàn tất. Database thật vẫn có 3 học sinh, 2 kế hoạch, 60 mục tiêu, 0 đánh giá và 0 lớp; không để lại dữ liệu thử.

## Chạy lại ở môi trường khác

Chạy `powershell -File database/mysql/apply_goal_evaluations.ps1` để kiểm tra. Sau khi dừng các ứng dụng ghi dữ liệu, chạy cùng lệnh với `-Apply`. Script sao lưu trước khi thay schema, không chạy lại nếu đã hoàn tất V11, và dừng khi phát hiện trạng thái schema không đầy đủ. MySQL DDL không có rollback toàn transaction; dùng backup đã xác minh nếu cần phục hồi. Không dùng các script reset V8 hoặc áp dụng V7 trên schema hiện tại.

## Chọn mục tiêu cho kế hoạch tiếp theo

Màn hình lập kế hoạch mới dùng `GET /api/intervention/students/{student}/level-domains/{id}/targets` để lấy lựa chọn riêng của học sinh cùng lịch sử học tập:

- Có đánh giá dưới 80%: hiển thị nhãn nhỏ `Đã học · Tỷ lệ hoàn thành 55%` (theo giá trị thật).
- Đã có trong kế hoạch nhưng chưa có đánh giá bằng phần trăm: `Chưa thực hiện`.
- Chưa từng có trong kế hoạch chưa bị hủy: `Mục tiêu mới`.
- Kết quả gần nhất từ 80% trở lên: ẩn khỏi lựa chọn kế hoạch mới.

Kết quả được xét riêng theo `goal_id`/`specific_goal_id`. Với VB, mục tiêu chính đã đạt có thể vẫn xuất hiện như tiêu đề nhóm không chọn được nếu còn mục tiêu con chưa đạt; mục tiêu con đã đạt luôn bị ẩn. Không thay đổi thư viện dùng chung.

Mỗi mục tiêu lấy lần đánh giá có phần trăm với số lần lớn nhất trong từng bản ghi kế hoạch, sau đó chọn kết quả đo gần nhất qua các kế hoạch chưa bị hủy. Kế hoạch mới chưa thực hiện hoặc một lần đánh giá NULL không xóa kết quả đã đo trước đó; sửa lần đánh giá cũ không thay thế kết quả của lần mới hơn trong cùng kế hoạch. Bước xem lại và lưu kiểm tra lại trên backend, trả 409 nếu lựa chọn có mục tiêu vừa đạt trong lúc đang lập kế hoạch.

Triển khai tại `StudentGoalHistoryService.java`, `InterventionDtos.java`, `InterventionController.java`, `InterventionService.java`, `interventionApi.ts`, `InterventionDomainPicker.tsx`, `RealInterventionPlans.tsx`, `real-intervention.css`. Kiểm thử bổ sung tại `GoalEvaluationWorkflowTests.java`, `interventionApi.test.ts`, `new-plan-history.spec.ts` và fixture/config Playwright. Không cần thay schema hay ghi lại dữ liệu học sinh để phân loại lịch sử.

Kiểm tra sau cập nhật: 8 bài luồng đánh giá/lịch sử và 3 bài quy tắc Denver/VB đã đạt trên database sao chép, Vitest 20/20, 5 tình huống Playwright đã đạt và build frontend thành công. Nút lưu đánh giá có nhãn truy cập ổn định trong lúc chuyển trạng thái loading. Schema kiểm thử được dọn sau khi hoàn tất.
