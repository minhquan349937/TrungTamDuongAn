-- ONLY on a fresh disposable test database, after V1 and V2.
-- Fixtures are fictional. Procedures commit, so this test intentionally keeps its fixtures.
SET NAMES utf8mb4;
SET time_zone='+00:00';
SET @passed=0;
DELIMITER $$
CREATE PROCEDURE test_assert(IN p_ok BOOLEAN, IN p_label VARCHAR(200))
BEGIN
    IF p_ok IS NULL OR p_ok=0 THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT=p_label; END IF;
    SET @passed=@passed+1;
END$$
CREATE PROCEDURE test_error(IN p_sql TEXT, IN p_errno INT, IN p_label VARCHAR(200))
BEGIN
    DECLARE v_errno INT DEFAULT 0;
    SET @test_sql=p_sql;
    PREPARE test_statement FROM @test_sql;
    BEGIN
        DECLARE CONTINUE HANDLER FOR SQLEXCEPTION GET DIAGNOSTICS CONDITION 1 v_errno=MYSQL_ERRNO;
        EXECUTE test_statement;
    END;
    DEALLOCATE PREPARE test_statement;
    CALL test_assert(v_errno=p_errno,p_label);
END$$
DELIMITER ;

CALL test_assert((SELECT COUNT(*)=28 FROM information_schema.tables WHERE table_schema=DATABASE() AND table_type='BASE TABLE'),'28 tables');
CALL test_assert((SELECT COUNT(*)=0 FROM information_schema.columns WHERE table_schema=DATABASE() AND column_name IN ('branch_id','tenant_id','from_branch_id','to_branch_id','child_id')),'No branch or duplicate child keys');
CALL test_assert((SELECT COUNT(*)=0 FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name IN ('branch','student_transfer','branch_holiday','student_case_assignment','staff_certificate','room','class_teacher_assignment','session_teacher','session_student','daycare_daily_log','children')),'Removed tables absent');
INSERT INTO center(name) VALUES('Trung tâm kiểm thử giả');
CALL test_error('INSERT INTO center(id,name) VALUES(2,''Other'')',3819,'Only one center');
INSERT INTO app_user(id,email,password_hash,display_name,role) VALUES
('admin','admin@example.invalid','TEST_ONLY_NOT_A_HASH','Quản trị thử','ADMIN'),
('teacher','teacher@example.invalid','TEST_ONLY_NOT_A_HASH','Giáo viên thử','TEACHER'),
('parent','parent@example.invalid','TEST_ONLY_NOT_A_HASH','Phụ huynh thử','PARENT');
CALL test_error('INSERT INTO app_user(id,email,password_hash,display_name,role) VALUES(''bad-role'',''bad@example.invalid'',''x'',''x'',''OWNER'')',3819,'Only three roles');
CALL test_error('INSERT INTO app_user(id,email,password_hash,display_name,role) VALUES(''bad-email'',''UPPER@example.invalid'',''x'',''x'',''ADMIN'')',3819,'Canonical email');
INSERT INTO staff(id,code,user_id,full_name,joined_on) VALUES('staff1','GV01','teacher','Giáo viên thử','2026-01-01');
INSERT INTO staff(id,code,full_name,joined_on) VALUES('staff2','GV02','Giáo viên thử 2','2026-01-01');
CALL test_error('INSERT INTO staff(id,code,user_id,full_name,joined_on) VALUES(''bad-staff'',''BAD'',''parent'',''Bad'',''2026-01-01'')',1452,'Parent cannot be teacher');
INSERT INTO guardian(id,code,user_id,full_name) VALUES('guardian1','PH01','parent','Phụ huynh thử');
INSERT INTO guardian(id,code,full_name) VALUES('guardian2','PH02','Phụ huynh thử 2');
INSERT INTO student(id,code,full_name,birth_date,admitted_on) VALUES
('student1','HS01','Trẻ giả lập 1','2020-01-01','2026-01-01'),
('student2','HS02','Trẻ giả lập 2','2021-01-01','2026-01-01');
CALL test_error('UPDATE student SET withdrawn_on=''2026-10-01'' WHERE id=''student1''',3819,'Withdrawal consistent with status');
INSERT INTO student_guardian(student_id,guardian_id,relationship,is_primary) VALUES('student1','guardian1','MOTHER',1);
CALL test_error('INSERT INTO student_guardian(student_id,guardian_id,relationship,is_primary) VALUES(''student1'',''guardian2'',''FATHER'',1)',1062,'One active primary guardian');
UPDATE student_guardian SET revoked_at=CURRENT_TIMESTAMP(6) WHERE student_id='student1';
INSERT INTO student_guardian(student_id,guardian_id,relationship,is_primary) VALUES('student1','guardian2','FATHER',1);
CALL test_assert((SELECT COUNT(*)=1 FROM student_guardian WHERE primary_student_id='student1'),'Revocation permits new primary');
INSERT INTO learning_class(id,code,name,class_type,teacher_id,capacity,starts_on) VALUES
('class1','L01','Lớp thử 1','GROUP','staff1',5,'2026-01-01'),
('class2','L02','Lớp thử 2','GROUP','staff2',5,'2026-01-01');
CALL test_error('UPDATE learning_class SET class_type=''INDIVIDUAL'' WHERE id=''class1''',3819,'Individual class capacity one');
INSERT INTO enrollment(id,class_id,student_id,started_on) VALUES('enrollment1','class1','student1','2026-01-01');

