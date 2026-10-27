-- Run after V1. Procedures own their transactions: CALL without an outer transaction.
-- Runtime MUST use these procedures and have no direct DML on class_session/staff_salary.
SET NAMES utf8mb4;
SET time_zone = '+00:00';
DELIMITER $$

CREATE TRIGGER plan_goals_snapshot_insert
BEFORE INSERT ON plan_goals FOR EACH ROW
BEGIN
    DECLARE v_goal JSON;
    DECLARE v_criteria JSON;
    SELECT JSON_OBJECT(
        'schema_version',1,'specific_goal_id',sg.id,'code',sg.code,
        'description',sg.description,'expected_behavior',sg.expected_behavior,
        'goal_id',g.id,'goal_code',g.code,'goal_name',g.name,
        'age_min_months',g.age_min_months,'age_max_months',g.age_max_months,
        'skill_level_id',sl.id,'skill_level_code',sl.code,'skill_level_name',sl.name,
        'domain_id',d.id,'domain_code',d.code,'domain_name',d.name),
        JSON_OBJECT('schema_version',1,'criteria_defined',IF(gc.id IS NULL,0,1),
        'measurement_type',gc.measurement_type,'target_count',gc.target_count,
        'observation_minutes',gc.observation_minutes,
        'successful_opportunities',gc.successful_opportunities,'total_opportunities',gc.total_opportunities,
        'target_percentage',gc.target_percentage,'support_level',gc.support_level,
        'required_periods',gc.required_periods,'period_unit',gc.period_unit,
        'must_be_consecutive',gc.must_be_consecutive,'adult_count',gc.adult_count,'setting_count',gc.setting_count)
    INTO v_goal,v_criteria
    FROM specific_goals sg JOIN goals g ON g.id=sg.goal_id
    JOIN skill_levels sl ON sl.id=g.skill_level_id JOIN domains d ON d.id=sl.domain_id
    LEFT JOIN goal_criteria gc ON gc.specific_goal_id=sg.id
    WHERE sg.id=NEW.specific_goal_id AND sg.is_active=1 AND g.is_active=1 AND sl.is_active=1 AND d.is_active=1;
    IF v_goal IS NULL THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Specific goal does not exist or its hierarchy is inactive';
    END IF;
    IF NEW.supersedes_id IS NOT NULL AND NOT EXISTS (
        SELECT 1 FROM plan_goals old_goal WHERE old_goal.id=NEW.supersedes_id
        AND old_goal.plan_id=NEW.plan_id AND old_goal.specific_goal_id=NEW.specific_goal_id
        AND old_goal.status='PAUSED'
    ) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Replacement requires a paused predecessor in the same plan and specific goal';
    END IF;
    SET NEW.goal_snapshot=v_goal;
    SET NEW.criteria_snapshot=v_criteria;
END$$

CREATE TRIGGER plan_goals_snapshot_immutable
BEFORE UPDATE ON plan_goals FOR EACH ROW
BEGIN
    IF NOT (NEW.id <=> OLD.id) OR NOT (NEW.plan_id <=> OLD.plan_id)
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

CREATE TRIGGER learning_plan_student_immutable
BEFORE UPDATE ON learning_plans FOR EACH ROW
BEGIN
    IF NOT (NEW.student_id <=> OLD.student_id) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='A learning plan cannot be reassigned to another student';
    END IF;
END$$

