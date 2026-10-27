CALL assert_v6_test_database();
DELIMITER $$
CREATE PROCEDURE assert_v6_migration()
BEGIN
    IF (SELECT COUNT(*) FROM plan_goals)<>3 OR (SELECT COUNT(*) FROM progress_records)<>1
       OR (SELECT COUNT(*) FROM migration_v6_catalog_ids)<>5 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V6 lost catalog, assignments or progress';
    END IF;
    IF EXISTS (SELECT 1 FROM plan_goals pg JOIN v6_test_expected old ON old.id=pg.id
        WHERE NOT(pg.goal_snapshot <=> old.goal_snapshot) OR NOT(pg.criteria_snapshot <=> old.criteria_snapshot)
           OR pg.assigned_at<>old.assigned_at OR pg.assigned_by<>old.assigned_by
           OR pg.updated_at<>old.updated_at OR pg.status<>old.status OR NOT(pg.supersedes_id <=> old.supersedes_id)) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V6 changed historical snapshots or assignment metadata';
    END IF;
    IF EXISTS (SELECT 1 FROM plan_goals pg LEFT JOIN goals g ON g.id=pg.goal_id
        LEFT JOIN specific_goals sg ON sg.id=pg.specific_goal_id
        WHERE g.id IS NULL OR (sg.id IS NOT NULL AND sg.goal_id<>pg.goal_id)) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V6 lost plan parent/child relationships';
    END IF;
    IF (SELECT COUNT(*) FROM goal_criteria gc JOIN specific_goals sg ON sg.id=gc.specific_goal_id)<>1
       OR (SELECT COUNT(*) FROM plan_goals WHERE supersedes_id IS NOT NULL)<>1 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V6 lost criteria or replacement chain';
    END IF;
    IF (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE()
        AND table_name IN ('programs','program_levels','domains','goals','specific_goals')
        AND column_name='id' AND data_type='varchar' AND character_maximum_length=8)<>5 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V6 ID types are incorrect';
    END IF;
END$$
DELIMITER ;
CALL assert_v6_migration();
DROP PROCEDURE assert_v6_migration;
SELECT 'PASS: V6 mapping, snapshots, criteria, progress and replacement chain preserved' AS test_result;
DELETE FROM progress_records;
DELETE FROM plan_goals WHERE supersedes_id IS NOT NULL;
DELETE FROM plan_goals;
DELETE FROM goal_criteria;
DELETE FROM specific_goals;
DELETE FROM goals;
DELETE FROM domains;
DELETE FROM program_levels;
DELETE FROM programs;
DELETE FROM learning_plans WHERE id='00000000-0000-0000-0000-000000009003';
DELETE FROM student WHERE id='00000000-0000-0000-0000-000000009002';
DELETE FROM app_user WHERE id='00000000-0000-0000-0000-000000009001';
DROP TABLE v6_test_expected;
DROP PROCEDURE assert_v6_test_database;
