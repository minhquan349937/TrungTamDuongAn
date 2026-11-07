package com.example.TTGDHNDuongAn.service;

import com.example.TTGDHNDuongAn.repository.ShortIds;
import java.net.URI;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

import com.example.TTGDHNDuongAn.dto.WebsiteDtos.*;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.ObjectMapper;

/**
 * Draft content lives in the website_* tables; publishing serialises the visible, consented part into an
 * immutable snapshot (website_publications). Visitors only ever read the latest snapshot.
 */
@Service
public class WebsiteContentService {
    private enum Kind { TEXT, BOOL, CHOICE, MEDIA, URL, PHONE }
    private record F(String json, String column, Kind kind, String label, int max, boolean required, List<String> allowed) {}
    private record Spec(String table, String pk, String orderBy, boolean listOfRecords, boolean ordered, List<F> fields) {}

    private static final Pattern PHONE_PATTERN = Pattern.compile("[0-9 +().-]{6,30}");

    private static F text(String json, String label, int max, boolean required) {
        return new F(json, snake(json), Kind.TEXT, label, max, required, List.of());
    }
    private static F media(String json, String label) { return new F(json, snake(json), Kind.MEDIA, label, 36, false, List.of()); }
    private static F bool(String json, String label) { return new F(json, snake(json), Kind.BOOL, label, 0, false, List.of()); }
    private static F visible() { return new F("visible", "is_visible", Kind.BOOL, "Hiển thị", 0, false, List.of()); }
    private static F choice(String json, String label, String... allowed) { return new F(json, snake(json), Kind.CHOICE, label, 20, true, List.of(allowed)); }
    private static F url(String json, String label, int max, String... hosts) { return new F(json, snake(json), Kind.URL, label, max, false, List.of(hosts)); }
    private static String snake(String camel) { return camel.replaceAll("([A-Z])", "_$1").toLowerCase(); }

    private static final Map<String, Spec> SPECS = Map.of(
            "items", new Spec("website_section_items", "id", "section_key,sort_order,created_at", true, true, List.of(
                    choice("sectionKey", "Mục", "STORY", "AUDIENCE", "PROCESS", "PROGRESS"), text("label", "Nhãn", 100, false),
                    text("title", "Tiêu đề", 200, true), text("description", "Mô tả", 2000, false), visible())),
            "services", new Spec("website_services", "id", "sort_order,created_at", true, true, List.of(
                    text("title", "Tên chương trình", 200, true), text("summary", "Mô tả ngắn", 500, false),
                    text("details", "Nội dung chi tiết", 6000, false), text("audience", "Đối tượng phù hợp", 300, false),
                    text("format", "Hình thức tổ chức", 300, false), media("imageMediaId", "Ảnh đại diện"), visible())),
            "team", new Spec("website_team_members", "id", "sort_order,created_at", true, true, List.of(
                    text("fullName", "Họ tên", 150, true), text("jobTitle", "Chức danh", 150, false),
                    text("qualifications", "Bằng cấp", 1000, false), text("experience", "Kinh nghiệm", 1000, false),
                    text("bio", "Giới thiệu ngắn", 1000, false), media("photoMediaId", "Ảnh"),
                    bool("publicConsent", "Đồng ý công khai"), visible())),
            "faqs", new Spec("website_faqs", "id", "sort_order,created_at", true, true, List.of(
                    text("question", "Câu hỏi", 300, true), text("answer", "Câu trả lời", 3000, true), visible())),
            "albums", new Spec("website_albums", "id", "sort_order,created_at", true, true, List.of(
                    text("title", "Tên album", 200, true), choice("category", "Phân loại", "SPACE", "ACTIVITY"),
                    text("description", "Mô tả", 1000, false), visible())),
            "sections", new Spec("website_sections", "section_key",
                    "FIELD(section_key,'HERO','STORY','AUDIENCE','SERVICES','TEAM','PROCESS','PROGRESS','GALLERY','FAQ','CONTACT')", false, false, List.of(
                    text("title", "Tiêu đề", 200, false), text("summary", "Mô tả ngắn", 500, false), text("body", "Nội dung", 8000, false),
                    media("imageMediaId", "Ảnh"), text("buttonLabel", "Nhãn nút", 60, false), visible())),
            "settings", new Spec("website_settings", "id", "id", false, false, List.of(
                    text("centerName", "Tên trung tâm", 200, true), text("address", "Địa chỉ", 500, false),
                    new F("hotline", "hotline", Kind.PHONE, "Hotline", 30, false, List.of()),
                    text("workingHours", "Giờ làm việc", 300, false),
                    url("zaloUrl", "Liên kết Zalo", 300, "zalo.me"),
                    url("facebookUrl", "Liên kết Facebook", 300, "facebook.com", "fb.com", "m.me"),
                    url("mapEmbedUrl", "Bản đồ nhúng", 600, "google.com", "maps.google.com"),
                    media("logoMediaId", "Logo"))));

