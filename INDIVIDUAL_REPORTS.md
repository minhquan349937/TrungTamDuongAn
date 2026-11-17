# Báo cáo cá nhân và PDF

Truy cập **Quản lý lớp cá nhân → Báo cáo cá nhân** (`/#/individual/report`). Giáo viên có thêm mục này trong menu công việc. Chọn học sinh để xem tất cả bản nhận xét kế hoạch đã lưu, chỉnh sửa ba phần và xem trước/tải PDF.

Chỉ học sinh có `plan_evaluations` xuất hiện trong danh sách. Mỗi báo cáo dùng `plan_evaluations.id`, kể cả khi có nhiều bản trong cùng tháng. Trang danh sách phân biệt **tháng/năm đánh giá**, **ngày đánh giá** và **kỳ kế hoạch** (`start_date`–`end_date`), không lấy tháng đánh giá làm tháng kế hoạch. Truy cập trực tiếp học sinh được phép nhưng chưa có báo cáo sẽ hiển thị danh sách rỗng.

## API mới

Tất cả nằm dưới `/api/individual-reports`, dùng session và quyền ADMIN/TEACHER; thao tác PUT yêu cầu CSRF. Giáo viên chỉ đọc dữ liệu học sinh đang được phân công theo `PlanAccessService`.

| Method | Đường dẫn | Chức năng |
| --- | --- | --- |
| GET | `/students?q=...` | Tìm học sinh có báo cáo, trả mã/tên/số báo cáo, lọc quyền ngay trong SQL |
| GET | `/students/{studentId}` | Thông tin học sinh và số báo cáo |
| GET | `/students/{studentId}/reports` | Danh sách bản đánh giá riêng biệt, kỳ kế hoạch, ngày/người đánh giá, quyền sửa |
| GET | `/{reportId}` | Chi tiết nhận xét gốc, ba phần đã tách, mục tiêu đúng thời điểm và cảnh báo dữ liệu lịch sử |
| PUT | `/{reportId}/sections` | Ghép ba phần bằng Java, lưu qua `PlanEvaluationService.update` |
| GET | `/{reportId}/pdf` | PDF inline để xem trước |
| GET | `/{reportId}/pdf?download=true` | PDF attachment để tải xuống |

Ví dụ body PUT:

```json
{
  "strengths": "Tiến bộ được giáo viên ghi nhận...",
  "difficulties": "Khó khăn cần hỗ trợ...",
  "supportDirections": "Đề xuất hỗ trợ...",
  "expectedUpdatedAt": "2026-11-12T14:00:00.123456"
}
```

`expectedUpdatedAt` lấy nguyên từ `detail.report.updatedAt`, dùng để từ chối bản chỉnh sửa cũ bằng HTTP 409. Service khóa bản ghi và đọc phiên bản hiện tại bằng locking read trước khi kiểm tra. Thời gian trong JSON là UTC; giao diện/PDF chuyển sang giờ Việt Nam.

Quyền sửa hiện có được giữ nguyên: ADMIN hoặc giáo viên phụ trách **đồng thời là tác giả** của bản nháp. Bản lịch sử `sent_to_parent=1` vẫn khóa chỉnh sửa; không bỏ kiểm tra trong service hay trigger DB. Không thêm nút/API công bố hoặc gửi báo cáo. Không tạo bảng/cột/migration.

## Parser và bảo toàn nhận xét

`ReportSectionParser` nhận dạng tiêu đề thường, số 1/2/3, số La Mã I/II/III, Markdown `**...**`, `__...__`, tiêu đề `###`, xuống dòng CRLF/LF và dấu hai chấm. Một số tên tương đương: Điểm mạnh/Ưu điểm/Những tiến bộ; Khó khăn/Hạn chế/Những khó khăn; Hướng hỗ trợ/Phương hướng hỗ trợ/Đề xuất hỗ trợ.

Chỉ tách khi xác định đủ ba phần đúng thứ tự. Văn bản thiếu phần, tiêu đề lặp/mơ hồ hoặc có lời dẫn ngoài cấu trúc được trả dưới dạng `structured=false` kèm nguyên văn `originalText`, không tự tạo nội dung. Nhận xét có ba tiêu đề đánh số rõ ràng ưu tiên các tiêu đề đánh số; từ “Khó khăn” đứng riêng trong nội dung không bị hiểu nhầm thành tiêu đề mới.

Mở xem/PDF không cập nhật nhận xét. Với văn bản cũ, giáo viên phải chọn rõ thao tác đưa toàn bộ văn bản vào phần I rồi tự phân chia/chỉnh sửa; bản gốc vẫn hiển thị để đối chiếu. Khi lưu, Java ghép theo mẫu:

