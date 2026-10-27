-- MySQL 8.4 migration for a shared Denver/VB goal bank.
-- Run during a maintenance window. It intentionally stops if legacy catalog rows exist:
-- their old domain -> skill_level -> goal hierarchy cannot be mapped to program levels
-- without a reviewed source mapping. Student plans and progress are never deleted.
SET NAMES utf8mb4;
SET time_zone = '+00:00';

DELIMITER $$
CREATE PROCEDURE assert_goal_bank_empty_for_v5()
BEGIN
    IF EXISTS (SELECT 1 FROM programs LIMIT 1)
       OR EXISTS (SELECT 1 FROM domains LIMIT 1)
       OR EXISTS (SELECT 1 FROM skill_levels LIMIT 1)
       OR EXISTS (SELECT 1 FROM goals LIMIT 1)
       OR EXISTS (SELECT 1 FROM specific_goals LIMIT 1)
       OR EXISTS (SELECT 1 FROM goal_criteria LIMIT 1)
       OR EXISTS (SELECT 1 FROM plan_goals LIMIT 1)
       OR EXISTS (SELECT 1 FROM progress_records LIMIT 1) THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT='V5 requires empty legacy goal catalog and assignments; review and migrate existing data first';
    END IF;
END$$
DELIMITER ;
CALL assert_goal_bank_empty_for_v5();
DROP PROCEDURE assert_goal_bank_empty_for_v5;

DROP TRIGGER plan_goals_snapshot_insert;
DROP TRIGGER plan_goals_snapshot_immutable;

ALTER TABLE programs
    DROP CHECK ck_program_age_range,
    DROP COLUMN age_min_months,
    DROP COLUMN age_max_months,
    MODIFY COLUMN name VARCHAR(255) NOT NULL;

