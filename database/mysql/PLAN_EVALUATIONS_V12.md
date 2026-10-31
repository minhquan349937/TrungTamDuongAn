# Nhận xét kế hoạch học tập hằng tháng (V12)

## Database

`V12__plan_evaluations.sql` thêm `plan_evaluations` vào `duong_an_v11`. Trước khi chạy đã kiểm tra `learning_plans.id` và `app_user.id`: cả hai là `CHAR(36) CHARACTER SET ascii COLLATE ascii_bin`; FK của bảng mới dùng đúng kiểu này. Schema do các file SQL phiên bản quản lý, Hibernate chỉ `validate`; dự án không dùng Flyway/Liquibase.

| Cột | Ý nghĩa |
| --- | --- |
| `id` | UUID, khóa chính |
| `learning_plan_id` | FK đến kế hoạch; nhiều nhận xét trên một kế hoạch |
| `evaluation_notes` | Nội dung giáo viên viết, không tự sinh từ kết quả mục tiêu |
| `evaluated_by`, `evaluated_at` | Người và thời điểm tạo bản nháp |
| `sent_to_parent`, `sent_at` | Đã công bố trong cổng phụ huynh và thời điểm công bố |
| `updated_at` | Tự động cập nhật |

Không có `created_at`, số lượng mục tiêu, tỷ lệ hoàn thành hoặc trạng thái đạt/không đạt. CHECK giữ `sent_at` khớp cờ công bố. Trigger từ chối cập nhật/xóa nhận xét đã công bố và ngăn đổi định danh/tác giả/thời điểm tạo. Có index riêng trên `learning_plan_id` và `evaluated_at`; không có unique trên kế hoạch. Không thay đổi schema/quan hệ mục tiêu, catalog Denver/VB hoặc quan hệ N–N cấp độ–lĩnh vực.

Chạy `./database/mysql/apply_plan_evaluations.ps1` để kiểm tra, thêm `-Apply` để sao lưu và áp dụng. Đã chạy V12 trên MySQL Docker `127.0.0.1:3308` ngày 29/10/2026. Bản sao lưu: `database/.backups/duong_an_v11_before_plan_evaluations_v12_20261029_103947.sql`, SHA256 `BCCC562F0BEEA84F651169F101C84AB9171C4A1665823A6E9A7FA009F051DDBA`. Số bản ghi trước/sau: 3 học sinh, 2 kế hoạch, 60 mục tiêu kế hoạch, 4 đánh giá mục tiêu. Bảng mới ban đầu có 0 bản ghi.

## Quyền và luồng

- Giáo viên được phân công học sinh và ADMIN tạo nhận xét; tác giả hoặc ADMIN sửa/công bố bản nháp.
- Phụ huynh chỉ xem nhận xét đã công bố nếu tài khoản `guardian.user_id` đang hoạt động và liên kết `student_guardian` còn hiệu lực, có `can_view_portal=1`.
- Nút **Công bố trong cổng phụ huynh** chuyển trạng thái trong cùng transaction sau khi xác nhận có tài khoản phụ huynh đủ quyền. Đây là công bố trong ứng dụng, không gửi email/SMS.
- Kế hoạch giữ nguyên trạng thái khi công bố. Giáo viên vẫn có thể sửa thông tin kế hoạch, thêm mục tiêu và đánh giá mục tiêu sau đó. Nội dung nhận xét cũ không thay đổi theo kết quả mục tiêu mới; muốn bổ sung phải tạo bản nhận xét khác.

API: `POST/GET /api/learning-plans/{planId}/evaluations`, `GET/PUT /api/plan-evaluations/{id}`, `POST /api/plan-evaluations/{id}/send`, `GET /api/parent/plan-evaluations`. Các thao tác ghi yêu cầu CSRF.

Frontend: nhận xét hằng tháng nằm trong chi tiết từng kế hoạch; phụ huynh đọc tại `/#/reports`; giáo viên thêm hoặc thay mục tiêu ở chi tiết kế hoạch. V13 điều chỉnh trigger `plan_goals_snapshot_insert` để dùng khóa `supersedes_id` hiện có cho việc thay mục tiêu: bản ghi cũ cùng toàn bộ lịch sử đánh giá vẫn ở database, danh sách kế hoạch hiển thị mục tiêu mới. V13 không thay đổi cột/bảng nào khác. Chạy `apply_plan_goal_replacement.ps1` sau V12 để kiểm tra, thêm `-Apply` để sao lưu và áp dụng.

Trong hồ sơ kế hoạch của học sinh, tab **Đánh giá kế hoạch** cho chọn tất cả kế hoạch, kể cả kế hoạch hiện tại, để đánh giá từng mục tiêu và nhận xét chung ở cuối. Mỗi mục tiêu hiển thị tỷ lệ của lần đánh giá gần nhất từ API workspace. Form nhận xét chung có tên kế hoạch, tên học sinh, ngày áp dụng, ngày đánh giá gần nhất, tên người đánh giá và nhận xét giáo viên. Nút cuối là **Đánh giá kế hoạch** khi chưa có nhận xét và **Chỉnh sửa đánh giá** khi đã có; sửa bản đã công bố tạo bản nháp mới để giữ nguyên bản cũ. Tab **Kế hoạch hiện tại** và **Lịch sử kế hoạch** không có thao tác đánh giá mục tiêu hoặc nhận xét chung. Điều chỉnh giao diện này không cần migration.

