# Kết quả kiểm thử — 27/10/2026

Đã chạy trên Windows với Node.js, Chrome headless và MySQL Docker.

| Kiểm tra | Kết quả |
| --- | --- |
| `npm run build` — TypeScript và Vite production | Đạt |
| `npm test` — Vitest | 19/19 đạt |
| `npm run test:e2e` — Playwright | 8/8 đạt |
| `database/mysql/test_goal_bank.ps1` — Maven + MySQL V1–V7 trên database tạm | 35 đạt; 1 bài đọc DB thật bỏ qua có chủ đích |
| `CatalogReadOnlySmokeTests` — API mới trên Docker 3308, chỉ đọc | 1/1 đạt |

Playwright kiểm tra đăng nhập và phân quyền bằng API giả lập chỉ trong test, sáu luồng giao diện cũ với dữ liệu LocalStorage riêng của test, một luồng giao kế hoạch, và một luồng ngân hàng theo học sinh. Kiểm tra tạo/chọn học sinh, chương trình/cấp độ/lĩnh vực, chọn Denver chính, VB con và VB không con; giữ lựa chọn qua các độ tuổi/lĩnh vực; xem lại chưa ghi dữ liệu; hoàn tất và tải lại ngân hàng; giao mục tiêu vào kế hoạch và bố cục mobile. Không có tài khoản hay hồ sơ mẫu được tạo trong database nghiệp vụ.

Kiểm thử backend dùng database MySQL tạm, áp dụng V1–V7. Quy trình phục hồi backup V4 rồi nâng cấp V5–V6 đã được kiểm tra trước đó. Xác minh catalog, phân trang, lỗi 400/404/409, FK cha/con, tuổi, snapshot, tiến độ, ID đồng thời, hết dải ID, TEXT nguyên vẹn và quyền ghi. Bổ sung kiểm tra Denver không gọi truy vấn con, VB cùng cha chỉ tải một lần mỗi lượt xác minh, xem lại chưa ghi, token cũ/sai học sinh, lưu lặp/đồng thời và CSRF. Script tự xóa database tạm khi kết thúc. Ngày 27/10/2026 đã áp dụng V7 trên local 3306 và Docker 3308; kiểm thử Spring Boot chỉ đọc chạy riêng trên từng kết nối đều đạt. Playwright còn kiểm tra phản hồi cấp độ đến muộn không ghi đè lựa chọn chương trình mới, hợp đồng `goalId/specificGoalId` và danh sách con chỉ hiện khi có dữ liệu.

Không kiểm định nội dung chuyên môn hoặc tự chạy lại file import người dùng. Sau COMMIT, Docker có Denver 445 mục tiêu chính và VB 170 mục tiêu chính/738 mục tiêu con. API đọc trực tiếp dữ liệu nhập đã được kiểm tra; các ca ghi dữ liệu chạy trên DB tạm. Xem [luồng V7](../database/mysql/GOAL_BANK_FLOW.md). Các màn nghiệp vụ khác ngoài kế hoạch can thiệp vẫn sử dụng LocalStorage và chưa có API thật.

**Luồng 5 trang mới:** Playwright đã kiểm tra danh sách học sinh và thống kê, 5 tab hồ sơ, bảng chương trình, chọn tuổi riêng theo từng lĩnh vực, chọn nhiều mục tiêu qua các tuổi, xem lại theo mục lục, quay lại giữ lựa chọn, hoàn thành và tải lại. Bao gồm VB tải con khi mở mục tiêu chính, VB không con chọn chính, mobile và retry khi mất phản hồi hoàn tất. Backend mới kiểm tra rollback cả `learning_plans`/`plan_goals` khi lưu mục tiêu thứ hai lỗi, gửi đồng thời chỉ tạo một kế hoạch, metadata/token, snapshot và thống kê theo trạng thái. Kiểm tra chỉ đọc trên Docker xác nhận truy vấn học sinh, workspace và lĩnh vực từ dữ liệu thật. Chi tiết trong [INTERVENTION_WORKFLOW.md](../database/mysql/INTERVENTION_WORKFLOW.md).