    private final JdbcTemplate jdbc;
    private final WebsiteMediaService mediaService;
    private final ObjectMapper mapper = new ObjectMapper();

    public WebsiteContentService(JdbcTemplate jdbc, WebsiteMediaService mediaService) {
        this.jdbc = jdbc;
        this.mediaService = mediaService;
    }

    // ---------- admin: generic draft CRUD ----------

    private Spec spec(String resource) {
        Spec s = SPECS.get(resource);
        if (s == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy loại nội dung.");
        return s;
    }

    private static String id(String value) {
        if (value == null || !ShortIds.isId(value)) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy nội dung.");
        return value;
    }

    private String key(Spec s, String value) {
        return s.listOfRecords() ? id(value) : value;
    }

    private Map<String, Object> row(Spec s, String value) {
        var rows = jdbc.queryForList("SELECT * FROM " + s.table() + " WHERE " + s.pk() + "=?", key(s, value));
        if (rows.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy nội dung.");
        return rows.get(0);
    }

    private static boolean flag(Object v) {
        return v instanceof Boolean b ? b : v instanceof Number n && n.intValue() != 0;
    }

    private static String iso(Object v) {
        if (v instanceof LocalDateTime t) return t.toInstant(ZoneOffset.UTC).toString();
        if (v instanceof Timestamp t) return t.toInstant().toString();
        return null;
    }

    private Map<String, Object> view(Spec s, Map<String, Object> row) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("id", String.valueOf(row.get(s.pk())));
        for (F f : s.fields()) out.put(f.json(), f.kind() == Kind.BOOL ? (Object) flag(row.get(f.column())) : row.get(f.column()));
        out.put("sample", flag(row.get("is_sample")));
        out.put("updatedAt", iso(row.get("updated_at")));
        return out;
    }

    public List<Map<String, Object>> list(String resource, String section) {
        Spec s = spec(resource);
        boolean filter = "items".equals(resource) && section != null && !section.isBlank();
        if (filter && !s.fields().get(0).allowed().contains(section)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Mục không hợp lệ.");
        var rows = filter
                ? jdbc.queryForList("SELECT * FROM " + s.table() + " WHERE section_key=? ORDER BY " + s.orderBy(), section)
                : jdbc.queryForList("SELECT * FROM " + s.table() + " ORDER BY " + s.orderBy());
        return rows.stream().map(r -> view(s, r)).toList();
    }

    private Map<String, Object> validate(Spec s, Map<String, Object> body) {
        Map<String, Object> values = new LinkedHashMap<>();
        for (F f : s.fields()) {
            Object raw = body == null ? null : body.get(f.json());
            if (f.kind() == Kind.BOOL) {
                values.put(f.column(), raw instanceof Boolean b && b);
                continue;
            }
            if (raw != null && !(raw instanceof String)) throw bad(f, "không hợp lệ");
            String text = raw == null ? null : ((String) raw).replace("\r\n", "\n").replaceAll("[\\p{Cntrl}&&[^\\n\\t]]", "").trim();
            if (text != null && text.isEmpty()) text = null;
            if (text == null) {
                if (f.required()) throw bad(f, "là bắt buộc");
                values.put(f.column(), null);
                continue;
            }
            if (text.length() > f.max()) throw bad(f, "tối đa " + f.max() + " ký tự");
            switch (f.kind()) {
                case CHOICE -> { if (!f.allowed().contains(text)) throw bad(f, "không hợp lệ"); }
                case PHONE -> { if (!PHONE_PATTERN.matcher(text).matches()) throw bad(f, "chỉ gồm số, dấu cách, +, -, ( ), ."); }
                case URL -> checkUrl(f, text);
                case MEDIA -> {
                    if (!text.matches("MD[0-9]{3,6}") || jdbc.queryForObject(
                            "SELECT COUNT(*) FROM website_media WHERE id=? AND album_id IS NULL AND kind='IMAGE'", Integer.class, text) == 0)
                        throw bad(f, "không tồn tại, hãy tải ảnh lên lại");
                }
                default -> { }
            }
            values.put(f.column(), text);
        }
        return values;
    }

    /** https only and a fixed host list: the value ends up in links and an iframe on the public page. */
    private static void checkUrl(F f, String text) {
        try {
            URI uri = URI.create(text);
            String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase();
            boolean ok = "https".equals(uri.getScheme()) && uri.getUserInfo() == null
                    && f.allowed().stream().anyMatch(h -> host.equals(h) || host.endsWith("." + h));
            if (ok && f.json().equals("mapEmbedUrl")) ok = uri.getPath() != null && uri.getPath().startsWith("/maps");
            if (ok) return;
        } catch (IllegalArgumentException ignored) {
            // falls through to the error below
        }
        throw bad(f, f.json().equals("mapEmbedUrl") ? "phải là liên kết nhúng https của Google Maps" : "phải là liên kết https của " + String.join("/", f.allowed()));
    }

    private static ResponseStatusException bad(F f, String why) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, f.label() + " " + why + ".");
    }