CALL save_class_session('session1','class1','staff1','2026-10-26 01:00:00','2026-10-26 02:00:00','SCHEDULED',NULL,NULL,'admin');
CALL test_error('CALL save_class_session(''teacher-overlap'',''class2'',''staff1'',''2026-10-26 01:30:00'',''2026-10-26 02:30:00'',''SCHEDULED'',NULL,NULL,''admin'')',1644,'Reject teacher overlap');
CALL test_error('CALL save_class_session(''class-overlap'',''class1'',''staff2'',''2026-10-26 01:30:00'',''2026-10-26 02:30:00'',''SCHEDULED'',NULL,NULL,''admin'')',1644,'Reject class overlap');
CALL save_class_session('session2','class1','staff1','2026-10-26 02:00:00','2026-10-26 03:00:00','SCHEDULED',NULL,NULL,'admin');
CALL test_assert((SELECT COUNT(*)=2 FROM class_session),'Adjacent sessions accepted');
CALL save_class_session('cancelled','class1','staff1','2026-10-26 01:00:00','2026-10-26 02:00:00','CANCELLED',NULL,'Cancelled test','admin');
CALL test_error('CALL save_class_session(''cancelled'',''class1'',''staff1'',''2026-10-26 01:00:00'',''2026-10-26 02:00:00'',''SCHEDULED'',NULL,NULL,''admin'')',1644,'Reactivation checks overlap');
CALL test_error('CALL save_class_session(''session2'',''class1'',''staff1'',''2026-10-26 01:30:00'',''2026-10-26 03:00:00'',''SCHEDULED'',NULL,NULL,''admin'')',1644,'Reschedule checks overlap');
CALL test_assert((SELECT starts_at='2026-10-26 02:00:00' FROM class_session WHERE id='session2'),'Failed update rolled back');
CALL test_error('CALL save_class_session(''unauthorized'',''class1'',''staff1'',''2026-10-27 01:00:00'',''2026-10-27 02:00:00'',''SCHEDULED'',NULL,NULL,''parent'')',1644,'Only admin schedules');
INSERT INTO student_attendance(id,session_id,student_id,status,recorded_by) VALUES('att1','session1','student1','PRESENT','teacher');
CALL test_error('INSERT INTO student_attendance(id,session_id,student_id,status,recorded_by) VALUES(''att2'',''session1'',''student1'',''ABSENT'',''teacher'')',1062,'Unique attendance');
CALL test_error('DELETE FROM class_session WHERE id=''session1''',1451,'Attendance survives deletion attempt');

