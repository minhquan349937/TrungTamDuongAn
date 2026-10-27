-- MySQL 8.4. Apply AFTER V5, once, while all catalog/plan writers are stopped.
-- Back up the FULL database first. DDL implicitly commits; restore backup on failure.
-- UUIDs outside the five catalog tables are intentionally unchanged.
SET NAMES utf8mb4;
SET time_zone = '+00:00';

DELIMITER $$
CREATE PROCEDURE assert_catalog_ready_for_v6()
BEGIN
    IF (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE()
        AND table_name IN ('programs','program_levels','domains','goals','specific_goals')
        AND column_name='id' AND data_type='char' AND character_maximum_length=36) <> 5 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V6 requires the complete V5 UUID schema; do not rerun a partial migration';
    END IF;
    IF EXISTS (
        SELECT 1 FROM information_schema.key_column_usage
        WHERE referenced_table_schema=DATABASE()
          AND referenced_table_name IN ('programs','program_levels','domains','goals','specific_goals')
          AND (table_schema<>DATABASE() OR referenced_column_name<>'id' OR NOT (
              (table_name='program_levels' AND column_name='program_id' AND referenced_table_name='programs')
              OR (table_name='domains' AND column_name='program_level_id' AND referenced_table_name='program_levels')
              OR (table_name='goals' AND column_name='domain_id' AND referenced_table_name='domains')
              OR (table_name='specific_goals' AND column_name='goal_id' AND referenced_table_name='goals')
              OR (table_name='goal_criteria' AND column_name='specific_goal_id' AND referenced_table_name='specific_goals')
              OR (table_name='plan_goals' AND column_name='goal_id' AND referenced_table_name='goals')
              OR (table_name='plan_goals' AND column_name='specific_goal_id' AND referenced_table_name='specific_goals')
          ))
    ) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Unknown catalog FK found; add an explicit mapping before V6';
    END IF;
    IF (SELECT COUNT(*) FROM information_schema.key_column_usage
        WHERE referenced_table_schema=DATABASE()
          AND referenced_table_name IN ('programs','program_levels','domains','goals','specific_goals'))<>7 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V5 catalog FKs are incomplete; inspect schema before V6';
    END IF;
    IF EXISTS (SELECT 1 FROM (
        SELECT id FROM programs UNION ALL SELECT id FROM program_levels
        UNION ALL SELECT id FROM domains UNION ALL SELECT id FROM goals UNION ALL SELECT id FROM specific_goals
    ) ids WHERE id NOT REGEXP '^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$') THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Non-UUID catalog IDs need a reviewed mapping before V6';
    END IF;
    IF GREATEST((SELECT COUNT(*) FROM programs),(SELECT COUNT(*) FROM program_levels),
        (SELECT COUNT(*) FROM domains),(SELECT COUNT(*) FROM goals),(SELECT COUNT(*) FROM specific_goals)) > 999999 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Catalog exceeds the six-digit ID capacity';
    END IF;
    IF EXISTS (SELECT 1 FROM plan_goals pg LEFT JOIN specific_goals sg ON sg.id=pg.specific_goal_id
        WHERE (pg.goal_id IS NULL AND sg.id IS NULL)
           OR (pg.goal_id IS NOT NULL AND sg.id IS NOT NULL AND pg.goal_id<>sg.goal_id)) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Plan goal parent/child mapping is invalid';
    END IF;
END$$
DELIMITER ;
CALL assert_catalog_ready_for_v6();
DROP PROCEDURE assert_catalog_ready_for_v6;

-- Operational migration audit, not an additional educational catalog entity.
-- Retain this map with the backup; historical JSON snapshots keep their original IDs.
CREATE TABLE migration_v6_catalog_ids (
    entity_name VARCHAR(30) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    old_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    new_id VARCHAR(8) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    PRIMARY KEY(entity_name,old_id),
    UNIQUE KEY uq_v6_new_id(entity_name,new_id)
) ENGINE=InnoDB;

INSERT INTO migration_v6_catalog_ids
SELECT 'programs',id,CONCAT('PR',LPAD(n,GREATEST(4,LENGTH(n)),'0')) FROM
    (SELECT id,ROW_NUMBER() OVER(ORDER BY id) n FROM programs) rows_to_map;
