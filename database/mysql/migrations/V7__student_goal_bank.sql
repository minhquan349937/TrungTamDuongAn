-- Additive migration: student goal bank, independent of a learning plan.
-- Existing catalog, import IDs and plan/progress data are not modified.
SET NAMES utf8mb4;
CREATE TABLE student_goal_bank (
    id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin PRIMARY KEY,
    student_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    goal_id VARCHAR(8) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    specific_goal_id VARCHAR(8) CHARACTER SET ascii COLLATE ascii_bin NULL,
    target_key VARCHAR(10) CHARACTER SET ascii COLLATE ascii_bin GENERATED ALWAYS AS
        (IF(specific_goal_id IS NULL,CONCAT('G:',goal_id),CONCAT('S:',specific_goal_id))) STORED,
    goal_snapshot JSON NOT NULL,
    selected_by CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    UNIQUE KEY uq_student_bank_target(student_id,target_key),
    KEY ix_student_bank_order(student_id,created_at,id),
    CONSTRAINT fk_bank_student FOREIGN KEY(student_id) REFERENCES student(id),
    CONSTRAINT fk_bank_goal FOREIGN KEY(goal_id) REFERENCES goals(id),
    CONSTRAINT fk_bank_specific_parent FOREIGN KEY(specific_goal_id,goal_id) REFERENCES specific_goals(id,goal_id),
    CONSTRAINT fk_bank_actor FOREIGN KEY(selected_by) REFERENCES app_user(id),
    CONSTRAINT ck_bank_snapshot CHECK(JSON_TYPE(goal_snapshot)='OBJECT')
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
