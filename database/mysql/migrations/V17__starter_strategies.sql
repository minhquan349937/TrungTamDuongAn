-- Works after V15, both before and after the optional V16 ID conversion.
-- Only fill the empty strategy catalog; never overwrite teacher-managed data.
SET NAMES utf8mb4;
START TRANSACTION;
SET @seed_strategies = (SELECT COUNT(*)=0 FROM strategies);
INSERT INTO strategies(id,name,description,is_active,display_order)
SELECT 'CL001','Làm mẫu',NULL,1,0 WHERE @seed_strategies;
INSERT INTO strategies(id,name,description,is_active,display_order)
SELECT 'CL002','Chờ đợi',NULL,1,1 WHERE @seed_strategies;
-- V16 is optional: synchronize its counter only when it has been installed.
SET @sync_strategy_counter = IF(
    EXISTS(SELECT 1 FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='entity_id_sequences'),
    'UPDATE entity_id_sequences SET issued_value=GREATEST(issued_value,(SELECT COALESCE(MAX(CAST(SUBSTRING(id,3) AS UNSIGNED)),0) FROM strategies)) WHERE entity_name=''strategies''',
    'DO 0');
PREPARE strategy_counter_statement FROM @sync_strategy_counter;
EXECUTE strategy_counter_statement;
DEALLOCATE PREPARE strategy_counter_statement;
COMMIT;

-- Also complete V15 installations where only the two CREATE TABLE statements
-- were applied manually. Existing V15 normalization triggers are preserved.
CREATE TRIGGER IF NOT EXISTS plan_goal_strategies_before_insert
BEFORE INSERT ON plan_goal_strategies FOR EACH ROW
SET NEW.description = CASE
    WHEN REGEXP_LIKE(NEW.description, '^[[:space:]]*$') THEN NULL
    ELSE NEW.description END;

CREATE TRIGGER IF NOT EXISTS plan_goal_strategies_before_update
BEFORE UPDATE ON plan_goal_strategies FOR EACH ROW
SET NEW.description = CASE
    WHEN REGEXP_LIKE(NEW.description, '^[[:space:]]*$') THEN NULL
    ELSE NEW.description END;