INSERT INTO migration_v6_catalog_ids
SELECT 'program_levels',id,CONCAT('LV',LPAD(n,GREATEST(4,LENGTH(n)),'0')) FROM
    (SELECT id,ROW_NUMBER() OVER(ORDER BY id) n FROM program_levels) rows_to_map;
INSERT INTO migration_v6_catalog_ids
SELECT 'domains',id,CONCAT('DM',LPAD(n,GREATEST(4,LENGTH(n)),'0')) FROM
    (SELECT id,ROW_NUMBER() OVER(ORDER BY id) n FROM domains) rows_to_map;
INSERT INTO migration_v6_catalog_ids
SELECT 'goals',id,CONCAT('GL',LPAD(n,6,'0')) FROM
    (SELECT id,ROW_NUMBER() OVER(ORDER BY id) n FROM goals) rows_to_map;
INSERT INTO migration_v6_catalog_ids
SELECT 'specific_goals',id,CONCAT('SG',LPAD(n,6,'0')) FROM
    (SELECT id,ROW_NUMBER() OVER(ORDER BY id) n FROM specific_goals) rows_to_map;

DROP TRIGGER plan_goals_snapshot_insert;
DROP TRIGGER plan_goals_snapshot_immutable;
ALTER TABLE plan_goals DROP CHECK ck_plan_goal_one_target,
    DROP INDEX uq_plan_initial_target, DROP COLUMN initial_target_key;

-- Drop only the reviewed FK constraints, never disable FOREIGN_KEY_CHECKS.
DELIMITER $$
CREATE PROCEDURE detach_v6_catalog_fks()
BEGIN
    DECLARE finished BOOLEAN DEFAULT FALSE;
    DECLARE child_table VARCHAR(64);
    DECLARE fk_name VARCHAR(64);
    DECLARE fks CURSOR FOR SELECT DISTINCT table_name,constraint_name
        FROM information_schema.key_column_usage
        WHERE referenced_table_schema=DATABASE()
          AND referenced_table_name IN ('programs','program_levels','domains','goals','specific_goals');
    DECLARE CONTINUE HANDLER FOR NOT FOUND SET finished=TRUE;
    OPEN fks;
    detach_loop: LOOP
        FETCH fks INTO child_table,fk_name;
        IF finished THEN LEAVE detach_loop; END IF;
        SET @v6_ddl=CONCAT('ALTER TABLE `',child_table,'` DROP FOREIGN KEY `',fk_name,'`');
        PREPARE v6_stmt FROM @v6_ddl;
        EXECUTE v6_stmt;
        DEALLOCATE PREPARE v6_stmt;
    END LOOP;
    CLOSE fks;
END$$
DELIMITER ;
CALL detach_v6_catalog_fks();
DROP PROCEDURE detach_v6_catalog_fks;

START TRANSACTION;
UPDATE plan_goals pg JOIN specific_goals sg ON sg.id=pg.specific_goal_id
    SET pg.goal_id=sg.goal_id,pg.updated_at=pg.updated_at WHERE pg.goal_id IS NULL;
UPDATE plan_goals t JOIN migration_v6_catalog_ids m ON m.entity_name='goals' AND m.old_id=t.goal_id SET t.goal_id=m.new_id,t.updated_at=t.updated_at;
UPDATE plan_goals t JOIN migration_v6_catalog_ids m ON m.entity_name='specific_goals' AND m.old_id=t.specific_goal_id SET t.specific_goal_id=m.new_id,t.updated_at=t.updated_at;
UPDATE goal_criteria t JOIN migration_v6_catalog_ids m ON m.entity_name='specific_goals' AND m.old_id=t.specific_goal_id SET t.specific_goal_id=m.new_id,t.updated_at=t.updated_at;
UPDATE specific_goals t JOIN migration_v6_catalog_ids m ON m.entity_name='goals' AND m.old_id=t.goal_id SET t.goal_id=m.new_id,t.updated_at=t.updated_at;
UPDATE goals t JOIN migration_v6_catalog_ids m ON m.entity_name='domains' AND m.old_id=t.domain_id SET t.domain_id=m.new_id,t.updated_at=t.updated_at;
UPDATE domains t JOIN migration_v6_catalog_ids m ON m.entity_name='program_levels' AND m.old_id=t.program_level_id SET t.program_level_id=m.new_id,t.updated_at=t.updated_at;
UPDATE program_levels t JOIN migration_v6_catalog_ids m ON m.entity_name='programs' AND m.old_id=t.program_id SET t.program_id=m.new_id,t.updated_at=t.updated_at;
UPDATE programs t JOIN migration_v6_catalog_ids m ON m.entity_name='programs' AND m.old_id=t.id SET t.id=m.new_id,t.updated_at=t.updated_at;
UPDATE program_levels t JOIN migration_v6_catalog_ids m ON m.entity_name='program_levels' AND m.old_id=t.id SET t.id=m.new_id,t.updated_at=t.updated_at;
UPDATE domains t JOIN migration_v6_catalog_ids m ON m.entity_name='domains' AND m.old_id=t.id SET t.id=m.new_id,t.updated_at=t.updated_at;
UPDATE goals t JOIN migration_v6_catalog_ids m ON m.entity_name='goals' AND m.old_id=t.id SET t.id=m.new_id,t.updated_at=t.updated_at;
UPDATE specific_goals t JOIN migration_v6_catalog_ids m ON m.entity_name='specific_goals' AND m.old_id=t.id SET t.id=m.new_id,t.updated_at=t.updated_at;
COMMIT;