```text
I. Tiến bộ
...

II. Khó khăn cần hỗ trợ
...

III. Định hướng hỗ trợ
...
```

Giao diện giữ nội dung khi API lưu lỗi, yêu cầu xác nhận trước khi tải lại bản đã lưu khi có thay đổi chưa lưu, và khóa nút PDF cho đến khi đã lưu chỉnh sửa. Có xác nhận khi quay lại/bấm liên kết rời trang và cảnh báo khi đóng/tải lại tab.

## Kết quả mục tiêu theo kỳ

- Mốc lấy dữ liệu là thời điểm `plan_evaluations.evaluated_at`; nếu kế hoạch đã có ngày kết thúc sớm hơn, giới hạn tại cuối ngày kết thúc theo múi giờ Việt Nam.
- Chỉ lấy `plan_goals` của kế hoạch đó và đã được giao trước mốc này. Loại mục tiêu cũ chỉ khi bản thay thế đã được giao trước mốc; thay thế sau đó không làm thay đổi tập mục tiêu của báo cáo cũ.
- Nội dung mục tiêu/lĩnh vực lấy từ `goal_snapshot` bất biến, không lấy tên hiện tại trong danh mục để thay đổi nội dung lịch sử.
- Mỗi mục tiêu lấy lần đánh giá có `attempt_no` cao nhất trong các lần đã được tạo tại mốc báo cáo, không lấy lần đánh giá được tạo ở tháng sau. Không có lần đánh giá là **Chưa thực hiện**; tỷ lệ NULL cũng giữ trạng thái **Chưa thực hiện**, phân biệt **Không đạt**.
- Schema hiện tại cho phép sửa trực tiếp một lần đánh giá và không lưu phiên bản trước sửa. Nếu `updated_at` hoặc `evaluated_at` của lần được chọn sau mốc báo cáo, service trả **Không đủ dữ liệu lịch sử**, bỏ tỷ lệ/ghi chú mới và hiển thị cảnh báo. Không dùng kết quả mới hoặc tự suy đoán bản cũ. Muốn tái dựng tuyệt đối nội dung đã bị ghi đè cần lịch sử/snapshot ngoài schema hiện tại, nằm ngoài phạm vi yêu cầu này.
- Tên học sinh, tên kế hoạch và ngày bắt đầu/kết thúc lấy từ các bản ghi hiện có; schema không có snapshot lịch sử riêng của các trường thông tin này.

## PDF

