-- Apply once after V10, with application writes stopped and a full backup.
-- Catalog rows are never modified. Legacy selections are archived, not assigned to plans.
SET NAMES utf8mb4;
SET time_zone = '+00:00';
DELIMITER $$
CREATE PROCEDURE preflight_v11()
BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.key_column_usage
        WHERE constraint_schema=DATABASE()
          AND referenced_table_name IN ('student_goal_bank','goal_criteria','progress_records')) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V11: unexpected inbound foreign keys; review before migration';
    END IF;
END$$
CALL preflight_v11()$$
DROP PROCEDURE preflight_v11$$
DELIMITER ;

CREATE TABLE migration_v11_archive (
    source_table VARCHAR(32) NOT NULL,
    source_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    payload JSON NOT NULL,
    archived_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY(source_table,source_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE goal_evaluations (
    id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin PRIMARY KEY,
    plan_goal_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    attempt_no INT NOT NULL,
    achievement_percent DECIMAL(5,2),
    result_status VARCHAR(20) CHARACTER SET ascii COLLATE ascii_bin
        GENERATED ALWAYS AS (CASE WHEN achievement_percent IS NULL THEN 'NOT_STARTED'
            WHEN achievement_percent < 80 THEN 'NOT_ACHIEVED' ELSE 'ACHIEVED' END) STORED,
    restart_decision VARCHAR(8) CHARACTER SET ascii COLLATE ascii_bin,
    evaluation_notes TEXT,
    evaluated_at DATETIME(6) COMMENT 'UTC; NULL for a not-started evaluation',
    evaluated_by CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    UNIQUE KEY uq_goal_evaluation_attempt(plan_goal_id,attempt_no),
    FOREIGN KEY(plan_goal_id) REFERENCES plan_goals(id),
    FOREIGN KEY(evaluated_by) REFERENCES app_user(id),
    CONSTRAINT ck_evaluation_attempt CHECK(attempt_no>0),
    CONSTRAINT ck_evaluation_percent CHECK(achievement_percent IS NULL OR achievement_percent BETWEEN 0 AND 100),
    CONSTRAINT ck_evaluation_restart CHECK(restart_decision IS NULL OR restart_decision IN ('RETRY','NO_RETRY')),
    CONSTRAINT ck_evaluation_time CHECK(achievement_percent IS NULL OR evaluated_at IS NOT NULL)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

START TRANSACTION;
INSERT INTO migration_v11_archive(source_table,source_id,payload)
SELECT 'student_goal_bank',id,JSON_OBJECT('id',id,'student_id',student_id,'goal_id',goal_id,
    'specific_goal_id',specific_goal_id,'goal_snapshot',goal_snapshot,'selected_by',selected_by,'created_at',created_at)
FROM student_goal_bank;
INSERT INTO migration_v11_archive(source_table,source_id,payload)
SELECT 'goal_criteria',id,JSON_OBJECT('specific_goal_id',specific_goal_id,'measurement_type',measurement_type,
    'target_count',target_count,'observation_minutes',observation_minutes,'successful_opportunities',successful_opportunities,
    'total_opportunities',total_opportunities,'target_percentage',target_percentage,'support_level',support_level,
    'required_periods',required_periods,'period_unit',period_unit,'must_be_consecutive',must_be_consecutive,
    'adult_count',adult_count,'setting_count',setting_count,'created_at',created_at,'updated_at',updated_at)
FROM goal_criteria;
INSERT INTO migration_v11_archive(source_table,source_id,payload)
SELECT 'progress_records',id,JSON_OBJECT('plan_goal_id',plan_goal_id,'observed_at',observed_at,'session_key',session_key,
    'success_count',success_count,'opportunity_count',opportunity_count,'observation_minutes',observation_minutes,
    'support_level',support_level,'partner_reference',partner_reference,'setting',setting,'notes',notes,
    'recorded_by',recorded_by,'created_at',created_at,'updated_at',updated_at,
    'conversion',IF(success_count IS NOT NULL AND opportunity_count>0,'PERCENTAGE','NO_PERCENTAGE_AVAILABLE'))
FROM progress_records;
INSERT INTO migration_v11_archive(source_table,source_id,payload)
SELECT 'plan_goals',id,JSON_OBJECT('status',status,'supersedes_id',supersedes_id,'criteria_snapshot',criteria_snapshot)
FROM plan_goals;
INSERT INTO goal_evaluations(id,plan_goal_id,attempt_no,achievement_percent,evaluation_notes,evaluated_at,evaluated_by,created_at,updated_at)
SELECT id,plan_goal_id,ROW_NUMBER() OVER(PARTITION BY plan_goal_id ORDER BY observed_at,created_at,id),
    CASE WHEN success_count IS NOT NULL AND opportunity_count>0
         THEN ROUND(100.0*success_count/opportunity_count,2) ELSE NULL END,
    notes,observed_at,recorded_by,created_at,updated_at FROM progress_records;
COMMIT;

DELIMITER $$
CREATE PROCEDURE verify_v11_copy()
BEGIN
    IF (SELECT COUNT(*) FROM progress_records) <> (SELECT COUNT(*) FROM goal_evaluations)
       OR (SELECT COUNT(*) FROM student_goal_bank) <> (SELECT COUNT(*) FROM migration_v11_archive WHERE source_table='student_goal_bank')
       OR (SELECT COUNT(*) FROM goal_criteria) <> (SELECT COUNT(*) FROM migration_v11_archive WHERE source_table='goal_criteria') THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V11: copy verification failed; old tables retained';
    END IF;
END$$
CALL verify_v11_copy()$$
DROP PROCEDURE verify_v11_copy$$
DELIMITER ;

DROP TRIGGER plan_goals_snapshot_insert;
DROP TRIGGER plan_goals_snapshot_immutable;
ALTER TABLE plan_goals DROP CHECK ck_plan_goal_status;
UPDATE plan_goals pg SET status=COALESCE((SELECT e.result_status FROM goal_evaluations e
    WHERE e.plan_goal_id=pg.id ORDER BY e.attempt_no DESC LIMIT 1),'NOT_STARTED'),updated_at=updated_at;
ALTER TABLE plan_goals ADD CONSTRAINT ck_plan_goal_status CHECK(status IN ('NOT_STARTED','NOT_ACHIEVED','ACHIEVED'));

DELIMITER $$
CREATE TRIGGER plan_goals_snapshot_insert BEFORE INSERT ON plan_goals FOR EACH ROW
BEGIN
    DECLARE v_goal JSON DEFAULT NULL;
    IF NEW.supersedes_id IS NOT NULL THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Use a new evaluation attempt instead of replacing a plan goal';
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
CREATE TRIGGER plan_goals_snapshot_immutable BEFORE UPDATE ON plan_goals FOR EACH ROW
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
CREATE TRIGGER evaluation_attempt_insert BEFORE INSERT ON goal_evaluations FOR EACH ROW
BEGIN
    DECLARE v_next INT;
    SELECT COALESCE(MAX(attempt_no),0)+1 INTO v_next FROM goal_evaluations WHERE plan_goal_id=NEW.plan_goal_id;
    IF NEW.attempt_no<>v_next THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Evaluation attempts must be consecutive';
    END IF;
END$$
CREATE TRIGGER evaluation_identity_update BEFORE UPDATE ON goal_evaluations FOR EACH ROW
BEGIN
    IF NEW.id<>OLD.id OR NEW.plan_goal_id<>OLD.plan_goal_id OR NEW.attempt_no<>OLD.attempt_no OR NEW.created_at<>OLD.created_at THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Evaluation identity is immutable';
    END IF;
END$$
CREATE TRIGGER evaluation_status_insert AFTER INSERT ON goal_evaluations FOR EACH ROW
BEGIN
    UPDATE plan_goals SET status=NEW.result_status WHERE id=NEW.plan_goal_id;
END$$
CREATE TRIGGER evaluation_status_update AFTER UPDATE ON goal_evaluations FOR EACH ROW
BEGIN
    IF NEW.attempt_no=(SELECT MAX(attempt_no) FROM goal_evaluations WHERE plan_goal_id=NEW.plan_goal_id) THEN
        UPDATE plan_goals SET status=NEW.result_status WHERE id=NEW.plan_goal_id;
    END IF;
END$$
DELIMITER ;

DROP TABLE student_goal_bank;
DROP TABLE progress_records;
DROP TABLE goal_criteria;
SELECT source_table,COUNT(*) archived_rows FROM migration_v11_archive GROUP BY source_table;
SELECT COUNT(*) evaluations_without_convertible_percentage FROM migration_v11_archive
 WHERE source_table='progress_records' AND payload->>'$.conversion'='NO_PERCENTAGE_AVAILABLE';
