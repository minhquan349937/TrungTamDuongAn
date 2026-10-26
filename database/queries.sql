-- Illustrative prepared SQL for Spring JDBC/JPA, NOT an automatically executed migration.
-- :parameter names must be bound, never interpolated into SQL strings.
SET search_path TO duong_an, public;

-- PARENT: only linked children, with portal permission still active.
SELECT s.id, s.code, s.full_name, s.birth_date, s.status
FROM student s
JOIN student_guardian sg ON sg.student_id=s.id
JOIN guardian g ON g.id=sg.guardian_id
JOIN app_user u ON u.id=g.user_id
WHERE u.id=:authenticated_user_id AND u.role='PARENT' AND u.status='ACTIVE'
  AND sg.can_view_portal AND sg.revoked_at IS NULL
ORDER BY s.full_name, s.id;

-- TEACHER: concrete schedule in a half-open time interval.
SELECT cs.id, lc.name, r.name AS room_name, cs.starts_at, cs.ends_at, cs.status
FROM session_teacher st
JOIN staff t ON t.id=st.staff_id
JOIN app_user u ON u.id=t.user_id
JOIN class_session cs ON cs.id=st.session_id
JOIN learning_class lc ON lc.id=cs.class_id
JOIN room r ON r.id=cs.room_id
WHERE u.id=:authenticated_user_id AND u.role='TEACHER' AND u.status='ACTIVE'
  AND cs.starts_at < :to_time AND cs.ends_at > :from_time
ORDER BY cs.starts_at, cs.id;

-- ADMIN: active enrollments per type, not unique students across all types.
SELECT lc.type, count(e.id) AS enrollment_count,
       count(DISTINCT e.student_id) AS unique_students_in_type
FROM learning_class lc
LEFT JOIN enrollment e ON e.class_id=lc.id AND e.status<>'CANCELLED'
    AND daterange(e.starts_on,e.ends_on,'[)') @> CAST(:as_of_date AS date)
WHERE lc.status='ACTIVE'
GROUP BY lc.type;

-- ADMIN: unpaid issued invoices, independent of number of lines/payments.
SELECT i.id, i.code, s.full_name, i.billing_month, i.due_on,
       b.total_amount, b.paid_amount, b.outstanding_amount, b.payment_status
FROM invoice i JOIN invoice_balance b ON b.invoice_id=i.id
JOIN student s ON s.id=i.student_id
WHERE i.status='ISSUED' AND b.outstanding_amount>0
  AND i.billing_month=CAST(:billing_month AS date)
ORDER BY i.due_on, i.id;

-- PARENT: scope invoices using EXISTS; never trust a client-provided guardian ID.
SELECT i.code, b.*
FROM invoice i JOIN invoice_balance b ON b.invoice_id=i.id
WHERE i.status='ISSUED' AND EXISTS (
    SELECT 1 FROM student_guardian sg
    JOIN guardian g ON g.id=sg.guardian_id
    JOIN app_user u ON u.id=g.user_id
    WHERE sg.student_id=i.student_id AND u.id=:authenticated_user_id
      AND u.role='PARENT' AND u.status='ACTIVE'
      AND sg.can_view_portal AND sg.revoked_at IS NULL
)
ORDER BY i.billing_month DESC, i.id;

-- Attendance: unrecorded sessions are separate from absences.
-- Apply caller authorization BEFORE binding/accepting student_id.
SELECT count(*) FILTER (WHERE a.status IN ('PRESENT','LATE')) AS attended,
       count(*) FILTER (WHERE a.status='ABSENT') AS absent,
       count(*) FILTER (WHERE a.status='EXCUSED') AS excused,
       count(*) FILTER (WHERE a.status IS NULL) AS unrecorded,
       round(100.0*count(*) FILTER (WHERE a.status IN ('PRESENT','LATE')) /
             nullif(count(a.status),0),1) AS attendance_percent
FROM session_student roster
JOIN class_session cs ON cs.id=roster.session_id
LEFT JOIN student_attendance a ON a.session_id=roster.session_id AND a.student_id=roster.student_id
WHERE roster.student_id=:authorized_student_id AND cs.status<>'CANCELLED'
  AND cs.starts_at>=:from_time AND cs.starts_at<:to_time;
