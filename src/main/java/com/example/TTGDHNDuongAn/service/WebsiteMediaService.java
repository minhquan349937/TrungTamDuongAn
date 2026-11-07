package com.example.TTGDHNDuongAn.service;

import com.example.TTGDHNDuongAn.repository.ShortIds;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import com.example.TTGDHNDuongAn.dto.WebsiteDtos.AdminMedia;
import com.example.TTGDHNDuongAn.dto.WebsiteDtos.MediaMeta;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

/** Stores website images/videos on disk (WEBSITE_UPLOAD_DIR); the database keeps only key and metadata. */
@Service
public class WebsiteMediaService {
    static final long MAX_IMAGE = 5L * 1024 * 1024, MAX_VIDEO = 50L * 1024 * 1024;
    private record Sniffed(String kind, String type, String ext) {}
    public record Stored(String contentType, Resource file) {}

    private final JdbcTemplate jdbc;
    private final Path root;

    public WebsiteMediaService(JdbcTemplate jdbc, @Value("${website.upload-dir:uploads/website}") String dir) {
        this.jdbc = jdbc;
        this.root = Path.of(dir).toAbsolutePath().normalize();
    }

    private static final String COLUMNS = "id,kind,content_type,size_bytes,original_name,album_id,title,alt_text," +
            "sort_order,is_visible,public_consent,created_at";

    private AdminMedia map(java.sql.ResultSet rs, int row) throws java.sql.SQLException {
        LocalDateTime created = rs.getObject("created_at", LocalDateTime.class);
        return new AdminMedia(rs.getString("id"), rs.getString("kind"), rs.getString("content_type"), rs.getLong("size_bytes"),
                rs.getString("original_name"), rs.getString("album_id"), rs.getString("title"), rs.getString("alt_text"),
                rs.getInt("sort_order"), rs.getBoolean("is_visible"), rs.getBoolean("public_consent"),
                created.toInstant(ZoneOffset.UTC).toString());
    }

    /** Detects the real type from the first bytes; the client-supplied type and file name are never trusted. */
    private static Sniffed sniff(byte[] h) {
        if (h.length >= 3 && (h[0] & 0xFF) == 0xFF && (h[1] & 0xFF) == 0xD8 && (h[2] & 0xFF) == 0xFF) return new Sniffed("IMAGE", "image/jpeg", "jpg");
        if (h.length >= 8 && (h[0] & 0xFF) == 0x89 && h[1] == 'P' && h[2] == 'N' && h[3] == 'G') return new Sniffed("IMAGE", "image/png", "png");
        if (h.length >= 12 && new String(h, 0, 4, StandardCharsets.ISO_8859_1).equals("RIFF")
                && new String(h, 8, 4, StandardCharsets.ISO_8859_1).equals("WEBP")) return new Sniffed("IMAGE", "image/webp", "webp");
        if (h.length >= 12 && new String(h, 4, 4, StandardCharsets.ISO_8859_1).equals("ftyp")) return new Sniffed("VIDEO", "video/mp4", "mp4");
        if (h.length >= 4 && (h[0] & 0xFF) == 0x1A && (h[1] & 0xFF) == 0x45 && (h[2] & 0xFF) == 0xDF && (h[3] & 0xFF) == 0xA3) return new Sniffed("VIDEO", "video/webm", "webm");
        return null;
    }

    private static String clean(String value, int max) {
        if (value == null) return null;
        String text = value.replaceAll("\\p{Cntrl}", " ").trim();
        if (text.length() > max) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Nội dung chú thích quá dài (tối đa " + max + " ký tự).");
        return text.isEmpty() ? null : text;
    }

    @Transactional
    public AdminMedia upload(MultipartFile file, String albumId, String title, String alt, boolean consent, String userId) {
        if (file == null || file.isEmpty()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Chưa chọn tệp tải lên.");
        if (albumId != null && !albumId.isBlank() && jdbc.queryForObject("SELECT COUNT(*) FROM website_albums WHERE id=?", Integer.class, albumId) == 0)
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy album.");
        String album = albumId == null || albumId.isBlank() ? null : albumId;
        Sniffed type;
        try (InputStream in = file.getInputStream()) {
            type = sniff(in.readNBytes(16));
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Không đọc được tệp tải lên.");
        }
        if (type == null) throw new ResponseStatusException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Chỉ nhận ảnh JPG, PNG, WebP hoặc video MP4, WebM.");
        if (file.getSize() > ("IMAGE".equals(type.kind()) ? MAX_IMAGE : MAX_VIDEO))
            throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "IMAGE".equals(type.kind()) ? "Ảnh tối đa 5 MB." : "Video tối đa 50 MB.");
        if (album == null && "VIDEO".equals(type.kind()))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Video chỉ được thêm vào album.");
        String id = ShortIds.next(jdbc, "website_media"), key = UUID.randomUUID() + "." + type.ext();
        String raw = file.getOriginalFilename() == null ? "tep" : file.getOriginalFilename();
        String original = clean(raw.substring(Math.max(raw.lastIndexOf('/'), raw.lastIndexOf('\\')) + 1), 255);
        try {
            Files.createDirectories(root);
            file.transferTo(root.resolve(key));
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Không lưu được tệp. Kiểm tra thư mục lưu trữ.");
        }
        try {
            Integer next = jdbc.queryForObject("SELECT COALESCE(MAX(sort_order),0)+1 FROM website_media WHERE album_id <=> ?", Integer.class, album);
            jdbc.update("INSERT INTO website_media(id,kind,storage_key,original_name,content_type,size_bytes,album_id,title,alt_text," +
                            "sort_order,public_consent,consent_by,consent_at,created_by) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
                    id, type.kind(), key, original == null ? "tep" : original, type.type(), file.getSize(), album, clean(title, 300), clean(alt, 300),
                    next, consent, consent ? userId : null, consent ? LocalDateTime.now(ZoneOffset.UTC) : null, userId);
        } catch (RuntimeException e) {
            deleteFile(key);
            throw e;
        }
        return get(id);
    }

