-- Duong An: one organization, multiple branches; PostgreSQL 17/18.
-- Run once on an empty database; migration owner must be allowed to install btree_gist.
CREATE EXTENSION IF NOT EXISTS btree_gist;
CREATE SCHEMA duong_an;
SET search_path TO duong_an, public;

CREATE TABLE center (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    code varchar(30) NOT NULL UNIQUE,
    name varchar(200) NOT NULL,
    timezone varchar(60) NOT NULL DEFAULT 'Asia/Ho_Chi_Minh',
    address text,
    created_at timestamptz NOT NULL DEFAULT now()
);
-- This version intentionally serves ONE organization, not SaaS tenants.
CREATE UNIQUE INDEX one_center_only ON center ((true));

CREATE TABLE branch (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    center_id uuid NOT NULL REFERENCES center(id),
    code varchar(30) NOT NULL UNIQUE,
    name varchar(200) NOT NULL,
    address text,
    phone varchar(30),
    status varchar(20) NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE','INACTIVE')),
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE app_user (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    email varchar(254) NOT NULL CHECK (email = lower(btrim(email))),
    password_hash text NOT NULL,
    display_name varchar(150) NOT NULL,
    role varchar(10) NOT NULL CHECK (role IN ('ADMIN','TEACHER','PARENT')),
    status varchar(20) NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE','LOCKED','DISABLED')),
    token_version integer NOT NULL DEFAULT 0 CHECK (token_version >= 0),
    last_login_at timestamptz,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    UNIQUE (email),
    UNIQUE (id, role)
);

CREATE TABLE staff (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    code varchar(30) NOT NULL UNIQUE,
    user_id uuid UNIQUE,
    account_role varchar(10) NOT NULL DEFAULT 'TEACHER' CHECK (account_role IN ('ADMIN','TEACHER')),
    home_branch_id uuid NOT NULL REFERENCES branch(id),
    full_name varchar(150) NOT NULL,
    birth_date date,
    phone varchar(30),
    email varchar(254),
    specialty varchar(150),
    hired_on date NOT NULL,
    ended_on date,
    status varchar(20) NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE','ON_LEAVE','TERMINATED')),
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    FOREIGN KEY (user_id, account_role) REFERENCES app_user(id, role),
    CHECK (ended_on IS NULL OR ended_on >= hired_on)
);

CREATE TABLE guardian (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    code varchar(30) NOT NULL UNIQUE,
    user_id uuid UNIQUE,
    account_role varchar(10) NOT NULL DEFAULT 'PARENT' CHECK (account_role = 'PARENT'),
    full_name varchar(150) NOT NULL,
    phone varchar(30),
    email varchar(254),
    address text,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    FOREIGN KEY (user_id, account_role) REFERENCES app_user(id, role)
);

CREATE TABLE student (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    code varchar(30) NOT NULL UNIQUE,
    branch_id uuid NOT NULL REFERENCES branch(id),
    full_name varchar(150) NOT NULL,
    birth_date date NOT NULL,
    admitted_on date NOT NULL,
    status varchar(20) NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE','PAUSED','WITHDRAWN')),
    withdrawn_on date,
    notes text,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    CHECK (birth_date <= admitted_on),
    CHECK (withdrawn_on IS NULL OR withdrawn_on >= admitted_on),
    CHECK ((status = 'WITHDRAWN') = (withdrawn_on IS NOT NULL))
);

CREATE TABLE student_guardian (
    student_id uuid NOT NULL REFERENCES student(id),
    guardian_id uuid NOT NULL REFERENCES guardian(id),
    relationship varchar(30) NOT NULL CHECK (relationship IN ('MOTHER','FATHER','GUARDIAN','OTHER')),
    is_primary boolean NOT NULL DEFAULT false,
    can_view_portal boolean NOT NULL DEFAULT true,
    can_pick_up boolean NOT NULL DEFAULT false,
    linked_at timestamptz NOT NULL DEFAULT now(),
    revoked_at timestamptz,
    PRIMARY KEY (student_id, guardian_id),
    CHECK (revoked_at IS NULL OR revoked_at >= linked_at)
);
CREATE UNIQUE INDEX one_primary_guardian ON student_guardian(student_id)
    WHERE is_primary AND revoked_at IS NULL;
CREATE INDEX student_guardian_guardian_idx ON student_guardian(guardian_id) WHERE revoked_at IS NULL;

CREATE TABLE student_case_assignment (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    student_id uuid NOT NULL REFERENCES student(id),
    staff_id uuid NOT NULL REFERENCES staff(id),
    starts_on date NOT NULL,
    ends_on date,
    assigned_by uuid NOT NULL REFERENCES app_user(id),
    created_at timestamptz NOT NULL DEFAULT now(),
    CHECK (ends_on IS NULL OR ends_on > starts_on),
    EXCLUDE USING gist (student_id WITH =, daterange(starts_on, ends_on, '[)') WITH &&)
);
CREATE INDEX case_assignment_staff_idx ON student_case_assignment(staff_id, starts_on);

CREATE TABLE staff_certificate (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    staff_id uuid NOT NULL REFERENCES staff(id),
    title varchar(200) NOT NULL,
    issuer varchar(200),
    issued_on date,
    expires_on date,
    storage_key text,
    created_at timestamptz NOT NULL DEFAULT now(),
    CHECK (issued_on IS NULL OR expires_on IS NULL OR expires_on >= issued_on)
);
CREATE INDEX staff_certificate_staff_idx ON staff_certificate(staff_id);

CREATE TABLE staff_salary (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    staff_id uuid NOT NULL REFERENCES staff(id),
    amount numeric(14,0) NOT NULL CHECK (amount >= 0),
    currency char(3) NOT NULL DEFAULT 'VND' CHECK (currency = 'VND'),
    starts_on date NOT NULL,
    ends_on date,
    created_by uuid NOT NULL REFERENCES app_user(id),
    created_at timestamptz NOT NULL DEFAULT now(),
    CHECK (ends_on IS NULL OR ends_on > starts_on),
    EXCLUDE USING gist (staff_id WITH =, daterange(starts_on, ends_on, '[)') WITH &&)
);

CREATE TABLE room (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    branch_id uuid NOT NULL REFERENCES branch(id),
    code varchar(30) NOT NULL,
    name varchar(100) NOT NULL,
    capacity integer NOT NULL CHECK (capacity > 0),
    status varchar(20) NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE','INACTIVE')),
    UNIQUE (branch_id, code),
    UNIQUE (id, branch_id)
);

CREATE TABLE learning_class (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    branch_id uuid NOT NULL REFERENCES branch(id),
    code varchar(30) NOT NULL UNIQUE,
    name varchar(150) NOT NULL,
    type varchar(20) NOT NULL CHECK (type IN ('INDIVIDUAL','GROUP','DAYCARE')),
    capacity integer NOT NULL CHECK (capacity > 0),
    status varchar(20) NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE','PAUSED','CLOSED')),
    opened_on date NOT NULL,
    closed_on date,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    UNIQUE (id, branch_id),
    CHECK (type <> 'INDIVIDUAL' OR capacity = 1),
    CHECK (closed_on IS NULL OR closed_on >= opened_on)
);

CREATE TABLE class_teacher_assignment (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    class_id uuid NOT NULL REFERENCES learning_class(id),
    staff_id uuid NOT NULL REFERENCES staff(id),
    duty varchar(20) NOT NULL CHECK (duty IN ('LEAD','ASSISTANT')),
    starts_on date NOT NULL,
    ends_on date,
    created_at timestamptz NOT NULL DEFAULT now(),
    CHECK (ends_on IS NULL OR ends_on > starts_on),
    EXCLUDE USING gist (class_id WITH =, staff_id WITH =, daterange(starts_on, ends_on, '[)') WITH &&),
    EXCLUDE USING gist (class_id WITH =, daterange(starts_on, ends_on, '[)') WITH &&) WHERE (duty = 'LEAD')
);
CREATE INDEX class_assignment_staff_idx ON class_teacher_assignment(staff_id, starts_on);

CREATE TABLE enrollment (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    class_id uuid NOT NULL REFERENCES learning_class(id),
    student_id uuid NOT NULL REFERENCES student(id),
    starts_on date NOT NULL,
    ends_on date,
    status varchar(20) NOT NULL DEFAULT 'ENROLLED' CHECK (status IN ('ENROLLED','ENDED','CANCELLED')),
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    UNIQUE (id, class_id, student_id),
    CHECK (ends_on IS NULL OR ends_on > starts_on),
    CHECK (status <> 'ENDED' OR ends_on IS NOT NULL),
    EXCLUDE USING gist (class_id WITH =, student_id WITH =, daterange(starts_on, ends_on, '[)') WITH &&)
        WHERE (status <> 'CANCELLED')
);
CREATE INDEX enrollment_student_idx ON enrollment(student_id, starts_on);

CREATE TABLE class_session (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    class_id uuid NOT NULL,
    branch_id uuid NOT NULL,
    room_id uuid NOT NULL,
    starts_at timestamptz NOT NULL,
    ends_at timestamptz NOT NULL,
    status varchar(20) NOT NULL DEFAULT 'SCHEDULED' CHECK (status IN ('SCHEDULED','COMPLETED','CANCELLED')),
    change_reason text,
    created_by uuid NOT NULL REFERENCES app_user(id),
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    UNIQUE (id, class_id),
    FOREIGN KEY (class_id, branch_id) REFERENCES learning_class(id, branch_id),
    FOREIGN KEY (room_id, branch_id) REFERENCES room(id, branch_id),
    CHECK (ends_at > starts_at),
    EXCLUDE USING gist (class_id WITH =, tstzrange(starts_at, ends_at, '[)') WITH &&)
        WHERE (status <> 'CANCELLED') DEFERRABLE INITIALLY IMMEDIATE,
    EXCLUDE USING gist (room_id WITH =, tstzrange(starts_at, ends_at, '[)') WITH &&)
        WHERE (status <> 'CANCELLED') DEFERRABLE INITIALLY IMMEDIATE
);
CREATE INDEX session_branch_time_idx ON class_session(branch_id, starts_at);

-- Concrete session roster: changing class assignments later does not rewrite history.
-- starts_at/ends_at/cancelled are maintained by V2 triggers, never accepted from API DTOs.
CREATE TABLE session_teacher (
    session_id uuid NOT NULL REFERENCES class_session(id),
    staff_id uuid NOT NULL REFERENCES staff(id),
    duty varchar(20) NOT NULL DEFAULT 'LEAD' CHECK (duty IN ('LEAD','ASSISTANT','SUBSTITUTE')),
    starts_at timestamptz NOT NULL,
    ends_at timestamptz NOT NULL,
    cancelled boolean NOT NULL,
    PRIMARY KEY (session_id, staff_id),
    CHECK (ends_at > starts_at),
    EXCLUDE USING gist (staff_id WITH =, tstzrange(starts_at, ends_at, '[)') WITH &&)
        WHERE (NOT cancelled) DEFERRABLE INITIALLY IMMEDIATE
);
CREATE INDEX session_teacher_staff_idx ON session_teacher(staff_id, starts_at);

CREATE TABLE session_student (
    session_id uuid NOT NULL,
    class_id uuid NOT NULL,
    student_id uuid NOT NULL,
    enrollment_id uuid NOT NULL,
    starts_at timestamptz NOT NULL,
    ends_at timestamptz NOT NULL,
    cancelled boolean NOT NULL,
    PRIMARY KEY (session_id, student_id),
    FOREIGN KEY (session_id, class_id) REFERENCES class_session(id, class_id),
    FOREIGN KEY (enrollment_id, class_id, student_id) REFERENCES enrollment(id, class_id, student_id),
    CHECK (ends_at > starts_at),
    EXCLUDE USING gist (student_id WITH =, tstzrange(starts_at, ends_at, '[)') WITH &&)
        WHERE (NOT cancelled) DEFERRABLE INITIALLY IMMEDIATE
);
CREATE INDEX session_student_student_idx ON session_student(student_id, starts_at);
CREATE INDEX session_student_enrollment_idx ON session_student(enrollment_id);

CREATE TABLE student_attendance (
    session_id uuid NOT NULL,
    student_id uuid NOT NULL,
    status varchar(20) NOT NULL CHECK (status IN ('PRESENT','ABSENT','EXCUSED','LATE')),
    note text,
    recorded_by uuid NOT NULL REFERENCES app_user(id),
    recorded_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (session_id, student_id),
    FOREIGN KEY (session_id, student_id) REFERENCES session_student(session_id, student_id)
);

CREATE TABLE staff_checkin (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    staff_id uuid NOT NULL REFERENCES staff(id),
    branch_id uuid NOT NULL REFERENCES branch(id),
    work_date date NOT NULL,
    checked_in_at timestamptz NOT NULL,
    checked_out_at timestamptz,
    recorded_by uuid NOT NULL REFERENCES app_user(id),
    correction_reason text,
    updated_at timestamptz NOT NULL DEFAULT now(),
    UNIQUE (staff_id, work_date),
    CHECK (checked_out_at IS NULL OR checked_out_at >= checked_in_at)
);

CREATE TABLE leave_request (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    staff_id uuid NOT NULL REFERENCES staff(id),
    starts_on date NOT NULL,
    ends_on date NOT NULL,
    reason text NOT NULL CHECK (length(btrim(reason)) > 0),
    status varchar(20) NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING','APPROVED','REJECTED','CANCELLED')),
    requested_by uuid NOT NULL REFERENCES app_user(id),
    reviewed_by uuid REFERENCES app_user(id),
    reviewed_at timestamptz,
    review_note text,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    CHECK (ends_on >= starts_on),
    CHECK ((status IN ('APPROVED','REJECTED')) = (reviewed_by IS NOT NULL AND reviewed_at IS NOT NULL)),
    CHECK (reviewed_by IS NULL OR reviewed_by <> requested_by),
    EXCLUDE USING gist (staff_id WITH =, daterange(starts_on, ends_on, '[]') WITH &&)
        WHERE (status = 'APPROVED')
);
CREATE INDEX leave_status_date_idx ON leave_request(status, starts_on);

CREATE TABLE invoice (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    code varchar(30) NOT NULL UNIQUE,
    student_id uuid NOT NULL REFERENCES student(id),
    branch_id uuid NOT NULL REFERENCES branch(id),
    billing_month date NOT NULL CHECK (extract(day FROM billing_month) = 1),
    issued_on date NOT NULL,
    due_on date NOT NULL,
    status varchar(20) NOT NULL DEFAULT 'DRAFT' CHECK (status IN ('DRAFT','ISSUED','VOID')),
    currency char(3) NOT NULL DEFAULT 'VND' CHECK (currency = 'VND'),
    created_by uuid NOT NULL REFERENCES app_user(id),
    void_reason text,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    CHECK (due_on >= issued_on),
    CHECK (status <> 'VOID' OR nullif(btrim(void_reason),'') IS NOT NULL)
);
CREATE INDEX invoice_student_month_idx ON invoice(student_id, billing_month);
CREATE INDEX invoice_due_idx ON invoice(due_on) WHERE status = 'ISSUED';

CREATE TABLE invoice_line (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    invoice_id uuid NOT NULL REFERENCES invoice(id),
    description varchar(250) NOT NULL,
    quantity integer NOT NULL DEFAULT 1 CHECK (quantity > 0),
    unit_price numeric(14,0) NOT NULL CHECK (unit_price >= 0),
    discount numeric(14,0) NOT NULL DEFAULT 0 CHECK (discount >= 0),
    line_total numeric(16,0) GENERATED ALWAYS AS (quantity * unit_price - discount) STORED,
    CHECK (discount <= quantity * unit_price)
);
CREATE INDEX invoice_line_invoice_idx ON invoice_line(invoice_id);

CREATE TABLE payment (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    receipt_code varchar(40) NOT NULL UNIQUE,
    invoice_id uuid NOT NULL REFERENCES invoice(id),
    amount numeric(16,0) NOT NULL CHECK (amount > 0),
    method varchar(20) NOT NULL CHECK (method IN ('CASH','BANK_TRANSFER','OTHER')),
    status varchar(20) NOT NULL DEFAULT 'POSTED' CHECK (status IN ('POSTED','VOID')),
    paid_at timestamptz NOT NULL,
    reference varchar(150),
    idempotency_key uuid NOT NULL UNIQUE,
    received_by uuid NOT NULL REFERENCES app_user(id),
    voided_by uuid REFERENCES app_user(id),
    voided_at timestamptz,
    void_reason text,
    created_at timestamptz NOT NULL DEFAULT now(),
    CHECK ((status = 'VOID') = (voided_by IS NOT NULL AND voided_at IS NOT NULL AND nullif(btrim(void_reason),'') IS NOT NULL))
);
CREATE INDEX payment_invoice_idx ON payment(invoice_id, paid_at);

-- Totals are derived independently before joining; no multiplication of line/payment rows.
CREATE VIEW invoice_balance AS
WITH charges AS (
    SELECT invoice_id, sum(line_total) AS total FROM invoice_line GROUP BY invoice_id
), receipts AS (
    SELECT invoice_id, sum(amount) AS paid FROM payment WHERE status = 'POSTED' GROUP BY invoice_id
)
SELECT i.id AS invoice_id, i.student_id, i.billing_month, i.status,
       coalesce(c.total,0) AS total_amount, coalesce(p.paid,0) AS paid_amount,
       coalesce(c.total,0)-coalesce(p.paid,0) AS outstanding_amount,
       CASE WHEN i.status = 'VOID' THEN 'VOID'
            WHEN i.status = 'DRAFT' THEN 'DRAFT'
            WHEN coalesce(p.paid,0) >= coalesce(c.total,0) THEN 'PAID'
            WHEN coalesce(p.paid,0) > 0 THEN 'PARTIAL'
            ELSE 'UNPAID' END AS payment_status
FROM invoice i LEFT JOIN charges c ON c.invoice_id=i.id LEFT JOIN receipts p ON p.invoice_id=i.id;

CREATE TABLE notification (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    branch_id uuid REFERENCES branch(id),
    title varchar(200) NOT NULL,
    body text NOT NULL,
    status varchar(20) NOT NULL DEFAULT 'DRAFT' CHECK (status IN ('DRAFT','PUBLISHED','ARCHIVED')),
    created_by uuid NOT NULL REFERENCES app_user(id),
    published_at timestamptz,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    CHECK (status <> 'PUBLISHED' OR published_at IS NOT NULL)
);
CREATE TABLE notification_recipient (
    notification_id uuid NOT NULL REFERENCES notification(id),
    user_id uuid NOT NULL REFERENCES app_user(id),
    read_at timestamptz,
    PRIMARY KEY (notification_id, user_id)
);
CREATE INDEX notification_recipient_user_idx ON notification_recipient(user_id, read_at);

CREATE TABLE document (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    branch_id uuid REFERENCES branch(id),
    title varchar(250) NOT NULL,
    category varchar(20) NOT NULL CHECK (category IN ('INTERNAL','REFERENCE','FORM')),
    storage_key text,
    external_url text,
    mime_type varchar(100),
    byte_size bigint CHECK (byte_size >= 0),
    uploaded_by uuid NOT NULL REFERENCES app_user(id),
    archived_at timestamptz,
    created_at timestamptz NOT NULL DEFAULT now(),
    CHECK ((storage_key IS NOT NULL) <> (external_url IS NOT NULL))
);

CREATE TABLE branch_holiday (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    branch_id uuid NOT NULL REFERENCES branch(id),
    name varchar(150) NOT NULL,
    starts_on date NOT NULL,
    ends_on date NOT NULL,
    CHECK (ends_on >= starts_on)
);
CREATE INDEX branch_holiday_dates_idx ON branch_holiday(branch_id, starts_on, ends_on);

CREATE TABLE student_transfer (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    student_id uuid NOT NULL REFERENCES student(id),
    from_branch_id uuid NOT NULL REFERENCES branch(id),
    to_branch_id uuid NOT NULL REFERENCES branch(id),
    effective_on date NOT NULL,
    status varchar(20) NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING','COMPLETED','CANCELLED')),
    reason text,
    requested_by uuid NOT NULL REFERENCES app_user(id),
    completed_by uuid REFERENCES app_user(id),
    completed_at timestamptz,
    created_at timestamptz NOT NULL DEFAULT now(),
    CHECK (from_branch_id <> to_branch_id),
    CHECK ((status = 'COMPLETED') = (completed_by IS NOT NULL AND completed_at IS NOT NULL))
);
CREATE INDEX student_transfer_student_idx ON student_transfer(student_id, effective_on);

CREATE TABLE daycare_daily_log (
    session_id uuid NOT NULL,
    student_id uuid NOT NULL,
    meal_note text,
    sleep_start timestamptz,
    sleep_end timestamptz,
    activity_note text,
    recorded_by uuid NOT NULL REFERENCES app_user(id),
    updated_at timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (session_id, student_id),
    FOREIGN KEY (session_id, student_id) REFERENCES session_student(session_id, student_id),
    CHECK ((sleep_start IS NULL AND sleep_end IS NULL) OR
           (sleep_start IS NOT NULL AND sleep_end IS NOT NULL AND sleep_end > sleep_start))
);

CREATE TABLE audit_event (
    id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    actor_id uuid REFERENCES app_user(id),
    action varchar(60) NOT NULL,
    entity_type varchar(60) NOT NULL,
    entity_id uuid,
    request_id uuid,
    detail jsonb NOT NULL DEFAULT '{}'::jsonb,
    occurred_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX audit_entity_idx ON audit_event(entity_type, entity_id, occurred_at DESC);
CREATE INDEX student_branch_status_idx ON student(branch_id, status);
CREATE INDEX staff_branch_status_idx ON staff(home_branch_id, status);
CREATE INDEX class_branch_type_idx ON learning_class(branch_id, type, status);