ALTER TABLE programs MODIFY id VARCHAR(8) CHARACTER SET ascii COLLATE ascii_bin NOT NULL;
ALTER TABLE program_levels
    MODIFY id VARCHAR(8) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    MODIFY program_id VARCHAR(8) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    ADD CONSTRAINT fk_program_level_program FOREIGN KEY(program_id) REFERENCES programs(id);
ALTER TABLE domains
    MODIFY id VARCHAR(8) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    MODIFY program_level_id VARCHAR(8) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    ADD CONSTRAINT fk_domain_program_level FOREIGN KEY(program_level_id) REFERENCES program_levels(id);
ALTER TABLE goals
    MODIFY id VARCHAR(8) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    MODIFY domain_id VARCHAR(8) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    ADD CONSTRAINT fk_goal_domain FOREIGN KEY(domain_id) REFERENCES domains(id);
ALTER TABLE specific_goals
    MODIFY id VARCHAR(8) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    MODIFY goal_id VARCHAR(8) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    ADD UNIQUE KEY uq_specific_parent(id,goal_id),
    ADD KEY ix_specific_order_stable(goal_id,display_order,id),
    ADD CONSTRAINT fk_specific_goal_parent FOREIGN KEY(goal_id) REFERENCES goals(id);
ALTER TABLE goal_criteria
    MODIFY specific_goal_id VARCHAR(8) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    ADD CONSTRAINT fk_criteria_specific FOREIGN KEY(specific_goal_id) REFERENCES specific_goals(id);
ALTER TABLE plan_goals
    MODIFY goal_id VARCHAR(8) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    MODIFY specific_goal_id VARCHAR(8) CHARACTER SET ascii COLLATE ascii_bin NULL,
    ADD COLUMN initial_target_key VARCHAR(10) CHARACTER SET ascii COLLATE ascii_bin
        GENERATED ALWAYS AS (CASE WHEN supersedes_id IS NULL
            THEN IF(specific_goal_id IS NULL,CONCAT('G:',goal_id),CONCAT('S:',specific_goal_id)) ELSE NULL END) STORED,
    ADD UNIQUE KEY uq_plan_initial_target(plan_id,initial_target_key),
    ADD CONSTRAINT fk_plan_goal_direct FOREIGN KEY(goal_id) REFERENCES goals(id),
    ADD CONSTRAINT fk_plan_goal_specific_parent FOREIGN KEY(specific_goal_id,goal_id) REFERENCES specific_goals(id,goal_id);

-- Infrastructure shared by catalog writers. SELECT ... FOR UPDATE serializes allocation.
CREATE TABLE catalog_id_sequences (
    entity_name VARCHAR(30) CHARACTER SET ascii COLLATE ascii_bin PRIMARY KEY,
    next_value INT NOT NULL,
    CONSTRAINT ck_catalog_sequence CHECK(next_value BETWEEN 1 AND 1000000)
) ENGINE=InnoDB;
INSERT INTO catalog_id_sequences VALUES
    ('programs',(SELECT COUNT(*)+1 FROM programs)),
    ('program_levels',(SELECT COUNT(*)+1 FROM program_levels)),
    ('domains',(SELECT COUNT(*)+1 FROM domains)),
    ('goals',(SELECT COUNT(*)+1 FROM goals)),
    ('specific_goals',(SELECT COUNT(*)+1 FROM specific_goals));