CREATE TABLE program_levels (
    id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin PRIMARY KEY,
    program_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    level_number INT NOT NULL,
    name VARCHAR(100) NOT NULL,
    age_min_months INT NOT NULL,
    age_max_months INT NOT NULL,
    display_order INT NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    UNIQUE KEY uq_program_level_number (program_id,level_number),
    KEY ix_program_level_order (program_id,display_order,id),
    CONSTRAINT fk_program_level_program FOREIGN KEY (program_id) REFERENCES programs(id),
    CONSTRAINT ck_program_level_age CHECK (age_min_months>=0 AND age_max_months>=age_min_months),
    CONSTRAINT ck_program_level_order CHECK (display_order>=0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

ALTER TABLE goals DROP FOREIGN KEY goals_ibfk_1;
ALTER TABLE domains DROP FOREIGN KEY fk_domains_program;
ALTER TABLE domains DROP INDEX ix_domains_program;
ALTER TABLE domains DROP INDEX code;
ALTER TABLE domains
    DROP COLUMN program_id,
    ADD COLUMN program_level_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL AFTER id,
    ADD COLUMN display_order INT NOT NULL DEFAULT 0 AFTER name,
    MODIFY COLUMN name VARCHAR(150) NOT NULL,
    ADD UNIQUE KEY uq_domain_level_code (program_level_id,code),
    ADD KEY ix_domain_level_order (program_level_id,display_order,id),
    ADD CONSTRAINT fk_domain_program_level FOREIGN KEY (program_level_id) REFERENCES program_levels(id),
    ADD CONSTRAINT ck_domain_order CHECK (display_order>=0);

ALTER TABLE goals DROP CHECK ck_goals_ages;
ALTER TABLE goals DROP INDEX code;
ALTER TABLE goals
    DROP COLUMN skill_level_id,
    DROP COLUMN age_min_months,
    DROP COLUMN age_max_months,
    ADD COLUMN domain_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL AFTER id,
    MODIFY COLUMN code VARCHAR(50) NOT NULL,
    MODIFY COLUMN name TEXT NOT NULL,
    ADD COLUMN description TEXT NULL AFTER name,
    ADD COLUMN display_order INT NOT NULL DEFAULT 0 AFTER description,
    ADD UNIQUE KEY uq_goal_domain_code (domain_id,code),
    ADD KEY ix_goal_domain_order (domain_id,display_order,id),
    ADD CONSTRAINT fk_goal_domain FOREIGN KEY (domain_id) REFERENCES domains(id),
    ADD CONSTRAINT ck_goal_order CHECK (display_order>=0);

DROP TABLE skill_levels;

ALTER TABLE specific_goals DROP INDEX code;
ALTER TABLE specific_goals
    MODIFY COLUMN code VARCHAR(50) NOT NULL,
    ADD COLUMN age_min_months INT NULL AFTER description,
    ADD COLUMN age_max_months INT NULL AFTER age_min_months,
    ADD UNIQUE KEY uq_specific_goal_code (goal_id,code),
    ADD CONSTRAINT ck_specific_age CHECK (
        (age_min_months IS NULL OR age_min_months>=0)
        AND (age_max_months IS NULL OR age_max_months>=0)
        AND (age_min_months IS NULL OR age_max_months IS NULL OR age_min_months<=age_max_months));

ALTER TABLE plan_goals
    MODIFY COLUMN specific_goal_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NULL,
    ADD COLUMN goal_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NULL AFTER plan_id,
    ADD COLUMN initial_target_key CHAR(38) CHARACTER SET ascii COLLATE ascii_bin
        GENERATED ALWAYS AS (
            CASE WHEN supersedes_id IS NULL
                THEN CONCAT(IF(goal_id IS NULL,'S:','G:'),COALESCE(goal_id,specific_goal_id))
                ELSE NULL END) STORED,
    ADD KEY ix_plan_goal_direct (goal_id),
    ADD UNIQUE KEY uq_plan_initial_target (plan_id,initial_target_key),
    ADD CONSTRAINT fk_plan_goal_direct FOREIGN KEY (goal_id) REFERENCES goals(id),
    ADD CONSTRAINT ck_plan_goal_one_target CHECK ((goal_id IS NULL) <> (specific_goal_id IS NULL));

DELIMITER $$
CREATE TRIGGER plan_goals_snapshot_insert
BEFORE INSERT ON plan_goals FOR EACH ROW
BEGIN
    DECLARE v_goal JSON DEFAULT NULL;
    DECLARE v_criteria JSON DEFAULT NULL;

    IF NEW.goal_id IS NOT NULL THEN
        SELECT JSON_OBJECT(
            'schema_version',2,'target_type','GOAL','goal_id',g.id,
            'code',g.code,'name',g.name,'description',g.description,
            'domain_id',d.id,'domain_code',d.code,'domain_name',d.name,
            'program_level_id',pl.id,'level_number',pl.level_number,
            'age_min_months',pl.age_min_months,'age_max_months',pl.age_max_months,
            'program_id',p.id,'program_code',p.code,'program_name',p.name)
        INTO v_goal
        FROM goals g JOIN domains d ON d.id=g.domain_id
        JOIN program_levels pl ON pl.id=d.program_level_id
        JOIN programs p ON p.id=pl.program_id
        WHERE g.id=NEW.goal_id AND g.is_active=1 AND d.is_active=1 AND p.is_active=1;
        SET v_criteria=JSON_OBJECT('schema_version',2,'criteria_defined',0);
    ELSE
        SELECT JSON_OBJECT(
            'schema_version',2,'target_type','SPECIFIC_GOAL','specific_goal_id',sg.id,
            'code',sg.code,'name',sg.description,'description',sg.description,
            'expected_behavior',sg.expected_behavior,
            'goal_id',g.id,'goal_code',g.code,'goal_name',g.name,
            'goal_description',g.description,
            'domain_id',d.id,'domain_code',d.code,'domain_name',d.name,
            'program_level_id',pl.id,'level_number',pl.level_number,
            'age_min_months',COALESCE(sg.age_min_months,pl.age_min_months),
            'age_max_months',COALESCE(sg.age_max_months,pl.age_max_months),
            'program_id',p.id,'program_code',p.code,'program_name',p.name),
            JSON_OBJECT('schema_version',2,'criteria_defined',IF(gc.id IS NULL,0,1),
            'measurement_type',gc.measurement_type,'target_count',gc.target_count,
            'observation_minutes',gc.observation_minutes,
            'successful_opportunities',gc.successful_opportunities,
            'total_opportunities',gc.total_opportunities,
            'target_percentage',gc.target_percentage,'support_level',gc.support_level,
            'required_periods',gc.required_periods,'period_unit',gc.period_unit,
            'must_be_consecutive',gc.must_be_consecutive,'adult_count',gc.adult_count,
            'setting_count',gc.setting_count)
        INTO v_goal,v_criteria
        FROM specific_goals sg JOIN goals g ON g.id=sg.goal_id
        JOIN domains d ON d.id=g.domain_id
        JOIN program_levels pl ON pl.id=d.program_level_id
        JOIN programs p ON p.id=pl.program_id
        LEFT JOIN goal_criteria gc ON gc.specific_goal_id=sg.id
        WHERE sg.id=NEW.specific_goal_id AND sg.is_active=1 AND g.is_active=1
          AND d.is_active=1 AND p.is_active=1;
    END IF;

    IF v_goal IS NULL THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Goal does not exist or its hierarchy is inactive';
    END IF;
    IF NEW.supersedes_id IS NOT NULL AND NOT EXISTS (
        SELECT 1 FROM plan_goals old_goal WHERE old_goal.id=NEW.supersedes_id
          AND old_goal.plan_id=NEW.plan_id
          AND (old_goal.goal_id <=> NEW.goal_id)
          AND (old_goal.specific_goal_id <=> NEW.specific_goal_id)
          AND old_goal.status='PAUSED'
    ) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Replacement requires a paused predecessor for the same target and plan';
    END IF;
    SET NEW.goal_snapshot=v_goal;
    SET NEW.criteria_snapshot=v_criteria;
END$$

CREATE TRIGGER plan_goals_snapshot_immutable
BEFORE UPDATE ON plan_goals FOR EACH ROW
BEGIN
    IF NOT (NEW.id <=> OLD.id) OR NOT (NEW.plan_id <=> OLD.plan_id)
       OR NOT (NEW.goal_id <=> OLD.goal_id)
       OR NOT (NEW.specific_goal_id <=> OLD.specific_goal_id)
       OR NOT (NEW.goal_snapshot <=> OLD.goal_snapshot)
       OR NOT (NEW.criteria_snapshot <=> OLD.criteria_snapshot)
       OR NOT (NEW.assigned_at <=> OLD.assigned_at)
       OR NOT (NEW.assigned_by <=> OLD.assigned_by)
       OR NOT (NEW.supersedes_id <=> OLD.supersedes_id) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Assigned goal is immutable; create a replacement row to retain history';
    END IF;
    IF NEW.status<>'PAUSED' AND EXISTS (SELECT 1 FROM plan_goals WHERE supersedes_id=OLD.id) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Superseded goal must remain paused';
    END IF;
END$$
DELIMITER ;