PDF A4 dọc được tạo trong backend bằng [Apache PDFBox](https://pdfbox.apache.org/), dùng font Noto Sans Regular/Bold đóng gói trong ứng dụng cùng giấy phép OFL. Không cần font cài trên máy chủ hay kết nối internet khi xuất.

Bố cục gồm biểu tượng mầm cây theo nhận diện giao diện hiện tại, tên Trung tâm Đường An, thông tin học sinh/giáo viên/kỳ kế hoạch, bảng mục tiêu theo lĩnh vực, ba phần nhận xét và khối chữ ký. Văn bản cũ chưa tách được được xuất dưới mục “Nhận xét đã lưu” để giữ nguyên nội dung. Header/footer và số trang lặp trên từng trang; hàng bảng và khối chữ ký không cắt ngang trang. Hàng mục tiêu quá dài hơn một trang được chia thành các hàng tiếp nối có chỉ dẫn, giữ toàn bộ nội dung. Nội dung dài tự xuống dòng/ngắt trang.

PDF lấy chi tiết đã lưu; không gọi Gemini. Response dùng `Cache-Control: no-store`, `application/pdf`, `Content-Disposition` inline/attachment và tên file theo mã báo cáo. Không nhúng cấu hình API/key hoặc đường dẫn máy chủ.

## File thêm/sửa

| File | Thay đổi |
| --- | --- |
| `src/main/java/com/example/TTGDHNDuongAn/service/ReportSectionParser.java` | Mới: tách/ghép ba phần, giữ văn bản gốc khi không tách được |
| `src/main/java/com/example/TTGDHNDuongAn/service/IndividualReportService.java` | Mới: danh sách, phân quyền, truy vấn lịch sử, cập nhật qua nghiệp vụ hiện có |
| `src/main/java/com/example/TTGDHNDuongAn/service/IndividualReportPdfService.java` | Mới: layout PDF A4, tiếng Việt, phân trang, bảng, chữ ký |
| `src/main/java/com/example/TTGDHNDuongAn/controller/IndividualReportController.java` | Mới: API báo cáo, preview/download, thông báo lỗi |
| `src/main/java/com/example/TTGDHNDuongAn/dto/IndividualReportDtos.java` | Mới: DTO danh sách, chi tiết, phiên bản và nội dung sửa |
| `src/main/java/com/example/TTGDHNDuongAn/security/SecurityConfig.java` | Cho phép route báo cáo mới với ADMIN/TEACHER |
| `src/main/resources/report-fonts/NotoSans-Regular.ttf`, `NotoSans-Bold.ttf`, `OFL.txt` | Mới: font tiếng Việt và giấy phép |
| `pom.xml` | Thêm PDFBox 3.0.8 |
| `frontend/src/pages/IndividualReports.tsx`, `individual-reports.css` | Mới: ba màn hình báo cáo và PDF preview |
| `frontend/src/services/individualReportApi.ts` | Mới: API JSON/session/CSRF, tải Blob PDF và xử lý lỗi |
| `frontend/src/routes/AppRoutes.tsx` | Gắn route `/individual/report/...` |
| `frontend/src/routes/menu.ts` | Thêm mục báo cáo cho giáo viên; menu quản trị đã có sẵn |
| `src/test/java/com/example/TTGDHNDuongAn/service/ReportSectionParserTests.java` | Mới: 13 test parser/ghép nội dung |
| `src/test/java/com/example/TTGDHNDuongAn/service/IndividualReportPdfTests.java` | Mới: 3 test PDF, xuất ảnh mẫu để kiểm tra trực quan |
| `src/test/java/com/example/TTGDHNDuongAn/catalog/IndividualReportMysqlTests.java` | Mới: 9 test MySQL có rollback, lịch sử/phân quyền/sửa/PDF |
| `frontend/tests/individual-reports.spec.ts`, `frontend/playwright.reports.config.ts` | Mới: 4 test trình duyệt với API giả lập |
| `INDIVIDUAL_REPORTS.md` | Tài liệu triển khai và kết quả kiểm thử |

## Kết quả thực tế ngày 12/11/2026

- Backend `mvnw.cmd -q package` với `INDIVIDUAL_REPORT_TEST_DB_URL` trỏ tới MySQL 8.4 cục bộ: build thành công; 134 test được phát hiện, **61 đạt, 73 bỏ qua**, không có lỗi. Các test bỏ qua thuộc nhóm tích hợp có sẵn, cần biến môi trường riêng.
- Phần báo cáo mới: **25/25 đạt** — parser 13, PDF 3, MySQL 9. Dữ liệu kiểm thử học sinh/báo cáo là giả lập, các thay đổi DB trong test được rollback.
- MySQL xác nhận hai báo cáo cùng tháng không gộp; học sinh không có báo cáo bị loại khỏi danh sách; đánh giá lại sau báo cáo không bị đưa vào bản cũ; kết quả đã sửa không thể tái dựng được báo thiếu lịch sử; mục tiêu thay thế đúng thời điểm; giới hạn cuối kỳ theo giờ Việt Nam; giáo viên chỉ đọc/sửa theo phân công và tác giả; bản đã khóa vẫn khóa; người không có quyền bị 401/403; ID thiếu trả 404; PUT thiếu CSRF bị chặn; lưu ba phần không đổi mục tiêu/trạng thái kế hoạch; phiên bản cũ bị 409; PDF không sửa văn bản gốc.
- Frontend `npm test`: **25/25 đạt**.
- `npx playwright test --config playwright.reports.config.ts`: **4/4 đạt**, gồm tìm kiếm, danh sách hai bản cùng tháng, chỉnh sửa/lưu/xem trước/tải file, giữ văn bản cũ, lưu lỗi giữ nội dung, bản khóa chỉ đọc, trạng thái rỗng và lỗi API.
- `npm run build`: thành công; Vite có cảnh báo kích thước bundle lớn của ứng dụng.
- Đã kiểm tra ảnh trang đầu và trang cuối của PDF giả lập 6 trang: tiếng Việt hiển thị đúng, bảng không chồng chữ/footer, chữ ký cùng trang. Các artifact nằm trong `target/report-pdf-preview/`: `synthetic-individual-report.pdf`, `first-page.png`, `last-page.png` (không được Git theo dõi).
- Không gọi Gemini trong các test báo cáo/PDF và không sửa module Gemini.

Chạy lại kiểm thử:

```powershell
$env:INDIVIDUAL_REPORT_TEST_DB_URL='jdbc:mysql://127.0.0.1:3308/duong_an_v11?useUnicode=true&characterEncoding=utf8&serverTimezone=UTC'
.\mvnw.cmd -q package
cd frontend
npm test
npx playwright test --config playwright.reports.config.ts
npm run build
```

DB kiểm thử cần schema hiện tại và ít nhất ba mục tiêu hoạt động trong danh mục. Cần khởi động lại backend sau khi cập nhật để nạp API và thư viện PDF mới.