    @Transactional
    public Map<String, Object> save(String resource, String idOrNull, Map<String, Object> body, String userId) {
        Spec s = spec(resource);
        if (idOrNull == null && !s.listOfRecords()) throw new ResponseStatusException(HttpStatus.METHOD_NOT_ALLOWED, "Không thể thêm mục này.");
        Map<String, Object> values = validate(s, body);
        List<Object> args = new ArrayList<>(values.values());
        String target;
        if (idOrNull == null) {
            target = ShortIds.next(jdbc, s.table());
            String columns = String.join(",", values.keySet()), marks = "?,".repeat(values.size());
            if (s.ordered()) {
                args.add(jdbc.queryForObject("SELECT COALESCE(MAX(sort_order),0)+1 FROM " + s.table(), Integer.class));
                columns += ",sort_order";
                marks += "?,";
            }
            args.add(0, target);
            args.add(userId);
            jdbc.update("INSERT INTO " + s.table() + "(" + s.pk() + "," + columns + ",updated_by) VALUES(?," + marks + "?)", args.toArray());
        } else {
            Map<String, Object> before = row(s, idOrNull);
            target = String.valueOf(before.get(s.pk()));
            args.add(userId);
            args.add(target);
            jdbc.update("UPDATE " + s.table() + " SET " + String.join("=?,", values.keySet()) + "=?,is_sample=0,updated_by=? WHERE " + s.pk() + "=?", args.toArray());
            for (F f : s.fields()) {
                Object old = before.get(f.column());
                if (f.kind() == Kind.MEDIA && old != null && !old.equals(values.get(f.column()))) mediaService.deleteIfOrphan((String) old);
            }
        }
        return view(s, row(s, target));
    }

    @Transactional
    public void delete(String resource, String idValue) {
        Spec s = spec(resource);
        if (!s.listOfRecords()) throw new ResponseStatusException(HttpStatus.METHOD_NOT_ALLOWED, "Không thể xóa mục này.");
        Map<String, Object> before = row(s, idValue);
        List<String> albumFiles = "albums".equals(resource) ? mediaService.albumKeys(idValue) : List.of();
        jdbc.update("DELETE FROM " + s.table() + " WHERE id=?", idValue);
        mediaService.deleteFiles(albumFiles);
        for (F f : s.fields()) if (f.kind() == Kind.MEDIA) mediaService.deleteIfOrphan((String) before.get(f.column()));
    }

