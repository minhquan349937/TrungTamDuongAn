-- V8: EMPTY development catalog only. Run the separate authorized reset first.
-- Historical migrations remain unchanged. DDL commits implicitly.
SET NAMES utf8mb4;
SET SESSION lock_wait_timeout=5;
DELIMITER $$
CREATE PROCEDURE assert_empty_catalog_v8()
BEGIN
 IF DATABASE()<>'duong_an_v11' THEN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V8 requires duong_an_v11';
 END IF;
 IF EXISTS(SELECT 1 FROM programs) OR EXISTS(SELECT 1 FROM plan_goals)
    OR EXISTS(SELECT 1 FROM student_goal_bank) THEN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V8 requires an explicitly reset empty catalog';
 END IF;
END$$
DELIMITER ;
CALL assert_empty_catalog_v8();
DROP PROCEDURE assert_empty_catalog_v8;
DROP TRIGGER plan_goals_snapshot_insert;
ALTER TABLE goals DROP FOREIGN KEY fk_goal_domain;
ALTER TABLE domains DROP FOREIGN KEY fk_domain_program_level,
 DROP INDEX uq_domain_level_code, DROP INDEX ix_domain_level_order,
 CHANGE program_level_id program_id VARCHAR(8) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 ADD UNIQUE KEY uq_domain_program_code(program_id,code),
 ADD UNIQUE KEY uq_domain_program_pair(id,program_id),
 ADD CONSTRAINT fk_domain_program FOREIGN KEY(program_id) REFERENCES programs(id);
ALTER TABLE program_levels ADD UNIQUE KEY uq_level_program_pair(id,program_id);
-- program_id is an integrity discriminator, enforcing same-program links even for direct SQL.
CREATE TABLE program_level_domains (
 id VARCHAR(8) CHARACTER SET ascii COLLATE ascii_bin PRIMARY KEY,
 program_id VARCHAR(8) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 program_level_id VARCHAR(8) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 domain_id VARCHAR(8) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 display_order INT NOT NULL DEFAULT 0,
 UNIQUE KEY uq_level_domain(program_level_id,domain_id),
 KEY ix_level_domain_order(program_level_id,display_order,id),
 CONSTRAINT fk_ld_level_program FOREIGN KEY(program_level_id,program_id) REFERENCES program_levels(id,program_id),
 CONSTRAINT fk_ld_domain_program FOREIGN KEY(domain_id,program_id) REFERENCES domains(id,program_id),
 CONSTRAINT ck_ld_order CHECK(display_order>=0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
ALTER TABLE goals DROP INDEX uq_goal_domain_code, DROP INDEX ix_goal_domain_order,
 CHANGE domain_id level_domain_id VARCHAR(8) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 ADD UNIQUE KEY uq_goal_level_domain_code(level_domain_id,code),
 ADD KEY ix_goal_level_domain_order(level_domain_id,display_order,id),
 ADD CONSTRAINT fk_goal_level_domain FOREIGN KEY(level_domain_id) REFERENCES program_level_domains(id);
INSERT INTO catalog_id_sequences(entity_name,next_value) VALUES('program_level_domains',1);
DELIMITER $$
CREATE TRIGGER plan_goals_snapshot_insert
BEFORE INSERT ON plan_goals FOR EACH ROW
BEGIN
    DECLARE v_goal JSON DEFAULT NULL;
    DECLARE v_criteria JSON DEFAULT NULL;
    IF NEW.specific_goal_id IS NULL THEN
        SELECT JSON_OBJECT('schema_version',4,'target_type','GOAL','goal_id',g.id,
            'code',g.code,'name',g.name,'description',g.description,
            'level_domain_id',ld.id,'domain_id',d.id,'domain_code',d.code,'domain_name',d.name,
            'program_level_id',pl.id,'level_number',pl.level_number,
            'age_min_months',pl.age_min_months,'age_max_months',pl.age_max_months,
            'program_id',p.id,'program_code',p.code,'program_name',p.name)
        INTO v_goal FROM goals g JOIN program_level_domains ld ON ld.id=g.level_domain_id JOIN domains d ON d.id=ld.domain_id
        JOIN program_levels pl ON pl.id=ld.program_level_id JOIN programs p ON p.id=pl.program_id
        WHERE g.id=NEW.goal_id AND g.is_active=1 AND d.is_active=1 AND p.is_active=1;
        SET v_criteria=JSON_OBJECT('schema_version',4,'criteria_defined',0);
    ELSE
        SELECT JSON_OBJECT('schema_version',4,'target_type','SPECIFIC_GOAL','specific_goal_id',sg.id,
            'code',sg.code,'name',sg.description,'description',sg.description,'expected_behavior',sg.expected_behavior,
            'goal_id',g.id,'goal_code',g.code,'goal_name',g.name,'goal_description',g.description,
            'level_domain_id',ld.id,'domain_id',d.id,'domain_code',d.code,'domain_name',d.name,
            'program_level_id',pl.id,'level_number',pl.level_number,
            'age_min_months',COALESCE(sg.age_min_months,pl.age_min_months),
            'age_max_months',COALESCE(sg.age_max_months,pl.age_max_months),
            'program_id',p.id,'program_code',p.code,'program_name',p.name),
            JSON_OBJECT('schema_version',4,'criteria_defined',IF(gc.id IS NULL,0,1),
            'measurement_type',gc.measurement_type,'target_count',gc.target_count,
            'observation_minutes',gc.observation_minutes,'successful_opportunities',gc.successful_opportunities,
            'total_opportunities',gc.total_opportunities,'target_percentage',gc.target_percentage,
            'support_level',gc.support_level,'required_periods',gc.required_periods,'period_unit',gc.period_unit,
            'must_be_consecutive',gc.must_be_consecutive,'adult_count',gc.adult_count,'setting_count',gc.setting_count)
        INTO v_goal,v_criteria FROM specific_goals sg JOIN goals g ON g.id=sg.goal_id
        JOIN program_level_domains ld ON ld.id=g.level_domain_id JOIN domains d ON d.id=ld.domain_id JOIN program_levels pl ON pl.id=ld.program_level_id
        JOIN programs p ON p.id=pl.program_id LEFT JOIN goal_criteria gc ON gc.specific_goal_id=sg.id
        WHERE sg.id=NEW.specific_goal_id AND sg.goal_id=NEW.goal_id AND sg.is_active=1
          AND g.is_active=1 AND d.is_active=1 AND p.is_active=1;
    END IF;
    IF v_goal IS NULL THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Goal inactive, missing, or specific goal belongs to another parent';
    END IF;
    IF NEW.supersedes_id IS NOT NULL AND NOT EXISTS (
        SELECT 1 FROM plan_goals old_goal WHERE old_goal.id=NEW.supersedes_id
          AND old_goal.plan_id=NEW.plan_id AND old_goal.goal_id=NEW.goal_id
          AND (old_goal.specific_goal_id <=> NEW.specific_goal_id) AND old_goal.status='PAUSED'
    ) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Replacement requires a paused predecessor for the same target and plan';
    END IF;
    SET NEW.goal_snapshot=v_goal;
    SET NEW.criteria_snapshot=v_criteria;
END$$

DELIMITER ;
