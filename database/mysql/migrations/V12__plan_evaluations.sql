-- Monthly narrative reports. Publication makes the saved text visible in the parent portal.
SET NAMES utf8mb4;
SET time_zone = '+00:00';

CREATE TABLE plan_evaluations (
    id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin PRIMARY KEY,
    learning_plan_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    evaluation_notes TEXT NOT NULL,
    evaluated_by CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    evaluated_at DATETIME(6) NOT NULL,
    sent_to_parent BOOLEAN NOT NULL DEFAULT 0,
    sent_at DATETIME(6) NULL,
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    KEY ix_plan_evaluation_plan (learning_plan_id),
    KEY ix_plan_evaluation_time (evaluated_at),
    CONSTRAINT fk_plan_evaluation_plan FOREIGN KEY (learning_plan_id) REFERENCES learning_plans(id),
    CONSTRAINT fk_plan_evaluation_user FOREIGN KEY (evaluated_by) REFERENCES app_user(id),
    CONSTRAINT ck_plan_evaluation_publication CHECK
        ((sent_to_parent = 0 AND sent_at IS NULL) OR (sent_to_parent = 1 AND sent_at IS NOT NULL))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- Published narratives are immutable, including against direct SQL updates/deletes.
DELIMITER $$
CREATE TRIGGER trg_plan_evaluation_before_update BEFORE UPDATE ON plan_evaluations FOR EACH ROW
BEGIN
    IF OLD.sent_to_parent = 1 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Published plan evaluation is immutable';
    END IF;
    IF NEW.id <> OLD.id OR NEW.learning_plan_id <> OLD.learning_plan_id
       OR NEW.evaluated_by <> OLD.evaluated_by OR NEW.evaluated_at <> OLD.evaluated_at THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Plan evaluation identity is immutable';
    END IF;
END$$
CREATE TRIGGER trg_plan_evaluation_before_delete BEFORE DELETE ON plan_evaluations FOR EACH ROW
BEGIN
    IF OLD.sent_to_parent = 1 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Published plan evaluation cannot be deleted';
    END IF;
END$$
DELIMITER ;