-- New snapshots use schema v3. Existing JSON remains byte-for-byte unchanged.
DELIMITER $$
CREATE TRIGGER plan_goals_snapshot_insert
BEFORE INSERT ON plan_goals FOR EACH ROW
BEGIN
    DECLARE v_goal JSON DEFAULT NULL;
    DECLARE v_criteria JSON DEFAULT NULL;
    IF NEW.specific_goal_id IS NULL THEN
        SELECT JSON_OBJECT('schema_version',3,'target_type','GOAL','goal_id',g.id,
            'code',g.code,'name',g.name,'description',g.description,
            'domain_id',d.id,'domain_code',d.code,'domain_name',d.name,
            'program_level_id',pl.id,'level_number',pl.level_number,
            'age_min_months',pl.age_min_months,'age_max_months',pl.age_max_months,
            'program_id',p.id,'program_code',p.code,'program_name',p.name)
        INTO v_goal FROM goals g JOIN domains d ON d.id=g.domain_id
        JOIN program_levels pl ON pl.id=d.program_level_id JOIN programs p ON p.id=pl.program_id
        WHERE g.id=NEW.goal_id AND g.is_active=1 AND d.is_active=1 AND p.is_active=1;
        SET v_criteria=JSON_OBJECT('schema_version',3,'criteria_defined',0);
    ELSE
        SELECT JSON_OBJECT('schema_version',3,'target_type','SPECIFIC_GOAL','specific_goal_id',sg.id,
            'code',sg.code,'name',sg.description,'description',sg.description,'expected_behavior',sg.expected_behavior,
            'goal_id',g.id,'goal_code',g.code,'goal_name',g.name,'goal_description',g.description,
            'domain_id',d.id,'domain_code',d.code,'domain_name',d.name,
            'program_level_id',pl.id,'level_number',pl.level_number,
            'age_min_months',COALESCE(sg.age_min_months,pl.age_min_months),
            'age_max_months',COALESCE(sg.age_max_months,pl.age_max_months),
            'program_id',p.id,'program_code',p.code,'program_name',p.name),
            JSON_OBJECT('schema_version',3,'criteria_defined',IF(gc.id IS NULL,0,1),
            'measurement_type',gc.measurement_type,'target_count',gc.target_count,
            'observation_minutes',gc.observation_minutes,'successful_opportunities',gc.successful_opportunities,
            'total_opportunities',gc.total_opportunities,'target_percentage',gc.target_percentage,
            'support_level',gc.support_level,'required_periods',gc.required_periods,'period_unit',gc.period_unit,
            'must_be_consecutive',gc.must_be_consecutive,'adult_count',gc.adult_count,'setting_count',gc.setting_count)
        INTO v_goal,v_criteria FROM specific_goals sg JOIN goals g ON g.id=sg.goal_id
        JOIN domains d ON d.id=g.domain_id JOIN program_levels pl ON pl.id=d.program_level_id
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
CREATE TRIGGER plan_goals_snapshot_immutable
BEFORE UPDATE ON plan_goals FOR EACH ROW
BEGIN
    IF NOT (NEW.id <=> OLD.id) OR NOT (NEW.plan_id <=> OLD.plan_id)
       OR NOT (NEW.goal_id <=> OLD.goal_id) OR NOT (NEW.specific_goal_id <=> OLD.specific_goal_id)
       OR NOT (NEW.goal_snapshot <=> OLD.goal_snapshot) OR NOT (NEW.criteria_snapshot <=> OLD.criteria_snapshot)
       OR NOT (NEW.assigned_at <=> OLD.assigned_at) OR NOT (NEW.assigned_by <=> OLD.assigned_by)
       OR NOT (NEW.supersedes_id <=> OLD.supersedes_id) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Assigned goal is immutable; create a replacement row to retain history';
    END IF;
    IF NEW.status<>'PAUSED' AND EXISTS (SELECT 1 FROM plan_goals WHERE supersedes_id=OLD.id) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Superseded goal must remain paused';
    END IF;
END$$
DELIMITER ;