Tab đánh giá có hai nút **Tạm dừng kế hoạch** (đỏ) và **Hoàn thành kế hoạch** (xanh lá), đều mở xác nhận. `PATCH /api/plans/{id}/status` kiểm tra quyền phụ trách và chỉ cho chuyển PAUSED/COMPLETED; COMPLETED yêu cầu ít nhất một nhận xét chung đã lưu (bản nháp hoặc đã công bố). Điều kiện này cũng áp dụng cho API sửa kế hoạch để không thể bỏ qua bằng cách sửa trạng thái trực tiếp. Đánh giá từng mục tiêu riêng lẻ không thay thế nhận xét chung. Ngày hoàn thành thực tế theo giờ Việt Nam được lưu vào `learning_plans.end_date`; thao tác công bố nhận xét không tự hoàn thành kế hoạch.

Lịch sử kế hoạch hiển thị bảng tên kế hoạch, thời gian dạy, trạng thái, công bố cho phụ huynh và xem đánh giá. Chi tiết có ngày tạo kế hoạch, học sinh, giáo viên, ngày đánh giá, nhận xét và lựa chọn các lần nhận xét. Bảng mục tiêu dùng kết quả mới nhất: NULL → Chưa thực hiện, dưới 80% → Lặp lại mục tiêu, từ 80% → Đã hoàn thành. Nội dung nhận xét cũ vẫn giữ nguyên. Phần này dùng các cột hiện hữu, không chạy migration hoặc đổi dữ liệu nghiệp vụ khi triển khai.

Đã áp dụng V13 ngày 29/10/2026, sau khi thử trên bản sao database. Bản sao lưu: `database/.backups/duong_an_v11_before_plan_goal_replacement_v13_20261029_105605.sql`, SHA256 `567AC20A2D8D63985C1B0F10CFE26459463ED4D02DE1E6415D27AD1B763FFD42`.

## Kiểm thử

- `PlanEvaluationWorkflowTests` chạy trên database sao chép, mọi dữ liệu thử được rollback: nhiều kế hoạch/mục tiêu/nhận xét; sửa nháp; quyền giáo viên và phụ huynh; công bố; kế hoạch vẫn ACTIVE; thêm mục tiêu và đổi kết quả đánh giá sau công bố không sửa báo cáo cũ; ràng buộc và trigger chống sửa/xóa báo cáo đã công bố; không công bố khi chưa có tài khoản phụ huynh đủ quyền; tạm dừng/hoàn thành, điều kiện đánh giá chung và ngày kết thúc thực tế. Kết quả: 5/5.
- Chạy lại `GoalEvaluationWorkflowTests` sau V13: 8/8, gồm lịch sử nhiều lần đánh giá và quy tắc chọn mục tiêu.
- `frontend/tests/plan-reports.spec.ts`: giao diện tạo/sửa/công bố, trang phụ huynh, xác nhận tạm dừng/hoàn thành và lịch sử kế hoạch. Cùng bộ hồi quy Playwright: 8/8.
- `npm test`: 20/20; `npm run build` và `git diff --check` đạt.

## File đã thêm hoặc sửa cho V12–V13

- Database: `migrations/V12__plan_evaluations.sql`, `migrations/V13__replace_plan_goal.sql`, `apply_plan_evaluations.ps1`, `apply_plan_goal_replacement.ps1`, `README.md`, `PLAN_EVALUATIONS_V12.md`, `../../compose.yaml`.
- Backend: `model/plan/{LearningPlanEntity,AppUserEntity,PlanEvaluation}.java`, `repository/PlanEvaluationJpaRepository.java`, `dto/PlanEvaluationDtos.java`, `service/PlanEvaluationService.java`, `controller/PlanEvaluationController.java`, `security/SecurityConfig.java`, `controller/CatalogExceptionHandler.java`, `controller/{PlanGoalController,PlanProgressController}.java`, `service/{PlanGoalService,PlanProgressService}.java`, `repository/{PlanGoalRepository,PlanProgressRepository}.java`, `test/catalog/PlanEvaluationWorkflowTests.java` (các đường dẫn backend tương ứng dưới `src/main/java/com/example/TTGDHNDuongAn/`, kiểm thử dưới `src/test/java/com/example/TTGDHNDuongAn/`).
- Frontend: `src/pages/{PlanAssessmentWorkspace,PlanMonthlyEvaluations,PlanGoalAdder,PlanProgress,RealInterventionPlans}.tsx`, `src/pages/intervention-workflow.css`, `src/services/{planEvaluationApi,goalBankApi}.ts`, `src/routes/{AppRoutes,menu}.tsx/ts`, `src/services/authorization.ts`, `tests/{plan-reports.spec,intervention-fixture}.ts`, `playwright.workflow.config.ts`, `README.md` (các đường dẫn này dưới `frontend/`). README gốc cũng được cập nhật.
