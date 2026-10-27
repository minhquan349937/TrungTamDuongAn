-- Intervention programs and their development domains.
-- Existing domains remain valid with a NULL program_id; assign one when
-- associating an existing domain with a program.
SET NAMES utf8mb4;

CREATE TABLE programs (
    id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin PRIMARY KEY,
    code VARCHAR(30) NOT NULL UNIQUE,
    name VARCHAR(200) NOT NULL,
    age_min_months SMALLINT UNSIGNED NOT NULL,
    age_max_months SMALLINT UNSIGNED NOT NULL,
    description TEXT,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    CONSTRAINT ck_program_age_range CHECK (age_max_months >= age_min_months),
    CONSTRAINT ck_program_active CHECK (is_active IN (0,1))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

ALTER TABLE domains
    ADD COLUMN program_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NULL AFTER id,
    ADD KEY ix_domains_program (program_id),
    ADD CONSTRAINT fk_domains_program FOREIGN KEY (program_id) REFERENCES programs(id);
