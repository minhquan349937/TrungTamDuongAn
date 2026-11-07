-- V16: compact entity IDs, including all existing relationships and JSON references.

-- Apply ONCE after V15 with application writers STOPPED and a verified full backup.

-- MySQL DDL commits implicitly. If interrupted, restore the backup before retrying.

-- Structural keys (section_key, sequence singleton IDs, composite keys) are retained.

-- migration_v16_id_map permanently records old/new IDs for support and audit.

SET NAMES utf8mb4;

SET SESSION lock_wait_timeout=10;

SET SESSION innodb_lock_wait_timeout=10;

CREATE TABLE entity_id_sequences (
    entity_name VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin PRIMARY KEY,
    prefix VARCHAR(8) CHARACTER SET ascii COLLATE ascii_bin NOT NULL UNIQUE,
    issued_value INT NOT NULL DEFAULT 0,
    CONSTRAINT ck_entity_id_counter CHECK (issued_value BETWEEN 0 AND 999999)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE migration_v16_id_map (
    entity_name VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    old_id VARCHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    temporary_id VARCHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    new_id VARCHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    PRIMARY KEY(entity_name,old_id),
    UNIQUE KEY uq_v16_new_id(entity_name,new_id),
    UNIQUE KEY uq_v16_temporary_id(entity_name,temporary_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- Preserve existing plan IDs as idempotency tokens before converting the keys.

ALTER TABLE learning_plans ADD COLUMN request_key CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NULL, ADD UNIQUE KEY uq_plan_request_key(request_key);

INSERT INTO entity_id_sequences(entity_name,prefix) VALUES('app_user','TK');

INSERT INTO migration_v16_id_map(entity_name,old_id,temporary_id,new_id) SELECT 'app_user',CAST(id AS CHAR),CONCAT('@',LPAD(n,6,'0')),CONCAT('TK',LPAD(n,GREATEST(3,CHAR_LENGTH(n)),'0')) FROM (SELECT id,ROW_NUMBER() OVER(ORDER BY id) n FROM `app_user`) ranked;

INSERT INTO entity_id_sequences(entity_name,prefix) VALUES('audit_event','NK');

INSERT INTO migration_v16_id_map(entity_name,old_id,temporary_id,new_id) SELECT 'audit_event',CAST(id AS CHAR),CONCAT('@',LPAD(n,6,'0')),CONCAT('NK',LPAD(n,GREATEST(3,CHAR_LENGTH(n)),'0')) FROM (SELECT id,ROW_NUMBER() OVER(ORDER BY id) n FROM `audit_event`) ranked;

INSERT INTO entity_id_sequences(entity_name,prefix) VALUES('center','TT');

INSERT INTO migration_v16_id_map(entity_name,old_id,temporary_id,new_id) SELECT 'center',CAST(id AS CHAR),CONCAT('@',LPAD(n,6,'0')),CONCAT('TT',LPAD(n,GREATEST(3,CHAR_LENGTH(n)),'0')) FROM (SELECT id,ROW_NUMBER() OVER(ORDER BY id) n FROM `center`) ranked;

INSERT INTO entity_id_sequences(entity_name,prefix) VALUES('class_session','BH');

INSERT INTO migration_v16_id_map(entity_name,old_id,temporary_id,new_id) SELECT 'class_session',CAST(id AS CHAR),CONCAT('@',LPAD(n,6,'0')),CONCAT('BH',LPAD(n,GREATEST(3,CHAR_LENGTH(n)),'0')) FROM (SELECT id,ROW_NUMBER() OVER(ORDER BY id) n FROM `class_session`) ranked;

INSERT INTO entity_id_sequences(entity_name,prefix) VALUES('consultation_requests','TV');

INSERT INTO migration_v16_id_map(entity_name,old_id,temporary_id,new_id) SELECT 'consultation_requests',CAST(id AS CHAR),CONCAT('@',LPAD(n,6,'0')),CONCAT('TV',LPAD(n,GREATEST(3,CHAR_LENGTH(n)),'0')) FROM (SELECT id,ROW_NUMBER() OVER(ORDER BY id) n FROM `consultation_requests`) ranked;

INSERT INTO entity_id_sequences(entity_name,prefix) VALUES('document','TL');

INSERT INTO migration_v16_id_map(entity_name,old_id,temporary_id,new_id) SELECT 'document',CAST(id AS CHAR),CONCAT('@',LPAD(n,6,'0')),CONCAT('TL',LPAD(n,GREATEST(3,CHAR_LENGTH(n)),'0')) FROM (SELECT id,ROW_NUMBER() OVER(ORDER BY id) n FROM `document`) ranked;

INSERT INTO entity_id_sequences(entity_name,prefix) VALUES('domains','DM');

INSERT INTO migration_v16_id_map(entity_name,old_id,temporary_id,new_id) SELECT 'domains',CAST(id AS CHAR),CONCAT('@',LPAD(n,6,'0')),CONCAT('DM',LPAD(n,GREATEST(3,CHAR_LENGTH(n)),'0')) FROM (SELECT id,ROW_NUMBER() OVER(ORDER BY id) n FROM `domains`) ranked;

INSERT INTO entity_id_sequences(entity_name,prefix) VALUES('enrollment','GD');

INSERT INTO migration_v16_id_map(entity_name,old_id,temporary_id,new_id) SELECT 'enrollment',CAST(id AS CHAR),CONCAT('@',LPAD(n,6,'0')),CONCAT('GD',LPAD(n,GREATEST(3,CHAR_LENGTH(n)),'0')) FROM (SELECT id,ROW_NUMBER() OVER(ORDER BY id) n FROM `enrollment`) ranked;

INSERT INTO entity_id_sequences(entity_name,prefix) VALUES('goal_evaluations','DG');

INSERT INTO migration_v16_id_map(entity_name,old_id,temporary_id,new_id) SELECT 'goal_evaluations',CAST(id AS CHAR),CONCAT('@',LPAD(n,6,'0')),CONCAT('DG',LPAD(n,GREATEST(3,CHAR_LENGTH(n)),'0')) FROM (SELECT id,ROW_NUMBER() OVER(ORDER BY id) n FROM `goal_evaluations`) ranked;

INSERT INTO entity_id_sequences(entity_name,prefix) VALUES('goals','GL');

INSERT INTO migration_v16_id_map(entity_name,old_id,temporary_id,new_id) SELECT 'goals',CAST(id AS CHAR),CONCAT('@',LPAD(n,6,'0')),CONCAT('GL',LPAD(n,GREATEST(3,CHAR_LENGTH(n)),'0')) FROM (SELECT id,ROW_NUMBER() OVER(ORDER BY id) n FROM `goals`) ranked;

INSERT INTO entity_id_sequences(entity_name,prefix) VALUES('guardian','PH');

INSERT INTO migration_v16_id_map(entity_name,old_id,temporary_id,new_id) SELECT 'guardian',CAST(id AS CHAR),CONCAT('@',LPAD(n,6,'0')),CONCAT('PH',LPAD(n,GREATEST(3,CHAR_LENGTH(n)),'0')) FROM (SELECT id,ROW_NUMBER() OVER(ORDER BY id) n FROM `guardian`) ranked;

INSERT INTO entity_id_sequences(entity_name,prefix) VALUES('invoice','HD');

INSERT INTO migration_v16_id_map(entity_name,old_id,temporary_id,new_id) SELECT 'invoice',CAST(id AS CHAR),CONCAT('@',LPAD(n,6,'0')),CONCAT('HD',LPAD(n,GREATEST(3,CHAR_LENGTH(n)),'0')) FROM (SELECT id,ROW_NUMBER() OVER(ORDER BY id) n FROM `invoice`) ranked;

INSERT INTO entity_id_sequences(entity_name,prefix) VALUES('invoice_line','CTHD');

INSERT INTO migration_v16_id_map(entity_name,old_id,temporary_id,new_id) SELECT 'invoice_line',CAST(id AS CHAR),CONCAT('@',LPAD(n,6,'0')),CONCAT('CTHD',LPAD(n,GREATEST(3,CHAR_LENGTH(n)),'0')) FROM (SELECT id,ROW_NUMBER() OVER(ORDER BY id) n FROM `invoice_line`) ranked;

INSERT INTO entity_id_sequences(entity_name,prefix) VALUES('learning_class','LH');

INSERT INTO migration_v16_id_map(entity_name,old_id,temporary_id,new_id) SELECT 'learning_class',CAST(id AS CHAR),CONCAT('@',LPAD(n,6,'0')),CONCAT('LH',LPAD(n,GREATEST(3,CHAR_LENGTH(n)),'0')) FROM (SELECT id,ROW_NUMBER() OVER(ORDER BY id) n FROM `learning_class`) ranked;

INSERT INTO entity_id_sequences(entity_name,prefix) VALUES('learning_plans','KH');

INSERT INTO migration_v16_id_map(entity_name,old_id,temporary_id,new_id) SELECT 'learning_plans',CAST(id AS CHAR),CONCAT('@',LPAD(n,6,'0')),CONCAT('KH',LPAD(n,GREATEST(3,CHAR_LENGTH(n)),'0')) FROM (SELECT id,ROW_NUMBER() OVER(ORDER BY id) n FROM `learning_plans`) ranked;

INSERT INTO entity_id_sequences(entity_name,prefix) VALUES('leave_request','NP');

INSERT INTO migration_v16_id_map(entity_name,old_id,temporary_id,new_id) SELECT 'leave_request',CAST(id AS CHAR),CONCAT('@',LPAD(n,6,'0')),CONCAT('NP',LPAD(n,GREATEST(3,CHAR_LENGTH(n)),'0')) FROM (SELECT id,ROW_NUMBER() OVER(ORDER BY id) n FROM `leave_request`) ranked;

INSERT INTO entity_id_sequences(entity_name,prefix) VALUES('notification','TB');

INSERT INTO migration_v16_id_map(entity_name,old_id,temporary_id,new_id) SELECT 'notification',CAST(id AS CHAR),CONCAT('@',LPAD(n,6,'0')),CONCAT('TB',LPAD(n,GREATEST(3,CHAR_LENGTH(n)),'0')) FROM (SELECT id,ROW_NUMBER() OVER(ORDER BY id) n FROM `notification`) ranked;

INSERT INTO entity_id_sequences(entity_name,prefix) VALUES('payment','TTN');

INSERT INTO migration_v16_id_map(entity_name,old_id,temporary_id,new_id) SELECT 'payment',CAST(id AS CHAR),CONCAT('@',LPAD(n,6,'0')),CONCAT('TTN',LPAD(n,GREATEST(3,CHAR_LENGTH(n)),'0')) FROM (SELECT id,ROW_NUMBER() OVER(ORDER BY id) n FROM `payment`) ranked;

INSERT INTO entity_id_sequences(entity_name,prefix) VALUES('plan_evaluations','NX');

INSERT INTO migration_v16_id_map(entity_name,old_id,temporary_id,new_id) SELECT 'plan_evaluations',CAST(id AS CHAR),CONCAT('@',LPAD(n,6,'0')),CONCAT('NX',LPAD(n,GREATEST(3,CHAR_LENGTH(n)),'0')) FROM (SELECT id,ROW_NUMBER() OVER(ORDER BY id) n FROM `plan_evaluations`) ranked;

INSERT INTO entity_id_sequences(entity_name,prefix) VALUES('plan_goals','MTKH');

INSERT INTO migration_v16_id_map(entity_name,old_id,temporary_id,new_id) SELECT 'plan_goals',CAST(id AS CHAR),CONCAT('@',LPAD(n,6,'0')),CONCAT('MTKH',LPAD(n,GREATEST(3,CHAR_LENGTH(n)),'0')) FROM (SELECT id,ROW_NUMBER() OVER(ORDER BY id) n FROM `plan_goals`) ranked;

INSERT INTO entity_id_sequences(entity_name,prefix) VALUES('program_level_domains','LD');

INSERT INTO migration_v16_id_map(entity_name,old_id,temporary_id,new_id) SELECT 'program_level_domains',CAST(id AS CHAR),CONCAT('@',LPAD(n,6,'0')),CONCAT('LD',LPAD(n,GREATEST(3,CHAR_LENGTH(n)),'0')) FROM (SELECT id,ROW_NUMBER() OVER(ORDER BY id) n FROM `program_level_domains`) ranked;

INSERT INTO entity_id_sequences(entity_name,prefix) VALUES('program_levels','LV');

INSERT INTO migration_v16_id_map(entity_name,old_id,temporary_id,new_id) SELECT 'program_levels',CAST(id AS CHAR),CONCAT('@',LPAD(n,6,'0')),CONCAT('LV',LPAD(n,GREATEST(3,CHAR_LENGTH(n)),'0')) FROM (SELECT id,ROW_NUMBER() OVER(ORDER BY id) n FROM `program_levels`) ranked;

INSERT INTO entity_id_sequences(entity_name,prefix) VALUES('programs','PR');

INSERT INTO migration_v16_id_map(entity_name,old_id,temporary_id,new_id) SELECT 'programs',CAST(id AS CHAR),CONCAT('@',LPAD(n,6,'0')),CONCAT('PR',LPAD(n,GREATEST(3,CHAR_LENGTH(n)),'0')) FROM (SELECT id,ROW_NUMBER() OVER(ORDER BY id) n FROM `programs`) ranked;

INSERT INTO entity_id_sequences(entity_name,prefix) VALUES('specific_goals','SG');

INSERT INTO migration_v16_id_map(entity_name,old_id,temporary_id,new_id) SELECT 'specific_goals',CAST(id AS CHAR),CONCAT('@',LPAD(n,6,'0')),CONCAT('SG',LPAD(n,GREATEST(3,CHAR_LENGTH(n)),'0')) FROM (SELECT id,ROW_NUMBER() OVER(ORDER BY id) n FROM `specific_goals`) ranked;

INSERT INTO entity_id_sequences(entity_name,prefix) VALUES('staff','NV');

INSERT INTO migration_v16_id_map(entity_name,old_id,temporary_id,new_id) SELECT 'staff',CAST(id AS CHAR),CONCAT('@',LPAD(n,6,'0')),CONCAT('NV',LPAD(n,GREATEST(3,CHAR_LENGTH(n)),'0')) FROM (SELECT id,ROW_NUMBER() OVER(ORDER BY id) n FROM `staff`) ranked;

INSERT INTO entity_id_sequences(entity_name,prefix) VALUES('staff_checkin','CC');

INSERT INTO migration_v16_id_map(entity_name,old_id,temporary_id,new_id) SELECT 'staff_checkin',CAST(id AS CHAR),CONCAT('@',LPAD(n,6,'0')),CONCAT('CC',LPAD(n,GREATEST(3,CHAR_LENGTH(n)),'0')) FROM (SELECT id,ROW_NUMBER() OVER(ORDER BY id) n FROM `staff_checkin`) ranked;

INSERT INTO entity_id_sequences(entity_name,prefix) VALUES('staff_salary','LNV');

INSERT INTO migration_v16_id_map(entity_name,old_id,temporary_id,new_id) SELECT 'staff_salary',CAST(id AS CHAR),CONCAT('@',LPAD(n,6,'0')),CONCAT('LNV',LPAD(n,GREATEST(3,CHAR_LENGTH(n)),'0')) FROM (SELECT id,ROW_NUMBER() OVER(ORDER BY id) n FROM `staff_salary`) ranked;

INSERT INTO entity_id_sequences(entity_name,prefix) VALUES('student','HS');

INSERT INTO migration_v16_id_map(entity_name,old_id,temporary_id,new_id) SELECT 'student',CAST(id AS CHAR),CONCAT('@',LPAD(n,6,'0')),CONCAT('HS',LPAD(n,GREATEST(3,CHAR_LENGTH(n)),'0')) FROM (SELECT id,ROW_NUMBER() OVER(ORDER BY id) n FROM `student`) ranked;

INSERT INTO entity_id_sequences(entity_name,prefix) VALUES('student_attendance','DD');

INSERT INTO migration_v16_id_map(entity_name,old_id,temporary_id,new_id) SELECT 'student_attendance',CAST(id AS CHAR),CONCAT('@',LPAD(n,6,'0')),CONCAT('DD',LPAD(n,GREATEST(3,CHAR_LENGTH(n)),'0')) FROM (SELECT id,ROW_NUMBER() OVER(ORDER BY id) n FROM `student_attendance`) ranked;

INSERT INTO entity_id_sequences(entity_name,prefix) VALUES('website_albums','AL');

INSERT INTO migration_v16_id_map(entity_name,old_id,temporary_id,new_id) SELECT 'website_albums',CAST(id AS CHAR),CONCAT('@',LPAD(n,6,'0')),CONCAT('AL',LPAD(n,GREATEST(3,CHAR_LENGTH(n)),'0')) FROM (SELECT id,ROW_NUMBER() OVER(ORDER BY id) n FROM `website_albums`) ranked;

INSERT INTO entity_id_sequences(entity_name,prefix) VALUES('website_faqs','FAQ');

INSERT INTO migration_v16_id_map(entity_name,old_id,temporary_id,new_id) SELECT 'website_faqs',CAST(id AS CHAR),CONCAT('@',LPAD(n,6,'0')),CONCAT('FAQ',LPAD(n,GREATEST(3,CHAR_LENGTH(n)),'0')) FROM (SELECT id,ROW_NUMBER() OVER(ORDER BY id) n FROM `website_faqs`) ranked;

INSERT INTO entity_id_sequences(entity_name,prefix) VALUES('website_media','MD');

INSERT INTO migration_v16_id_map(entity_name,old_id,temporary_id,new_id) SELECT 'website_media',CAST(id AS CHAR),CONCAT('@',LPAD(n,6,'0')),CONCAT('MD',LPAD(n,GREATEST(3,CHAR_LENGTH(n)),'0')) FROM (SELECT id,ROW_NUMBER() OVER(ORDER BY id) n FROM `website_media`) ranked;

INSERT INTO entity_id_sequences(entity_name,prefix) VALUES('website_publications','XB');

INSERT INTO migration_v16_id_map(entity_name,old_id,temporary_id,new_id) SELECT 'website_publications',CAST(id AS CHAR),CONCAT('@',LPAD(n,6,'0')),CONCAT('XB',LPAD(n,GREATEST(3,CHAR_LENGTH(n)),'0')) FROM (SELECT id,ROW_NUMBER() OVER(ORDER BY id) n FROM `website_publications`) ranked;

INSERT INTO entity_id_sequences(entity_name,prefix) VALUES('website_section_items','ND');

INSERT INTO migration_v16_id_map(entity_name,old_id,temporary_id,new_id) SELECT 'website_section_items',CAST(id AS CHAR),CONCAT('@',LPAD(n,6,'0')),CONCAT('ND',LPAD(n,GREATEST(3,CHAR_LENGTH(n)),'0')) FROM (SELECT id,ROW_NUMBER() OVER(ORDER BY id) n FROM `website_section_items`) ranked;

INSERT INTO entity_id_sequences(entity_name,prefix) VALUES('website_services','DV');

INSERT INTO migration_v16_id_map(entity_name,old_id,temporary_id,new_id) SELECT 'website_services',CAST(id AS CHAR),CONCAT('@',LPAD(n,6,'0')),CONCAT('DV',LPAD(n,GREATEST(3,CHAR_LENGTH(n)),'0')) FROM (SELECT id,ROW_NUMBER() OVER(ORDER BY id) n FROM `website_services`) ranked;

INSERT INTO entity_id_sequences(entity_name,prefix) VALUES('website_settings','WCFG');

INSERT INTO migration_v16_id_map(entity_name,old_id,temporary_id,new_id) SELECT 'website_settings',CAST(id AS CHAR),CONCAT('@',LPAD(n,6,'0')),CONCAT('WCFG',LPAD(n,GREATEST(3,CHAR_LENGTH(n)),'0')) FROM (SELECT id,ROW_NUMBER() OVER(ORDER BY id) n FROM `website_settings`) ranked;

INSERT INTO entity_id_sequences(entity_name,prefix) VALUES('website_team_members','NS');

INSERT INTO migration_v16_id_map(entity_name,old_id,temporary_id,new_id) SELECT 'website_team_members',CAST(id AS CHAR),CONCAT('@',LPAD(n,6,'0')),CONCAT('NS',LPAD(n,GREATEST(3,CHAR_LENGTH(n)),'0')) FROM (SELECT id,ROW_NUMBER() OVER(ORDER BY id) n FROM `website_team_members`) ranked;

INSERT INTO entity_id_sequences(entity_name,prefix) VALUES('strategies','CL');

INSERT INTO migration_v16_id_map(entity_name,old_id,temporary_id,new_id) SELECT 'strategies',CAST(id AS CHAR),CONCAT('@',LPAD(n,6,'0')),CONCAT('CL',LPAD(n,GREATEST(3,CHAR_LENGTH(n)),'0')) FROM (SELECT id,ROW_NUMBER() OVER(ORDER BY id) n FROM `strategies`) ranked;

INSERT INTO entity_id_sequences(entity_name,prefix) VALUES('plan_goal_strategies','MTCL');

INSERT INTO migration_v16_id_map(entity_name,old_id,temporary_id,new_id) SELECT 'plan_goal_strategies',CAST(id AS CHAR),CONCAT('@',LPAD(n,6,'0')),CONCAT('MTCL',LPAD(n,GREATEST(3,CHAR_LENGTH(n)),'0')) FROM (SELECT id,ROW_NUMBER() OVER(ORDER BY id) n FROM `plan_goal_strategies`) ranked;

UPDATE migration_v16_id_map m JOIN (SELECT entity_name,old_id,CONCAT('@',LPAD(ROW_NUMBER() OVER(ORDER BY entity_name,old_id),7,'0')) temporary_id FROM migration_v16_id_map) ranked ON ranked.entity_name=m.entity_name AND ranked.old_id=m.old_id SET m.temporary_id=ranked.temporary_id;

DELIMITER $$

CREATE PROCEDURE assert_v16_capacity()
BEGIN
    IF (SELECT COUNT(*) FROM migration_v16_id_map)>9999999 OR EXISTS(SELECT 1 FROM migration_v16_id_map GROUP BY entity_name HAVING COUNT(*)>999999) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V16: an entity exceeds the short-ID capacity';
    END IF;
END$$

CALL assert_v16_capacity()$$

DROP PROCEDURE assert_v16_capacity$$

DELIMITER ;

DROP TRIGGER `evaluation_attempt_insert`;

DROP TRIGGER `evaluation_identity_update`;

DROP TRIGGER `evaluation_status_insert`;

DROP TRIGGER `evaluation_status_update`;

DROP TRIGGER `learning_plan_student_immutable`;

DROP TRIGGER `plan_goals_snapshot_immutable`;

DROP TRIGGER `plan_goals_snapshot_insert`;

DROP TRIGGER `trg_plan_evaluation_before_delete`;

DROP TRIGGER `trg_plan_evaluation_before_update`;

ALTER TABLE strategies DROP CHECK ck_strategy_id;

ALTER TABLE plan_goal_strategies DROP CHECK ck_plan_goal_strategy_id;

ALTER TABLE center DROP CHECK ck_center_singleton, MODIFY id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT 'TT001';

ALTER TABLE website_settings DROP CHECK ck_website_settings_singleton, MODIFY id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT 'WCFG001';

ALTER TABLE website_publication_media DROP FOREIGN KEY website_publication_media_ibfk_1;

ALTER TABLE website_publications MODIFY id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL;

ALTER TABLE website_publication_media MODIFY publication_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL;

ALTER TABLE website_publication_media ADD CONSTRAINT website_publication_media_ibfk_1 FOREIGN KEY(publication_id) REFERENCES website_publications(id) ON DELETE CASCADE;

ALTER TABLE audit_event MODIFY id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL;

DELIMITER $$

CREATE PROCEDURE remap_v16_json()
BEGIN
    DECLARE finished BOOLEAN DEFAULT FALSE;
    DECLARE entity VARCHAR(64);
    DECLARE old_value VARCHAR(36);
    DECLARE new_value VARCHAR(36);
    DECLARE mappings CURSOR FOR
        SELECT entity_name,source_value,target_value FROM (
            SELECT 1 phase,entity_name,old_id source_value,temporary_id target_value FROM migration_v16_id_map
            UNION ALL
            SELECT 2 phase,entity_name,temporary_id source_value,new_id target_value FROM migration_v16_id_map
        ) stages ORDER BY phase,entity_name,source_value;
    DECLARE CONTINUE HANDLER FOR NOT FOUND SET finished=TRUE;
    OPEN mappings;
    mapping_loop: LOOP
        FETCH mappings INTO entity,old_value,new_value;
        IF finished THEN LEAVE mapping_loop; END IF;

        UPDATE `audit_event` SET `detail`=REGEXP_REPLACE(CAST(`detail` AS CHAR CHARACTER SET utf8mb4),CONCAT('("(?:id|program_id|program_level_id|domain_id|level_domain_id|goal_id|specific_goal_id|plan_id|plan_goal_id|learning_plan_id|student_id|guardian_id|staff_id|teacher_id|user_id|assigned_by|created_by|evaluated_by|published_by|actor_id|source_id|media_id|album_id|strategy_id|programId|programLevelId|domainId|levelDomainId|goalId|specificGoalId|planId|planGoalId|studentId|guardianId|staffId|teacherId|userId|mediaId|albumId|strategyId)"[[:space:]]*:[[:space:]]*)',JSON_QUOTE(old_value)),CONCAT('$1',JSON_QUOTE(new_value))) WHERE LOCATE(JSON_QUOTE(old_value),CAST(`detail` AS CHAR CHARACTER SET utf8mb4))>0;

        UPDATE `migration_v11_archive` SET `payload`=REGEXP_REPLACE(CAST(`payload` AS CHAR CHARACTER SET utf8mb4),CONCAT('("(?:id|program_id|program_level_id|domain_id|level_domain_id|goal_id|specific_goal_id|plan_id|plan_goal_id|learning_plan_id|student_id|guardian_id|staff_id|teacher_id|user_id|assigned_by|created_by|evaluated_by|published_by|actor_id|source_id|media_id|album_id|strategy_id|programId|programLevelId|domainId|levelDomainId|goalId|specificGoalId|planId|planGoalId|studentId|guardianId|staffId|teacherId|userId|mediaId|albumId|strategyId)"[[:space:]]*:[[:space:]]*)',JSON_QUOTE(old_value)),CONCAT('$1',JSON_QUOTE(new_value))) WHERE LOCATE(JSON_QUOTE(old_value),CAST(`payload` AS CHAR CHARACTER SET utf8mb4))>0;

        UPDATE `plan_goals` SET `goal_snapshot`=REGEXP_REPLACE(CAST(`goal_snapshot` AS CHAR CHARACTER SET utf8mb4),CONCAT('("(?:id|program_id|program_level_id|domain_id|level_domain_id|goal_id|specific_goal_id|plan_id|plan_goal_id|learning_plan_id|student_id|guardian_id|staff_id|teacher_id|user_id|assigned_by|created_by|evaluated_by|published_by|actor_id|source_id|media_id|album_id|strategy_id|programId|programLevelId|domainId|levelDomainId|goalId|specificGoalId|planId|planGoalId|studentId|guardianId|staffId|teacherId|userId|mediaId|albumId|strategyId)"[[:space:]]*:[[:space:]]*)',JSON_QUOTE(old_value)),CONCAT('$1',JSON_QUOTE(new_value))),updated_at=updated_at WHERE LOCATE(JSON_QUOTE(old_value),CAST(`goal_snapshot` AS CHAR CHARACTER SET utf8mb4))>0;

        UPDATE `plan_goals` SET `criteria_snapshot`=REGEXP_REPLACE(CAST(`criteria_snapshot` AS CHAR CHARACTER SET utf8mb4),CONCAT('("(?:id|program_id|program_level_id|domain_id|level_domain_id|goal_id|specific_goal_id|plan_id|plan_goal_id|learning_plan_id|student_id|guardian_id|staff_id|teacher_id|user_id|assigned_by|created_by|evaluated_by|published_by|actor_id|source_id|media_id|album_id|strategy_id|programId|programLevelId|domainId|levelDomainId|goalId|specificGoalId|planId|planGoalId|studentId|guardianId|staffId|teacherId|userId|mediaId|albumId|strategyId)"[[:space:]]*:[[:space:]]*)',JSON_QUOTE(old_value)),CONCAT('$1',JSON_QUOTE(new_value))),updated_at=updated_at WHERE LOCATE(JSON_QUOTE(old_value),CAST(`criteria_snapshot` AS CHAR CHARACTER SET utf8mb4))>0;

        UPDATE `website_publications` SET `content`=REGEXP_REPLACE(CAST(`content` AS CHAR CHARACTER SET utf8mb4),CONCAT('("(?:id|program_id|program_level_id|domain_id|level_domain_id|goal_id|specific_goal_id|plan_id|plan_goal_id|learning_plan_id|student_id|guardian_id|staff_id|teacher_id|user_id|assigned_by|created_by|evaluated_by|published_by|actor_id|source_id|media_id|album_id|strategy_id|programId|programLevelId|domainId|levelDomainId|goalId|specificGoalId|planId|planGoalId|studentId|guardianId|staffId|teacherId|userId|mediaId|albumId|strategyId)"[[:space:]]*:[[:space:]]*)',JSON_QUOTE(old_value)),CONCAT('$1',JSON_QUOTE(new_value))) WHERE LOCATE(JSON_QUOTE(old_value),CAST(`content` AS CHAR CHARACTER SET utf8mb4))>0;

        IF entity='website_media' THEN

            UPDATE website_publications SET content=REPLACE(CAST(content AS CHAR CHARACTER SET utf8mb4),CONCAT('/api/public/website/media/',old_value,CHAR(34)),CONCAT('/api/public/website/media/',new_value,CHAR(34)));

        END IF;

        UPDATE audit_event SET entity_id=new_value WHERE entity_type=entity AND entity_id=old_value;

        UPDATE migration_v11_archive SET source_id=new_value WHERE source_table=entity AND source_id=old_value;

        UPDATE migration_v6_catalog_ids SET new_id=new_value WHERE entity_name=entity AND new_id=old_value;

    END LOOP;

    CLOSE mappings;

END$$

DELIMITER ;

DELIMITER $$

CREATE PROCEDURE verify_v16_links()

BEGIN

    IF EXISTS(SELECT 1 FROM `audit_event` a WHERE a.`actor_id` IS NOT NULL AND NOT EXISTS(SELECT 1 FROM `app_user` b WHERE a.`actor_id`=b.`id`)) THEN

        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V16: broken foreign key audit_event_ibfk_1';

    END IF;

    IF EXISTS(SELECT 1 FROM `class_session` a WHERE a.`class_id` IS NOT NULL AND NOT EXISTS(SELECT 1 FROM `learning_class` b WHERE a.`class_id`=b.`id`)) THEN

        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V16: broken foreign key class_session_ibfk_1';

    END IF;

    IF EXISTS(SELECT 1 FROM `class_session` a WHERE a.`teacher_id` IS NOT NULL AND NOT EXISTS(SELECT 1 FROM `staff` b WHERE a.`teacher_id`=b.`id`)) THEN

        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V16: broken foreign key class_session_ibfk_2';

    END IF;

    IF EXISTS(SELECT 1 FROM `class_session` a WHERE a.`created_by` IS NOT NULL AND NOT EXISTS(SELECT 1 FROM `app_user` b WHERE a.`created_by`=b.`id`)) THEN

        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V16: broken foreign key class_session_ibfk_3';

    END IF;

    IF EXISTS(SELECT 1 FROM `consultation_requests` a WHERE a.`assigned_user_id` IS NOT NULL AND NOT EXISTS(SELECT 1 FROM `app_user` b WHERE a.`assigned_user_id`=b.`id`)) THEN

        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V16: broken foreign key consultation_requests_ibfk_1';

    END IF;

    IF EXISTS(SELECT 1 FROM `consultation_requests` a WHERE a.`updated_by` IS NOT NULL AND NOT EXISTS(SELECT 1 FROM `app_user` b WHERE a.`updated_by`=b.`id`)) THEN

        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V16: broken foreign key consultation_requests_ibfk_2';

    END IF;

    IF EXISTS(SELECT 1 FROM `document` a WHERE a.`uploaded_by` IS NOT NULL AND NOT EXISTS(SELECT 1 FROM `app_user` b WHERE a.`uploaded_by`=b.`id`)) THEN

        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V16: broken foreign key document_ibfk_1';

    END IF;

    IF EXISTS(SELECT 1 FROM `domains` a WHERE a.`program_id` IS NOT NULL AND NOT EXISTS(SELECT 1 FROM `programs` b WHERE a.`program_id`=b.`id`)) THEN

        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V16: broken foreign key fk_domain_program';

    END IF;

    IF EXISTS(SELECT 1 FROM `enrollment` a WHERE a.`class_id` IS NOT NULL AND NOT EXISTS(SELECT 1 FROM `learning_class` b WHERE a.`class_id`=b.`id`)) THEN

        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V16: broken foreign key enrollment_ibfk_1';

    END IF;

    IF EXISTS(SELECT 1 FROM `enrollment` a WHERE a.`student_id` IS NOT NULL AND NOT EXISTS(SELECT 1 FROM `student` b WHERE a.`student_id`=b.`id`)) THEN

        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V16: broken foreign key enrollment_ibfk_2';

    END IF;

    IF EXISTS(SELECT 1 FROM `goal_evaluations` a WHERE a.`plan_goal_id` IS NOT NULL AND NOT EXISTS(SELECT 1 FROM `plan_goals` b WHERE a.`plan_goal_id`=b.`id`)) THEN

        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V16: broken foreign key goal_evaluations_ibfk_1';

    END IF;

    IF EXISTS(SELECT 1 FROM `goal_evaluations` a WHERE a.`evaluated_by` IS NOT NULL AND NOT EXISTS(SELECT 1 FROM `app_user` b WHERE a.`evaluated_by`=b.`id`)) THEN

        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V16: broken foreign key goal_evaluations_ibfk_2';

    END IF;

    IF EXISTS(SELECT 1 FROM `goals` a WHERE a.`level_domain_id` IS NOT NULL AND NOT EXISTS(SELECT 1 FROM `program_level_domains` b WHERE a.`level_domain_id`=b.`id`)) THEN

        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V16: broken foreign key fk_goal_level_domain';

    END IF;

    IF EXISTS(SELECT 1 FROM `guardian` a WHERE a.`user_id` IS NOT NULL AND a.`account_role` IS NOT NULL AND NOT EXISTS(SELECT 1 FROM `app_user` b WHERE a.`user_id`=b.`id` AND a.`account_role`=b.`role`)) THEN

        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V16: broken foreign key guardian_ibfk_1';

    END IF;

    IF EXISTS(SELECT 1 FROM `invoice` a WHERE a.`student_id` IS NOT NULL AND NOT EXISTS(SELECT 1 FROM `student` b WHERE a.`student_id`=b.`id`)) THEN

        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V16: broken foreign key invoice_ibfk_1';

    END IF;

    IF EXISTS(SELECT 1 FROM `invoice` a WHERE a.`created_by` IS NOT NULL AND NOT EXISTS(SELECT 1 FROM `app_user` b WHERE a.`created_by`=b.`id`)) THEN

        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V16: broken foreign key invoice_ibfk_2';

    END IF;

    IF EXISTS(SELECT 1 FROM `invoice_line` a WHERE a.`invoice_id` IS NOT NULL AND NOT EXISTS(SELECT 1 FROM `invoice` b WHERE a.`invoice_id`=b.`id`)) THEN

        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V16: broken foreign key invoice_line_ibfk_1';

    END IF;

    IF EXISTS(SELECT 1 FROM `learning_class` a WHERE a.`teacher_id` IS NOT NULL AND NOT EXISTS(SELECT 1 FROM `staff` b WHERE a.`teacher_id`=b.`id`)) THEN

        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V16: broken foreign key learning_class_ibfk_1';

    END IF;

    IF EXISTS(SELECT 1 FROM `learning_plans` a WHERE a.`student_id` IS NOT NULL AND NOT EXISTS(SELECT 1 FROM `student` b WHERE a.`student_id`=b.`id`)) THEN

        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V16: broken foreign key learning_plans_ibfk_1';

    END IF;

    IF EXISTS(SELECT 1 FROM `learning_plans` a WHERE a.`created_by` IS NOT NULL AND NOT EXISTS(SELECT 1 FROM `app_user` b WHERE a.`created_by`=b.`id`)) THEN

        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V16: broken foreign key learning_plans_ibfk_2';

    END IF;

    IF EXISTS(SELECT 1 FROM `leave_request` a WHERE a.`staff_id` IS NOT NULL AND NOT EXISTS(SELECT 1 FROM `staff` b WHERE a.`staff_id`=b.`id`)) THEN

        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V16: broken foreign key leave_request_ibfk_1';

    END IF;

    IF EXISTS(SELECT 1 FROM `leave_request` a WHERE a.`requested_by` IS NOT NULL AND NOT EXISTS(SELECT 1 FROM `app_user` b WHERE a.`requested_by`=b.`id`)) THEN

        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V16: broken foreign key leave_request_ibfk_2';

    END IF;

    IF EXISTS(SELECT 1 FROM `leave_request` a WHERE a.`reviewed_by` IS NOT NULL AND NOT EXISTS(SELECT 1 FROM `app_user` b WHERE a.`reviewed_by`=b.`id`)) THEN

        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V16: broken foreign key leave_request_ibfk_3';

    END IF;

    IF EXISTS(SELECT 1 FROM `notification` a WHERE a.`created_by` IS NOT NULL AND NOT EXISTS(SELECT 1 FROM `app_user` b WHERE a.`created_by`=b.`id`)) THEN

        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V16: broken foreign key notification_ibfk_1';

    END IF;

    IF EXISTS(SELECT 1 FROM `notification_recipient` a WHERE a.`notification_id` IS NOT NULL AND NOT EXISTS(SELECT 1 FROM `notification` b WHERE a.`notification_id`=b.`id`)) THEN

        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V16: broken foreign key notification_recipient_ibfk_1';

    END IF;

    IF EXISTS(SELECT 1 FROM `notification_recipient` a WHERE a.`user_id` IS NOT NULL AND NOT EXISTS(SELECT 1 FROM `app_user` b WHERE a.`user_id`=b.`id`)) THEN

        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V16: broken foreign key notification_recipient_ibfk_2';

    END IF;

    IF EXISTS(SELECT 1 FROM `payment` a WHERE a.`invoice_id` IS NOT NULL AND NOT EXISTS(SELECT 1 FROM `invoice` b WHERE a.`invoice_id`=b.`id`)) THEN

        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V16: broken foreign key payment_ibfk_1';

    END IF;

    IF EXISTS(SELECT 1 FROM `payment` a WHERE a.`received_by` IS NOT NULL AND NOT EXISTS(SELECT 1 FROM `app_user` b WHERE a.`received_by`=b.`id`)) THEN

        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V16: broken foreign key payment_ibfk_2';

    END IF;

    IF EXISTS(SELECT 1 FROM `payment` a WHERE a.`voided_by` IS NOT NULL AND NOT EXISTS(SELECT 1 FROM `app_user` b WHERE a.`voided_by`=b.`id`)) THEN

        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V16: broken foreign key payment_ibfk_3';

    END IF;

    IF EXISTS(SELECT 1 FROM `plan_evaluations` a WHERE a.`learning_plan_id` IS NOT NULL AND NOT EXISTS(SELECT 1 FROM `learning_plans` b WHERE a.`learning_plan_id`=b.`id`)) THEN

        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V16: broken foreign key fk_plan_evaluation_plan';

    END IF;

    IF EXISTS(SELECT 1 FROM `plan_evaluations` a WHERE a.`evaluated_by` IS NOT NULL AND NOT EXISTS(SELECT 1 FROM `app_user` b WHERE a.`evaluated_by`=b.`id`)) THEN

        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V16: broken foreign key fk_plan_evaluation_user';

    END IF;

    IF EXISTS(SELECT 1 FROM `plan_goals` a WHERE a.`goal_id` IS NOT NULL AND NOT EXISTS(SELECT 1 FROM `goals` b WHERE a.`goal_id`=b.`id`)) THEN

        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V16: broken foreign key fk_plan_goal_direct';

    END IF;

    IF EXISTS(SELECT 1 FROM `plan_goals` a WHERE a.`specific_goal_id` IS NOT NULL AND a.`goal_id` IS NOT NULL AND NOT EXISTS(SELECT 1 FROM `specific_goals` b WHERE a.`specific_goal_id`=b.`id` AND a.`goal_id`=b.`goal_id`)) THEN

        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V16: broken foreign key fk_plan_goal_specific_parent';

    END IF;

    IF EXISTS(SELECT 1 FROM `plan_goals` a WHERE a.`plan_id` IS NOT NULL AND NOT EXISTS(SELECT 1 FROM `learning_plans` b WHERE a.`plan_id`=b.`id`)) THEN

        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V16: broken foreign key plan_goals_ibfk_1';

    END IF;

    IF EXISTS(SELECT 1 FROM `plan_goals` a WHERE a.`assigned_by` IS NOT NULL AND NOT EXISTS(SELECT 1 FROM `app_user` b WHERE a.`assigned_by`=b.`id`)) THEN

        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V16: broken foreign key plan_goals_ibfk_3';

    END IF;

    IF EXISTS(SELECT 1 FROM `plan_goals` a WHERE a.`supersedes_id` IS NOT NULL AND NOT EXISTS(SELECT 1 FROM `plan_goals` b WHERE a.`supersedes_id`=b.`id`)) THEN

        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V16: broken foreign key plan_goals_ibfk_4';

    END IF;

    IF EXISTS(SELECT 1 FROM `program_level_domains` a WHERE a.`domain_id` IS NOT NULL AND a.`program_id` IS NOT NULL AND NOT EXISTS(SELECT 1 FROM `domains` b WHERE a.`domain_id`=b.`id` AND a.`program_id`=b.`program_id`)) THEN

        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V16: broken foreign key fk_ld_domain_program';

    END IF;

    IF EXISTS(SELECT 1 FROM `program_level_domains` a WHERE a.`program_level_id` IS NOT NULL AND a.`program_id` IS NOT NULL AND NOT EXISTS(SELECT 1 FROM `program_levels` b WHERE a.`program_level_id`=b.`id` AND a.`program_id`=b.`program_id`)) THEN

        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V16: broken foreign key fk_ld_level_program';

    END IF;

    IF EXISTS(SELECT 1 FROM `program_levels` a WHERE a.`program_id` IS NOT NULL AND NOT EXISTS(SELECT 1 FROM `programs` b WHERE a.`program_id`=b.`id`)) THEN

        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V16: broken foreign key fk_program_level_program';

    END IF;

    IF EXISTS(SELECT 1 FROM `specific_goals` a WHERE a.`goal_id` IS NOT NULL AND NOT EXISTS(SELECT 1 FROM `goals` b WHERE a.`goal_id`=b.`id`)) THEN

        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V16: broken foreign key fk_specific_goal_parent';

    END IF;

    IF EXISTS(SELECT 1 FROM `staff` a WHERE a.`user_id` IS NOT NULL AND a.`account_role` IS NOT NULL AND NOT EXISTS(SELECT 1 FROM `app_user` b WHERE a.`user_id`=b.`id` AND a.`account_role`=b.`role`)) THEN

        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V16: broken foreign key staff_ibfk_1';

    END IF;

    IF EXISTS(SELECT 1 FROM `staff_checkin` a WHERE a.`staff_id` IS NOT NULL AND NOT EXISTS(SELECT 1 FROM `staff` b WHERE a.`staff_id`=b.`id`)) THEN

        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V16: broken foreign key staff_checkin_ibfk_1';

    END IF;

    IF EXISTS(SELECT 1 FROM `staff_checkin` a WHERE a.`recorded_by` IS NOT NULL AND NOT EXISTS(SELECT 1 FROM `app_user` b WHERE a.`recorded_by`=b.`id`)) THEN

        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V16: broken foreign key staff_checkin_ibfk_2';

    END IF;

    IF EXISTS(SELECT 1 FROM `staff_salary` a WHERE a.`staff_id` IS NOT NULL AND NOT EXISTS(SELECT 1 FROM `staff` b WHERE a.`staff_id`=b.`id`)) THEN

        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V16: broken foreign key staff_salary_ibfk_1';

    END IF;

    IF EXISTS(SELECT 1 FROM `staff_salary` a WHERE a.`created_by` IS NOT NULL AND NOT EXISTS(SELECT 1 FROM `app_user` b WHERE a.`created_by`=b.`id`)) THEN

        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V16: broken foreign key staff_salary_ibfk_2';

    END IF;

    IF EXISTS(SELECT 1 FROM `student_attendance` a WHERE a.`session_id` IS NOT NULL AND NOT EXISTS(SELECT 1 FROM `class_session` b WHERE a.`session_id`=b.`id`)) THEN

        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V16: broken foreign key student_attendance_ibfk_1';

    END IF;

    IF EXISTS(SELECT 1 FROM `student_attendance` a WHERE a.`student_id` IS NOT NULL AND NOT EXISTS(SELECT 1 FROM `student` b WHERE a.`student_id`=b.`id`)) THEN

        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V16: broken foreign key student_attendance_ibfk_2';

    END IF;

    IF EXISTS(SELECT 1 FROM `student_attendance` a WHERE a.`recorded_by` IS NOT NULL AND NOT EXISTS(SELECT 1 FROM `app_user` b WHERE a.`recorded_by`=b.`id`)) THEN

        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V16: broken foreign key student_attendance_ibfk_3';

    END IF;

    IF EXISTS(SELECT 1 FROM `student_guardian` a WHERE a.`student_id` IS NOT NULL AND NOT EXISTS(SELECT 1 FROM `student` b WHERE a.`student_id`=b.`id`)) THEN

        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V16: broken foreign key student_guardian_ibfk_1';

    END IF;

    IF EXISTS(SELECT 1 FROM `student_guardian` a WHERE a.`guardian_id` IS NOT NULL AND NOT EXISTS(SELECT 1 FROM `guardian` b WHERE a.`guardian_id`=b.`id`)) THEN

        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V16: broken foreign key student_guardian_ibfk_2';

    END IF;

    IF EXISTS(SELECT 1 FROM `website_albums` a WHERE a.`updated_by` IS NOT NULL AND NOT EXISTS(SELECT 1 FROM `app_user` b WHERE a.`updated_by`=b.`id`)) THEN

        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V16: broken foreign key website_albums_ibfk_1';

    END IF;

    IF EXISTS(SELECT 1 FROM `website_faqs` a WHERE a.`updated_by` IS NOT NULL AND NOT EXISTS(SELECT 1 FROM `app_user` b WHERE a.`updated_by`=b.`id`)) THEN

        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V16: broken foreign key website_faqs_ibfk_1';

    END IF;

    IF EXISTS(SELECT 1 FROM `website_media` a WHERE a.`album_id` IS NOT NULL AND NOT EXISTS(SELECT 1 FROM `website_albums` b WHERE a.`album_id`=b.`id`)) THEN

        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V16: broken foreign key website_media_ibfk_1';

    END IF;

    IF EXISTS(SELECT 1 FROM `website_media` a WHERE a.`consent_by` IS NOT NULL AND NOT EXISTS(SELECT 1 FROM `app_user` b WHERE a.`consent_by`=b.`id`)) THEN

        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V16: broken foreign key website_media_ibfk_2';

    END IF;

    IF EXISTS(SELECT 1 FROM `website_media` a WHERE a.`created_by` IS NOT NULL AND NOT EXISTS(SELECT 1 FROM `app_user` b WHERE a.`created_by`=b.`id`)) THEN

        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V16: broken foreign key website_media_ibfk_3';

    END IF;

    IF EXISTS(SELECT 1 FROM `website_publication_media` a WHERE a.`publication_id` IS NOT NULL AND NOT EXISTS(SELECT 1 FROM `website_publications` b WHERE a.`publication_id`=b.`id`)) THEN

        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V16: broken foreign key website_publication_media_ibfk_1';

    END IF;

    IF EXISTS(SELECT 1 FROM `website_publications` a WHERE a.`published_by` IS NOT NULL AND NOT EXISTS(SELECT 1 FROM `app_user` b WHERE a.`published_by`=b.`id`)) THEN

        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V16: broken foreign key website_publications_ibfk_1';

    END IF;

    IF EXISTS(SELECT 1 FROM `website_section_items` a WHERE a.`section_key` IS NOT NULL AND NOT EXISTS(SELECT 1 FROM `website_sections` b WHERE a.`section_key`=b.`section_key`)) THEN

        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V16: broken foreign key website_section_items_ibfk_1';

    END IF;

    IF EXISTS(SELECT 1 FROM `website_section_items` a WHERE a.`updated_by` IS NOT NULL AND NOT EXISTS(SELECT 1 FROM `app_user` b WHERE a.`updated_by`=b.`id`)) THEN

        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V16: broken foreign key website_section_items_ibfk_2';

    END IF;

    IF EXISTS(SELECT 1 FROM `website_sections` a WHERE a.`image_media_id` IS NOT NULL AND NOT EXISTS(SELECT 1 FROM `website_media` b WHERE a.`image_media_id`=b.`id`)) THEN

        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V16: broken foreign key website_sections_ibfk_1';

    END IF;

    IF EXISTS(SELECT 1 FROM `website_sections` a WHERE a.`updated_by` IS NOT NULL AND NOT EXISTS(SELECT 1 FROM `app_user` b WHERE a.`updated_by`=b.`id`)) THEN

        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V16: broken foreign key website_sections_ibfk_2';

    END IF;

    IF EXISTS(SELECT 1 FROM `website_services` a WHERE a.`image_media_id` IS NOT NULL AND NOT EXISTS(SELECT 1 FROM `website_media` b WHERE a.`image_media_id`=b.`id`)) THEN

        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V16: broken foreign key website_services_ibfk_1';

    END IF;

    IF EXISTS(SELECT 1 FROM `website_services` a WHERE a.`updated_by` IS NOT NULL AND NOT EXISTS(SELECT 1 FROM `app_user` b WHERE a.`updated_by`=b.`id`)) THEN

        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V16: broken foreign key website_services_ibfk_2';

    END IF;

    IF EXISTS(SELECT 1 FROM `website_settings` a WHERE a.`logo_media_id` IS NOT NULL AND NOT EXISTS(SELECT 1 FROM `website_media` b WHERE a.`logo_media_id`=b.`id`)) THEN

        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V16: broken foreign key website_settings_ibfk_1';

    END IF;

    IF EXISTS(SELECT 1 FROM `website_settings` a WHERE a.`updated_by` IS NOT NULL AND NOT EXISTS(SELECT 1 FROM `app_user` b WHERE a.`updated_by`=b.`id`)) THEN

        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V16: broken foreign key website_settings_ibfk_2';

    END IF;

    IF EXISTS(SELECT 1 FROM `website_team_members` a WHERE a.`photo_media_id` IS NOT NULL AND NOT EXISTS(SELECT 1 FROM `website_media` b WHERE a.`photo_media_id`=b.`id`)) THEN

        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V16: broken foreign key website_team_members_ibfk_1';

    END IF;

    IF EXISTS(SELECT 1 FROM `website_team_members` a WHERE a.`updated_by` IS NOT NULL AND NOT EXISTS(SELECT 1 FROM `app_user` b WHERE a.`updated_by`=b.`id`)) THEN

        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V16: broken foreign key website_team_members_ibfk_2';

    END IF;

    IF EXISTS(SELECT 1 FROM `plan_goal_strategies` a WHERE a.`plan_goal_id` IS NOT NULL AND NOT EXISTS(SELECT 1 FROM `plan_goals` b WHERE a.`plan_goal_id`=b.`id`)) THEN

        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V16: broken foreign key fk_plan_goal_strategy_goal';

    END IF;

    IF EXISTS(SELECT 1 FROM `plan_goal_strategies` a WHERE a.`strategy_id` IS NOT NULL AND NOT EXISTS(SELECT 1 FROM `strategies` b WHERE a.`strategy_id`=b.`id`)) THEN

        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V16: broken foreign key fk_plan_goal_strategy_strategy';

    END IF;

END$$

DELIMITER ;

SET SESSION FOREIGN_KEY_CHECKS=0;

START TRANSACTION;

UPDATE learning_plans SET request_key=id,updated_at=updated_at;

-- Remap old_id -> temporary_id without collisions with existing primary keys.

UPDATE `app_user` t LEFT JOIN migration_v16_id_map m0 ON m0.entity_name='app_user' AND m0.old_id=t.`id` SET t.`id`=COALESCE(m0.temporary_id,t.`id`),t.updated_at=t.updated_at;

UPDATE `audit_event` t LEFT JOIN migration_v16_id_map m0 ON m0.entity_name='audit_event' AND m0.old_id=t.`id` LEFT JOIN migration_v16_id_map m1 ON m1.entity_name='app_user' AND m1.old_id=t.`actor_id` SET t.`id`=COALESCE(m0.temporary_id,t.`id`),t.`actor_id`=COALESCE(m1.temporary_id,t.`actor_id`);

UPDATE `center` t LEFT JOIN migration_v16_id_map m0 ON m0.entity_name='center' AND m0.old_id=t.`id` SET t.`id`=COALESCE(m0.temporary_id,t.`id`),t.updated_at=t.updated_at;

UPDATE `class_session` t LEFT JOIN migration_v16_id_map m0 ON m0.entity_name='class_session' AND m0.old_id=t.`id` LEFT JOIN migration_v16_id_map m1 ON m1.entity_name='learning_class' AND m1.old_id=t.`class_id` LEFT JOIN migration_v16_id_map m2 ON m2.entity_name='staff' AND m2.old_id=t.`teacher_id` LEFT JOIN migration_v16_id_map m3 ON m3.entity_name='app_user' AND m3.old_id=t.`created_by` SET t.`id`=COALESCE(m0.temporary_id,t.`id`),t.`class_id`=COALESCE(m1.temporary_id,t.`class_id`),t.`teacher_id`=COALESCE(m2.temporary_id,t.`teacher_id`),t.`created_by`=COALESCE(m3.temporary_id,t.`created_by`),t.updated_at=t.updated_at;

UPDATE `consultation_requests` t LEFT JOIN migration_v16_id_map m0 ON m0.entity_name='consultation_requests' AND m0.old_id=t.`id` LEFT JOIN migration_v16_id_map m1 ON m1.entity_name='app_user' AND m1.old_id=t.`assigned_user_id` LEFT JOIN migration_v16_id_map m2 ON m2.entity_name='app_user' AND m2.old_id=t.`updated_by` SET t.`id`=COALESCE(m0.temporary_id,t.`id`),t.`assigned_user_id`=COALESCE(m1.temporary_id,t.`assigned_user_id`),t.`updated_by`=COALESCE(m2.temporary_id,t.`updated_by`),t.updated_at=t.updated_at;

UPDATE `document` t LEFT JOIN migration_v16_id_map m0 ON m0.entity_name='document' AND m0.old_id=t.`id` LEFT JOIN migration_v16_id_map m1 ON m1.entity_name='app_user' AND m1.old_id=t.`uploaded_by` SET t.`id`=COALESCE(m0.temporary_id,t.`id`),t.`uploaded_by`=COALESCE(m1.temporary_id,t.`uploaded_by`);

UPDATE `domains` t LEFT JOIN migration_v16_id_map m0 ON m0.entity_name='domains' AND m0.old_id=t.`id` LEFT JOIN migration_v16_id_map m1 ON m1.entity_name='programs' AND m1.old_id=t.`program_id` SET t.`id`=COALESCE(m0.temporary_id,t.`id`),t.`program_id`=COALESCE(m1.temporary_id,t.`program_id`),t.updated_at=t.updated_at;

UPDATE `enrollment` t LEFT JOIN migration_v16_id_map m0 ON m0.entity_name='enrollment' AND m0.old_id=t.`id` LEFT JOIN migration_v16_id_map m1 ON m1.entity_name='learning_class' AND m1.old_id=t.`class_id` LEFT JOIN migration_v16_id_map m2 ON m2.entity_name='student' AND m2.old_id=t.`student_id` SET t.`id`=COALESCE(m0.temporary_id,t.`id`),t.`class_id`=COALESCE(m1.temporary_id,t.`class_id`),t.`student_id`=COALESCE(m2.temporary_id,t.`student_id`),t.updated_at=t.updated_at;

UPDATE `goal_evaluations` t LEFT JOIN migration_v16_id_map m0 ON m0.entity_name='goal_evaluations' AND m0.old_id=t.`id` LEFT JOIN migration_v16_id_map m1 ON m1.entity_name='plan_goals' AND m1.old_id=t.`plan_goal_id` LEFT JOIN migration_v16_id_map m2 ON m2.entity_name='app_user' AND m2.old_id=t.`evaluated_by` SET t.`id`=COALESCE(m0.temporary_id,t.`id`),t.`plan_goal_id`=COALESCE(m1.temporary_id,t.`plan_goal_id`),t.`evaluated_by`=COALESCE(m2.temporary_id,t.`evaluated_by`),t.updated_at=t.updated_at;

UPDATE `goals` t LEFT JOIN migration_v16_id_map m0 ON m0.entity_name='goals' AND m0.old_id=t.`id` LEFT JOIN migration_v16_id_map m1 ON m1.entity_name='program_level_domains' AND m1.old_id=t.`level_domain_id` SET t.`id`=COALESCE(m0.temporary_id,t.`id`),t.`level_domain_id`=COALESCE(m1.temporary_id,t.`level_domain_id`),t.updated_at=t.updated_at;

UPDATE `guardian` t LEFT JOIN migration_v16_id_map m0 ON m0.entity_name='guardian' AND m0.old_id=t.`id` LEFT JOIN migration_v16_id_map m1 ON m1.entity_name='app_user' AND m1.old_id=t.`user_id` SET t.`id`=COALESCE(m0.temporary_id,t.`id`),t.`user_id`=COALESCE(m1.temporary_id,t.`user_id`),t.updated_at=t.updated_at;

UPDATE `invoice` t LEFT JOIN migration_v16_id_map m0 ON m0.entity_name='invoice' AND m0.old_id=t.`id` LEFT JOIN migration_v16_id_map m1 ON m1.entity_name='student' AND m1.old_id=t.`student_id` LEFT JOIN migration_v16_id_map m2 ON m2.entity_name='app_user' AND m2.old_id=t.`created_by` SET t.`id`=COALESCE(m0.temporary_id,t.`id`),t.`student_id`=COALESCE(m1.temporary_id,t.`student_id`),t.`created_by`=COALESCE(m2.temporary_id,t.`created_by`),t.updated_at=t.updated_at;

UPDATE `invoice_line` t LEFT JOIN migration_v16_id_map m0 ON m0.entity_name='invoice_line' AND m0.old_id=t.`id` LEFT JOIN migration_v16_id_map m1 ON m1.entity_name='invoice' AND m1.old_id=t.`invoice_id` SET t.`id`=COALESCE(m0.temporary_id,t.`id`),t.`invoice_id`=COALESCE(m1.temporary_id,t.`invoice_id`);

UPDATE `learning_class` t LEFT JOIN migration_v16_id_map m0 ON m0.entity_name='learning_class' AND m0.old_id=t.`id` LEFT JOIN migration_v16_id_map m1 ON m1.entity_name='staff' AND m1.old_id=t.`teacher_id` SET t.`id`=COALESCE(m0.temporary_id,t.`id`),t.`teacher_id`=COALESCE(m1.temporary_id,t.`teacher_id`),t.updated_at=t.updated_at;

UPDATE `learning_plans` t LEFT JOIN migration_v16_id_map m0 ON m0.entity_name='learning_plans' AND m0.old_id=t.`id` LEFT JOIN migration_v16_id_map m1 ON m1.entity_name='student' AND m1.old_id=t.`student_id` LEFT JOIN migration_v16_id_map m2 ON m2.entity_name='app_user' AND m2.old_id=t.`created_by` SET t.`id`=COALESCE(m0.temporary_id,t.`id`),t.`student_id`=COALESCE(m1.temporary_id,t.`student_id`),t.`created_by`=COALESCE(m2.temporary_id,t.`created_by`),t.updated_at=t.updated_at;

UPDATE `leave_request` t LEFT JOIN migration_v16_id_map m0 ON m0.entity_name='leave_request' AND m0.old_id=t.`id` LEFT JOIN migration_v16_id_map m1 ON m1.entity_name='staff' AND m1.old_id=t.`staff_id` LEFT JOIN migration_v16_id_map m2 ON m2.entity_name='app_user' AND m2.old_id=t.`requested_by` LEFT JOIN migration_v16_id_map m3 ON m3.entity_name='app_user' AND m3.old_id=t.`reviewed_by` SET t.`id`=COALESCE(m0.temporary_id,t.`id`),t.`staff_id`=COALESCE(m1.temporary_id,t.`staff_id`),t.`requested_by`=COALESCE(m2.temporary_id,t.`requested_by`),t.`reviewed_by`=COALESCE(m3.temporary_id,t.`reviewed_by`),t.updated_at=t.updated_at;

UPDATE `notification` t LEFT JOIN migration_v16_id_map m0 ON m0.entity_name='notification' AND m0.old_id=t.`id` LEFT JOIN migration_v16_id_map m1 ON m1.entity_name='app_user' AND m1.old_id=t.`created_by` SET t.`id`=COALESCE(m0.temporary_id,t.`id`),t.`created_by`=COALESCE(m1.temporary_id,t.`created_by`),t.updated_at=t.updated_at;

UPDATE `notification_recipient` t LEFT JOIN migration_v16_id_map m0 ON m0.entity_name='notification' AND m0.old_id=t.`notification_id` LEFT JOIN migration_v16_id_map m1 ON m1.entity_name='app_user' AND m1.old_id=t.`user_id` SET t.`notification_id`=COALESCE(m0.temporary_id,t.`notification_id`),t.`user_id`=COALESCE(m1.temporary_id,t.`user_id`);

UPDATE `payment` t LEFT JOIN migration_v16_id_map m0 ON m0.entity_name='payment' AND m0.old_id=t.`id` LEFT JOIN migration_v16_id_map m1 ON m1.entity_name='invoice' AND m1.old_id=t.`invoice_id` LEFT JOIN migration_v16_id_map m2 ON m2.entity_name='app_user' AND m2.old_id=t.`received_by` LEFT JOIN migration_v16_id_map m3 ON m3.entity_name='app_user' AND m3.old_id=t.`voided_by` SET t.`id`=COALESCE(m0.temporary_id,t.`id`),t.`invoice_id`=COALESCE(m1.temporary_id,t.`invoice_id`),t.`received_by`=COALESCE(m2.temporary_id,t.`received_by`),t.`voided_by`=COALESCE(m3.temporary_id,t.`voided_by`);

UPDATE `plan_evaluations` t LEFT JOIN migration_v16_id_map m0 ON m0.entity_name='plan_evaluations' AND m0.old_id=t.`id` LEFT JOIN migration_v16_id_map m1 ON m1.entity_name='learning_plans' AND m1.old_id=t.`learning_plan_id` LEFT JOIN migration_v16_id_map m2 ON m2.entity_name='app_user' AND m2.old_id=t.`evaluated_by` SET t.`id`=COALESCE(m0.temporary_id,t.`id`),t.`learning_plan_id`=COALESCE(m1.temporary_id,t.`learning_plan_id`),t.`evaluated_by`=COALESCE(m2.temporary_id,t.`evaluated_by`),t.updated_at=t.updated_at;

UPDATE `plan_goal_strategies` t LEFT JOIN migration_v16_id_map m0 ON m0.entity_name='plan_goal_strategies' AND m0.old_id=t.`id` LEFT JOIN migration_v16_id_map m1 ON m1.entity_name='plan_goals' AND m1.old_id=t.`plan_goal_id` LEFT JOIN migration_v16_id_map m2 ON m2.entity_name='strategies' AND m2.old_id=t.`strategy_id` SET t.`id`=COALESCE(m0.temporary_id,t.`id`),t.`plan_goal_id`=COALESCE(m1.temporary_id,t.`plan_goal_id`),t.`strategy_id`=COALESCE(m2.temporary_id,t.`strategy_id`);

UPDATE `plan_goals` t LEFT JOIN migration_v16_id_map m0 ON m0.entity_name='plan_goals' AND m0.old_id=t.`id` LEFT JOIN migration_v16_id_map m1 ON m1.entity_name='goals' AND m1.old_id=t.`goal_id` LEFT JOIN migration_v16_id_map m2 ON m2.entity_name='specific_goals' AND m2.old_id=t.`specific_goal_id` LEFT JOIN migration_v16_id_map m3 ON m3.entity_name='learning_plans' AND m3.old_id=t.`plan_id` LEFT JOIN migration_v16_id_map m4 ON m4.entity_name='app_user' AND m4.old_id=t.`assigned_by` LEFT JOIN migration_v16_id_map m5 ON m5.entity_name='plan_goals' AND m5.old_id=t.`supersedes_id` SET t.`id`=COALESCE(m0.temporary_id,t.`id`),t.`goal_id`=COALESCE(m1.temporary_id,t.`goal_id`),t.`specific_goal_id`=COALESCE(m2.temporary_id,t.`specific_goal_id`),t.`plan_id`=COALESCE(m3.temporary_id,t.`plan_id`),t.`assigned_by`=COALESCE(m4.temporary_id,t.`assigned_by`),t.`supersedes_id`=COALESCE(m5.temporary_id,t.`supersedes_id`),t.updated_at=t.updated_at;

UPDATE `program_level_domains` t LEFT JOIN migration_v16_id_map m0 ON m0.entity_name='program_level_domains' AND m0.old_id=t.`id` LEFT JOIN migration_v16_id_map m1 ON m1.entity_name='domains' AND m1.old_id=t.`domain_id` LEFT JOIN migration_v16_id_map m2 ON m2.entity_name='program_levels' AND m2.old_id=t.`program_level_id` LEFT JOIN migration_v16_id_map m3 ON m3.entity_name='programs' AND m3.old_id=t.`program_id` SET t.`id`=COALESCE(m0.temporary_id,t.`id`),t.`domain_id`=COALESCE(m1.temporary_id,t.`domain_id`),t.`program_level_id`=COALESCE(m2.temporary_id,t.`program_level_id`),t.`program_id`=COALESCE(m3.temporary_id,t.`program_id`);

UPDATE `program_levels` t LEFT JOIN migration_v16_id_map m0 ON m0.entity_name='program_levels' AND m0.old_id=t.`id` LEFT JOIN migration_v16_id_map m1 ON m1.entity_name='programs' AND m1.old_id=t.`program_id` SET t.`id`=COALESCE(m0.temporary_id,t.`id`),t.`program_id`=COALESCE(m1.temporary_id,t.`program_id`),t.updated_at=t.updated_at;

UPDATE `programs` t LEFT JOIN migration_v16_id_map m0 ON m0.entity_name='programs' AND m0.old_id=t.`id` SET t.`id`=COALESCE(m0.temporary_id,t.`id`),t.updated_at=t.updated_at;

UPDATE `specific_goals` t LEFT JOIN migration_v16_id_map m0 ON m0.entity_name='specific_goals' AND m0.old_id=t.`id` LEFT JOIN migration_v16_id_map m1 ON m1.entity_name='goals' AND m1.old_id=t.`goal_id` SET t.`id`=COALESCE(m0.temporary_id,t.`id`),t.`goal_id`=COALESCE(m1.temporary_id,t.`goal_id`),t.updated_at=t.updated_at;

UPDATE `staff` t LEFT JOIN migration_v16_id_map m0 ON m0.entity_name='staff' AND m0.old_id=t.`id` LEFT JOIN migration_v16_id_map m1 ON m1.entity_name='app_user' AND m1.old_id=t.`user_id` SET t.`id`=COALESCE(m0.temporary_id,t.`id`),t.`user_id`=COALESCE(m1.temporary_id,t.`user_id`),t.updated_at=t.updated_at;

UPDATE `staff_checkin` t LEFT JOIN migration_v16_id_map m0 ON m0.entity_name='staff_checkin' AND m0.old_id=t.`id` LEFT JOIN migration_v16_id_map m1 ON m1.entity_name='staff' AND m1.old_id=t.`staff_id` LEFT JOIN migration_v16_id_map m2 ON m2.entity_name='app_user' AND m2.old_id=t.`recorded_by` SET t.`id`=COALESCE(m0.temporary_id,t.`id`),t.`staff_id`=COALESCE(m1.temporary_id,t.`staff_id`),t.`recorded_by`=COALESCE(m2.temporary_id,t.`recorded_by`),t.updated_at=t.updated_at;

UPDATE `staff_salary` t LEFT JOIN migration_v16_id_map m0 ON m0.entity_name='staff_salary' AND m0.old_id=t.`id` LEFT JOIN migration_v16_id_map m1 ON m1.entity_name='staff' AND m1.old_id=t.`staff_id` LEFT JOIN migration_v16_id_map m2 ON m2.entity_name='app_user' AND m2.old_id=t.`created_by` SET t.`id`=COALESCE(m0.temporary_id,t.`id`),t.`staff_id`=COALESCE(m1.temporary_id,t.`staff_id`),t.`created_by`=COALESCE(m2.temporary_id,t.`created_by`),t.updated_at=t.updated_at;

UPDATE `strategies` t LEFT JOIN migration_v16_id_map m0 ON m0.entity_name='strategies' AND m0.old_id=t.`id` SET t.`id`=COALESCE(m0.temporary_id,t.`id`);

UPDATE `student` t LEFT JOIN migration_v16_id_map m0 ON m0.entity_name='student' AND m0.old_id=t.`id` SET t.`id`=COALESCE(m0.temporary_id,t.`id`),t.updated_at=t.updated_at;

UPDATE `student_attendance` t LEFT JOIN migration_v16_id_map m0 ON m0.entity_name='student_attendance' AND m0.old_id=t.`id` LEFT JOIN migration_v16_id_map m1 ON m1.entity_name='class_session' AND m1.old_id=t.`session_id` LEFT JOIN migration_v16_id_map m2 ON m2.entity_name='student' AND m2.old_id=t.`student_id` LEFT JOIN migration_v16_id_map m3 ON m3.entity_name='app_user' AND m3.old_id=t.`recorded_by` SET t.`id`=COALESCE(m0.temporary_id,t.`id`),t.`session_id`=COALESCE(m1.temporary_id,t.`session_id`),t.`student_id`=COALESCE(m2.temporary_id,t.`student_id`),t.`recorded_by`=COALESCE(m3.temporary_id,t.`recorded_by`),t.updated_at=t.updated_at;

UPDATE `student_guardian` t LEFT JOIN migration_v16_id_map m0 ON m0.entity_name='student' AND m0.old_id=t.`student_id` LEFT JOIN migration_v16_id_map m1 ON m1.entity_name='guardian' AND m1.old_id=t.`guardian_id` SET t.`student_id`=COALESCE(m0.temporary_id,t.`student_id`),t.`guardian_id`=COALESCE(m1.temporary_id,t.`guardian_id`);

UPDATE `website_albums` t LEFT JOIN migration_v16_id_map m0 ON m0.entity_name='website_albums' AND m0.old_id=t.`id` LEFT JOIN migration_v16_id_map m1 ON m1.entity_name='app_user' AND m1.old_id=t.`updated_by` SET t.`id`=COALESCE(m0.temporary_id,t.`id`),t.`updated_by`=COALESCE(m1.temporary_id,t.`updated_by`),t.updated_at=t.updated_at;

UPDATE `website_faqs` t LEFT JOIN migration_v16_id_map m0 ON m0.entity_name='website_faqs' AND m0.old_id=t.`id` LEFT JOIN migration_v16_id_map m1 ON m1.entity_name='app_user' AND m1.old_id=t.`updated_by` SET t.`id`=COALESCE(m0.temporary_id,t.`id`),t.`updated_by`=COALESCE(m1.temporary_id,t.`updated_by`),t.updated_at=t.updated_at;

UPDATE `website_media` t LEFT JOIN migration_v16_id_map m0 ON m0.entity_name='website_media' AND m0.old_id=t.`id` LEFT JOIN migration_v16_id_map m1 ON m1.entity_name='website_albums' AND m1.old_id=t.`album_id` LEFT JOIN migration_v16_id_map m2 ON m2.entity_name='app_user' AND m2.old_id=t.`consent_by` LEFT JOIN migration_v16_id_map m3 ON m3.entity_name='app_user' AND m3.old_id=t.`created_by` SET t.`id`=COALESCE(m0.temporary_id,t.`id`),t.`album_id`=COALESCE(m1.temporary_id,t.`album_id`),t.`consent_by`=COALESCE(m2.temporary_id,t.`consent_by`),t.`created_by`=COALESCE(m3.temporary_id,t.`created_by`),t.updated_at=t.updated_at;

UPDATE `website_publication_media` t LEFT JOIN migration_v16_id_map m0 ON m0.entity_name='website_publications' AND m0.old_id=t.`publication_id` LEFT JOIN migration_v16_id_map m1 ON m1.entity_name='website_media' AND m1.old_id=t.`media_id` SET t.`publication_id`=COALESCE(m0.temporary_id,t.`publication_id`),t.`media_id`=COALESCE(m1.temporary_id,t.`media_id`);

UPDATE `website_publications` t LEFT JOIN migration_v16_id_map m0 ON m0.entity_name='website_publications' AND m0.old_id=t.`id` LEFT JOIN migration_v16_id_map m1 ON m1.entity_name='app_user' AND m1.old_id=t.`published_by` SET t.`id`=COALESCE(m0.temporary_id,t.`id`),t.`published_by`=COALESCE(m1.temporary_id,t.`published_by`);

UPDATE `website_section_items` t LEFT JOIN migration_v16_id_map m0 ON m0.entity_name='website_section_items' AND m0.old_id=t.`id` LEFT JOIN migration_v16_id_map m1 ON m1.entity_name='app_user' AND m1.old_id=t.`updated_by` SET t.`id`=COALESCE(m0.temporary_id,t.`id`),t.`updated_by`=COALESCE(m1.temporary_id,t.`updated_by`),t.updated_at=t.updated_at;

UPDATE `website_sections` t LEFT JOIN migration_v16_id_map m0 ON m0.entity_name='website_media' AND m0.old_id=t.`image_media_id` LEFT JOIN migration_v16_id_map m1 ON m1.entity_name='app_user' AND m1.old_id=t.`updated_by` SET t.`image_media_id`=COALESCE(m0.temporary_id,t.`image_media_id`),t.`updated_by`=COALESCE(m1.temporary_id,t.`updated_by`),t.updated_at=t.updated_at;

UPDATE `website_services` t LEFT JOIN migration_v16_id_map m0 ON m0.entity_name='website_services' AND m0.old_id=t.`id` LEFT JOIN migration_v16_id_map m1 ON m1.entity_name='website_media' AND m1.old_id=t.`image_media_id` LEFT JOIN migration_v16_id_map m2 ON m2.entity_name='app_user' AND m2.old_id=t.`updated_by` SET t.`id`=COALESCE(m0.temporary_id,t.`id`),t.`image_media_id`=COALESCE(m1.temporary_id,t.`image_media_id`),t.`updated_by`=COALESCE(m2.temporary_id,t.`updated_by`),t.updated_at=t.updated_at;

UPDATE `website_settings` t LEFT JOIN migration_v16_id_map m0 ON m0.entity_name='website_settings' AND m0.old_id=t.`id` LEFT JOIN migration_v16_id_map m1 ON m1.entity_name='website_media' AND m1.old_id=t.`logo_media_id` LEFT JOIN migration_v16_id_map m2 ON m2.entity_name='app_user' AND m2.old_id=t.`updated_by` SET t.`id`=COALESCE(m0.temporary_id,t.`id`),t.`logo_media_id`=COALESCE(m1.temporary_id,t.`logo_media_id`),t.`updated_by`=COALESCE(m2.temporary_id,t.`updated_by`),t.updated_at=t.updated_at;

UPDATE `website_team_members` t LEFT JOIN migration_v16_id_map m0 ON m0.entity_name='website_team_members' AND m0.old_id=t.`id` LEFT JOIN migration_v16_id_map m1 ON m1.entity_name='website_media' AND m1.old_id=t.`photo_media_id` LEFT JOIN migration_v16_id_map m2 ON m2.entity_name='app_user' AND m2.old_id=t.`updated_by` SET t.`id`=COALESCE(m0.temporary_id,t.`id`),t.`photo_media_id`=COALESCE(m1.temporary_id,t.`photo_media_id`),t.`updated_by`=COALESCE(m2.temporary_id,t.`updated_by`),t.updated_at=t.updated_at;

-- Remap temporary_id -> new_id without collisions with existing primary keys.

UPDATE `app_user` t LEFT JOIN migration_v16_id_map m0 ON m0.entity_name='app_user' AND m0.temporary_id=t.`id` SET t.`id`=COALESCE(m0.new_id,t.`id`),t.updated_at=t.updated_at;

UPDATE `audit_event` t LEFT JOIN migration_v16_id_map m0 ON m0.entity_name='audit_event' AND m0.temporary_id=t.`id` LEFT JOIN migration_v16_id_map m1 ON m1.entity_name='app_user' AND m1.temporary_id=t.`actor_id` SET t.`id`=COALESCE(m0.new_id,t.`id`),t.`actor_id`=COALESCE(m1.new_id,t.`actor_id`);

UPDATE `center` t LEFT JOIN migration_v16_id_map m0 ON m0.entity_name='center' AND m0.temporary_id=t.`id` SET t.`id`=COALESCE(m0.new_id,t.`id`),t.updated_at=t.updated_at;

UPDATE `class_session` t LEFT JOIN migration_v16_id_map m0 ON m0.entity_name='class_session' AND m0.temporary_id=t.`id` LEFT JOIN migration_v16_id_map m1 ON m1.entity_name='learning_class' AND m1.temporary_id=t.`class_id` LEFT JOIN migration_v16_id_map m2 ON m2.entity_name='staff' AND m2.temporary_id=t.`teacher_id` LEFT JOIN migration_v16_id_map m3 ON m3.entity_name='app_user' AND m3.temporary_id=t.`created_by` SET t.`id`=COALESCE(m0.new_id,t.`id`),t.`class_id`=COALESCE(m1.new_id,t.`class_id`),t.`teacher_id`=COALESCE(m2.new_id,t.`teacher_id`),t.`created_by`=COALESCE(m3.new_id,t.`created_by`),t.updated_at=t.updated_at;

UPDATE `consultation_requests` t LEFT JOIN migration_v16_id_map m0 ON m0.entity_name='consultation_requests' AND m0.temporary_id=t.`id` LEFT JOIN migration_v16_id_map m1 ON m1.entity_name='app_user' AND m1.temporary_id=t.`assigned_user_id` LEFT JOIN migration_v16_id_map m2 ON m2.entity_name='app_user' AND m2.temporary_id=t.`updated_by` SET t.`id`=COALESCE(m0.new_id,t.`id`),t.`assigned_user_id`=COALESCE(m1.new_id,t.`assigned_user_id`),t.`updated_by`=COALESCE(m2.new_id,t.`updated_by`),t.updated_at=t.updated_at;

UPDATE `document` t LEFT JOIN migration_v16_id_map m0 ON m0.entity_name='document' AND m0.temporary_id=t.`id` LEFT JOIN migration_v16_id_map m1 ON m1.entity_name='app_user' AND m1.temporary_id=t.`uploaded_by` SET t.`id`=COALESCE(m0.new_id,t.`id`),t.`uploaded_by`=COALESCE(m1.new_id,t.`uploaded_by`);

UPDATE `domains` t LEFT JOIN migration_v16_id_map m0 ON m0.entity_name='domains' AND m0.temporary_id=t.`id` LEFT JOIN migration_v16_id_map m1 ON m1.entity_name='programs' AND m1.temporary_id=t.`program_id` SET t.`id`=COALESCE(m0.new_id,t.`id`),t.`program_id`=COALESCE(m1.new_id,t.`program_id`),t.updated_at=t.updated_at;

UPDATE `enrollment` t LEFT JOIN migration_v16_id_map m0 ON m0.entity_name='enrollment' AND m0.temporary_id=t.`id` LEFT JOIN migration_v16_id_map m1 ON m1.entity_name='learning_class' AND m1.temporary_id=t.`class_id` LEFT JOIN migration_v16_id_map m2 ON m2.entity_name='student' AND m2.temporary_id=t.`student_id` SET t.`id`=COALESCE(m0.new_id,t.`id`),t.`class_id`=COALESCE(m1.new_id,t.`class_id`),t.`student_id`=COALESCE(m2.new_id,t.`student_id`),t.updated_at=t.updated_at;

UPDATE `goal_evaluations` t LEFT JOIN migration_v16_id_map m0 ON m0.entity_name='goal_evaluations' AND m0.temporary_id=t.`id` LEFT JOIN migration_v16_id_map m1 ON m1.entity_name='plan_goals' AND m1.temporary_id=t.`plan_goal_id` LEFT JOIN migration_v16_id_map m2 ON m2.entity_name='app_user' AND m2.temporary_id=t.`evaluated_by` SET t.`id`=COALESCE(m0.new_id,t.`id`),t.`plan_goal_id`=COALESCE(m1.new_id,t.`plan_goal_id`),t.`evaluated_by`=COALESCE(m2.new_id,t.`evaluated_by`),t.updated_at=t.updated_at;

UPDATE `goals` t LEFT JOIN migration_v16_id_map m0 ON m0.entity_name='goals' AND m0.temporary_id=t.`id` LEFT JOIN migration_v16_id_map m1 ON m1.entity_name='program_level_domains' AND m1.temporary_id=t.`level_domain_id` SET t.`id`=COALESCE(m0.new_id,t.`id`),t.`level_domain_id`=COALESCE(m1.new_id,t.`level_domain_id`),t.updated_at=t.updated_at;

UPDATE `guardian` t LEFT JOIN migration_v16_id_map m0 ON m0.entity_name='guardian' AND m0.temporary_id=t.`id` LEFT JOIN migration_v16_id_map m1 ON m1.entity_name='app_user' AND m1.temporary_id=t.`user_id` SET t.`id`=COALESCE(m0.new_id,t.`id`),t.`user_id`=COALESCE(m1.new_id,t.`user_id`),t.updated_at=t.updated_at;

UPDATE `invoice` t LEFT JOIN migration_v16_id_map m0 ON m0.entity_name='invoice' AND m0.temporary_id=t.`id` LEFT JOIN migration_v16_id_map m1 ON m1.entity_name='student' AND m1.temporary_id=t.`student_id` LEFT JOIN migration_v16_id_map m2 ON m2.entity_name='app_user' AND m2.temporary_id=t.`created_by` SET t.`id`=COALESCE(m0.new_id,t.`id`),t.`student_id`=COALESCE(m1.new_id,t.`student_id`),t.`created_by`=COALESCE(m2.new_id,t.`created_by`),t.updated_at=t.updated_at;

UPDATE `invoice_line` t LEFT JOIN migration_v16_id_map m0 ON m0.entity_name='invoice_line' AND m0.temporary_id=t.`id` LEFT JOIN migration_v16_id_map m1 ON m1.entity_name='invoice' AND m1.temporary_id=t.`invoice_id` SET t.`id`=COALESCE(m0.new_id,t.`id`),t.`invoice_id`=COALESCE(m1.new_id,t.`invoice_id`);

UPDATE `learning_class` t LEFT JOIN migration_v16_id_map m0 ON m0.entity_name='learning_class' AND m0.temporary_id=t.`id` LEFT JOIN migration_v16_id_map m1 ON m1.entity_name='staff' AND m1.temporary_id=t.`teacher_id` SET t.`id`=COALESCE(m0.new_id,t.`id`),t.`teacher_id`=COALESCE(m1.new_id,t.`teacher_id`),t.updated_at=t.updated_at;

UPDATE `learning_plans` t LEFT JOIN migration_v16_id_map m0 ON m0.entity_name='learning_plans' AND m0.temporary_id=t.`id` LEFT JOIN migration_v16_id_map m1 ON m1.entity_name='student' AND m1.temporary_id=t.`student_id` LEFT JOIN migration_v16_id_map m2 ON m2.entity_name='app_user' AND m2.temporary_id=t.`created_by` SET t.`id`=COALESCE(m0.new_id,t.`id`),t.`student_id`=COALESCE(m1.new_id,t.`student_id`),t.`created_by`=COALESCE(m2.new_id,t.`created_by`),t.updated_at=t.updated_at;

UPDATE `leave_request` t LEFT JOIN migration_v16_id_map m0 ON m0.entity_name='leave_request' AND m0.temporary_id=t.`id` LEFT JOIN migration_v16_id_map m1 ON m1.entity_name='staff' AND m1.temporary_id=t.`staff_id` LEFT JOIN migration_v16_id_map m2 ON m2.entity_name='app_user' AND m2.temporary_id=t.`requested_by` LEFT JOIN migration_v16_id_map m3 ON m3.entity_name='app_user' AND m3.temporary_id=t.`reviewed_by` SET t.`id`=COALESCE(m0.new_id,t.`id`),t.`staff_id`=COALESCE(m1.new_id,t.`staff_id`),t.`requested_by`=COALESCE(m2.new_id,t.`requested_by`),t.`reviewed_by`=COALESCE(m3.new_id,t.`reviewed_by`),t.updated_at=t.updated_at;

UPDATE `notification` t LEFT JOIN migration_v16_id_map m0 ON m0.entity_name='notification' AND m0.temporary_id=t.`id` LEFT JOIN migration_v16_id_map m1 ON m1.entity_name='app_user' AND m1.temporary_id=t.`created_by` SET t.`id`=COALESCE(m0.new_id,t.`id`),t.`created_by`=COALESCE(m1.new_id,t.`created_by`),t.updated_at=t.updated_at;

UPDATE `notification_recipient` t LEFT JOIN migration_v16_id_map m0 ON m0.entity_name='notification' AND m0.temporary_id=t.`notification_id` LEFT JOIN migration_v16_id_map m1 ON m1.entity_name='app_user' AND m1.temporary_id=t.`user_id` SET t.`notification_id`=COALESCE(m0.new_id,t.`notification_id`),t.`user_id`=COALESCE(m1.new_id,t.`user_id`);

UPDATE `payment` t LEFT JOIN migration_v16_id_map m0 ON m0.entity_name='payment' AND m0.temporary_id=t.`id` LEFT JOIN migration_v16_id_map m1 ON m1.entity_name='invoice' AND m1.temporary_id=t.`invoice_id` LEFT JOIN migration_v16_id_map m2 ON m2.entity_name='app_user' AND m2.temporary_id=t.`received_by` LEFT JOIN migration_v16_id_map m3 ON m3.entity_name='app_user' AND m3.temporary_id=t.`voided_by` SET t.`id`=COALESCE(m0.new_id,t.`id`),t.`invoice_id`=COALESCE(m1.new_id,t.`invoice_id`),t.`received_by`=COALESCE(m2.new_id,t.`received_by`),t.`voided_by`=COALESCE(m3.new_id,t.`voided_by`);

UPDATE `plan_evaluations` t LEFT JOIN migration_v16_id_map m0 ON m0.entity_name='plan_evaluations' AND m0.temporary_id=t.`id` LEFT JOIN migration_v16_id_map m1 ON m1.entity_name='learning_plans' AND m1.temporary_id=t.`learning_plan_id` LEFT JOIN migration_v16_id_map m2 ON m2.entity_name='app_user' AND m2.temporary_id=t.`evaluated_by` SET t.`id`=COALESCE(m0.new_id,t.`id`),t.`learning_plan_id`=COALESCE(m1.new_id,t.`learning_plan_id`),t.`evaluated_by`=COALESCE(m2.new_id,t.`evaluated_by`),t.updated_at=t.updated_at;

UPDATE `plan_goal_strategies` t LEFT JOIN migration_v16_id_map m0 ON m0.entity_name='plan_goal_strategies' AND m0.temporary_id=t.`id` LEFT JOIN migration_v16_id_map m1 ON m1.entity_name='plan_goals' AND m1.temporary_id=t.`plan_goal_id` LEFT JOIN migration_v16_id_map m2 ON m2.entity_name='strategies' AND m2.temporary_id=t.`strategy_id` SET t.`id`=COALESCE(m0.new_id,t.`id`),t.`plan_goal_id`=COALESCE(m1.new_id,t.`plan_goal_id`),t.`strategy_id`=COALESCE(m2.new_id,t.`strategy_id`);

UPDATE `plan_goals` t LEFT JOIN migration_v16_id_map m0 ON m0.entity_name='plan_goals' AND m0.temporary_id=t.`id` LEFT JOIN migration_v16_id_map m1 ON m1.entity_name='goals' AND m1.temporary_id=t.`goal_id` LEFT JOIN migration_v16_id_map m2 ON m2.entity_name='specific_goals' AND m2.temporary_id=t.`specific_goal_id` LEFT JOIN migration_v16_id_map m3 ON m3.entity_name='learning_plans' AND m3.temporary_id=t.`plan_id` LEFT JOIN migration_v16_id_map m4 ON m4.entity_name='app_user' AND m4.temporary_id=t.`assigned_by` LEFT JOIN migration_v16_id_map m5 ON m5.entity_name='plan_goals' AND m5.temporary_id=t.`supersedes_id` SET t.`id`=COALESCE(m0.new_id,t.`id`),t.`goal_id`=COALESCE(m1.new_id,t.`goal_id`),t.`specific_goal_id`=COALESCE(m2.new_id,t.`specific_goal_id`),t.`plan_id`=COALESCE(m3.new_id,t.`plan_id`),t.`assigned_by`=COALESCE(m4.new_id,t.`assigned_by`),t.`supersedes_id`=COALESCE(m5.new_id,t.`supersedes_id`),t.updated_at=t.updated_at;

UPDATE `program_level_domains` t LEFT JOIN migration_v16_id_map m0 ON m0.entity_name='program_level_domains' AND m0.temporary_id=t.`id` LEFT JOIN migration_v16_id_map m1 ON m1.entity_name='domains' AND m1.temporary_id=t.`domain_id` LEFT JOIN migration_v16_id_map m2 ON m2.entity_name='program_levels' AND m2.temporary_id=t.`program_level_id` LEFT JOIN migration_v16_id_map m3 ON m3.entity_name='programs' AND m3.temporary_id=t.`program_id` SET t.`id`=COALESCE(m0.new_id,t.`id`),t.`domain_id`=COALESCE(m1.new_id,t.`domain_id`),t.`program_level_id`=COALESCE(m2.new_id,t.`program_level_id`),t.`program_id`=COALESCE(m3.new_id,t.`program_id`);

UPDATE `program_levels` t LEFT JOIN migration_v16_id_map m0 ON m0.entity_name='program_levels' AND m0.temporary_id=t.`id` LEFT JOIN migration_v16_id_map m1 ON m1.entity_name='programs' AND m1.temporary_id=t.`program_id` SET t.`id`=COALESCE(m0.new_id,t.`id`),t.`program_id`=COALESCE(m1.new_id,t.`program_id`),t.updated_at=t.updated_at;

UPDATE `programs` t LEFT JOIN migration_v16_id_map m0 ON m0.entity_name='programs' AND m0.temporary_id=t.`id` SET t.`id`=COALESCE(m0.new_id,t.`id`),t.updated_at=t.updated_at;

UPDATE `specific_goals` t LEFT JOIN migration_v16_id_map m0 ON m0.entity_name='specific_goals' AND m0.temporary_id=t.`id` LEFT JOIN migration_v16_id_map m1 ON m1.entity_name='goals' AND m1.temporary_id=t.`goal_id` SET t.`id`=COALESCE(m0.new_id,t.`id`),t.`goal_id`=COALESCE(m1.new_id,t.`goal_id`),t.updated_at=t.updated_at;

UPDATE `staff` t LEFT JOIN migration_v16_id_map m0 ON m0.entity_name='staff' AND m0.temporary_id=t.`id` LEFT JOIN migration_v16_id_map m1 ON m1.entity_name='app_user' AND m1.temporary_id=t.`user_id` SET t.`id`=COALESCE(m0.new_id,t.`id`),t.`user_id`=COALESCE(m1.new_id,t.`user_id`),t.updated_at=t.updated_at;

UPDATE `staff_checkin` t LEFT JOIN migration_v16_id_map m0 ON m0.entity_name='staff_checkin' AND m0.temporary_id=t.`id` LEFT JOIN migration_v16_id_map m1 ON m1.entity_name='staff' AND m1.temporary_id=t.`staff_id` LEFT JOIN migration_v16_id_map m2 ON m2.entity_name='app_user' AND m2.temporary_id=t.`recorded_by` SET t.`id`=COALESCE(m0.new_id,t.`id`),t.`staff_id`=COALESCE(m1.new_id,t.`staff_id`),t.`recorded_by`=COALESCE(m2.new_id,t.`recorded_by`),t.updated_at=t.updated_at;

UPDATE `staff_salary` t LEFT JOIN migration_v16_id_map m0 ON m0.entity_name='staff_salary' AND m0.temporary_id=t.`id` LEFT JOIN migration_v16_id_map m1 ON m1.entity_name='staff' AND m1.temporary_id=t.`staff_id` LEFT JOIN migration_v16_id_map m2 ON m2.entity_name='app_user' AND m2.temporary_id=t.`created_by` SET t.`id`=COALESCE(m0.new_id,t.`id`),t.`staff_id`=COALESCE(m1.new_id,t.`staff_id`),t.`created_by`=COALESCE(m2.new_id,t.`created_by`),t.updated_at=t.updated_at;

UPDATE `strategies` t LEFT JOIN migration_v16_id_map m0 ON m0.entity_name='strategies' AND m0.temporary_id=t.`id` SET t.`id`=COALESCE(m0.new_id,t.`id`);

UPDATE `student` t LEFT JOIN migration_v16_id_map m0 ON m0.entity_name='student' AND m0.temporary_id=t.`id` SET t.`id`=COALESCE(m0.new_id,t.`id`),t.updated_at=t.updated_at;

UPDATE `student_attendance` t LEFT JOIN migration_v16_id_map m0 ON m0.entity_name='student_attendance' AND m0.temporary_id=t.`id` LEFT JOIN migration_v16_id_map m1 ON m1.entity_name='class_session' AND m1.temporary_id=t.`session_id` LEFT JOIN migration_v16_id_map m2 ON m2.entity_name='student' AND m2.temporary_id=t.`student_id` LEFT JOIN migration_v16_id_map m3 ON m3.entity_name='app_user' AND m3.temporary_id=t.`recorded_by` SET t.`id`=COALESCE(m0.new_id,t.`id`),t.`session_id`=COALESCE(m1.new_id,t.`session_id`),t.`student_id`=COALESCE(m2.new_id,t.`student_id`),t.`recorded_by`=COALESCE(m3.new_id,t.`recorded_by`),t.updated_at=t.updated_at;

UPDATE `student_guardian` t LEFT JOIN migration_v16_id_map m0 ON m0.entity_name='student' AND m0.temporary_id=t.`student_id` LEFT JOIN migration_v16_id_map m1 ON m1.entity_name='guardian' AND m1.temporary_id=t.`guardian_id` SET t.`student_id`=COALESCE(m0.new_id,t.`student_id`),t.`guardian_id`=COALESCE(m1.new_id,t.`guardian_id`);

UPDATE `website_albums` t LEFT JOIN migration_v16_id_map m0 ON m0.entity_name='website_albums' AND m0.temporary_id=t.`id` LEFT JOIN migration_v16_id_map m1 ON m1.entity_name='app_user' AND m1.temporary_id=t.`updated_by` SET t.`id`=COALESCE(m0.new_id,t.`id`),t.`updated_by`=COALESCE(m1.new_id,t.`updated_by`),t.updated_at=t.updated_at;

UPDATE `website_faqs` t LEFT JOIN migration_v16_id_map m0 ON m0.entity_name='website_faqs' AND m0.temporary_id=t.`id` LEFT JOIN migration_v16_id_map m1 ON m1.entity_name='app_user' AND m1.temporary_id=t.`updated_by` SET t.`id`=COALESCE(m0.new_id,t.`id`),t.`updated_by`=COALESCE(m1.new_id,t.`updated_by`),t.updated_at=t.updated_at;

UPDATE `website_media` t LEFT JOIN migration_v16_id_map m0 ON m0.entity_name='website_media' AND m0.temporary_id=t.`id` LEFT JOIN migration_v16_id_map m1 ON m1.entity_name='website_albums' AND m1.temporary_id=t.`album_id` LEFT JOIN migration_v16_id_map m2 ON m2.entity_name='app_user' AND m2.temporary_id=t.`consent_by` LEFT JOIN migration_v16_id_map m3 ON m3.entity_name='app_user' AND m3.temporary_id=t.`created_by` SET t.`id`=COALESCE(m0.new_id,t.`id`),t.`album_id`=COALESCE(m1.new_id,t.`album_id`),t.`consent_by`=COALESCE(m2.new_id,t.`consent_by`),t.`created_by`=COALESCE(m3.new_id,t.`created_by`),t.updated_at=t.updated_at;

UPDATE `website_publication_media` t LEFT JOIN migration_v16_id_map m0 ON m0.entity_name='website_publications' AND m0.temporary_id=t.`publication_id` LEFT JOIN migration_v16_id_map m1 ON m1.entity_name='website_media' AND m1.temporary_id=t.`media_id` SET t.`publication_id`=COALESCE(m0.new_id,t.`publication_id`),t.`media_id`=COALESCE(m1.new_id,t.`media_id`);

UPDATE `website_publications` t LEFT JOIN migration_v16_id_map m0 ON m0.entity_name='website_publications' AND m0.temporary_id=t.`id` LEFT JOIN migration_v16_id_map m1 ON m1.entity_name='app_user' AND m1.temporary_id=t.`published_by` SET t.`id`=COALESCE(m0.new_id,t.`id`),t.`published_by`=COALESCE(m1.new_id,t.`published_by`);

UPDATE `website_section_items` t LEFT JOIN migration_v16_id_map m0 ON m0.entity_name='website_section_items' AND m0.temporary_id=t.`id` LEFT JOIN migration_v16_id_map m1 ON m1.entity_name='app_user' AND m1.temporary_id=t.`updated_by` SET t.`id`=COALESCE(m0.new_id,t.`id`),t.`updated_by`=COALESCE(m1.new_id,t.`updated_by`),t.updated_at=t.updated_at;

UPDATE `website_sections` t LEFT JOIN migration_v16_id_map m0 ON m0.entity_name='website_media' AND m0.temporary_id=t.`image_media_id` LEFT JOIN migration_v16_id_map m1 ON m1.entity_name='app_user' AND m1.temporary_id=t.`updated_by` SET t.`image_media_id`=COALESCE(m0.new_id,t.`image_media_id`),t.`updated_by`=COALESCE(m1.new_id,t.`updated_by`),t.updated_at=t.updated_at;

UPDATE `website_services` t LEFT JOIN migration_v16_id_map m0 ON m0.entity_name='website_services' AND m0.temporary_id=t.`id` LEFT JOIN migration_v16_id_map m1 ON m1.entity_name='website_media' AND m1.temporary_id=t.`image_media_id` LEFT JOIN migration_v16_id_map m2 ON m2.entity_name='app_user' AND m2.temporary_id=t.`updated_by` SET t.`id`=COALESCE(m0.new_id,t.`id`),t.`image_media_id`=COALESCE(m1.new_id,t.`image_media_id`),t.`updated_by`=COALESCE(m2.new_id,t.`updated_by`),t.updated_at=t.updated_at;

UPDATE `website_settings` t LEFT JOIN migration_v16_id_map m0 ON m0.entity_name='website_settings' AND m0.temporary_id=t.`id` LEFT JOIN migration_v16_id_map m1 ON m1.entity_name='website_media' AND m1.temporary_id=t.`logo_media_id` LEFT JOIN migration_v16_id_map m2 ON m2.entity_name='app_user' AND m2.temporary_id=t.`updated_by` SET t.`id`=COALESCE(m0.new_id,t.`id`),t.`logo_media_id`=COALESCE(m1.new_id,t.`logo_media_id`),t.`updated_by`=COALESCE(m2.new_id,t.`updated_by`),t.updated_at=t.updated_at;

UPDATE `website_team_members` t LEFT JOIN migration_v16_id_map m0 ON m0.entity_name='website_team_members' AND m0.temporary_id=t.`id` LEFT JOIN migration_v16_id_map m1 ON m1.entity_name='website_media' AND m1.temporary_id=t.`photo_media_id` LEFT JOIN migration_v16_id_map m2 ON m2.entity_name='app_user' AND m2.temporary_id=t.`updated_by` SET t.`id`=COALESCE(m0.new_id,t.`id`),t.`photo_media_id`=COALESCE(m1.new_id,t.`photo_media_id`),t.`updated_by`=COALESCE(m2.new_id,t.`updated_by`),t.updated_at=t.updated_at;

CALL remap_v16_json();

CALL verify_v16_links();

UPDATE entity_id_sequences SET issued_value=(SELECT COUNT(*) FROM migration_v16_id_map WHERE entity_name='app_user') WHERE entity_name='app_user';

UPDATE entity_id_sequences SET issued_value=(SELECT COUNT(*) FROM migration_v16_id_map WHERE entity_name='audit_event') WHERE entity_name='audit_event';

UPDATE entity_id_sequences SET issued_value=(SELECT COUNT(*) FROM migration_v16_id_map WHERE entity_name='center') WHERE entity_name='center';

UPDATE entity_id_sequences SET issued_value=(SELECT COUNT(*) FROM migration_v16_id_map WHERE entity_name='class_session') WHERE entity_name='class_session';

UPDATE entity_id_sequences SET issued_value=(SELECT COUNT(*) FROM migration_v16_id_map WHERE entity_name='consultation_requests') WHERE entity_name='consultation_requests';

UPDATE entity_id_sequences SET issued_value=(SELECT COUNT(*) FROM migration_v16_id_map WHERE entity_name='document') WHERE entity_name='document';

UPDATE entity_id_sequences SET issued_value=(SELECT COUNT(*) FROM migration_v16_id_map WHERE entity_name='domains') WHERE entity_name='domains';

UPDATE entity_id_sequences SET issued_value=(SELECT COUNT(*) FROM migration_v16_id_map WHERE entity_name='enrollment') WHERE entity_name='enrollment';

UPDATE entity_id_sequences SET issued_value=(SELECT COUNT(*) FROM migration_v16_id_map WHERE entity_name='goal_evaluations') WHERE entity_name='goal_evaluations';

UPDATE entity_id_sequences SET issued_value=(SELECT COUNT(*) FROM migration_v16_id_map WHERE entity_name='goals') WHERE entity_name='goals';

UPDATE entity_id_sequences SET issued_value=(SELECT COUNT(*) FROM migration_v16_id_map WHERE entity_name='guardian') WHERE entity_name='guardian';

UPDATE entity_id_sequences SET issued_value=(SELECT COUNT(*) FROM migration_v16_id_map WHERE entity_name='invoice') WHERE entity_name='invoice';

UPDATE entity_id_sequences SET issued_value=(SELECT COUNT(*) FROM migration_v16_id_map WHERE entity_name='invoice_line') WHERE entity_name='invoice_line';

UPDATE entity_id_sequences SET issued_value=(SELECT COUNT(*) FROM migration_v16_id_map WHERE entity_name='learning_class') WHERE entity_name='learning_class';

UPDATE entity_id_sequences SET issued_value=(SELECT COUNT(*) FROM migration_v16_id_map WHERE entity_name='learning_plans') WHERE entity_name='learning_plans';

UPDATE entity_id_sequences SET issued_value=(SELECT COUNT(*) FROM migration_v16_id_map WHERE entity_name='leave_request') WHERE entity_name='leave_request';

UPDATE entity_id_sequences SET issued_value=(SELECT COUNT(*) FROM migration_v16_id_map WHERE entity_name='notification') WHERE entity_name='notification';

UPDATE entity_id_sequences SET issued_value=(SELECT COUNT(*) FROM migration_v16_id_map WHERE entity_name='payment') WHERE entity_name='payment';

UPDATE entity_id_sequences SET issued_value=(SELECT COUNT(*) FROM migration_v16_id_map WHERE entity_name='plan_evaluations') WHERE entity_name='plan_evaluations';

UPDATE entity_id_sequences SET issued_value=(SELECT COUNT(*) FROM migration_v16_id_map WHERE entity_name='plan_goals') WHERE entity_name='plan_goals';

UPDATE entity_id_sequences SET issued_value=(SELECT COUNT(*) FROM migration_v16_id_map WHERE entity_name='program_level_domains') WHERE entity_name='program_level_domains';

UPDATE entity_id_sequences SET issued_value=(SELECT COUNT(*) FROM migration_v16_id_map WHERE entity_name='program_levels') WHERE entity_name='program_levels';

UPDATE entity_id_sequences SET issued_value=(SELECT COUNT(*) FROM migration_v16_id_map WHERE entity_name='programs') WHERE entity_name='programs';

UPDATE entity_id_sequences SET issued_value=(SELECT COUNT(*) FROM migration_v16_id_map WHERE entity_name='specific_goals') WHERE entity_name='specific_goals';

UPDATE entity_id_sequences SET issued_value=(SELECT COUNT(*) FROM migration_v16_id_map WHERE entity_name='staff') WHERE entity_name='staff';

UPDATE entity_id_sequences SET issued_value=(SELECT COUNT(*) FROM migration_v16_id_map WHERE entity_name='staff_checkin') WHERE entity_name='staff_checkin';

UPDATE entity_id_sequences SET issued_value=(SELECT COUNT(*) FROM migration_v16_id_map WHERE entity_name='staff_salary') WHERE entity_name='staff_salary';

UPDATE entity_id_sequences SET issued_value=(SELECT COUNT(*) FROM migration_v16_id_map WHERE entity_name='student') WHERE entity_name='student';

UPDATE entity_id_sequences SET issued_value=(SELECT COUNT(*) FROM migration_v16_id_map WHERE entity_name='student_attendance') WHERE entity_name='student_attendance';

UPDATE entity_id_sequences SET issued_value=(SELECT COUNT(*) FROM migration_v16_id_map WHERE entity_name='website_albums') WHERE entity_name='website_albums';

UPDATE entity_id_sequences SET issued_value=(SELECT COUNT(*) FROM migration_v16_id_map WHERE entity_name='website_faqs') WHERE entity_name='website_faqs';

UPDATE entity_id_sequences SET issued_value=(SELECT COUNT(*) FROM migration_v16_id_map WHERE entity_name='website_media') WHERE entity_name='website_media';

UPDATE entity_id_sequences SET issued_value=(SELECT COUNT(*) FROM migration_v16_id_map WHERE entity_name='website_publications') WHERE entity_name='website_publications';

UPDATE entity_id_sequences SET issued_value=(SELECT COUNT(*) FROM migration_v16_id_map WHERE entity_name='website_section_items') WHERE entity_name='website_section_items';

UPDATE entity_id_sequences SET issued_value=(SELECT COUNT(*) FROM migration_v16_id_map WHERE entity_name='website_services') WHERE entity_name='website_services';

UPDATE entity_id_sequences SET issued_value=(SELECT COUNT(*) FROM migration_v16_id_map WHERE entity_name='website_settings') WHERE entity_name='website_settings';

UPDATE entity_id_sequences SET issued_value=(SELECT COUNT(*) FROM migration_v16_id_map WHERE entity_name='website_team_members') WHERE entity_name='website_team_members';

UPDATE entity_id_sequences SET issued_value=(SELECT COUNT(*) FROM migration_v16_id_map WHERE entity_name='strategies') WHERE entity_name='strategies';

UPDATE entity_id_sequences SET issued_value=(SELECT COUNT(*) FROM migration_v16_id_map WHERE entity_name='plan_goal_strategies') WHERE entity_name='plan_goal_strategies';

INSERT INTO student_code_sequence(id,next_value) SELECT 1,COALESCE(MAX(CAST(SUBSTRING(code,3) AS UNSIGNED)),0)+1 FROM student WHERE code REGEXP '^HS[0-9]+$' ON DUPLICATE KEY UPDATE next_value=GREATEST(next_value,VALUES(next_value));

INSERT INTO profile_code_sequences(entity_name,next_value) SELECT 'staff',COALESCE(MAX(CAST(SUBSTRING(code,3) AS UNSIGNED)),0)+1 FROM `staff` WHERE code REGEXP '^NV[0-9]+$' ON DUPLICATE KEY UPDATE next_value=GREATEST(next_value,VALUES(next_value));

INSERT INTO profile_code_sequences(entity_name,next_value) SELECT 'guardian',COALESCE(MAX(CAST(SUBSTRING(code,3) AS UNSIGNED)),0)+1 FROM `guardian` WHERE code REGEXP '^PH[0-9]+$' ON DUPLICATE KEY UPDATE next_value=GREATEST(next_value,VALUES(next_value));

COMMIT;

SET SESSION FOREIGN_KEY_CHECKS=1;

DROP PROCEDURE verify_v16_links;

DROP PROCEDURE remap_v16_json;

ALTER TABLE center ADD CONSTRAINT ck_center_singleton CHECK(id='TT001');

ALTER TABLE website_settings ADD CONSTRAINT ck_website_settings_singleton CHECK(id='WCFG001');

ALTER TABLE strategies ADD CONSTRAINT ck_strategy_id CHECK(REGEXP_LIKE(id,'^CL[0-9]{3,6}$','c'));

ALTER TABLE plan_goal_strategies ADD CONSTRAINT ck_plan_goal_strategy_id CHECK(REGEXP_LIKE(id,'^MTCL[0-9]{3,6}$','c'));

-- Restore all existing business rules after identity conversion.

DELIMITER $$

CREATE TRIGGER `evaluation_attempt_insert` BEFORE INSERT ON `goal_evaluations` FOR EACH ROW
BEGIN
    DECLARE v_next INT;
    SELECT COALESCE(MAX(attempt_no),0)+1 INTO v_next FROM goal_evaluations WHERE plan_goal_id=NEW.plan_goal_id;
    IF NEW.attempt_no<>v_next THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Evaluation attempts must be consecutive';
    END IF;
END$$

CREATE TRIGGER `evaluation_identity_update` BEFORE UPDATE ON `goal_evaluations` FOR EACH ROW
BEGIN
    IF NEW.id<>OLD.id OR NEW.plan_goal_id<>OLD.plan_goal_id OR NEW.attempt_no<>OLD.attempt_no OR NEW.created_at<>OLD.created_at THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Evaluation identity is immutable';
    END IF;
END$$

CREATE TRIGGER `evaluation_status_insert` AFTER INSERT ON `goal_evaluations` FOR EACH ROW
BEGIN
    UPDATE plan_goals SET status=NEW.result_status WHERE id=NEW.plan_goal_id;
END$$

CREATE TRIGGER `evaluation_status_update` AFTER UPDATE ON `goal_evaluations` FOR EACH ROW
BEGIN
    IF NEW.attempt_no=(SELECT MAX(attempt_no) FROM goal_evaluations WHERE plan_goal_id=NEW.plan_goal_id) THEN
        UPDATE plan_goals SET status=NEW.result_status WHERE id=NEW.plan_goal_id;
    END IF;
END$$

CREATE TRIGGER `learning_plan_student_immutable` BEFORE UPDATE ON `learning_plans` FOR EACH ROW
BEGIN
    IF NOT (NEW.student_id <=> OLD.student_id) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='A learning plan cannot be reassigned to another student';
    END IF;
END$$

CREATE TRIGGER `plan_goals_snapshot_immutable` BEFORE UPDATE ON `plan_goals` FOR EACH ROW
BEGIN
    DECLARE v_status VARCHAR(20);
    IF NOT (NEW.id <=> OLD.id) OR NOT (NEW.plan_id <=> OLD.plan_id)
       OR NOT (NEW.goal_id <=> OLD.goal_id) OR NOT (NEW.specific_goal_id <=> OLD.specific_goal_id)
       OR NOT (NEW.goal_snapshot <=> OLD.goal_snapshot) OR NOT (NEW.criteria_snapshot <=> OLD.criteria_snapshot)
       OR NOT (NEW.assigned_at <=> OLD.assigned_at) OR NOT (NEW.assigned_by <=> OLD.assigned_by)
       OR NOT (NEW.supersedes_id <=> OLD.supersedes_id) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Assigned goal identity and snapshots are immutable';
    END IF;
    SET v_status=COALESCE((SELECT result_status FROM goal_evaluations WHERE plan_goal_id=OLD.id ORDER BY attempt_no DESC LIMIT 1),'NOT_STARTED');
    IF NEW.status<>v_status THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Plan goal status is determined by its latest evaluation';
    END IF;
END$$

CREATE TRIGGER `plan_goals_snapshot_insert` BEFORE INSERT ON `plan_goals` FOR EACH ROW
BEGIN
    DECLARE v_goal JSON DEFAULT NULL;
    IF NEW.supersedes_id IS NOT NULL THEN
        IF NOT EXISTS (SELECT 1 FROM plan_goals old_goal WHERE old_goal.id=NEW.supersedes_id AND old_goal.plan_id=NEW.plan_id)
           OR EXISTS (SELECT 1 FROM plan_goals next_goal WHERE next_goal.supersedes_id=NEW.supersedes_id) THEN
            SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Replacement must follow one current goal in the same plan';
        END IF;
    END IF;
    IF NEW.specific_goal_id IS NULL THEN
        SELECT JSON_OBJECT('schema_version',5,'target_type','GOAL','goal_id',g.id,
            'code',g.code,'name',g.name,'description',g.description,
            'level_domain_id',ld.id,'domain_id',d.id,'domain_code',d.code,'domain_name',d.name,
            'program_level_id',pl.id,'level_number',pl.level_number,'level_name',pl.name,
            'age_min_months',pl.age_min_months,'age_max_months',pl.age_max_months,
            'program_id',p.id,'program_code',p.code,'program_name',p.name)
        INTO v_goal FROM goals g JOIN program_level_domains ld ON ld.id=g.level_domain_id
        JOIN domains d ON d.id=ld.domain_id JOIN program_levels pl ON pl.id=ld.program_level_id
        JOIN programs p ON p.id=pl.program_id
        WHERE g.id=NEW.goal_id AND g.is_active=1 AND d.is_active=1 AND p.is_active=1;
    ELSE
        SELECT JSON_OBJECT('schema_version',5,'target_type','SPECIFIC_GOAL','specific_goal_id',sg.id,
            'code',sg.code,'name',sg.description,'description',sg.description,'expected_behavior',sg.expected_behavior,
            'goal_id',g.id,'goal_code',g.code,'goal_name',g.name,'goal_description',g.description,
            'level_domain_id',ld.id,'domain_id',d.id,'domain_code',d.code,'domain_name',d.name,
            'program_level_id',pl.id,'level_number',pl.level_number,'level_name',pl.name,
            'age_min_months',COALESCE(sg.age_min_months,pl.age_min_months),
            'age_max_months',COALESCE(sg.age_max_months,pl.age_max_months),
            'program_id',p.id,'program_code',p.code,'program_name',p.name)
        INTO v_goal FROM specific_goals sg JOIN goals g ON g.id=sg.goal_id
        JOIN program_level_domains ld ON ld.id=g.level_domain_id JOIN domains d ON d.id=ld.domain_id
        JOIN program_levels pl ON pl.id=ld.program_level_id JOIN programs p ON p.id=pl.program_id
        WHERE sg.id=NEW.specific_goal_id AND sg.goal_id=NEW.goal_id AND sg.is_active=1
          AND g.is_active=1 AND d.is_active=1 AND p.is_active=1 AND p.code NOT IN ('ESDM','DENVER');
    END IF;
    IF v_goal IS NULL THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Invalid or inactive catalog target';
    END IF;
    SET NEW.goal_snapshot=v_goal;
    SET NEW.criteria_snapshot=JSON_OBJECT();
    SET NEW.status='NOT_STARTED';
END$$

CREATE TRIGGER `trg_plan_evaluation_before_delete` BEFORE DELETE ON `plan_evaluations` FOR EACH ROW
BEGIN
    IF OLD.sent_to_parent = 1 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Published plan evaluation cannot be deleted';
    END IF;
END$$

CREATE TRIGGER `trg_plan_evaluation_before_update` BEFORE UPDATE ON `plan_evaluations` FOR EACH ROW
BEGIN
    IF OLD.sent_to_parent = 1 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Published plan evaluation is immutable';
    END IF;
    IF NEW.id <> OLD.id OR NEW.learning_plan_id <> OLD.learning_plan_id
       OR NEW.evaluated_by <> OLD.evaluated_by OR NEW.evaluated_at <> OLD.evaluated_at THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Plan evaluation identity is immutable';
    END IF;
END$$

DELIMITER ;

-- Completed: inspect migration_v16_id_map and deploy the updated application together.
