-- Apply once after V14. Only the two new tables and their triggers are created.
-- IDs are supplied by the caller: CL001..CL999999 and MTCL001..MTCL999999.
-- Use at least three numeric digits; do not truncate numbers above 999.
-- plan_goal_id retains the exact type/collation of the existing plan_goals.id.
SET NAMES utf8mb4;

CREATE TABLE strategies (
    id VARCHAR(8) CHARACTER SET ascii COLLATE ascii_bin PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    description TEXT NULL DEFAULT NULL,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    display_order INT NOT NULL DEFAULT 0,
    KEY ix_strategy_order (display_order, id),
    CONSTRAINT ck_strategy_id CHECK (REGEXP_LIKE(id, '^CL[0-9]{3,6}$', 'c')),
    CONSTRAINT ck_strategy_name CHECK (CHAR_LENGTH(TRIM(name)) > 0),
    CONSTRAINT ck_strategy_active CHECK (is_active IN (0, 1)),
    CONSTRAINT ck_strategy_order CHECK (display_order >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE plan_goal_strategies (
    id VARCHAR(10) CHARACTER SET ascii COLLATE ascii_bin PRIMARY KEY,
    plan_goal_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    strategy_id VARCHAR(8) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    description TEXT NULL DEFAULT NULL,
    display_order INT NOT NULL DEFAULT 0,
    UNIQUE KEY uq_plan_goal_strategy (plan_goal_id, strategy_id),
    KEY ix_plan_goal_strategy_order (plan_goal_id, display_order, id),
    KEY ix_plan_goal_strategy_strategy (strategy_id),
    CONSTRAINT fk_plan_goal_strategy_goal FOREIGN KEY (plan_goal_id)
        REFERENCES plan_goals(id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_plan_goal_strategy_strategy FOREIGN KEY (strategy_id)
        REFERENCES strategies(id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT ck_plan_goal_strategy_id CHECK (REGEXP_LIKE(id, '^MTCL[0-9]{3,6}$', 'c')),
    CONSTRAINT ck_plan_goal_strategy_order CHECK (display_order >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- Normalize blank teacher instructions on both INSERT and UPDATE, including
-- whitespace-only input. Preserve the original text when it is not blank.
CREATE TRIGGER plan_goal_strategies_before_insert
BEFORE INSERT ON plan_goal_strategies FOR EACH ROW
SET NEW.description = CASE
    WHEN REGEXP_LIKE(NEW.description, '^[[:space:]]*$') THEN NULL
    ELSE NEW.description END;

CREATE TRIGGER plan_goal_strategies_before_update
BEFORE UPDATE ON plan_goal_strategies FOR EACH ROW
SET NEW.description = CASE
    WHEN REGEXP_LIKE(NEW.description, '^[[:space:]]*$') THEN NULL
    ELSE NEW.description END;
