-- Run after V1 and V2 on an isolated empty test database only.
-- Fixture and assertions are rolled back together.
BEGIN;
SET search_path TO duong_an, public;
SET TIME ZONE 'Asia/Ho_Chi_Minh';

CREATE FUNCTION pg_temp.assert_true(ok boolean, label text) RETURNS void LANGUAGE plpgsql AS $$
BEGIN
    IF ok IS DISTINCT FROM true THEN RAISE EXCEPTION 'FAIL: %', label; END IF;
    RAISE NOTICE 'PASS: %', label;
END;
$$;
CREATE FUNCTION pg_temp.expect_error(command text, expected_state text, label text) RETURNS void LANGUAGE plpgsql AS $$
DECLARE actual_state text;
BEGIN
    BEGIN
        EXECUTE command;
    EXCEPTION WHEN OTHERS THEN
        GET STACKED DIAGNOSTICS actual_state = RETURNED_SQLSTATE;
    END;
    IF actual_state IS DISTINCT FROM expected_state THEN
        RAISE EXCEPTION 'FAIL: %, expected %, received %', label, expected_state, coalesce(actual_state,'NO ERROR');
    END IF;
    RAISE NOTICE 'PASS: %', label;
END;
$$;

INSERT INTO center(id,code,name) VALUES ('00000000-0000-0000-0000-000000000001','TEST','Trung tâm kiểm thử');
INSERT INTO branch(id,center_id,code,name) VALUES
('00000000-0000-0000-0000-000000000011','00000000-0000-0000-0000-000000000001','B1','Cơ sở 1'),
('00000000-0000-0000-0000-000000000012','00000000-0000-0000-0000-000000000001','B2','Cơ sở 2');
INSERT INTO app_user(id,email,password_hash,display_name,role) VALUES
('00000000-0000-0000-0000-000000000021','admin@example.invalid','TEST-NOT-A-LOGIN','Admin giả lập','ADMIN'),
('00000000-0000-0000-0000-000000000022','teacher@example.invalid','TEST-NOT-A-LOGIN','Giáo viên giả lập','TEACHER'),
('00000000-0000-0000-0000-000000000023','parent@example.invalid','TEST-NOT-A-LOGIN','Phụ huynh giả lập','PARENT');
INSERT INTO staff(id,code,user_id,home_branch_id,full_name,hired_on) VALUES
('00000000-0000-0000-0000-000000000031','T1','00000000-0000-0000-0000-000000000022','00000000-0000-0000-0000-000000000011','Giáo viên kiểm thử','2026-01-01');
INSERT INTO guardian(id,code,user_id,full_name) VALUES
('00000000-0000-0000-0000-000000000041','P1','00000000-0000-0000-0000-000000000023','Phụ huynh kiểm thử'),
('00000000-0000-0000-0000-000000000042','P2',NULL,'Phụ huynh thứ hai');
INSERT INTO student(id,code,branch_id,full_name,birth_date,admitted_on) VALUES
('00000000-0000-0000-0000-000000000051','S1','00000000-0000-0000-0000-000000000011','Học sinh kiểm thử','2021-01-01','2026-01-01'),
('00000000-0000-0000-0000-000000000052','S2','00000000-0000-0000-0000-000000000011','Học sinh thứ hai','2021-02-01','2026-01-01');
INSERT INTO student_guardian(student_id,guardian_id,relationship,is_primary) VALUES
('00000000-0000-0000-0000-000000000051','00000000-0000-0000-0000-000000000041','MOTHER',true),
('00000000-0000-0000-0000-000000000051','00000000-0000-0000-0000-000000000042','FATHER',false),
('00000000-0000-0000-0000-000000000052','00000000-0000-0000-0000-000000000041','MOTHER',true);
INSERT INTO room(id,branch_id,code,name,capacity) VALUES
('00000000-0000-0000-0000-000000000061','00000000-0000-0000-0000-000000000011','R1','Phòng 1',10),
('00000000-0000-0000-0000-000000000062','00000000-0000-0000-0000-000000000011','R2','Phòng 2',10),
('00000000-0000-0000-0000-000000000063','00000000-0000-0000-0000-000000000012','R3','Phòng khác cơ sở',10);
INSERT INTO learning_class(id,branch_id,code,name,type,capacity,opened_on) VALUES
('00000000-0000-0000-0000-000000000071','00000000-0000-0000-0000-000000000011','C1','Lớp cá nhân','INDIVIDUAL',1,'2026-01-01'),
('00000000-0000-0000-0000-000000000072','00000000-0000-0000-0000-000000000011','C2','Lớp nhóm','GROUP',10,'2026-01-01');
INSERT INTO enrollment(id,class_id,student_id,starts_on) VALUES
('00000000-0000-0000-0000-000000000081','00000000-0000-0000-0000-000000000071','00000000-0000-0000-0000-000000000051','2026-01-01'),
('00000000-0000-0000-0000-000000000082','00000000-0000-0000-0000-000000000072','00000000-0000-0000-0000-000000000051','2026-01-01');
INSERT INTO class_session(id,class_id,branch_id,room_id,starts_at,ends_at,created_by) VALUES
('00000000-0000-0000-0000-000000000091','00000000-0000-0000-0000-000000000071','00000000-0000-0000-0000-000000000011','00000000-0000-0000-0000-000000000061','2026-10-22 08:00+07','2026-10-22 09:00+07','00000000-0000-0000-0000-000000000021'),
('00000000-0000-0000-0000-000000000092','00000000-0000-0000-0000-000000000072','00000000-0000-0000-0000-000000000011','00000000-0000-0000-0000-000000000062','2026-10-22 09:00+07','2026-10-22 10:00+07','00000000-0000-0000-0000-000000000021');
INSERT INTO session_teacher(session_id,staff_id) VALUES
('00000000-0000-0000-0000-000000000091','00000000-0000-0000-0000-000000000031'),
('00000000-0000-0000-0000-000000000092','00000000-0000-0000-0000-000000000031');
INSERT INTO session_student(session_id,class_id,student_id,enrollment_id) VALUES
('00000000-0000-0000-0000-000000000091','00000000-0000-0000-0000-000000000071','00000000-0000-0000-0000-000000000051','00000000-0000-0000-0000-000000000081'),
('00000000-0000-0000-0000-000000000092','00000000-0000-0000-0000-000000000072','00000000-0000-0000-0000-000000000051','00000000-0000-0000-0000-000000000082');