CALL save_staff_salary('salary1','staff1',8000000,'2026-01-01','2026-07-01','admin');
CALL save_staff_salary('salary2','staff1',9000000,'2026-07-01',NULL,'admin');
CALL test_assert((SELECT COUNT(*)=2 FROM staff_salary),'Adjacent salary periods');
CALL test_error('CALL save_staff_salary(''salary3'',''staff1'',10000000,''2026-06-01'',NULL,''admin'')',1644,'Reject overlapping salary');
CALL test_error('CALL save_staff_salary(''salary1'',''staff1'',8000000,''2026-01-01'',''2026-08-01'',''admin'')',1644,'Salary update overlap');
CALL test_error('INSERT INTO leave_request(id,staff_id,starts_on,ends_on,reason,status,requested_by,reviewed_by,reviewed_at) VALUES(''leave1'',''staff1'',''2026-10-26'',''2026-10-26'',''Test'',''APPROVED'',''teacher'',''teacher'',NOW())',3819,'No self approval');

INSERT INTO invoice(id,code,student_id,billing_month,issued_on,due_on,status,created_by)
VALUES('invoice1','HP01','student1','2026-10-01','2026-10-01','2026-10-31','ISSUED','admin');
INSERT INTO invoice_line(id,invoice_id,description,quantity,unit_price,discount) VALUES
('line1','invoice1','Học phí thử',10,100000,100000),('line2','invoice1','Khoản thử',1,100000,0);
INSERT INTO payment(id,receipt_code,invoice_id,amount,method,paid_at,idempotency_key,received_by) VALUES
('pay1','PT01','invoice1',200000,'CASH',NOW(),'request1','admin'),
('pay2','PT02','invoice1',300000,'BANK_TRANSFER',NOW(),'request2','admin');
CALL test_assert((SELECT total_amount=1000000 AND paid_amount=500000 AND outstanding_amount=500000 AND payment_status='PARTIAL' FROM invoice_balance WHERE invoice_id='invoice1'),'Invoice aggregate avoids join multiplication');
CALL test_error('INSERT INTO payment(id,receipt_code,invoice_id,amount,method,paid_at,idempotency_key,received_by) VALUES(''pay3'',''PT03'',''invoice1'',100000,''CASH'',NOW(),''request1'',''admin'')',1062,'Payment request idempotency');
UPDATE payment SET status='VOID',voided_by='admin',voided_at=NOW(),void_reason='Test' WHERE id='pay2';
CALL test_assert((SELECT paid_amount=200000 AND outstanding_amount=800000 FROM invoice_balance WHERE invoice_id='invoice1'),'Void payment excluded');
CALL test_error('UPDATE invoice_line SET discount=2000000 WHERE id=''line1''',3819,'No negative invoice line');

