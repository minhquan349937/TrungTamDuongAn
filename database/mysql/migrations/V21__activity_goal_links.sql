-- A reusable activity can be suggested for multiple catalog goals.
-- The existing plan_goal_activities table already links activities and plan goals N:N.
SET NAMES utf8mb4;

CREATE TABLE suggested_activity_goals (
    activity_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    goal_id VARCHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    PRIMARY KEY (activity_id, goal_id),
    KEY ix_suggested_activity_goals_goal (goal_id, activity_id),
    CONSTRAINT fk_suggested_activity_goals_activity FOREIGN KEY (activity_id)
        REFERENCES suggested_activities(id) ON DELETE RESTRICT,
    CONSTRAINT fk_suggested_activity_goals_goal FOREIGN KEY (goal_id)
        REFERENCES goals(id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

INSERT INTO suggested_activity_goals(activity_id, goal_id)
SELECT id, goal_id FROM suggested_activities;
