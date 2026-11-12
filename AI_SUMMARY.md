# Gemini hỗ trợ nhận xét kế hoạch

`POST /api/learning-plans/{planId}/ai-summary` nhận `{"keywords":"..."}` (được phép rỗng hoặc bỏ thuộc tính) và trả `{"generatedText":"..."}`. Endpoint dùng session, CSRF và quyền ADMIN hoặc giáo viên đang phụ trách học sinh theo `PlanAccessService`. API lưu nhận xét hiện có giữ nguyên.

## Cấu hình và dữ liệu

Thiết lập các biến phía backend, sau đó khởi động lại ứng dụng:

```dotenv
GEMINI_ENABLED=false
GEMINI_API_KEY=
GEMINI_MODEL=gemini-3.5-flash-lite
GEMINI_TIMEOUT_SECONDS=30
GEMINI_REQUESTS_PER_USER_HOUR=10
GEMINI_REQUESTS_PER_HOUR=100
```

Chỉ đặt `GEMINI_ENABLED=true` sau khi trung tâm đáp ứng điều kiện bảo vệ dữ liệu đối với dịch vụ bên ngoài. Khóa được cung cấp trong cuộc trò chuyện không được lưu vào mã nguồn hoặc frontend và không được dùng trong kiểm thử này. Điền khóa hợp lệ qua môi trường triển khai hoặc `.env` đã được Git bỏ qua. Không bật log HTTP body/header hoặc TRACE request payload trong môi trường có dữ liệu thật.

Payload gửi Gemini chỉ gồm trạng thái kế hoạch, tên/mô tả mục tiêu, tỷ lệ/trạng thái/ghi chú của lần đánh giá mới nhất và ghi nhận của giáo viên. Không gửi tên/ID học sinh, ngày sinh, tên/ID kế hoạch, người đánh giá, thông tin liên hệ hoặc toàn bộ hồ sơ. **Ghi chú tự do vẫn có thể chứa thông tin định danh**: giáo viên cần loại bỏ các thông tin này trong ghi nhận và ghi chú mục tiêu trước khi dùng. Đây là giảm thiểu dữ liệu, không phải cơ chế bảo đảm ẩn danh hoàn toàn. Chỉ dùng dữ liệu giả lập khi thử free tier.

Truy vấn lấy đúng `plan_id`, bỏ các mục tiêu đã có bản thay thế cùng kế hoạch, chọn `MAX(attempt_no)` theo quy ước hiện tại của ứng dụng. Không cộng dồn các lần đánh giá. Mục tiêu không có đánh giá hoặc có lần mới nhất với tỷ lệ NULL được gửi dưới trạng thái `NOT_STARTED`, khác `NOT_ACHIEVED`. Tỷ lệ 80% trở lên là `ACHIEVED`, dưới 80% là `NOT_ACHIEVED`, theo cột sinh sẵn trong MySQL. Không gửi lịch sử để tránh khẳng định tiến bộ theo thời gian khi không có dữ liệu so sánh.

Prompt yêu cầu tiếng Việt 200–300 từ, ba phần Tiến bộ / Khó khăn cần hỗ trợ / Định hướng hỗ trợ, không chẩn đoán hay tự đánh giá lại mục tiêu. Dữ liệu tự do được đóng gói JSON và prompt yêu cầu bỏ qua chỉ dẫn nằm trong dữ liệu. Giáo viên vẫn phải rà soát tính chính xác của nội dung AI.

Kết nối có timeout 5 giây, mỗi request mặc định 30 giây; không tự retry và không tự đổi model. Giới hạn đầu vào 2.000 ký tự ghi nhận, 60.000 ký tự dữ liệu kế hoạch, 2.048 output tokens. Phản hồi bị chặn, thiếu, JSON lỗi hoặc bị cắt ngắn không được sử dụng. Các lỗi có thông báo tiếng Việt, không trả body thô của Gemini:

| Trường hợp | HTTP trả cho frontend |
| --- | --- |
| Gemini 404 / lỗi model | 502 |
| Gemini 429 hoặc hết lượt nội bộ | 429 |
| Gemini 503 / chưa bật hoặc thiếu khóa | 503 |
| Timeout | 504 |
| Không có quyền / kế hoạch không tồn tại | 403 / 404 |

Quota tính cả lượt gọi thất bại, dùng cửa sổ giờ UTC cố định và bộ nhớ của từng tiến trình. Quota đặt lại khi restart; triển khai nhiều instance cần bộ đếm chung hoặc giới hạn ở gateway để giữ tổng chi phí. Không có bảng mới. Snapshot DB kết thúc transaction đọc trước khi gọi dịch vụ ngoài; không giữ transaction trong thời gian đợi AI.

AI không lưu vào DB khi tạo. Giao diện hiển thị bản đề xuất riêng; chỉ đưa vào ô nhận xét khi bấm “Sử dụng nhận xét này”, có xác nhận thay thế nội dung đang nhập. Giáo viên chỉnh sửa rồi bấm nút lưu hiện có. Không thêm thao tác công bố, gửi phụ huynh, xuất PDF, RAG hoặc fine-tuning.

