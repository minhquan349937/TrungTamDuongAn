-- Synthetic migration fixtures ONLY for the isolated test database.
DELIMITER $$
CREATE PROCEDURE assert_v6_test_database()
BEGIN
    IF DATABASE() NOT REGEXP '^goalbank_test_[a-f0-9]{12}$' THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Test fixtures require a disposable goalbank_test database';
    END IF;
END$$
DELIMITER ;
CALL assert_v6_test_database();
SET @actor='00000000-0000-0000-0000-000000009001';
SET @student='00000000-0000-0000-0000-000000009002';
SET @plan='00000000-0000-0000-0000-000000009003';
SET @p='00000000-0000-0000-0000-000000009004';
SET @level='00000000-0000-0000-0000-000000009005';
SET @domain='00000000-0000-0000-0000-000000009006';
SET @goal='00000000-0000-0000-0000-000000009007';
SET @sg='00000000-0000-0000-0000-000000009008';
INSERT INTO app_user(id,email,password_hash,display_name,role) VALUES(@actor,'migration-test@example.invalid','unused','Test','ADMIN');
INSERT INTO student(id,code,full_name,birth_date,admitted_on) VALUES(@student,'V6-TEST','Test','2022-01-01','2023-01-01');
INSERT INTO learning_plans(id,student_id,name,start_date,created_by) VALUES(@plan,@student,'Test','2026-01-01',@actor);
INSERT INTO programs(id,code,name) VALUES(@p,'MIGRATION-TEST','Test');
INSERT INTO program_levels(id,program_id,level_number,name,age_min_months,age_max_months,display_order) VALUES(@level,@p,1,'Test',0,48,1);
INSERT INTO domains(id,program_level_id,code,name) VALUES(@domain,@level,'TEST','Test');
INSERT INTO goals(id,domain_id,code,name,description) VALUES(@goal,@domain,'TEST.1','Test name','Full original description');
INSERT INTO specific_goals(id,goal_id,code,description) VALUES(@sg,@goal,'TEST.1A','Test specific description');
INSERT INTO goal_criteria(id,specific_goal_id,measurement_type,target_count) VALUES('00000000-0000-0000-0000-000000009009',@sg,'COUNT',3);
INSERT INTO plan_goals(id,plan_id,specific_goal_id,goal_snapshot,criteria_snapshot,assigned_by,status)
VALUES('00000000-0000-0000-0000-000000009010',@plan,@sg,JSON_OBJECT(),JSON_OBJECT(),@actor,'PAUSED');
INSERT INTO plan_goals(id,plan_id,specific_goal_id,goal_snapshot,criteria_snapshot,assigned_by,supersedes_id)
VALUES('00000000-0000-0000-0000-000000009011',@plan,@sg,JSON_OBJECT(),JSON_OBJECT(),@actor,'00000000-0000-0000-0000-000000009010');
INSERT INTO plan_goals(id,plan_id,goal_id,goal_snapshot,criteria_snapshot,assigned_by)
VALUES('00000000-0000-0000-0000-000000009012',@plan,@goal,JSON_OBJECT(),JSON_OBJECT(),@actor);
INSERT INTO progress_records(id,plan_goal_id,observed_at,recorded_by,notes)
VALUES('00000000-0000-0000-0000-000000009013','00000000-0000-0000-0000-000000009010',UTC_TIMESTAMP(6),@actor,'Original progress');
CREATE TABLE v6_test_expected AS SELECT id,goal_snapshot,criteria_snapshot,assigned_at,assigned_by,updated_at,status,supersedes_id FROM plan_goals;
