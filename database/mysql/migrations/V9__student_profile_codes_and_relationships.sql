-- Apply once to an existing V8 database. New databases also run this after V8.
CREATE TABLE IF NOT EXISTS student_code_sequence (
    id TINYINT PRIMARY KEY,
    next_value BIGINT NOT NULL,
    CONSTRAINT ck_student_code_sequence CHECK (id = 1 AND next_value >= 1)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

SELECT COALESCE(MAX(CAST(SUBSTRING(code, 3) AS UNSIGNED)), 0) + 1 INTO @student_next_code
FROM student WHERE code REGEXP '^HS[0-9]+$';
INSERT IGNORE INTO student_code_sequence(id, next_value) VALUES(1, @student_next_code);
UPDATE student_code_sequence SET next_value = GREATEST(next_value, @student_next_code) WHERE id = 1;

ALTER TABLE student_guardian
    DROP CHECK ck_guardian_relationship,
    ADD CONSTRAINT ck_guardian_relationship
        CHECK (relationship IN ('MOTHER','FATHER','GRANDFATHER','GRANDMOTHER','GUARDIAN','OTHER'));