CREATE PROCEDURE save_class_session(
    IN p_id CHAR(36), IN p_class_id CHAR(36), IN p_teacher_id CHAR(36),
    IN p_starts_at DATETIME(6), IN p_ends_at DATETIME(6),
    IN p_status VARCHAR(20), IN p_location_note VARCHAR(250),
    IN p_change_reason TEXT, IN p_actor_id CHAR(36)
)
SQL SECURITY DEFINER
BEGIN
    DECLARE v_center TINYINT DEFAULT NULL;
    DECLARE v_conflict CHAR(36) DEFAULT NULL;
    DECLARE v_existing_class CHAR(36) DEFAULT NULL;
    DECLARE v_actor CHAR(36) DEFAULT NULL;
    DECLARE EXIT HANDLER FOR SQLEXCEPTION BEGIN ROLLBACK; RESIGNAL; END;
    START TRANSACTION;
    -- One center: serialize schedule writers, including inserts into empty schedules.
    SELECT id INTO v_center FROM center WHERE id=1 FOR UPDATE;
    IF v_center IS NULL THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Configure center id=1 before scheduling';
    END IF;
    SELECT id INTO v_actor FROM app_user WHERE id=p_actor_id AND role='ADMIN' AND status='ACTIVE' FOR SHARE;
    IF v_actor IS NULL THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='An active administrator must schedule sessions';
    END IF;
    IF p_starts_at IS NULL OR p_ends_at IS NULL OR p_ends_at<=p_starts_at THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Session end must be later than start';
    END IF;
    SELECT class_id INTO v_existing_class FROM class_session WHERE id=p_id FOR UPDATE;
    IF v_existing_class IS NOT NULL AND v_existing_class<>p_class_id THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Create a new session to change its class';
    END IF;
    IF p_status<>'CANCELLED' THEN
        -- A locking read sees latest committed data even under REPEATABLE READ.
        SELECT id INTO v_conflict FROM class_session
        WHERE id<>p_id AND status<>'CANCELLED'
          AND (class_id=p_class_id OR teacher_id=p_teacher_id)
          AND starts_at<p_ends_at AND ends_at>p_starts_at
        ORDER BY id LIMIT 1 FOR UPDATE;
        IF v_conflict IS NOT NULL THEN
            SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Class or teacher has an overlapping session';
        END IF;
    END IF;
    IF v_existing_class IS NULL THEN
        INSERT INTO class_session(id,class_id,teacher_id,starts_at,ends_at,status,location_note,change_reason,created_by)
        VALUES(p_id,p_class_id,p_teacher_id,p_starts_at,p_ends_at,p_status,p_location_note,p_change_reason,p_actor_id);
    ELSE
        UPDATE class_session SET teacher_id=p_teacher_id,starts_at=p_starts_at,ends_at=p_ends_at,
            status=p_status,location_note=p_location_note,change_reason=p_change_reason WHERE id=p_id;
    END IF;
    INSERT INTO audit_event(actor_id,action,entity_type,entity_id,detail)
    VALUES(p_actor_id,'SAVE_SESSION','class_session',p_id,
        JSON_OBJECT('class_id',p_class_id,'teacher_id',p_teacher_id,'starts_at',p_starts_at,'ends_at',p_ends_at,'status',p_status));
    COMMIT;
END$$

CREATE PROCEDURE save_staff_salary(
    IN p_id CHAR(36), IN p_staff_id CHAR(36), IN p_base_salary DECIMAL(14,0),
    IN p_effective_from DATE, IN p_effective_to DATE, IN p_actor_id CHAR(36)
)
SQL SECURITY DEFINER
BEGIN
    DECLARE v_staff CHAR(36) DEFAULT NULL;
    DECLARE v_existing_staff CHAR(36) DEFAULT NULL;
    DECLARE v_conflict CHAR(36) DEFAULT NULL;
    DECLARE v_actor CHAR(36) DEFAULT NULL;
    DECLARE EXIT HANDLER FOR SQLEXCEPTION BEGIN ROLLBACK; RESIGNAL; END;
    START TRANSACTION;
    SELECT id INTO v_staff FROM staff WHERE id=p_staff_id FOR UPDATE;
    IF v_staff IS NULL THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Staff does not exist'; END IF;
    SELECT id INTO v_actor FROM app_user WHERE id=p_actor_id AND role='ADMIN' AND status='ACTIVE' FOR SHARE;
    IF v_actor IS NULL THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='An active administrator must manage salary'; END IF;
    SELECT staff_id INTO v_existing_staff FROM staff_salary WHERE id=p_id FOR UPDATE;
    IF v_existing_staff IS NOT NULL AND v_existing_staff<>p_staff_id THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Salary history cannot change staff';
    END IF;
    SELECT id INTO v_conflict FROM staff_salary
    WHERE staff_id=p_staff_id AND id<>p_id
      AND (p_effective_to IS NULL OR effective_from<p_effective_to)
      AND (effective_to IS NULL OR effective_to>p_effective_from)
    ORDER BY id LIMIT 1 FOR UPDATE;
    IF v_conflict IS NOT NULL THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Salary effective periods overlap'; END IF;
    IF v_existing_staff IS NULL THEN
        INSERT INTO staff_salary(id,staff_id,base_salary,effective_from,effective_to,created_by)
        VALUES(p_id,p_staff_id,p_base_salary,p_effective_from,p_effective_to,p_actor_id);
    ELSE
        UPDATE staff_salary SET base_salary=p_base_salary,effective_from=p_effective_from,effective_to=p_effective_to WHERE id=p_id;
    END IF;
    INSERT INTO audit_event(actor_id,action,entity_type,entity_id,detail)
    VALUES(p_actor_id,'SAVE_SALARY','staff_salary',p_id,
        JSON_OBJECT('staff_id',p_staff_id,'base_salary',p_base_salary,'effective_from',p_effective_from,'effective_to',p_effective_to));
    COMMIT;
END$$
DELIMITER ;
