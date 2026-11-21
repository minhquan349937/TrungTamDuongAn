-- Activities belong to catalog goals. Existing plans remain untouched.
SET NAMES utf8mb4;

CREATE TABLE suggested_activities (
    id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin PRIMARY KEY,
    goal_id VARCHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    name VARCHAR(255) NOT NULL,
    description TEXT NULL,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uq_suggested_activity_goal_name (goal_id, name),
    KEY ix_suggested_activity_goal (goal_id, is_active, created_at),
    CONSTRAINT fk_suggested_activity_goal FOREIGN KEY (goal_id) REFERENCES goals(id) ON DELETE RESTRICT,
    CONSTRAINT ck_suggested_activity_name CHECK (CHAR_LENGTH(TRIM(name)) > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE plan_goal_activities (
    id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin PRIMARY KEY,
    plan_goal_id VARCHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    activity_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    name_snapshot VARCHAR(255) NOT NULL,
    description_snapshot TEXT NULL,
    note TEXT NULL,
    display_order INT NOT NULL DEFAULT 0,
    UNIQUE KEY uq_plan_goal_activity (plan_goal_id, activity_id),
    KEY ix_plan_goal_activity_order (plan_goal_id, display_order),
    CONSTRAINT fk_plan_goal_activity_goal FOREIGN KEY (plan_goal_id) REFERENCES plan_goals(id) ON DELETE RESTRICT,
    CONSTRAINT fk_plan_goal_activity_activity FOREIGN KEY (activity_id) REFERENCES suggested_activities(id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
