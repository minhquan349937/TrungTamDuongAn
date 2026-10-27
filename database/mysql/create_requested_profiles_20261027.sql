-- Run manually once on the verified development Docker MySQL localhost:3308.
-- Creates the four profiles requested by the user; not an automatic seed/migration.
SET NAMES utf8mb4;
USE duong_an_v11;
CREATE TEMPORARY TABLE requested_profile_checks(ok TINYINT NOT NULL CHECK(ok=1));
START TRANSACTION;
SET @parent_account=(SELECT id FROM app_user WHERE email='parent@duongan.local' AND role='PARENT' AND status='ACTIVE');
SET @teacher_account=(SELECT id FROM app_user WHERE email='teacher@duongan.local' AND role='TEACHER' AND status='ACTIVE');
INSERT INTO requested_profile_checks
SELECT IF(DATABASE()='duong_an_v11' AND @parent_account IS NOT NULL AND @teacher_account IS NOT NULL
 AND NOT EXISTS(SELECT 1 FROM guardian WHERE code='PH001' OR user_id=@parent_account)
 AND NOT EXISTS(SELECT 1 FROM staff WHERE code='GV001' OR user_id=@teacher_account)
 AND NOT EXISTS(SELECT 1 FROM student WHERE code IN('HS001','HS002')),1,0);

SET @parent_id=UUID();
SET @teacher_id=UUID();
SET @student_one=UUID();
SET @student_two=UUID();

INSERT INTO guardian(id,code,user_id,account_role,full_name,email)
VALUES(@parent_id,'PH001',@parent_account,'PARENT','Trần Thu Hương','parent@duongan.local');

INSERT INTO staff(id,code,user_id,account_role,full_name,position,specialty,email,joined_on,status)
VALUES(@teacher_id,'GV001',@teacher_account,'TEACHER','Lê Thị Mai','Giáo viên can thiệp','Can thiệp sớm',
 'teacher@duongan.local','2026-10-27','ACTIVE');

INSERT INTO student(id,code,full_name,birth_date,admitted_on,status)
VALUES(@student_one,'HS001','Nguyễn Minh An','2022-06-15','2026-10-27','ACTIVE'),
      (@student_two,'HS002','Nguyễn Gia Bảo','2023-03-20','2026-10-27','ACTIVE');

INSERT INTO student_guardian(student_id,guardian_id,relationship,is_primary,can_view_portal,can_pick_up)
VALUES(@student_one,@parent_id,'MOTHER',1,1,1),(@student_two,@parent_id,'MOTHER',1,1,1);

INSERT INTO requested_profile_checks
SELECT IF((SELECT COUNT(*) FROM student_guardian WHERE guardian_id=@parent_id)=2
 AND (SELECT COUNT(*) FROM staff WHERE id=@teacher_id AND user_id=@teacher_account)=1,1,0);
COMMIT;
DROP TEMPORARY TABLE requested_profile_checks;

SELECT s.code,s.full_name,s.birth_date,s.status,g.full_name guardian_name,sg.relationship
FROM student s JOIN student_guardian sg ON sg.student_id=s.id JOIN guardian g ON g.id=sg.guardian_id
WHERE s.id IN(@student_one,@student_two) ORDER BY s.code;
SELECT st.code,st.full_name,st.position,u.email,u.role
FROM staff st JOIN app_user u ON u.id=st.user_id WHERE st.id=@teacher_id;
