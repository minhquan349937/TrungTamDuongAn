# Website giới thiệu và quản lý nội dung (V14)

## Truy cập

| Khu vực | Đường dẫn | Ai dùng |
| --- | --- | --- |
| Website công khai | `/#/gioi-thieu` | Mọi người, không đăng nhập; chỉ thấy bản đã xuất bản |
| Xem trước bản nháp | `/#/gioi-thieu/xem-truoc` | Chỉ ADMIN đã đăng nhập (nút "Xem trước bản nháp") |
| Quản lý website | `/#/website` (menu **Quản lý hệ thống → Quản lý website**) | Chỉ ADMIN |

Ứng dụng dùng `HashRouter`, nên trang `/` và các màn quản lý cũ giữ nguyên; website công khai nằm ở đường dẫn riêng `#/gioi-thieu`.

## Migration

Chạy một lần, chỉ thêm bảng và nội dung mẫu, không đổi bảng cũ:

```powershell
.\database\mysql\backup_database.ps1 -Label before_website_v14
Get-Content database\mysql\migrations\V14__website.sql -Raw | docker compose exec -T -e "MYSQL_PWD=<mật khẩu root trong .env>" mysql mysql -uroot -Dduong_an_v11 --default-character-set=utf8mb4
```

Volume Docker mới tự nạp V14 (đã khai báo trong `compose.yaml`). Chạy lại V14 là an toàn: bảng đã có được bỏ qua, nội dung mẫu chỉ thêm vào bảng còn trống.

Bảng mới: `website_settings`, `website_sections`, `website_section_items`, `website_services`, `website_team_members`, `website_albums`, `website_media`, `website_faqs`, `website_publications`, `website_publication_media`, `consultation_requests`.

## Bản nháp và xuất bản

- Các bảng `website_*` là **bản nháp** (quản trị viên sửa trực tiếp).
- **Xuất bản** ghi một bản chụp bất biến vào `website_publications` (chỉ mục đang hiển thị, tư liệu đã xác nhận công khai). Khách chỉ đọc bản chụp mới nhất, nên lưu nháp không đổi website.
- Chưa xuất bản lần nào thì khách thấy "Website đang được cập nhật".
- Ảnh/video chỉ được phục vụ công khai khi **đã xác nhận được phép công khai** và nằm trong bản xuất bản mới nhất; bỏ xác nhận thì ngừng phục vụ ngay.

## Ảnh và video

- Tệp lưu trên đĩa tại `WEBSITE_UPLOAD_DIR` (mặc định `./uploads/website`, cạnh nơi chạy backend); database chỉ lưu khóa và metadata.
- **Khi triển khai, trỏ `WEBSITE_UPLOAD_DIR` tới ổ đĩa/volume bền vững** và sao lưu cùng database, nếu không ảnh mất khi triển khai lại.
- Nhận JPG/PNG/WebP (≤ 5 MB) và MP4/WebM (≤ 50 MB, chỉ trong album). Loại tệp được kiểm tra theo nội dung, không theo tên/đuôi; không nhận SVG.

## Phân quyền

Không có hệ thống quyền riêng: quyền quản lý website là vai trò **ADMIN** (kiểm tra ở `SecurityConfig` và `@PreAuthorize` trên `WebsiteAdminController`). Cấp quyền bằng cách tạo/đổi tài khoản ADMIN như hiện có. TEACHER/PARENT bị từ chối (403) ở cả giao diện lẫn API.

## API

Công khai (`/api/public`): `GET /website`, `GET /website/media/{id}`, `POST /consultations`. Không có API công khai nào liệt kê hay đọc yêu cầu tư vấn.
Quản trị (`/api/admin/website`): `status`, `preview`, `publish`, `{items|services|team|faqs|albums|sections|settings}`, `media`, `consultations`.

Chống spam form tư vấn: trường bẫy `website`, tối đa 5 lần/15 phút mỗi địa chỉ (theo `X-Forwarded-For` hoặc IP kết nối), 200 lần/giờ toàn hệ thống, tối đa 3 lần/ngày mỗi số điện thoại. Bộ đếm nằm trong bộ nhớ một tiến trình backend.

## Kiểm thử

- `WebsiteApiTests` (Maven, cần `WEBSITE_TEST_DB_URL` trỏ tới DB đã chạy V1–V14; test chạy trong giao dịch và hoàn tác).
- `frontend/src/services/websiteApi.test.ts` (Vitest) và `frontend/tests/website.spec.ts` (Playwright, API giả lập).