    @Transactional
    public void reorder(String resource, List<String> ids) {
        Spec s = spec(resource);
        if (!s.ordered()) throw new ResponseStatusException(HttpStatus.METHOD_NOT_ALLOWED, "Mục này không sắp xếp được.");
        if (ids == null || ids.size() > 500) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Danh sách thứ tự không hợp lệ.");
        for (int i = 0; i < ids.size(); i++) jdbc.update("UPDATE " + s.table() + " SET sort_order=? WHERE id=?", i + 1, id(ids.get(i)));
    }

    // ---------- building the public DTO ----------

    private static final class Ctx {
        final boolean preview;
        final Set<String> used = new LinkedHashSet<>();
        Ctx(boolean preview) { this.preview = preview; }
        String url(String mediaId) {
            if (mediaId == null) return null;
            used.add(mediaId);
            return (preview ? "/api/admin/website/media/" : "/api/public/website/media/") + mediaId + (preview ? "/file" : "");
        }
    }

    private record SectionRow(String key, Section section) {}

    private Content build(Ctx c) {
        // Only media with public_consent=1 is joined; anything else comes back as a NULL url.
        Settings settings = jdbc.query("SELECT s.center_name,s.address,s.hotline,s.working_hours,s.zalo_url,s.facebook_url,s.map_embed_url,m.id logo " +
                        "FROM website_settings s LEFT JOIN website_media m ON m.id=s.logo_media_id AND m.public_consent=1 AND m.kind='IMAGE' WHERE s.id='WCFG001'",
                (rs, i) -> new Settings(rs.getString("center_name"), c.url(rs.getString("logo")), rs.getString("address"), rs.getString("hotline"),
                        rs.getString("working_hours"), rs.getString("zalo_url"), rs.getString("facebook_url"), rs.getString("map_embed_url")))
                .stream().findFirst().orElse(null);
        Map<String, Section> sections = new LinkedHashMap<>();
        jdbc.query("SELECT s.section_key,s.title,s.summary,s.body,s.button_label,m.id image FROM website_sections s " +
                        "LEFT JOIN website_media m ON m.id=s.image_media_id AND m.public_consent=1 AND m.kind='IMAGE' " +
                        "WHERE s.is_visible=1 ORDER BY " + SPECS.get("sections").orderBy(),
                (rs, i) -> new SectionRow(rs.getString("section_key"), new Section(rs.getString("title"), rs.getString("summary"),
                        rs.getString("body"), c.url(rs.getString("image")), rs.getString("button_label"))))
                .forEach(r -> sections.put(r.key(), r.section()));
        List<Item> items = jdbc.query("SELECT i.section_key,i.label,i.title,i.description FROM website_section_items i " +
                        "JOIN website_sections s ON s.section_key=i.section_key AND s.is_visible=1 WHERE i.is_visible=1 ORDER BY i.section_key,i.sort_order,i.created_at",
                (rs, i) -> new Item(rs.getString(1), rs.getString(2), rs.getString(3), rs.getString(4)));
        List<ServiceItem> services = !sections.containsKey("SERVICES") ? List.of() : jdbc.query(
                "SELECT v.id,v.title,v.summary,v.details,v.audience,v.format,m.id image FROM website_services v " +
                        "LEFT JOIN website_media m ON m.id=v.image_media_id AND m.public_consent=1 AND m.kind='IMAGE' WHERE v.is_visible=1 ORDER BY v.sort_order,v.created_at",
                (rs, i) -> new ServiceItem(rs.getString("id"), rs.getString("title"), rs.getString("summary"), rs.getString("details"),
                        rs.getString("audience"), rs.getString("format"), c.url(rs.getString("image"))));
        List<Member> team = !sections.containsKey("TEAM") ? List.of() : jdbc.query(
                "SELECT t.full_name,t.job_title,t.qualifications,t.experience,t.bio,m.id photo FROM website_team_members t " +
                        "LEFT JOIN website_media m ON m.id=t.photo_media_id AND m.public_consent=1 AND m.kind='IMAGE' " +
                        "WHERE t.is_visible=1 AND t.public_consent=1 ORDER BY t.sort_order,t.created_at",
                (rs, i) -> new Member(rs.getString("full_name"), rs.getString("job_title"), rs.getString("qualifications"),
                        rs.getString("experience"), rs.getString("bio"), c.url(rs.getString("photo"))));
        List<Faq> faqs = !sections.containsKey("FAQ") ? List.of() : jdbc.query(
                "SELECT question,answer FROM website_faqs WHERE is_visible=1 ORDER BY sort_order,created_at",
                (rs, i) -> new Faq(rs.getString(1), rs.getString(2)));
        return new Content(settings, sections, items, services, team, sections.containsKey("GALLERY") ? albums(c) : List.of(), faqs);
    }

