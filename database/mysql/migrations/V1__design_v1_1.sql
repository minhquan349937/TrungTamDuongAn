-- Duong An design 1.1. MySQL 8.4+ / InnoDB. Exactly 28 application tables.
-- Select an EMPTY database before running. IDs are UUID strings supplied by the application.
-- All DATETIME values represent UTC. DATE values represent local calendar dates.
SET NAMES utf8mb4;
SET time_zone = '+00:00';

CREATE TABLE center (
    id TINYINT UNSIGNED NOT NULL DEFAULT 1 PRIMARY KEY,
    name VARCHAR(200) NOT NULL,
    address TEXT,
    phone VARCHAR(30),
    timezone VARCHAR(60) NOT NULL DEFAULT 'Asia/Ho_Chi_Minh',
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    CONSTRAINT ck_center_singleton CHECK (id = 1)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE app_user (
    id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin PRIMARY KEY,
    email VARCHAR(254) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    display_name VARCHAR(150) NOT NULL,
    role VARCHAR(10) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    status VARCHAR(20) CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT 'ACTIVE',
    token_version INT NOT NULL DEFAULT 0,
    last_login_at DATETIME(6),
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    UNIQUE KEY uq_user_id_role (id, role),
    CONSTRAINT ck_user_email CHECK (CAST(email AS BINARY) = CAST(LOWER(TRIM(email)) AS BINARY)),
    CONSTRAINT ck_user_role CHECK (role IN ('ADMIN','TEACHER','PARENT')),
    CONSTRAINT ck_user_status CHECK (status IN ('ACTIVE','LOCKED','DISABLED')),
    CONSTRAINT ck_user_token_version CHECK (token_version >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE staff (
    id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin PRIMARY KEY,
    code VARCHAR(30) NOT NULL UNIQUE,
    user_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin UNIQUE,
    account_role VARCHAR(10) CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT 'TEACHER',
    full_name VARCHAR(150) NOT NULL,
    position VARCHAR(150),
    specialty VARCHAR(150),
    phone VARCHAR(30),
    email VARCHAR(254),
    joined_on DATE NOT NULL,
    left_on DATE,
    status VARCHAR(20) CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT 'ACTIVE',
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    FOREIGN KEY (user_id, account_role) REFERENCES app_user(id, role),
    CONSTRAINT ck_staff_role CHECK (account_role IN ('ADMIN','TEACHER')),
    CONSTRAINT ck_staff_dates CHECK (left_on IS NULL OR left_on >= joined_on),
    CONSTRAINT ck_staff_status CHECK (status IN ('ACTIVE','ON_LEAVE','TERMINATED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE guardian (
    id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin PRIMARY KEY,
    code VARCHAR(30) NOT NULL UNIQUE,
    user_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin UNIQUE,
    account_role VARCHAR(10) CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT 'PARENT',
    full_name VARCHAR(150) NOT NULL,
    phone VARCHAR(30),
    email VARCHAR(254),
    address TEXT,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    FOREIGN KEY (user_id, account_role) REFERENCES app_user(id, role),
    CONSTRAINT ck_guardian_role CHECK (account_role = 'PARENT')
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE student (
    id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin PRIMARY KEY,
    code VARCHAR(30) NOT NULL UNIQUE,
    full_name VARCHAR(150) NOT NULL,
    birth_date DATE NOT NULL,
    admitted_on DATE NOT NULL,
    withdrawn_on DATE,
    status VARCHAR(20) CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT 'ACTIVE',
    notes TEXT,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    KEY ix_student_status_name (status, full_name),
    CONSTRAINT ck_student_status CHECK (status IN ('ACTIVE','PAUSED','WITHDRAWN')),
    CONSTRAINT ck_student_birth CHECK (birth_date <= admitted_on),
    CONSTRAINT ck_student_exit CHECK (withdrawn_on IS NULL OR withdrawn_on >= admitted_on),
    CONSTRAINT ck_student_withdrawal CHECK ((status = 'WITHDRAWN') = (withdrawn_on IS NOT NULL))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE student_guardian (
    student_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    guardian_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    relationship VARCHAR(20) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    is_primary BOOLEAN NOT NULL DEFAULT FALSE,
    can_view_portal BOOLEAN NOT NULL DEFAULT TRUE,
    can_pick_up BOOLEAN NOT NULL DEFAULT FALSE,
    linked_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    revoked_at DATETIME(6),
    -- MySQL UNIQUE allows multiple NULLs: this replaces a partial unique index.
    primary_student_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin
        GENERATED ALWAYS AS (CASE WHEN is_primary=1 AND revoked_at IS NULL THEN student_id ELSE NULL END) STORED,
    PRIMARY KEY (student_id, guardian_id),
    UNIQUE KEY uq_primary_guardian (primary_student_id),
    KEY ix_guardian_portal (guardian_id, revoked_at, can_view_portal),
    FOREIGN KEY (student_id) REFERENCES student(id),
    FOREIGN KEY (guardian_id) REFERENCES guardian(id),
    CONSTRAINT ck_guardian_relationship CHECK (relationship IN ('MOTHER','FATHER','GRANDFATHER','GRANDMOTHER','GUARDIAN','OTHER')),
    CONSTRAINT ck_guardian_flags CHECK (is_primary IN (0,1) AND can_view_portal IN (0,1) AND can_pick_up IN (0,1)),
    CONSTRAINT ck_guardian_revoked CHECK (revoked_at IS NULL OR revoked_at >= linked_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE staff_salary (
    id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin PRIMARY KEY,
    staff_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    base_salary DECIMAL(14,0) NOT NULL,
    currency CHAR(3) CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT 'VND',
    effective_from DATE NOT NULL,
    effective_to DATE,
    created_by CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    KEY ix_salary_staff_period (staff_id, effective_from, effective_to),
    FOREIGN KEY (staff_id) REFERENCES staff(id),
    FOREIGN KEY (created_by) REFERENCES app_user(id),
    CONSTRAINT ck_salary_amount CHECK (base_salary >= 0 AND currency='VND'),
    CONSTRAINT ck_salary_dates CHECK (effective_to IS NULL OR effective_to > effective_from)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE learning_class (
    id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin PRIMARY KEY,
    code VARCHAR(30) NOT NULL UNIQUE,
    name VARCHAR(150) NOT NULL,
    class_type VARCHAR(20) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    teacher_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    capacity INT NOT NULL,
    starts_on DATE NOT NULL,
    ends_on DATE,
    status VARCHAR(20) CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT 'ACTIVE',
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    KEY ix_class_teacher_status (teacher_id, status),
    FOREIGN KEY (teacher_id) REFERENCES staff(id),
    CONSTRAINT ck_class_type CHECK (class_type IN ('INDIVIDUAL','GROUP','DAYCARE')),
    CONSTRAINT ck_class_capacity CHECK (capacity > 0 AND (class_type <> 'INDIVIDUAL' OR capacity=1)),
    CONSTRAINT ck_class_dates CHECK (ends_on IS NULL OR ends_on > starts_on),
    CONSTRAINT ck_class_status CHECK (status IN ('ACTIVE','PAUSED','CLOSED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE enrollment (
    id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin PRIMARY KEY,
    class_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    student_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    started_on DATE NOT NULL,
    ended_on DATE,
    status VARCHAR(20) CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT 'ENROLLED',
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    UNIQUE KEY uq_enrollment_start (class_id, student_id, started_on),
    KEY ix_enrollment_student_dates (student_id, started_on, ended_on),
    KEY ix_enrollment_class_dates (class_id, started_on, ended_on),
    FOREIGN KEY (class_id) REFERENCES learning_class(id),
    FOREIGN KEY (student_id) REFERENCES student(id),
    CONSTRAINT ck_enrollment_dates CHECK (ended_on IS NULL OR ended_on > started_on),
    CONSTRAINT ck_enrollment_status CHECK (status IN ('ENROLLED','ENDED','CANCELLED')),
    CONSTRAINT ck_enrollment_end CHECK (status <> 'ENDED' OR ended_on IS NOT NULL)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE class_session (
    id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin PRIMARY KEY,
    class_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    teacher_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    starts_at DATETIME(6) NOT NULL COMMENT 'UTC',
    ends_at DATETIME(6) NOT NULL COMMENT 'UTC',
    status VARCHAR(20) CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT 'SCHEDULED',
    location_note VARCHAR(250),
    change_reason TEXT,
    created_by CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    KEY ix_session_class_time (class_id, starts_at, ends_at),
    KEY ix_session_teacher_time (teacher_id, starts_at, ends_at),
    KEY ix_session_time (starts_at),
    FOREIGN KEY (class_id) REFERENCES learning_class(id),
    FOREIGN KEY (teacher_id) REFERENCES staff(id),
    FOREIGN KEY (created_by) REFERENCES app_user(id),
    CONSTRAINT ck_session_time CHECK (ends_at > starts_at),
    CONSTRAINT ck_session_status CHECK (status IN ('SCHEDULED','COMPLETED','CANCELLED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE student_attendance (
    id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin PRIMARY KEY,
    session_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    student_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    status VARCHAR(20) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    marked_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    recorded_by CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    notes TEXT,
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    UNIQUE KEY uq_attendance_session_student (session_id, student_id),
    KEY ix_attendance_student (student_id, session_id),
    FOREIGN KEY (session_id) REFERENCES class_session(id),
    FOREIGN KEY (student_id) REFERENCES student(id),
    FOREIGN KEY (recorded_by) REFERENCES app_user(id),
    CONSTRAINT ck_attendance_status CHECK (status IN ('PRESENT','ABSENT','EXCUSED','LATE'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE staff_checkin (
    id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin PRIMARY KEY,
    staff_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    work_date DATE NOT NULL,
    checked_in_at DATETIME(6) NOT NULL,
    checked_out_at DATETIME(6),
    recorded_by CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    correction_reason TEXT,
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    UNIQUE KEY uq_checkin_day (staff_id, work_date),
    FOREIGN KEY (staff_id) REFERENCES staff(id),
    FOREIGN KEY (recorded_by) REFERENCES app_user(id),
    CONSTRAINT ck_checkin_time CHECK (checked_out_at IS NULL OR checked_out_at >= checked_in_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE leave_request (
    id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin PRIMARY KEY,
    staff_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    starts_on DATE NOT NULL,
    ends_on DATE NOT NULL,
    reason TEXT NOT NULL,
    status VARCHAR(20) CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT 'PENDING',
    requested_by CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    reviewed_by CHAR(36) CHARACTER SET ascii COLLATE ascii_bin,
    reviewed_at DATETIME(6),
    review_note TEXT,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    KEY ix_leave_status_date (status, starts_on),
    FOREIGN KEY (staff_id) REFERENCES staff(id),
    FOREIGN KEY (requested_by) REFERENCES app_user(id),
    FOREIGN KEY (reviewed_by) REFERENCES app_user(id),
    CONSTRAINT ck_leave_dates CHECK (ends_on >= starts_on),
    CONSTRAINT ck_leave_reason CHECK (CHAR_LENGTH(TRIM(reason)) > 0),
    CONSTRAINT ck_leave_status CHECK (status IN ('PENDING','APPROVED','REJECTED','CANCELLED')),
    CONSTRAINT ck_leave_review CHECK (
       (status IN ('APPROVED','REJECTED') AND reviewed_by IS NOT NULL AND reviewed_at IS NOT NULL)
       OR (status IN ('PENDING','CANCELLED') AND reviewed_by IS NULL AND reviewed_at IS NULL)),
    CONSTRAINT ck_leave_reviewer CHECK (reviewed_by IS NULL OR reviewed_by <> requested_by)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE invoice (
    id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin PRIMARY KEY,
    code VARCHAR(30) NOT NULL UNIQUE,
    student_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    billing_month DATE NOT NULL,
    issued_on DATE NOT NULL,
    due_on DATE NOT NULL,
    status VARCHAR(20) CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT 'DRAFT',
    currency CHAR(3) CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT 'VND',
    created_by CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    void_reason TEXT,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    KEY ix_invoice_student_month (student_id, billing_month),
    KEY ix_invoice_status_due (status, due_on),
    FOREIGN KEY (student_id) REFERENCES student(id),
    FOREIGN KEY (created_by) REFERENCES app_user(id),
    CONSTRAINT ck_invoice_month CHECK (DAYOFMONTH(billing_month)=1),
    CONSTRAINT ck_invoice_dates CHECK (due_on >= issued_on),
    CONSTRAINT ck_invoice_status CHECK (status IN ('DRAFT','ISSUED','VOID')),
    CONSTRAINT ck_invoice_currency CHECK (currency='VND'),
    CONSTRAINT ck_invoice_void CHECK (status <> 'VOID' OR (void_reason IS NOT NULL AND CHAR_LENGTH(TRIM(void_reason))>0))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE invoice_line (
    id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin PRIMARY KEY,
    invoice_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    description VARCHAR(250) NOT NULL,
    quantity INT NOT NULL DEFAULT 1,
    unit_price DECIMAL(14,0) NOT NULL,
    discount DECIMAL(14,0) NOT NULL DEFAULT 0,
    line_total DECIMAL(20,0) GENERATED ALWAYS AS (quantity*unit_price-discount) STORED,
    FOREIGN KEY (invoice_id) REFERENCES invoice(id),
    CONSTRAINT ck_invoice_line_values CHECK (quantity>0 AND unit_price>=0 AND discount>=0 AND discount<=quantity*unit_price)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE payment (
    id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin PRIMARY KEY,
    receipt_code VARCHAR(40) NOT NULL UNIQUE,
    invoice_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    amount DECIMAL(20,0) NOT NULL,
    method VARCHAR(20) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    status VARCHAR(20) CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT 'POSTED',
    paid_at DATETIME(6) NOT NULL,
    reference VARCHAR(150),
    idempotency_key CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL UNIQUE,
    received_by CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    voided_by CHAR(36) CHARACTER SET ascii COLLATE ascii_bin,
    voided_at DATETIME(6),
    void_reason TEXT,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    KEY ix_payment_invoice_time (invoice_id, paid_at),
    FOREIGN KEY (invoice_id) REFERENCES invoice(id),
    FOREIGN KEY (received_by) REFERENCES app_user(id),
    FOREIGN KEY (voided_by) REFERENCES app_user(id),
    CONSTRAINT ck_payment_amount CHECK (amount>0),
    CONSTRAINT ck_payment_method CHECK (method IN ('CASH','BANK_TRANSFER','OTHER')),
    CONSTRAINT ck_payment_status CHECK (status IN ('POSTED','VOID')),
    CONSTRAINT ck_payment_void CHECK (
        (status='POSTED' AND voided_by IS NULL AND voided_at IS NULL AND void_reason IS NULL) OR
        (status='VOID' AND voided_by IS NOT NULL AND voided_at IS NOT NULL AND void_reason IS NOT NULL AND CHAR_LENGTH(TRIM(void_reason))>0))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE SQL SECURITY INVOKER VIEW invoice_balance AS
SELECT i.id AS invoice_id, i.student_id, i.billing_month, i.status,
       COALESCE(c.total,0) AS total_amount, COALESCE(p.paid,0) AS paid_amount,
       COALESCE(c.total,0)-COALESCE(p.paid,0) AS outstanding_amount,
       CASE WHEN i.status='VOID' THEN 'VOID' WHEN i.status='DRAFT' THEN 'DRAFT'
            WHEN COALESCE(p.paid,0)>=COALESCE(c.total,0) THEN 'PAID'
            WHEN COALESCE(p.paid,0)>0 THEN 'PARTIAL' ELSE 'UNPAID' END AS payment_status
FROM invoice i
LEFT JOIN (SELECT invoice_id,SUM(line_total) AS total FROM invoice_line GROUP BY invoice_id) c ON c.invoice_id=i.id
LEFT JOIN (SELECT invoice_id,SUM(amount) AS paid FROM payment WHERE status='POSTED' GROUP BY invoice_id) p ON p.invoice_id=i.id;

CREATE TABLE notification (
    id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin PRIMARY KEY,
    title VARCHAR(200) NOT NULL,
    body TEXT NOT NULL,
    status VARCHAR(20) CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT 'DRAFT',
    created_by CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    published_at DATETIME(6),
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    FOREIGN KEY (created_by) REFERENCES app_user(id),
    CONSTRAINT ck_notification_status CHECK (status IN ('DRAFT','PUBLISHED','ARCHIVED')),
    CONSTRAINT ck_notification_published CHECK (status <> 'PUBLISHED' OR published_at IS NOT NULL)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE notification_recipient (
    notification_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    user_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    read_at DATETIME(6),
    PRIMARY KEY (notification_id,user_id),
    KEY ix_notification_user_read (user_id,read_at),
    FOREIGN KEY (notification_id) REFERENCES notification(id),
    FOREIGN KEY (user_id) REFERENCES app_user(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE document (
    id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin PRIMARY KEY,
    title VARCHAR(250) NOT NULL,
    category VARCHAR(20) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    storage_key TEXT,
    external_url TEXT,
    mime_type VARCHAR(100),
    byte_size BIGINT,
    uploaded_by CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    archived_at DATETIME(6),
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    FOREIGN KEY (uploaded_by) REFERENCES app_user(id),
    CONSTRAINT ck_document_category CHECK (category IN ('INTERNAL','REFERENCE','FORM')),
    CONSTRAINT ck_document_size CHECK (byte_size IS NULL OR byte_size>=0),
    CONSTRAINT ck_document_source CHECK ((storage_key IS NOT NULL) <> (external_url IS NOT NULL))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE audit_event (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
    actor_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin,
    action VARCHAR(60) NOT NULL,
    entity_type VARCHAR(60) NOT NULL,
    entity_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin,
    request_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin,
    detail JSON NOT NULL,
    occurred_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    KEY ix_audit_entity_time (entity_type,entity_id,occurred_at),
    FOREIGN KEY (actor_id) REFERENCES app_user(id),
    CONSTRAINT ck_audit_detail CHECK (JSON_TYPE(detail)='OBJECT')
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE domains (
    id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin PRIMARY KEY,
    code VARCHAR(30) NOT NULL UNIQUE,
    name VARCHAR(200) NOT NULL,
    description TEXT,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    CONSTRAINT ck_domains_active CHECK (is_active IN (0,1))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE skill_levels (
    id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin PRIMARY KEY,
    domain_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    code VARCHAR(30) NOT NULL,
    name VARCHAR(150) NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    UNIQUE KEY uq_level_domain_code (domain_id,code),
    FOREIGN KEY (domain_id) REFERENCES domains(id),
    CONSTRAINT ck_levels_active CHECK (is_active IN (0,1))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE goals (
    id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin PRIMARY KEY,
    skill_level_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    code VARCHAR(60) NOT NULL UNIQUE,
    name VARCHAR(500) NOT NULL,
    age_min_months INT,
    age_max_months INT,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    FOREIGN KEY (skill_level_id) REFERENCES skill_levels(id),
    CONSTRAINT ck_goals_ages CHECK ((age_min_months IS NULL OR age_min_months>=0) AND
        (age_max_months IS NULL OR age_max_months>=0) AND
        (age_min_months IS NULL OR age_max_months IS NULL OR age_max_months>=age_min_months)),
    CONSTRAINT ck_goals_active CHECK (is_active IN (0,1))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE specific_goals (
    id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin PRIMARY KEY,
    goal_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    code VARCHAR(80) NOT NULL UNIQUE,
    description TEXT NOT NULL,
    expected_behavior TEXT,
    display_order INT NOT NULL DEFAULT 0,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    KEY ix_specific_goal_order (goal_id,display_order),
    FOREIGN KEY (goal_id) REFERENCES goals(id),
    CONSTRAINT ck_specific_order CHECK (display_order>=0),
    CONSTRAINT ck_specific_active CHECK (is_active IN (0,1))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE goal_criteria (
    id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin PRIMARY KEY,
    specific_goal_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL UNIQUE,
    measurement_type VARCHAR(20) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    target_count INT,
    observation_minutes DECIMAL(10,2),
    successful_opportunities INT,
    total_opportunities INT,
    target_percentage DECIMAL(5,2),
    support_level VARCHAR(30) CHARACTER SET ascii COLLATE ascii_bin,
    required_periods INT,
    period_unit VARCHAR(15) CHARACTER SET ascii COLLATE ascii_bin,
    must_be_consecutive BOOLEAN NOT NULL DEFAULT FALSE,
    adult_count INT,
    setting_count INT,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    FOREIGN KEY (specific_goal_id) REFERENCES specific_goals(id),
    CONSTRAINT ck_criteria_measure CHECK (measurement_type IN ('COUNT','OPPORTUNITIES','PERCENTAGE','DURATION')),
    CONSTRAINT ck_criteria_count CHECK (target_count IS NULL OR target_count>=0),
    CONSTRAINT ck_criteria_minutes CHECK (observation_minutes IS NULL OR observation_minutes>=0),
    CONSTRAINT ck_criteria_opportunities CHECK ((successful_opportunities IS NULL OR successful_opportunities>=0) AND
        (total_opportunities IS NULL OR total_opportunities>=0) AND
        (successful_opportunities IS NULL OR total_opportunities IS NULL OR successful_opportunities<=total_opportunities)),
    CONSTRAINT ck_criteria_percentage CHECK (target_percentage IS NULL OR target_percentage BETWEEN 0 AND 100),
    CONSTRAINT ck_criteria_support CHECK (support_level IS NULL OR support_level IN ('INDEPENDENT','VERBAL','GESTURAL','MODEL','PARTIAL_PHYSICAL','FULL_PHYSICAL')),
    CONSTRAINT ck_criteria_period CHECK (
        (required_periods IS NULL AND period_unit IS NULL AND must_be_consecutive=0) OR
        (required_periods IS NOT NULL AND required_periods>0 AND period_unit IS NOT NULL AND period_unit IN ('SESSION','DAY','WEEK','MONTH'))),
    CONSTRAINT ck_criteria_consecutive CHECK (must_be_consecutive IN (0,1)),
    CONSTRAINT ck_criteria_context CHECK ((adult_count IS NULL OR adult_count>0) AND (setting_count IS NULL OR setting_count>0))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE learning_plans (
    id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin PRIMARY KEY,
    student_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    name VARCHAR(200) NOT NULL,
    start_date DATE NOT NULL,
    end_date DATE,
    status VARCHAR(20) CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT 'DRAFT',
    created_by CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    notes TEXT,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    KEY ix_plan_student_status (student_id,status,start_date),
    FOREIGN KEY (student_id) REFERENCES student(id),
    FOREIGN KEY (created_by) REFERENCES app_user(id),
    CONSTRAINT ck_plan_dates CHECK (end_date IS NULL OR end_date>=start_date),
    CONSTRAINT ck_plan_status CHECK (status IN ('DRAFT','ACTIVE','COMPLETED','PAUSED','CANCELLED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE plan_goals (
    id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin PRIMARY KEY,
    plan_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    specific_goal_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    goal_snapshot JSON NOT NULL,
    criteria_snapshot JSON NOT NULL,
    status VARCHAR(20) CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT 'NOT_STARTED',
    assigned_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    assigned_by CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    -- New snapshot versions are NEW rows, retaining observation history on old rows.
    supersedes_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin UNIQUE,
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    KEY ix_plan_goal_status (plan_id,status),
    FOREIGN KEY (plan_id) REFERENCES learning_plans(id),
    FOREIGN KEY (specific_goal_id) REFERENCES specific_goals(id),
    FOREIGN KEY (assigned_by) REFERENCES app_user(id),
    FOREIGN KEY (supersedes_id) REFERENCES plan_goals(id),
    CONSTRAINT ck_plan_goal_status CHECK (status IN ('NOT_STARTED','IN_PROGRESS','ACHIEVED','PAUSED')),
    CONSTRAINT ck_plan_goal_snapshots CHECK (JSON_TYPE(goal_snapshot)='OBJECT' AND JSON_TYPE(criteria_snapshot)='OBJECT'),
    CONSTRAINT ck_plan_goal_predecessor CHECK (supersedes_id IS NULL OR supersedes_id<>id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE progress_records (
    id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin PRIMARY KEY,
    plan_goal_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    observed_at DATETIME(6) NOT NULL COMMENT 'UTC',
    session_key VARCHAR(100) COMMENT 'Business reference only; not a class_session FK',
    success_count INT,
    opportunity_count INT,
    observation_minutes DECIMAL(10,2),
    support_level VARCHAR(30) CHARACTER SET ascii COLLATE ascii_bin,
    partner_reference VARCHAR(100) COLLATE utf8mb4_0900_as_cs,
    setting VARCHAR(20) CHARACTER SET ascii COLLATE ascii_bin,
    notes TEXT,
    recorded_by CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    KEY ix_progress_goal_time (plan_goal_id,observed_at),
    KEY ix_progress_actor_time (recorded_by,observed_at),
    FOREIGN KEY (plan_goal_id) REFERENCES plan_goals(id),
    FOREIGN KEY (recorded_by) REFERENCES app_user(id),
    CONSTRAINT ck_progress_counts CHECK ((success_count IS NULL OR success_count>=0) AND
        (opportunity_count IS NULL OR opportunity_count>=0) AND
        (success_count IS NULL OR opportunity_count IS NULL OR success_count<=opportunity_count)),
    CONSTRAINT ck_progress_minutes CHECK (observation_minutes IS NULL OR observation_minutes>=0),
    CONSTRAINT ck_progress_support CHECK (support_level IS NULL OR support_level IN ('INDEPENDENT','VERBAL','GESTURAL','MODEL','PARTIAL_PHYSICAL','FULL_PHYSICAL')),
    CONSTRAINT ck_progress_setting CHECK (setting IS NULL OR setting IN ('HOME','CLASSROOM','COMMUNITY','OTHER'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
