-- Initialize business-code counters from existing records; only numeric NV/PH codes count.
CREATE TABLE IF NOT EXISTS profile_code_sequences (
    entity_name VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin PRIMARY KEY,
    next_value BIGINT NOT NULL,
    CONSTRAINT ck_profile_code_sequence CHECK (next_value >= 1)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

SELECT COALESCE(MAX(CAST(SUBSTRING(code, 3) AS UNSIGNED)), 0) + 1 INTO @next_staff_code
FROM staff WHERE code REGEXP '^NV[0-9]+$';
INSERT IGNORE INTO profile_code_sequences(entity_name, next_value) VALUES('staff', @next_staff_code);
UPDATE profile_code_sequences SET next_value=GREATEST(next_value,@next_staff_code) WHERE entity_name='staff';

SELECT COALESCE(MAX(CAST(SUBSTRING(code, 3) AS UNSIGNED)), 0) + 1 INTO @next_guardian_code
FROM guardian WHERE code REGEXP '^PH[0-9]+$';
INSERT IGNORE INTO profile_code_sequences(entity_name, next_value) VALUES('guardian', @next_guardian_code);
UPDATE profile_code_sequences SET next_value=GREATEST(next_value,@next_guardian_code) WHERE entity_name='guardian';
