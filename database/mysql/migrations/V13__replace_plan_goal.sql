-- Preserve evaluated goals while allowing a plan target to be replaced.
-- The existing supersedes_id FK/unique key records the replacement chain.
SET NAMES utf8mb4;
SET time_zone = '+00:00';
DROP TRIGGER IF EXISTS plan_goals_snapshot_insert;
DELIMITER $$
CREATE TRIGGER plan_goals_snapshot_insert BEFORE INSERT ON plan_goals FOR EACH ROW
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
DELIMITER ;