    private List<Album> albums(Ctx c) {
        Map<String, List<Media>> byAlbum = new LinkedHashMap<>();
        jdbc.query("SELECT m.album_id,m.id,m.kind,m.title,m.alt_text FROM website_media m JOIN website_albums a ON a.id=m.album_id AND a.is_visible=1 " +
                        "WHERE m.is_visible=1 AND m.public_consent=1 ORDER BY a.sort_order,m.sort_order,m.created_at",
                (rs, i) -> Map.entry(rs.getString(1), new Media("VIDEO".equals(rs.getString(3)) ? "video" : "image", c.url(rs.getString(2)),
                        rs.getString(4), rs.getString(5))))
                .forEach(e -> byAlbum.computeIfAbsent(e.getKey(), k -> new ArrayList<>()).add(e.getValue()));
        return jdbc.query("SELECT id,title,category,description FROM website_albums WHERE is_visible=1 ORDER BY sort_order,created_at",
                (rs, i) -> new Album(rs.getString(1), rs.getString(2), rs.getString(3), rs.getString(4), byAlbum.getOrDefault(rs.getString(1), List.of())))
                .stream().filter(a -> !a.media().isEmpty()).toList();
    }

    /** Draft as the admin sees it; media URLs point at the authenticated admin file endpoint. */
    public Content preview() {
        return build(new Ctx(true));
    }

    public Optional<String> published() {
        return jdbc.queryForList("SELECT content FROM website_publications ORDER BY published_at DESC,CAST(SUBSTRING(id,3) AS UNSIGNED) DESC LIMIT 1", String.class).stream().findFirst();
    }

    @Transactional
    public Status publish(String userId) {
        Ctx c = new Ctx(false);
        String json = mapper.writeValueAsString(build(c));
        String publication = ShortIds.next(jdbc, "website_publications");
        jdbc.update("INSERT INTO website_publications(id,content,published_by) VALUES(?,CAST(? AS JSON),?)",
                publication, json, userId);
        for (String media : c.used) jdbc.update("INSERT INTO website_publication_media(publication_id,media_id) VALUES(?,?)", publication, media);
        return status();
    }

    public Status status() {
        var latest = jdbc.queryForList("SELECT p.content,p.published_at,u.display_name FROM website_publications p " +
                "JOIN app_user u ON u.id=p.published_by ORDER BY p.published_at DESC,CAST(SUBSTRING(p.id,3) AS UNSIGNED) DESC LIMIT 1");
        int samples = jdbc.queryForObject("SELECT (SELECT COUNT(*) FROM website_sections WHERE is_sample=1)+(SELECT COUNT(*) FROM website_section_items WHERE is_sample=1)" +
                "+(SELECT COUNT(*) FROM website_services WHERE is_sample=1)+(SELECT COUNT(*) FROM website_team_members WHERE is_sample=1)" +
                "+(SELECT COUNT(*) FROM website_faqs WHERE is_sample=1)+(SELECT COUNT(*) FROM website_albums WHERE is_sample=1)" +
                "+(SELECT COUNT(*) FROM website_settings WHERE is_sample=1)", Integer.class);
        if (latest.isEmpty()) return new Status(false, null, null, true, samples);
        var row = latest.get(0);
        boolean changed = !mapper.valueToTree(build(new Ctx(false))).equals(mapper.readTree((String) row.get("content")));
        return new Status(true, iso(row.get("published_at")), (String) row.get("display_name"), changed, samples);
    }
}