SELECT pg_temp.assert_true((SELECT count(*)=3 FROM student_guardian),'many-to-many guardians');
SELECT pg_temp.expect_error($q$UPDATE student_guardian SET is_primary=true WHERE guardian_id='00000000-0000-0000-0000-000000000042'$q$,'23505','only one primary guardian');
SELECT pg_temp.expect_error($q$INSERT INTO app_user(email,password_hash,display_name,role) VALUES ('other@example.invalid','test','Test','SUPERADMIN')$q$,'23514','exactly three application roles');
SELECT pg_temp.expect_error($q$UPDATE guardian SET user_id='00000000-0000-0000-0000-000000000021' WHERE code='P1'$q$,'23503','guardian account must be PARENT');
SELECT pg_temp.expect_error($q$UPDATE learning_class SET capacity=2 WHERE code='C1'$q$,'23514','individual class capacity is one');
SELECT pg_temp.expect_error($q$INSERT INTO enrollment(class_id,student_id,starts_on) VALUES ('00000000-0000-0000-0000-000000000071','00000000-0000-0000-0000-000000000051','2026-02-01')$q$,'23P01','reject overlapping enrollment in same class');
SELECT pg_temp.assert_true((SELECT count(*)=2 FROM session_teacher),'adjacent sessions allowed');
SELECT pg_temp.expect_error($q$UPDATE class_session SET ends_at=starts_at WHERE id='00000000-0000-0000-0000-000000000091'$q$,'23514','reject invalid session interval');
SELECT pg_temp.expect_error($q$UPDATE class_session SET room_id='00000000-0000-0000-0000-000000000063' WHERE id='00000000-0000-0000-0000-000000000091'$q$,'23503','room must belong to session branch');
SELECT pg_temp.expect_error($q$UPDATE class_session SET starts_at='2026-10-22 08:30+07' WHERE id='00000000-0000-0000-0000-000000000092'$q$,'23P01','reject teacher conflict when rescheduling');
DELETE FROM session_teacher WHERE session_id='00000000-0000-0000-0000-000000000092';
SELECT pg_temp.expect_error($q$UPDATE class_session SET starts_at='2026-10-22 08:30+07' WHERE id='00000000-0000-0000-0000-000000000092'$q$,'23P01','reject shared student conflict');
DELETE FROM session_student WHERE session_id='00000000-0000-0000-0000-000000000092';
UPDATE class_session SET starts_at='2026-10-22 08:30+07' WHERE id='00000000-0000-0000-0000-000000000092';
SELECT pg_temp.expect_error($q$UPDATE class_session SET room_id='00000000-0000-0000-0000-000000000061' WHERE id='00000000-0000-0000-0000-000000000092'$q$,'23P01','reject room conflict');
SELECT pg_temp.expect_error($q$UPDATE class_session SET class_id='00000000-0000-0000-0000-000000000071' WHERE id='00000000-0000-0000-0000-000000000092'$q$,'23P01','reject class conflict');
SELECT pg_temp.expect_error($q$INSERT INTO session_teacher(session_id,staff_id) VALUES ('00000000-0000-0000-0000-000000000092','00000000-0000-0000-0000-000000000031')$q$,'23P01','reject conflict on adding a teacher');
UPDATE class_session SET status='CANCELLED' WHERE id='00000000-0000-0000-0000-000000000091';
INSERT INTO session_teacher(session_id,staff_id) VALUES ('00000000-0000-0000-0000-000000000092','00000000-0000-0000-0000-000000000031');
SELECT pg_temp.assert_true((SELECT cancelled FROM session_teacher WHERE session_id='00000000-0000-0000-0000-000000000091'),'cancellation releases roster reservation');
SELECT pg_temp.expect_error($q$UPDATE class_session SET status='SCHEDULED' WHERE id='00000000-0000-0000-0000-000000000091'$q$,'23P01','reactivation rechecks conflicts');
SELECT pg_temp.expect_error($q$INSERT INTO student_attendance(session_id,student_id,status,recorded_by) VALUES ('00000000-0000-0000-0000-000000000092','00000000-0000-0000-0000-000000000052','PRESENT','00000000-0000-0000-0000-000000000021')$q$,'23503','attendance requires concrete roster membership');
SELECT pg_temp.expect_error($q$INSERT INTO session_student(session_id,class_id,student_id,enrollment_id) VALUES ('00000000-0000-0000-0000-000000000092','00000000-0000-0000-0000-000000000072','00000000-0000-0000-0000-000000000052','00000000-0000-0000-0000-000000000081')$q$,'23503','roster enrollment must match class and student');