REST request dùng `systemInstruction`, `contents` và header `x-goog-api-key` theo [tài liệu GenerateContent của Google](https://ai.google.dev/api/generate-content?hl=en).

## Các file của thay đổi này

| File | Thay đổi |
| --- | --- |
| `src/main/java/com/example/TTGDHNDuongAn/controller/AiSummaryController.java` | Mới: endpoint, DTO che nội dung trong toString, thông báo lỗi an toàn |
| `src/main/java/com/example/TTGDHNDuongAn/service/AiSummaryDataService.java` | Mới: phân quyền, snapshot chỉ đọc và truy vấn lần đánh giá mới nhất |
| `src/main/java/com/example/TTGDHNDuongAn/service/GeminiSummaryClient.java` | Mới: REST client, prompt, timeout, kiểm tra response |
| `src/main/java/com/example/TTGDHNDuongAn/service/AiSummaryQuota.java` | Mới: quota tài khoản và toàn tiến trình |
| `src/main/java/com/example/TTGDHNDuongAn/security/SecurityConfig.java` | Thêm quyền cho endpoint AI |
| `src/main/resources/application.properties`, `.env.example` | Thêm cấu hình môi trường, không có khóa thật |
| `pom.xml` | H2 chỉ dùng trong test để kiểm thử SQL độc lập |
| `frontend/src/pages/PlanAiSummary.tsx` | Mới: từ khóa, loading, đề xuất, xác nhận thay thế |
| `frontend/src/pages/PlanMonthlyEvaluations.tsx` | Gắn trợ lý AI vào form đánh giá hiện có |
| `frontend/src/services/planEvaluationApi.ts` | Thêm phương thức gọi AI qua cơ chế session/CSRF hiện có |
| `src/test/java/com/example/TTGDHNDuongAn/service/AiSummaryControllerTests.java` | Mới: endpoint, role, CSRF, lỗi và validation |
| `src/test/java/com/example/TTGDHNDuongAn/service/AiSummaryDataTests.java` | Mới: SQL H2, latest attempt, chuỗi thay thế, tách kế hoạch, không thay đổi dữ liệu |
| `src/test/java/com/example/TTGDHNDuongAn/service/GeminiSummaryClientTests.java` | Mới: HTTP server giả lập Gemini, thành công, lỗi, timeout, quota |
| `src/test/java/com/example/TTGDHNDuongAn/catalog/AiSummaryMysqlTests.java` | Mới: MySQL thực với dữ liệu giả lập, rollback, phân quyền và lưu nội dung đã sửa |
| `frontend/tests/ai-summary.spec.ts`, `frontend/playwright.ai.config.ts` | Mới: kiểm thử trình duyệt, mock API, không cần backend |
| `AI_SUMMARY.md` | Tài liệu cấu hình, giới hạn và báo cáo kiểm thử |

Các thay đổi khác có sẵn trong workspace được giữ nguyên.

## Kiểm thử thực tế (10/11/2026)

- `npm run build` trong `frontend`: thành công (có cảnh báo kích thước bundle).
- `npm test` trong `frontend`: 25/25 thành công.
- `npx playwright test --config playwright.ai.config.ts`: 2/2 thành công, gồm loading, không ghi đè, hủy/xác nhận thay thế, sửa và lưu, giữ nguyên dữ liệu khi lỗi 429, thử lại với ghi nhận rỗng.
- `mvnw.cmd -q test`: 36 thành công, 71 bỏ qua theo điều kiện biến môi trường của các test tích hợp có sẵn, không có lỗi tại thời điểm chạy bộ test này.
- Các test AI độc lập gồm 16 test: 4 controller/security, 3 truy vấn H2, 9 client/quota. Bao phủ từ khóa rỗng/ngắn; mục tiêu đạt/chưa đạt/chưa thực hiện; lần đánh giá lại và chuỗi thay thế; Gemini 404/429/503; timeout; phản hồi lỗi; không gọi Gemini khi không có quyền.
- Test MySQL: 2/2 thành công, dùng `AI_SUMMARY_TEST_DB_URL` trỏ đến MySQL 8.4 cục bộ; các bản ghi giả lập được rollback. Xác nhận lấy lần mới nhất, bỏ mục tiêu được thay thế, không lưu ngay khi tạo AI, lưu nội dung đã sửa qua API hiện có, không đổi mục tiêu/trạng thái kế hoạch. Giáo viên chỉ được dùng sau khi phân công và bị từ chối sau khi tài khoản bị vô hiệu hóa. Client Gemini được mock nên không có dữ liệu gửi ra ngoài.

Chạy test MySQL trên DB kiểm thử đã áp dụng schema hiện tại và có ít nhất bốn mục tiêu trong danh mục:

```powershell
$env:AI_SUMMARY_TEST_DB_URL='jdbc:mysql://127.0.0.1:3308/duong_an_v11?useUnicode=true&characterEncoding=utf8&serverTimezone=UTC'
.\mvnw.cmd -q '-Dtest=AiSummaryMysqlTests,AiSummaryControllerTests,AiSummaryDataTests,GeminiSummaryClientTests' test
```

Chưa gọi Gemini thật, chưa xác minh khóa/hạn mức/quyền truy cập model trên tài khoản triển khai và chưa đánh giá chất lượng văn bản của model thật. Kiểm thử thành công về nội dung trả về ở đây sử dụng phản hồi giả lập.
