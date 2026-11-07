package com.example.TTGDHNDuongAn.service;

import com.example.TTGDHNDuongAn.repository.ShortIds;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import com.example.TTGDHNDuongAn.dto.WebsiteDtos.*;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ConsultationService {
    static final List<String> STATUSES = List.of("NEW", "CONTACTING", "CONSULTED", "CLOSED");
    private static final ZoneId LOCAL = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final Pattern MOBILE = Pattern.compile("0[235789]\\d{8}");
    private static final String SELECT = "SELECT c.id,c.parent_name,c.phone,c.child_age,c.message,c.status,c.assigned_user_id,a.display_name assigned_name," +
            "c.internal_note,u.display_name updated_name,c.consented_at,c.created_at,c.updated_at FROM consultation_requests c " +
            "LEFT JOIN app_user a ON a.id=c.assigned_user_id LEFT JOIN app_user u ON u.id=c.updated_by";

    private final JdbcTemplate jdbc;
    // ponytail: in-memory throttle, correct for one backend instance; move to the database/Redis if the app is scaled out.
    private final Map<String, ArrayDeque<Long>> hits = new HashMap<>();

    public ConsultationService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Accepts 0xxxxxxxxx, +84xxxxxxxxx and 84xxxxxxxxx (spaces, dots, dashes, brackets ignored); returns 0xxxxxxxxx or null. */
    public static String normalizePhone(String raw) {
        if (raw == null) return null;
        String s = raw.replaceAll("[\\s.\\-()]", "");
        if (s.startsWith("+84")) s = "0" + s.substring(3);
        else if (s.startsWith("84") && s.length() == 11) s = "0" + s.substring(2);
        return MOBILE.matcher(s).matches() ? s : null;
    }

    private synchronized boolean tooMany(String key, int max, long windowMillis) {
        long now = System.currentTimeMillis();
        if (hits.size() > 5000) hits.values().removeIf(q -> q.isEmpty() || now - q.peekLast() > 3_600_000L);
        ArrayDeque<Long> queue = hits.computeIfAbsent(key, k -> new ArrayDeque<>());
        while (!queue.isEmpty() && now - queue.peekFirst() > windowMillis) queue.pollFirst();
        if (queue.size() >= max) return true;
        queue.addLast(now);
        return false;
    }

    private static ResponseStatusException bad(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }

    private static String line(String value, int max, String label) {
        String text = value == null ? "" : value.replaceAll("\\p{Cntrl}", " ").replaceAll("\\s+", " ").trim();
        if (text.length() > max) throw bad(label + " tối đa " + max + " ký tự.");
        return text;
    }

    @Transactional
    public void submit(ConsultationInput in, String clientIp) {
        if (in == null) throw bad("Thông tin gửi lên không hợp lệ.");
        if (in.website() != null && !in.website().isBlank()) throw bad("Không thể gửi yêu cầu.");
        String name = line(in.parentName(), 150, "Họ tên");
        if (name.length() < 2) throw bad("Vui lòng nhập họ tên phụ huynh.");
        String phone = normalizePhone(in.phone());
        if (phone == null) throw bad("Số điện thoại chưa hợp lệ. Ví dụ: 0912 345 678 hoặc +84 912 345 678.");
        String age = line(in.childAge(), 50, "Độ tuổi của trẻ");
        String message = in.message() == null ? "" : in.message().replace("\r\n", "\n").replaceAll("[\\p{Cntrl}&&[^\\n\\t]]", "").trim();
        if (message.length() > 2000) throw bad("Nội dung cần tư vấn tối đa 2000 ký tự.");
        if (!Boolean.TRUE.equals(in.consent())) throw bad("Vui lòng đồng ý để trung tâm liên hệ tư vấn.");
        if (tooMany("ip:" + clientIp, 5, 15 * 60_000L) || tooMany("all", 200, 3_600_000L))
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Bạn gửi quá nhiều lần. Vui lòng thử lại sau ít phút.");
        Integer recent = jdbc.queryForObject("SELECT COUNT(*) FROM consultation_requests WHERE phone=? AND created_at > UTC_TIMESTAMP(6) - INTERVAL 1 DAY", Integer.class, phone);
        if (recent != null && recent >= 3)
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Số điện thoại này đã gửi yêu cầu gần đây. Trung tâm sẽ liên hệ với bạn sớm.");
        jdbc.update("INSERT INTO consultation_requests(id,parent_name,phone,child_age,message,consented_at) VALUES(?,?,?,?,?,UTC_TIMESTAMP(6))",
                ShortIds.next(jdbc, "consultation_requests"), name, phone, age.isEmpty() ? null : age, message.isEmpty() ? null : message);
    }

    private Consultation map(java.sql.ResultSet rs, int row) throws java.sql.SQLException {
        return new Consultation(rs.getString("id"), rs.getString("parent_name"), rs.getString("phone"), rs.getString("child_age"),
                rs.getString("message"), rs.getString("status"), rs.getString("assigned_user_id"), rs.getString("assigned_name"),
                rs.getString("internal_note"), rs.getString("updated_name"), iso(rs, "consented_at"), iso(rs, "created_at"), iso(rs, "updated_at"));
    }

    private static String iso(java.sql.ResultSet rs, String column) throws java.sql.SQLException {
        return rs.getObject(column, LocalDateTime.class).toInstant(ZoneOffset.UTC).toString();
    }

    private static String likeEscape(String value) {
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }

    public PageOf<Consultation> list(String q, String status, LocalDate from, LocalDate to, int page, int size) {
        if (status != null && !status.isBlank() && !STATUSES.contains(status)) throw bad("Trạng thái không hợp lệ.");
        StringBuilder where = new StringBuilder(" WHERE 1=1");
        List<Object> args = new ArrayList<>();
        if (q != null && !q.isBlank()) {
            String term = "%" + likeEscape(q.trim()) + "%";
            String digits = normalizePhone(q.trim()) != null ? normalizePhone(q.trim()) : q.replaceAll("[^0-9]", "");
            where.append(" AND (c.parent_name LIKE ?");
            args.add(term);
            if (!digits.isEmpty()) {
                where.append(" OR c.phone LIKE ?");
                args.add("%" + digits + "%");
            }
            where.append(")");
        }
        if (status != null && !status.isBlank()) {
            where.append(" AND c.status=?");
            args.add(status);
        }
        if (from != null) {
            where.append(" AND c.created_at >= ?");
            args.add(from.atStartOfDay(LOCAL).withZoneSameInstant(ZoneOffset.UTC).toLocalDateTime());
        }
        if (to != null) {
            where.append(" AND c.created_at < ?");
            args.add(to.plusDays(1).atStartOfDay(LOCAL).withZoneSameInstant(ZoneOffset.UTC).toLocalDateTime());
        }
        int pageSize = Math.min(Math.max(size, 1), 100), pageIndex = Math.max(page, 0);
        Long total = jdbc.queryForObject("SELECT COUNT(*) FROM consultation_requests c" + where, Long.class, args.toArray());
        List<Object> pageArgs = new ArrayList<>(args);
        pageArgs.add(pageSize);
        pageArgs.add((long) pageIndex * pageSize);
        List<Consultation> items = jdbc.query(SELECT + where + " ORDER BY c.created_at DESC,c.id LIMIT ? OFFSET ?", this::map, pageArgs.toArray());
        return new PageOf<>(items, total == null ? 0 : total, pageIndex, pageSize);
    }

    public Consultation get(String id) {
        return jdbc.query(SELECT + " WHERE c.id=?", this::map, id).stream().findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy yêu cầu tư vấn."));
    }

    @Transactional
    public Consultation update(String id, ConsultationUpdate in, String userId) {
        get(id);
        if (in == null || in.status() == null || !STATUSES.contains(in.status())) throw bad("Trạng thái không hợp lệ.");
        String assignee = in.assignedUserId() == null || in.assignedUserId().isBlank() ? null : in.assignedUserId();
        if (assignee != null && jdbc.queryForObject("SELECT COUNT(*) FROM app_user WHERE id=? AND status='ACTIVE' AND role IN ('ADMIN','TEACHER')", Integer.class, assignee) == 0)
            throw bad("Người phụ trách phải là tài khoản quản trị hoặc giáo viên đang hoạt động.");
        String note = in.internalNote() == null ? "" : in.internalNote().replace("\r\n", "\n").replaceAll("[\\p{Cntrl}&&[^\\n\\t]]", "").trim();
        if (note.length() > 4000) throw bad("Ghi chú nội bộ tối đa 4000 ký tự.");
        jdbc.update("UPDATE consultation_requests SET status=?,assigned_user_id=?,internal_note=?,updated_by=? WHERE id=?",
                in.status(), assignee, note.isEmpty() ? null : note, userId, id);
        return get(id);
    }

    public List<Assignee> assignees() {
        return jdbc.query("SELECT id,display_name,role FROM app_user WHERE status='ACTIVE' AND role IN ('ADMIN','TEACHER') ORDER BY display_name",
                (rs, i) -> new Assignee(rs.getString(1), rs.getString(2), rs.getString(3)));
    }
}
