# Chọn chiến lược khi tạo kế hoạch

Trang `/individual/plan/{studentId}/new/targets` có mục Chiến lược ở từng
mục tiêu chính và mục tiêu cụ thể. Mỗi mục tiêu chọn được nhiều chiến lược;
chọn chiến lược tự chọn luôn mục tiêu đó. Bỏ chọn mục tiêu sẽ bỏ các chiến
lược của mục tiêu khỏi bản nháp. Khi chỉ bỏ chiến lược cuối cùng, mục tiêu
vẫn được giữ. Chiến lược là tùy chọn.

Danh mục tải một lần cho trang qua `GET /api/intervention/strategies`, dành
cho ADMIN/TEACHER. Chỉ trả chiến lược đang hoạt động, theo display_order/ID.
Có trạng thái đang tải, danh mục trống, lỗi và nút Thử lại.

Các request preview/complete hiện có nhận thêm `strategies` trong từng
selection: `{strategyId, description}`. Server đọc tên từ danh mục, không
tin tên do client gửi. Mô tả chỉ có khoảng trắng được lưu NULL. Review token
bao gồm chiến lược và cách áp dụng; sửa chúng phải xem trước lại. Gửi lại
cùng yêu cầu hoàn tất không tạo thêm kế hoạch hoặc liên kết chiến lược;
gửi lại với chiến lược khác trả 409.

Kế hoạch, mục tiêu và chiến lược được lưu trong một transaction. Nếu bất kỳ
liên kết chiến lược nào lưu lỗi, toàn bộ kế hoạch mới được rollback. API đọc
mục tiêu trả cả chiến lược đã lưu; giao diện hiển thị chúng ở bước xem trước
và kế hoạch đã lưu. Bản ghi cũ không có chiến lược vẫn hoạt động.

Schema cần V15. V16 chuyển toàn bộ ID là tùy chọn: ứng dụng vẫn dùng UUID
và đường dẫn học sinh hiện tại trước khi chuyển đổi. Trong trường hợp đó,
ID danh mục/chiến lược dùng bộ đếm catalog_id_sequences hiện có; sau V16,
dùng entity_id_sequences. Không tự động đổi ID hoặc chạy V16 khi dùng tính
năng chọn chiến lược.

V17 bổ sung hai ví dụ người dùng đã yêu cầu: CL001 — Làm mẫu và CL002 — Chờ
đợi, chỉ khi danh mục hoàn toàn trống. Không ghi đè danh mục đang có. Nếu V16
đã chạy, migration đồng bộ bộ đếm strategies. Không thêm màn hình sửa danh
mục trong đợt này.
V17 cũng bổ sung trigger chuẩn hóa mô tả nếu trước đó V15 chỉ được tạo bảng
thủ công; giữ nguyên trigger đã tồn tại.

Đã kiểm tra lưu, chuẩn hóa NULL, lựa chọn độc lập giữa các mục tiêu, gửi lặp,
thay đổi sau review, chiến lược trùng/ngừng sử dụng và rollback trên bản sao
schema trước/sau V16. Kiểm thử trình duyệt kiểm tra mục tiêu chính/cụ thể,
đổi độ tuổi, quay lại từ bước xem trước, giữ mô tả, bỏ chọn và thử lại khi
tải lỗi.