    public AdminMedia get(String id) {
        return jdbc.query("SELECT " + COLUMNS + " FROM website_media WHERE id=?", this::map, id).stream().findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy tệp."));
    }

    public List<AdminMedia> list(String albumId) {
        return albumId == null || albumId.isBlank()
                ? jdbc.query("SELECT " + COLUMNS + " FROM website_media WHERE album_id IS NULL ORDER BY created_at DESC", this::map)
                : jdbc.query("SELECT " + COLUMNS + " FROM website_media WHERE album_id=? ORDER BY sort_order,created_at", this::map, albumId);
    }

    @Transactional
    public AdminMedia update(String id, MediaMeta meta, String userId) {
        AdminMedia old = get(id);
        boolean consent = meta.publicConsent() == null ? old.publicConsent() : meta.publicConsent();
        jdbc.update("UPDATE website_media SET title=?,alt_text=?,is_visible=?,public_consent=? WHERE id=?",
                clean(meta.title(), 300), clean(meta.altText(), 300), meta.visible() == null ? old.visible() : meta.visible(), consent, id);
        if (consent && !old.publicConsent()) jdbc.update("UPDATE website_media SET consent_by=?,consent_at=UTC_TIMESTAMP(6) WHERE id=?", userId, id);
        else if (!consent) jdbc.update("UPDATE website_media SET consent_by=NULL,consent_at=NULL WHERE id=?", id);
        return get(id);
    }

    @Transactional
    public void reorder(List<String> ids) {
        for (int i = 0; i < ids.size(); i++) jdbc.update("UPDATE website_media SET sort_order=? WHERE id=?", i + 1, ids.get(i));
    }

    @Transactional
    public void delete(String id) {
        String key = storageKey(id);
        jdbc.update("DELETE FROM website_media WHERE id=?", id);
        deleteFile(key);
    }

    /** Removes a standalone image that no content row references any more (called after a replace/delete). */
    public void deleteIfOrphan(String id) {
        if (id == null) return;
        List<String> keys = jdbc.queryForList("SELECT storage_key FROM website_media m WHERE m.id=? AND m.album_id IS NULL " +
                "AND NOT EXISTS (SELECT 1 FROM website_settings WHERE logo_media_id=m.id) " +
                "AND NOT EXISTS (SELECT 1 FROM website_sections WHERE image_media_id=m.id) " +
                "AND NOT EXISTS (SELECT 1 FROM website_services WHERE image_media_id=m.id) " +
                "AND NOT EXISTS (SELECT 1 FROM website_team_members WHERE photo_media_id=m.id)", String.class, id);
        if (!keys.isEmpty() && jdbc.update("DELETE FROM website_media WHERE id=?", id) == 1) deleteFile(keys.get(0));
    }

    public List<String> albumKeys(String albumId) {
        return jdbc.queryForList("SELECT storage_key FROM website_media WHERE album_id=?", String.class, albumId);
    }

    private String storageKey(String id) {
        return jdbc.queryForList("SELECT storage_key FROM website_media WHERE id=?", String.class, id).stream().findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy tệp."));
    }

    public void deleteFiles(List<String> keys) {
        keys.forEach(this::deleteFile);
    }

    private void deleteFile(String key) {
        try {
            Files.deleteIfExists(root.resolve(key));
        } catch (IOException ignored) {
            // ponytail: an undeletable orphan file only wastes disk; the row is already gone.
        }
    }

    /** Admin preview: any stored file. */
    public Stored open(String id) {
        return openWhere("SELECT content_type,storage_key FROM website_media WHERE id=?", id);
    }

    /** Public: only a consented file that the latest published snapshot references. */
    public Stored openPublished(String id) {
        return openWhere("SELECT m.content_type,m.storage_key FROM website_media m JOIN website_publication_media pm ON pm.media_id=m.id " +
                "WHERE m.id=? AND m.public_consent=1 AND pm.publication_id=(SELECT id FROM website_publications ORDER BY published_at DESC,CAST(SUBSTRING(id,3) AS UNSIGNED) DESC LIMIT 1)", id);
    }

    private Stored openWhere(String sql, String id) {
        var rows = jdbc.queryForList(sql, id);
        if (rows.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy tệp.");
        Path path = root.resolve((String) rows.get(0).get("storage_key")).normalize();
        if (!path.startsWith(root) || !Files.isRegularFile(path)) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy tệp.");
        return new Stored((String) rows.get(0).get("content_type"), new FileSystemResource(path));
    }
}
