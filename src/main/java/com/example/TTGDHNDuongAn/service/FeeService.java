package com.example.TTGDHNDuongAn.service;

import com.example.TTGDHNDuongAn.repository.ShortIds;
import com.example.TTGDHNDuongAn.security.AppUserPrincipal;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional(readOnly = true)
public class FeeService {
    public record Student(String id, String code, String fullName) {}
    public record CreateFee(String studentId, String title, String month, BigDecimal amount, LocalDate dueOn) {}
    public record Fee(String id, String code, String studentId, String studentName, String title, String month,
                      BigDecimal amount, BigDecimal paidAmount, BigDecimal outstandingAmount, LocalDate dueOn, LocalDateTime paidAt) {}
    private final JdbcTemplate jdbc;
    public FeeService(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    // Financial visibility is granted by an active family link. Academic report publication
    // flags are independent: this must not enable access to unpublished plan evaluations.
    private static final String FAMILY = """
        EXISTS (SELECT 1 FROM student_guardian sg JOIN guardian g ON g.id=sg.guardian_id
        JOIN app_user parent ON parent.id=g.user_id
        WHERE sg.student_id=s.id AND sg.revoked_at IS NULL AND g.user_id=?
        AND parent.role='PARENT' AND parent.status='ACTIVE')
        """;

    private void reader(AppUserPrincipal actor) {
        if (actor == null || !Set.of("ADMIN", "PARENT").contains(actor.role()))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Bạn không có quyền xem học phí.");
    }
    private void admin(AppUserPrincipal actor) {
        if (actor == null || !"ADMIN".equals(actor.role()))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Chỉ quản trị viên được cập nhật khoản thu.");
    }
    public List<Student> students(AppUserPrincipal actor) {
        reader(actor);
        return jdbc.query("SELECT s.id,s.code,s.full_name FROM student s " +
            (actor.role().equals("PARENT") ? "WHERE " + FAMILY : "") + " ORDER BY s.full_name,s.id",
            (rs,n) -> new Student(rs.getString("id"),rs.getString("code"),rs.getString("full_name")),
            actor.role().equals("PARENT") ? new Object[]{actor.id()} : new Object[]{});
    }
    public List<Fee> list(AppUserPrincipal actor) {
        reader(actor);
        return query(actor, null);
    }
    private List<Fee> query(AppUserPrincipal actor, String id) {
        var args = new ArrayList<Object>();
        String where = " WHERE i.status='ISSUED'";
        if (actor.role().equals("PARENT")) { where += " AND " + FAMILY; args.add(actor.id()); }
        if (id != null) { where += " AND i.id=?"; args.add(id); }
        return jdbc.query("""
            SELECT i.id,i.code,i.student_id,s.full_name,i.billing_month,i.due_on,
                b.total_amount,b.paid_amount,b.outstanding_amount,
                (SELECT GROUP_CONCAT(l.description ORDER BY l.id SEPARATOR '; ') FROM invoice_line l WHERE l.invoice_id=i.id) title,
                (SELECT MAX(p.paid_at) FROM payment p WHERE p.invoice_id=i.id AND p.status='POSTED') last_payment
            FROM invoice i JOIN student s ON s.id=i.student_id JOIN invoice_balance b ON b.invoice_id=i.id
            """ + where + " ORDER BY i.created_at DESC,i.id DESC", (rs,n) -> new Fee(
                rs.getString("id"),rs.getString("code"),rs.getString("student_id"),rs.getString("full_name"),
                rs.getString("title"),rs.getDate("billing_month").toLocalDate().toString().substring(0,7),
                rs.getBigDecimal("total_amount"),rs.getBigDecimal("paid_amount"),rs.getBigDecimal("outstanding_amount"),
                rs.getDate("due_on").toLocalDate(),rs.getBigDecimal("outstanding_amount").signum() <= 0 && rs.getTimestamp("last_payment") != null
                    ? rs.getTimestamp("last_payment").toLocalDateTime() : null), args.toArray());
    }
    @Transactional
    public Fee create(AppUserPrincipal actor, CreateFee input) {
        admin(actor);
        if (input == null || input.studentId() == null || input.studentId().isBlank() || input.title() == null ||
            input.title().isBlank() || input.title().strip().length() > 250 || input.month() == null ||
            !input.month().matches("[0-9]{4}-(0[1-9]|1[0-2])") || input.amount() == null ||
            input.amount().signum() <= 0 || input.amount().stripTrailingZeros().scale() > 0 ||
            input.amount().compareTo(new BigDecimal("99999999999999")) > 0)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Vui lòng nhập học sinh, nội dung, tháng và số tiền nguyên dương hợp lệ.");
        LocalDate billing;
        try { billing = YearMonth.parse(input.month()).atDay(1); }
        catch (DateTimeException ex) { throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Tháng học phí không hợp lệ."); }
        if (billing.getYear() < 1000) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Tháng học phí không hợp lệ.");
        LocalDate issued = LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh"));
        LocalDate due = input.dueOn() == null ? (billing.withDayOfMonth(billing.lengthOfMonth()).isBefore(issued)
            ? issued : billing.withDayOfMonth(billing.lengthOfMonth())) : input.dueOn();
        if (due.isBefore(issued)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Hạn thanh toán không được trước ngày tạo.");
        var found = jdbc.queryForList("SELECT id FROM student WHERE id=? FOR SHARE",String.class,input.studentId());
        if (found.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Không tìm thấy học sinh.");
        String id = ShortIds.next(jdbc,"invoice");
        String code = "HP-" + UUID.randomUUID().toString().replace("-", "").substring(0,24);
        jdbc.update("INSERT INTO invoice(id,code,student_id,billing_month,issued_on,due_on,status,created_by) VALUES(?,?,?,?,?,?,'ISSUED',?)",
            id,code,input.studentId(),billing,issued,due,actor.id());
        jdbc.update("INSERT INTO invoice_line(id,invoice_id,description,quantity,unit_price,discount) VALUES(?,?,?,1,?,0)",
            ShortIds.next(jdbc,"invoice_line"),id,input.title().strip(),input.amount());
        return query(actor,id).getFirst();
    }
    @Transactional
    public Fee pay(AppUserPrincipal actor, String id) {
        admin(actor);
        var invoice = jdbc.queryForList("SELECT status FROM invoice WHERE id=? FOR UPDATE",String.class,id);
        if (invoice.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Không tìm thấy khoản thu.");
        if (!invoice.getFirst().equals("ISSUED")) throw new ResponseStatusException(HttpStatus.CONFLICT,"Khoản thu chưa phát hành hoặc đã hủy.");
        // Locking the invoice serializes payments; repeating the request cannot charge twice.
        BigDecimal remaining = jdbc.queryForObject("SELECT outstanding_amount FROM invoice_balance WHERE invoice_id=?",BigDecimal.class,id);
        if (remaining != null && remaining.signum() > 0) {
            String paymentId = ShortIds.next(jdbc,"payment");
            jdbc.update("INSERT INTO payment(id,receipt_code,invoice_id,amount,method,status,paid_at,idempotency_key,received_by) VALUES(?,?,?,?,'CASH','POSTED',UTC_TIMESTAMP(6),?,?)",
                paymentId,"PT-"+UUID.randomUUID().toString().replace("-", ""),id,remaining,UUID.randomUUID().toString(),actor.id());
        }
        return query(actor,id).getFirst();
    }
}
