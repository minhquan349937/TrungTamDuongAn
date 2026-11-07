-- Public website content, media, publications and consultation requests.
-- Additive only: no existing table is changed. Re-running skips existing tables and sample rows.
-- Content tables hold the editable DRAFT; website_publications holds the immutable PUBLISHED snapshots.
-- Sample rows (is_sample=1) are replaced by administrators; hidden samples contain unverified facts.
SET NAMES utf8mb4;
SET time_zone = '+00:00';

CREATE TABLE IF NOT EXISTS website_albums (
    id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin PRIMARY KEY,
    title VARCHAR(200) NOT NULL,
    category VARCHAR(10) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    description VARCHAR(1000),
    sort_order INT NOT NULL DEFAULT 0,
    is_visible BOOLEAN NOT NULL DEFAULT TRUE,
    is_sample BOOLEAN NOT NULL DEFAULT FALSE,
    updated_by CHAR(36) CHARACTER SET ascii COLLATE ascii_bin,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    FOREIGN KEY (updated_by) REFERENCES app_user(id),
    CONSTRAINT ck_website_album_category CHECK (category IN ('SPACE','ACTIVITY'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- Files live on disk (WEBSITE_UPLOAD_DIR); the database keeps only the storage key and metadata.
-- album_id IS NULL: standalone image (logo, section, service, team photo).
CREATE TABLE IF NOT EXISTS website_media (
    id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin PRIMARY KEY,
    kind VARCHAR(5) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    storage_key VARCHAR(80) CHARACTER SET ascii COLLATE ascii_bin NOT NULL UNIQUE,
    original_name VARCHAR(255) NOT NULL,
    content_type VARCHAR(50) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    size_bytes BIGINT UNSIGNED NOT NULL,
    album_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin,
    title VARCHAR(300),
    alt_text VARCHAR(300),
    sort_order INT NOT NULL DEFAULT 0,
    is_visible BOOLEAN NOT NULL DEFAULT TRUE,
    public_consent BOOLEAN NOT NULL DEFAULT FALSE,
    consent_by CHAR(36) CHARACTER SET ascii COLLATE ascii_bin,
    consent_at DATETIME(6),
    created_by CHAR(36) CHARACTER SET ascii COLLATE ascii_bin,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    KEY ix_website_media_album (album_id, sort_order),
    FOREIGN KEY (album_id) REFERENCES website_albums(id) ON DELETE CASCADE,
    FOREIGN KEY (consent_by) REFERENCES app_user(id),
    FOREIGN KEY (created_by) REFERENCES app_user(id),
    CONSTRAINT ck_website_media_kind CHECK (kind IN ('IMAGE','VIDEO')),
    CONSTRAINT ck_website_media_consent CHECK (public_consent IN (0,1))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS website_settings (
    id TINYINT UNSIGNED NOT NULL DEFAULT 1 PRIMARY KEY,
    center_name VARCHAR(200) NOT NULL,
    logo_media_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin,
    address VARCHAR(500),
    hotline VARCHAR(30),
    working_hours VARCHAR(300),
    zalo_url VARCHAR(300),
    facebook_url VARCHAR(300),
    map_embed_url VARCHAR(600),
    is_sample BOOLEAN NOT NULL DEFAULT FALSE,
    updated_by CHAR(36) CHARACTER SET ascii COLLATE ascii_bin,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    FOREIGN KEY (logo_media_id) REFERENCES website_media(id) ON DELETE SET NULL,
    FOREIGN KEY (updated_by) REFERENCES app_user(id),
    CONSTRAINT ck_website_settings_singleton CHECK (id = 1)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- One row per public block: HERO, STORY, AUDIENCE, SERVICES, TEAM, PROCESS, PROGRESS, GALLERY, FAQ, CONTACT.
CREATE TABLE IF NOT EXISTS website_sections (
    section_key VARCHAR(20) CHARACTER SET ascii COLLATE ascii_bin PRIMARY KEY,
    title VARCHAR(200),
    summary VARCHAR(500),
    body TEXT,
    image_media_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin,
    button_label VARCHAR(60),
    is_visible BOOLEAN NOT NULL DEFAULT TRUE,
    is_sample BOOLEAN NOT NULL DEFAULT FALSE,
    updated_by CHAR(36) CHARACTER SET ascii COLLATE ascii_bin,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    FOREIGN KEY (image_media_id) REFERENCES website_media(id) ON DELETE SET NULL,
    FOREIGN KEY (updated_by) REFERENCES app_user(id),
    CONSTRAINT ck_website_section_key CHECK (section_key IN
        ('HERO','STORY','AUDIENCE','SERVICES','TEAM','PROCESS','PROGRESS','GALLERY','FAQ','CONTACT'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- List entries of a section: core values (STORY), audience groups (AUDIENCE), process steps (PROCESS), progress points (PROGRESS).
CREATE TABLE IF NOT EXISTS website_section_items (
    id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin PRIMARY KEY,
    section_key VARCHAR(20) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    label VARCHAR(100),
    title VARCHAR(200) NOT NULL,
    description VARCHAR(2000),
    sort_order INT NOT NULL DEFAULT 0,
    is_visible BOOLEAN NOT NULL DEFAULT TRUE,
    is_sample BOOLEAN NOT NULL DEFAULT FALSE,
    updated_by CHAR(36) CHARACTER SET ascii COLLATE ascii_bin,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    KEY ix_website_items_section (section_key, sort_order),
    FOREIGN KEY (section_key) REFERENCES website_sections(section_key) ON DELETE CASCADE,
    FOREIGN KEY (updated_by) REFERENCES app_user(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS website_services (
    id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin PRIMARY KEY,
    title VARCHAR(200) NOT NULL,
    summary VARCHAR(500),
    details TEXT,
    audience VARCHAR(300),
    format VARCHAR(300),
    image_media_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin,
    sort_order INT NOT NULL DEFAULT 0,
    is_visible BOOLEAN NOT NULL DEFAULT TRUE,
    is_sample BOOLEAN NOT NULL DEFAULT FALSE,
    updated_by CHAR(36) CHARACTER SET ascii COLLATE ascii_bin,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    KEY ix_website_services_order (sort_order),
    FOREIGN KEY (image_media_id) REFERENCES website_media(id) ON DELETE SET NULL,
    FOREIGN KEY (updated_by) REFERENCES app_user(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- Hand-entered public profiles. Deliberately NOT linked to staff, so no internal data can leak by join.
CREATE TABLE IF NOT EXISTS website_team_members (
    id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin PRIMARY KEY,
    full_name VARCHAR(150) NOT NULL,
    job_title VARCHAR(150),
    qualifications VARCHAR(1000),
    experience VARCHAR(1000),
    bio VARCHAR(1000),
    photo_media_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin,
    public_consent BOOLEAN NOT NULL DEFAULT FALSE,
    sort_order INT NOT NULL DEFAULT 0,
    is_visible BOOLEAN NOT NULL DEFAULT TRUE,
    is_sample BOOLEAN NOT NULL DEFAULT FALSE,
    updated_by CHAR(36) CHARACTER SET ascii COLLATE ascii_bin,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    KEY ix_website_team_order (sort_order),
    FOREIGN KEY (photo_media_id) REFERENCES website_media(id) ON DELETE SET NULL,
    FOREIGN KEY (updated_by) REFERENCES app_user(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS website_faqs (
    id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin PRIMARY KEY,
    question VARCHAR(300) NOT NULL,
    answer VARCHAR(3000) NOT NULL,
    sort_order INT NOT NULL DEFAULT 0,
    is_visible BOOLEAN NOT NULL DEFAULT TRUE,
    is_sample BOOLEAN NOT NULL DEFAULT FALSE,
    updated_by CHAR(36) CHARACTER SET ascii COLLATE ascii_bin,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    KEY ix_website_faqs_order (sort_order),
    FOREIGN KEY (updated_by) REFERENCES app_user(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- Immutable published snapshots (public DTO as JSON). The latest row is what visitors see.
CREATE TABLE IF NOT EXISTS website_publications (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
    content JSON NOT NULL,
    published_by CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    published_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    FOREIGN KEY (published_by) REFERENCES app_user(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- Media files a snapshot may serve publicly. No FK to media: a deleted file simply stops being served.
CREATE TABLE IF NOT EXISTS website_publication_media (
    publication_id BIGINT UNSIGNED NOT NULL,
    media_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    PRIMARY KEY (publication_id, media_id),
    FOREIGN KEY (publication_id) REFERENCES website_publications(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS consultation_requests (
    id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin PRIMARY KEY,
    parent_name VARCHAR(150) NOT NULL,
    phone VARCHAR(15) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    child_age VARCHAR(50),
    message VARCHAR(2000),
    consented_at DATETIME(6) NOT NULL,
    status VARCHAR(12) CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT 'NEW',
    assigned_user_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin,
    internal_note TEXT,
    updated_by CHAR(36) CHARACTER SET ascii COLLATE ascii_bin,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    KEY ix_consultation_status_created (status, created_at),
    KEY ix_consultation_phone_created (phone, created_at),
    KEY ix_consultation_created (created_at),
    FOREIGN KEY (assigned_user_id) REFERENCES app_user(id),
    FOREIGN KEY (updated_by) REFERENCES app_user(id),
    CONSTRAINT ck_consultation_status CHECK (status IN ('NEW','CONTACTING','CONSULTED','CLOSED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- ---- Sample content: inserted only into EMPTY tables, never overwrites administrator input. ----
-- Facts the center must confirm (audiences, services, contact) are seeded HIDDEN or empty.
INSERT INTO website_settings (id, center_name, is_sample)
SELECT 1, 'Trung tâm Đường An', 1 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM website_settings);

INSERT INTO website_sections (section_key, title, summary, body, button_label, is_visible, is_sample)
SELECT t.* FROM (
  SELECT 'HERO' k, 'Mỗi bước nhỏ, một niềm vui lớn.' title,
         'Trung tâm Đường An đồng hành cùng trẻ và gia đình bằng sự kiên nhẫn, thấu hiểu và những mục tiêu phù hợp với từng bé.' summary,
         NULL body, 'Đăng ký tư vấn' button_label, 1 vis, 1 smp
  UNION ALL SELECT 'STORY', 'Về Trung tâm Đường An', 'Mỗi trẻ có nhịp phát triển riêng.',
         'Mỗi trẻ có một nhịp phát triển riêng. Tại Đường An, chúng tôi lắng nghe gia đình, quan sát trẻ và cùng xây dựng những mục tiêu nhỏ, vừa sức để trẻ tự tin tiến bộ từng ngày.', NULL, 1, 1
  UNION ALL SELECT 'AUDIENCE', 'Đường An đồng hành cùng ai?', 'Độ tuổi và nhu cầu mà trung tâm tiếp nhận.', NULL, NULL, 1, 1
  UNION ALL SELECT 'SERVICES', 'Chương trình và dịch vụ', 'Các hình thức đồng hành tại trung tâm.', NULL, NULL, 1, 1
  UNION ALL SELECT 'TEAM', 'Đội ngũ chuyên môn', 'Những người đồng hành cùng trẻ mỗi ngày.', NULL, NULL, 1, 1
  UNION ALL SELECT 'PROCESS', 'Quy trình đồng hành', 'Từ lần gặp đầu tiên đến việc theo dõi lâu dài.', NULL, NULL, 1, 1
  UNION ALL SELECT 'PROGRESS', 'Theo dõi tiến bộ của trẻ', 'Phụ huynh luôn biết con đang học gì và tiến bộ ra sao.', NULL, NULL, 1, 1
  UNION ALL SELECT 'GALLERY', 'Không gian và hoạt động tại Đường An', 'Hình ảnh không gian học tập và các hoạt động của trẻ.', NULL, NULL, 1, 1
  UNION ALL SELECT 'FAQ', 'Câu hỏi thường gặp', NULL, NULL, NULL, 1, 1
  UNION ALL SELECT 'CONTACT', 'Liên hệ và đăng ký tư vấn', 'Để lại thông tin, trung tâm sẽ liên hệ để trao đổi cùng gia đình.', NULL, NULL, 1, 1
) t WHERE NOT EXISTS (SELECT 1 FROM website_sections);

INSERT INTO website_section_items (id, section_key, label, title, description, sort_order, is_visible, is_sample)
SELECT UUID(), t.k, t.lbl, t.title, t.descr, t.ord, t.vis, 1 FROM (
  SELECT 'STORY' k, NULL lbl, 'Thấu hiểu' title, 'Lắng nghe và tôn trọng nhịp phát triển riêng của từng trẻ.' descr, 1 ord, 1 vis
  UNION ALL SELECT 'STORY', NULL, 'Kiên nhẫn', 'Ghi nhận từng bước nhỏ và tạo môi trường an toàn để trẻ thử lại.', 2, 1
  UNION ALL SELECT 'STORY', NULL, 'Đồng hành cùng gia đình', 'Trao đổi cởi mở để việc học ở trung tâm và ở nhà hỗ trợ lẫn nhau.', 3, 1
  UNION ALL SELECT 'AUDIENCE', 'Độ tuổi', '[Cần xác nhận] Độ tuổi trẻ được tiếp nhận', 'Cập nhật khoảng tuổi thực tế của trung tâm rồi bật hiển thị.', 1, 0
  UNION ALL SELECT 'AUDIENCE', 'Nhu cầu', '[Cần xác nhận] Nhóm nhu cầu được hỗ trợ', 'Cập nhật các nhóm nhu cầu trung tâm thực sự tiếp nhận rồi bật hiển thị.', 2, 0
  UNION ALL SELECT 'PROCESS', 'Bước 1', 'Tiếp nhận và tư vấn', 'Trung tâm lắng nghe mong muốn của gia đình và giải đáp các thắc mắc ban đầu.', 1, 1
  UNION ALL SELECT 'PROCESS', 'Bước 2', 'Đánh giá ban đầu', 'Quan sát và đánh giá để hiểu điểm mạnh, nhu cầu hiện tại của trẻ.', 2, 1
  UNION ALL SELECT 'PROCESS', 'Bước 3', 'Xây dựng kế hoạch phù hợp', 'Cùng phụ huynh thống nhất mục tiêu cá nhân vừa sức với trẻ.', 3, 1
  UNION ALL SELECT 'PROCESS', 'Bước 4', 'Tổ chức học, theo dõi và trao đổi với phụ huynh', 'Triển khai buổi học, ghi nhận quá trình và trao đổi thường xuyên với gia đình.', 4, 1
  UNION ALL SELECT 'PROGRESS', NULL, 'Mục tiêu cá nhân', 'Mỗi trẻ có những mục tiêu riêng, được xây dựng dựa trên nhu cầu và khả năng của chính trẻ.', 1, 1
  UNION ALL SELECT 'PROGRESS', NULL, 'Ghi nhận quá trình học', 'Giáo viên ghi lại quá trình học để theo dõi sự thay đổi theo thời gian.', 2, 1
  UNION ALL SELECT 'PROGRESS', NULL, 'Nhận xét định kỳ', 'Trung tâm trao đổi nhận xét định kỳ với phụ huynh để cùng điều chỉnh hướng đồng hành.', 3, 1
) t WHERE NOT EXISTS (SELECT 1 FROM website_section_items);

INSERT INTO website_services (id, title, summary, details, audience, format, sort_order, is_visible, is_sample)
SELECT UUID(), t.title, t.summary, t.details, t.audience, t.format, t.ord, t.vis, 1 FROM (
  SELECT '[Cần xác nhận] Lớp cá nhân' title, 'Học một thầy một trò theo kế hoạch riêng.' summary,
         'Cập nhật nội dung thực tế của chương trình rồi bật hiển thị.' details, NULL audience, 'Cá nhân' format, 1 ord, 0 vis
  UNION ALL SELECT '[Cần xác nhận] Lớp nhóm', 'Học theo nhóm nhỏ.', 'Cập nhật nội dung thực tế của chương trình rồi bật hiển thị.', NULL, 'Nhóm', 2, 0
  UNION ALL SELECT '[Cần xác nhận] Lớp bán trú', 'Chương trình bán trú tại trung tâm.', 'Cập nhật nội dung thực tế của chương trình rồi bật hiển thị.', NULL, 'Bán trú', 3, 0
) t WHERE NOT EXISTS (SELECT 1 FROM website_services);

INSERT INTO website_faqs (id, question, answer, sort_order, is_visible, is_sample)
SELECT UUID(), t.question, t.answer, t.ord, t.vis, 1 FROM (
  SELECT 'Làm thế nào để đăng ký tư vấn?' question,
         'Phụ huynh để lại họ tên và số điện thoại ở biểu mẫu cuối trang, trung tâm sẽ liên hệ để trao đổi.' answer, 1 ord, 1 vis
  UNION ALL SELECT 'Thông tin tôi gửi có được bảo mật không?',
         'Thông tin chỉ được trung tâm dùng để liên hệ tư vấn cho gia đình bạn.', 2, 1
) t WHERE NOT EXISTS (SELECT 1 FROM website_faqs);