INSERT INTO invoice(id,code,student_id,branch_id,billing_month,issued_on,due_on,status,created_by) VALUES
('00000000-0000-0000-0000-000000000101','INV-TEST','00000000-0000-0000-0000-000000000051','00000000-0000-0000-0000-000000000011','2026-10-01','2026-10-01','2026-10-10','ISSUED','00000000-0000-0000-0000-000000000021');
INSERT INTO invoice_line(invoice_id,description,quantity,unit_price,discount) VALUES
('00000000-0000-0000-0000-000000000101','Học phí',10,200000,0),
('00000000-0000-0000-0000-000000000101','Bán trú',1,500000,100000);
INSERT INTO payment(receipt_code,invoice_id,amount,method,paid_at,idempotency_key,received_by) VALUES
('PT1','00000000-0000-0000-0000-000000000101',1000000,'CASH',now(),'00000000-0000-0000-0000-000000000111','00000000-0000-0000-0000-000000000021');
SELECT pg_temp.assert_true((SELECT total_amount=2400000 AND paid_amount=1000000 AND outstanding_amount=1400000 AND payment_status='PARTIAL' FROM invoice_balance),'partial payment balance');
INSERT INTO payment(receipt_code,invoice_id,amount,method,paid_at,idempotency_key,received_by) VALUES
('PT2','00000000-0000-0000-0000-000000000101',1400000,'BANK_TRANSFER',now(),'00000000-0000-0000-0000-000000000112','00000000-0000-0000-0000-000000000021');
SELECT pg_temp.assert_true((SELECT total_amount=2400000 AND paid_amount=2400000 AND outstanding_amount=0 AND payment_status='PAID' FROM invoice_balance),'multiple invoice lines and payments do not multiply totals');
SELECT pg_temp.expect_error($q$UPDATE payment SET idempotency_key='00000000-0000-0000-0000-000000000111' WHERE receipt_code='PT2'$q$,'23505','duplicate payment request key rejected');
SELECT pg_temp.expect_error($q$UPDATE invoice_line SET discount=9999999 WHERE description='Bán trú'$q$,'23514','discount cannot exceed line amount');
UPDATE payment SET status='VOID',voided_by='00000000-0000-0000-0000-000000000021',voided_at=now(),void_reason='Ghi nhận nhầm' WHERE receipt_code='PT2';
SELECT pg_temp.assert_true((SELECT outstanding_amount=1400000 AND payment_status='PARTIAL' FROM invoice_balance),'voided receipt excluded from balance');
SELECT pg_temp.expect_error($q$DELETE FROM student WHERE code='S1'$q$,'23503','student with history cannot be physically deleted');
INSERT INTO staff_salary(staff_id,amount,starts_on,ends_on,created_by) VALUES
('00000000-0000-0000-0000-000000000031',12000000,'2026-01-01','2026-07-01','00000000-0000-0000-0000-000000000021'),
('00000000-0000-0000-0000-000000000031',13000000,'2026-07-01',NULL,'00000000-0000-0000-0000-000000000021');
SELECT pg_temp.assert_true((SELECT count(*)=2 FROM staff_salary),'adjacent salary history allowed');
SELECT pg_temp.expect_error($q$INSERT INTO staff_salary(staff_id,amount,starts_on,created_by) VALUES ('00000000-0000-0000-0000-000000000031',14000000,'2026-06-01','00000000-0000-0000-0000-000000000021')$q$,'23P01','salary periods cannot overlap');
INSERT INTO staff_checkin(staff_id,branch_id,work_date,checked_in_at,recorded_by) VALUES
('00000000-0000-0000-0000-000000000031','00000000-0000-0000-0000-000000000011','2026-10-22','2026-10-22 07:30+07','00000000-0000-0000-0000-000000000022');
SELECT pg_temp.expect_error($q$INSERT INTO staff_checkin(staff_id,branch_id,work_date,checked_in_at,recorded_by) VALUES ('00000000-0000-0000-0000-000000000031','00000000-0000-0000-0000-000000000011','2026-10-22','2026-10-22 08:30+07','00000000-0000-0000-0000-000000000022')$q$,'23505','one checkin per staff per day');
SELECT pg_temp.expect_error($q$UPDATE staff_checkin SET checked_out_at='2026-10-22 07:00+07'$q$,'23514','checkout cannot precede checkin');
INSERT INTO leave_request(id,staff_id,starts_on,ends_on,reason,requested_by) VALUES
('00000000-0000-0000-0000-000000000121','00000000-0000-0000-0000-000000000031','2026-10-25','2026-10-26','Nghỉ phép minh họa','00000000-0000-0000-0000-000000000022');
SELECT pg_temp.expect_error($q$UPDATE leave_request SET status='APPROVED'$q$,'23514','approval requires reviewer and review time');
SELECT pg_temp.expect_error($q$UPDATE leave_request SET status='APPROVED',reviewed_by=requested_by,reviewed_at=now()$q$,'23514','requester cannot approve own leave');
UPDATE leave_request SET status='APPROVED',reviewed_by='00000000-0000-0000-0000-000000000021',reviewed_at=now();
SELECT pg_temp.expect_error($q$INSERT INTO leave_request(staff_id,starts_on,ends_on,reason,requested_by,status,reviewed_by,reviewed_at) VALUES ('00000000-0000-0000-0000-000000000031','2026-10-26','2026-10-27','Trùng ngày','00000000-0000-0000-0000-000000000022','APPROVED','00000000-0000-0000-0000-000000000021',now())$q$,'23P01','approved leave includes its last day and cannot overlap');
SELECT pg_temp.assert_true((SELECT count(*)=30 FROM information_schema.tables WHERE table_schema='duong_an' AND table_type='BASE TABLE'),'core schema has 30 tables');
ROLLBACK;