INSERT INTO domains(id,code,name) VALUES('domain1','ES.1','Ngôn ngữ tiếp nhận'),('domain2','ES.2','Lĩnh vực thử');
INSERT INTO skill_levels(id,domain_id,code,name) VALUES('level1','domain1','LV1','Cấp độ 1'),('level2','domain2','LV1','Cấp độ 1');
CALL test_assert((SELECT COUNT(*)=2 FROM skill_levels WHERE code='LV1'),'Level code scoped to domain');
INSERT INTO goals(id,skill_level_id,code,name) VALUES('goal1','level1','ES.1.LV1.1','Định vị nguồn âm thanh');
INSERT INTO specific_goals(id,goal_id,code,description,expected_behavior) VALUES
('specific1','goal1','ES.1.LV1.1A','Quay đầu về nguồn âm thanh','Quay đầu'),
('specific2','goal1','ES.1.LV1.1B','Mục tiêu chưa có tiêu chí',NULL);
INSERT INTO goal_criteria(id,specific_goal_id,measurement_type,target_count,observation_minutes,required_periods,period_unit,must_be_consecutive,adult_count,setting_count)
VALUES('criteria1','specific1','COUNT',1,30,3,'DAY',1,2,2);
CALL test_error('INSERT INTO goal_criteria(id,specific_goal_id,measurement_type) VALUES(''duplicate-criteria'',''specific1'',''COUNT'')',1062,'One criteria set per specific goal');
CALL test_error('UPDATE goal_criteria SET target_percentage=101 WHERE id=''criteria1''',3819,'Percentage range');
CALL test_error('UPDATE goal_criteria SET successful_opportunities=4,total_opportunities=3 WHERE id=''criteria1''',3819,'Criteria successes cannot exceed opportunities');
CALL test_error('UPDATE goal_criteria SET required_periods=NULL WHERE id=''criteria1''',3819,'Complete period definition');
INSERT INTO learning_plans(id,student_id,name,start_date,status,created_by) VALUES('plan1','student1','Kế hoạch thử','2026-10-01','ACTIVE','teacher');
INSERT INTO plan_goals(id,plan_id,specific_goal_id,goal_snapshot,criteria_snapshot,assigned_by)
VALUES('assigned1','plan1','specific1',JSON_OBJECT('untrusted',1),JSON_OBJECT('untrusted',1),'teacher'),
('assigned2','plan1','specific2',JSON_OBJECT(),JSON_OBJECT(),'teacher');
CALL test_assert((SELECT JSON_UNQUOTE(goal_snapshot->'$.code')='ES.1.LV1.1A' AND criteria_snapshot->'$.required_periods'=3 AND JSON_CONTAINS_PATH(goal_snapshot,'one','$.untrusted')=0 FROM plan_goals WHERE id='assigned1'),'Snapshot captured from bank');
CALL test_assert((SELECT criteria_snapshot->'$.criteria_defined'=0 FROM plan_goals WHERE id='assigned2'),'Optional criteria explicit in snapshot');
UPDATE specific_goals SET description='Nội dung mới' WHERE id='specific1';
UPDATE goal_criteria SET target_count=2 WHERE id='criteria1';
CALL test_assert((SELECT JSON_UNQUOTE(goal_snapshot->'$.description')='Quay đầu về nguồn âm thanh' AND criteria_snapshot->'$.target_count'=1 FROM plan_goals WHERE id='assigned1'),'Bank changes preserve assigned snapshot');
CALL test_error('UPDATE plan_goals SET criteria_snapshot=JSON_OBJECT() WHERE id=''assigned1''',1644,'Snapshot immutable');
CALL test_error('UPDATE learning_plans SET student_id=''student2'' WHERE id=''plan1''',1644,'Plan student immutable');
INSERT INTO progress_records(id,plan_goal_id,observed_at,session_key,success_count,opportunity_count,observation_minutes,partner_reference,setting,recorded_by)
VALUES('progress1','assigned1','2026-10-26 03:00:00','HOME-OBSERVATION-1',1,2,30,'adult:001','HOME','teacher');
CALL test_assert((SELECT status='NOT_STARTED' FROM plan_goals WHERE id='assigned1'),'One observation does not auto-achieve goal');
CALL test_error('UPDATE progress_records SET success_count=3 WHERE id=''progress1''',3819,'Observation successes cannot exceed opportunities');
CALL test_error('UPDATE progress_records SET setting=''UNKNOWN'' WHERE id=''progress1''',3819,'Consistent context vocabulary');
CALL test_error('DELETE FROM plan_goals WHERE id=''assigned1''',1451,'Observation history retained');
CALL test_error('INSERT INTO plan_goals(id,plan_id,specific_goal_id,goal_snapshot,criteria_snapshot,assigned_by,supersedes_id) VALUES(''bad-replacement'',''plan1'',''specific1'',JSON_OBJECT(),JSON_OBJECT(),''teacher'',''assigned1'')',1644,'Pause predecessor before replacement');
UPDATE plan_goals SET status='PAUSED' WHERE id='assigned1';
INSERT INTO plan_goals(id,plan_id,specific_goal_id,goal_snapshot,criteria_snapshot,assigned_by,supersedes_id)
VALUES('assigned3','plan1','specific1',JSON_OBJECT(),JSON_OBJECT(),'teacher','assigned1');
CALL test_assert((SELECT criteria_snapshot->'$.target_count'=2 FROM plan_goals WHERE id='assigned3') AND (SELECT plan_goal_id='assigned1' FROM progress_records WHERE id='progress1'),'Replacement captures new criteria and keeps old observations');
CALL test_error('UPDATE plan_goals SET status=''IN_PROGRESS'' WHERE id=''assigned1''',1644,'Cannot reactivate superseded version');
CALL test_error('DELETE FROM specific_goals WHERE id=''specific2''',1451,'Assigned master goal retained');
CALL test_assert((SELECT COUNT(*)=5 FROM audit_event WHERE action IN ('SAVE_SESSION','SAVE_SALARY')),'Only successful procedure calls audited');
SELECT @passed AS passed_assertions, VERSION() AS mysql_version;
DROP PROCEDURE test_error;
DROP PROCEDURE test_assert;
