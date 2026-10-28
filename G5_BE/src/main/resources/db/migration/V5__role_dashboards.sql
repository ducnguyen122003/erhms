-- Dashboard sources. Only role definitions are seeded; operational records are never fabricated.
INSERT INTO m_role (code, name) SELECT 'ADMIN', 'Quản trị viên' WHERE NOT EXISTS (SELECT 1 FROM m_role WHERE UPPER(code) = 'ADMIN');
INSERT INTO m_role (code, name) SELECT 'LECTURER', 'Giảng viên' WHERE NOT EXISTS (SELECT 1 FROM m_role WHERE UPPER(code) = 'LECTURER');
INSERT INTO m_role (code, name) SELECT 'STUDENT', 'Học viên' WHERE NOT EXISTS (SELECT 1 FROM m_role WHERE UPPER(code) = 'STUDENT');
INSERT INTO m_role (code, name) SELECT 'ACCOUNTANT', 'Kế toán' WHERE NOT EXISTS (SELECT 1 FROM m_role WHERE UPPER(code) = 'ACCOUNTANT');
INSERT INTO m_role (code, name) SELECT 'IT_SUPPORT', 'Hỗ trợ thiết bị' WHERE NOT EXISTS (SELECT 1 FROM m_role WHERE UPPER(code) = 'IT_SUPPORT');
INSERT INTO m_role (code, name) SELECT 'MANAGER', 'Quản lý học viên' WHERE NOT EXISTS (SELECT 1 FROM m_role WHERE UPPER(code) = 'MANAGER');
INSERT INTO m_role (code, name) SELECT 'HR', 'Tuyển sinh và tuyển dụng' WHERE NOT EXISTS (SELECT 1 FROM m_role WHERE UPPER(code) = 'HR');

ALTER TABLE m_user ADD COLUMN account_enabled BOOLEAN NOT NULL DEFAULT TRUE;
ALTER TABLE m_user ADD COLUMN account_locked BOOLEAN NOT NULL DEFAULT FALSE;

CREATE TABLE user_login_events (
    id UUID PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES m_user(user_id) ON DELETE CASCADE,
    occurred_at TIMESTAMP WITH TIME ZONE NOT NULL
);
CREATE INDEX idx_login_events_occurred ON user_login_events(occurred_at, user_id);

CREATE TABLE training_classes (
    id UUID PRIMARY KEY,
    code VARCHAR(40) NOT NULL UNIQUE,
    name VARCHAR(160) NOT NULL,
    lecturer_id BIGINT REFERENCES m_user(user_id),
    starts_on DATE,
    ends_on DATE,
    status VARCHAR(20) NOT NULL CHECK (status IN ('PLANNED', 'ACTIVE', 'COMPLETED', 'CANCELLED')),
    total_sessions INTEGER NOT NULL DEFAULT 0 CHECK (total_sessions >= 0),
    CHECK (starts_on IS NULL OR ends_on IS NULL OR ends_on >= starts_on)
);
CREATE INDEX idx_training_classes_lecturer ON training_classes(lecturer_id, status);

CREATE TABLE training_enrollments (
    id UUID PRIMARY KEY,
    class_id UUID NOT NULL REFERENCES training_classes(id),
    student_id BIGINT NOT NULL REFERENCES m_user(user_id),
    status VARCHAR(20) NOT NULL CHECK (status IN ('PENDING', 'ACTIVE', 'COMPLETED', 'WITHDRAWN')),
    assignment_status VARCHAR(20) NOT NULL DEFAULT 'PENDING' CHECK (assignment_status IN ('PENDING', 'CONFIRMED')),
    records_complete BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (class_id, student_id)
);
CREATE INDEX idx_training_enrollments_student ON training_enrollments(student_id, status);

CREATE TABLE training_sessions (
    id UUID PRIMARY KEY,
    class_id UUID NOT NULL REFERENCES training_classes(id),
    title VARCHAR(160) NOT NULL,
    starts_at TIMESTAMP WITH TIME ZONE NOT NULL,
    ends_at TIMESTAMP WITH TIME ZONE NOT NULL,
    room VARCHAR(100),
    attendance_finalized BOOLEAN NOT NULL DEFAULT FALSE,
    CHECK (ends_at > starts_at)
);
CREATE INDEX idx_training_sessions_class_start ON training_sessions(class_id, starts_at);

CREATE TABLE training_attendance (
    id UUID PRIMARY KEY,
    session_id UUID NOT NULL REFERENCES training_sessions(id),
    student_id BIGINT NOT NULL REFERENCES m_user(user_id),
    status VARCHAR(20) NOT NULL CHECK (status IN ('PRESENT', 'ABSENT', 'EXCUSED', 'LATE')),
    UNIQUE (session_id, student_id)
);
CREATE INDEX idx_training_attendance_student ON training_attendance(student_id, session_id);

CREATE TABLE training_grades (
    id UUID PRIMARY KEY,
    enrollment_id UUID NOT NULL REFERENCES training_enrollments(id),
    title VARCHAR(160) NOT NULL,
    score NUMERIC(4,2) CHECK (score BETWEEN 0 AND 10),
    published_at TIMESTAMP WITH TIME ZONE
);
CREATE INDEX idx_training_grades_enrollment ON training_grades(enrollment_id, published_at);

CREATE TABLE training_contracts (
    id UUID PRIMARY KEY,
    code VARCHAR(40) NOT NULL UNIQUE,
    student_id BIGINT NOT NULL REFERENCES m_user(user_id),
    consultation_id UUID REFERENCES consultation_registrations(id),
    tuition_due NUMERIC(18,2) NOT NULL DEFAULT 0 CHECK (tuition_due >= 0),
    deposit_due NUMERIC(18,2) NOT NULL DEFAULT 0 CHECK (deposit_due >= 0),
    status VARCHAR(20) NOT NULL CHECK (status IN ('DRAFT', 'SIGNED', 'ACTIVE', 'COMPLETED', 'CANCELLED')),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_training_contracts_student ON training_contracts(student_id, status);

CREATE TABLE financial_transactions (
    id UUID PRIMARY KEY,
    contract_id UUID NOT NULL REFERENCES training_contracts(id),
    kind VARCHAR(20) NOT NULL CHECK (kind IN ('TUITION', 'DEPOSIT', 'REFUND', 'COMPENSATION')),
    amount NUMERIC(18,2) NOT NULL CHECK (amount > 0),
    status VARCHAR(20) NOT NULL CHECK (status IN ('PENDING', 'CONFIRMED', 'MISMATCH')),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_financial_transactions_status ON financial_transactions(status, kind, created_at);

CREATE TABLE equipment_assets (
    id UUID PRIMARY KEY,
    code VARCHAR(40) NOT NULL UNIQUE,
    name VARCHAR(160) NOT NULL,
    status VARCHAR(20) NOT NULL CHECK (status IN ('AVAILABLE', 'ASSIGNED', 'MAINTENANCE', 'RETIRED'))
);

CREATE TABLE equipment_requests (
    id UUID PRIMARY KEY,
    requested_by BIGINT NOT NULL REFERENCES m_user(user_id),
    class_id UUID REFERENCES training_classes(id),
    title VARCHAR(160) NOT NULL,
    quantity INTEGER NOT NULL CHECK (quantity > 0),
    status VARCHAR(20) NOT NULL CHECK (status IN ('NEW', 'PREPARING', 'READY', 'DELIVERED', 'REJECTED')),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_equipment_requests_owner ON equipment_requests(requested_by, status);

CREATE TABLE equipment_loans (
    id UUID PRIMARY KEY,
    asset_id UUID NOT NULL REFERENCES equipment_assets(id),
    student_id BIGINT NOT NULL REFERENCES m_user(user_id),
    class_id UUID REFERENCES training_classes(id),
    received_at TIMESTAMP WITH TIME ZONE,
    return_due_at TIMESTAMP WITH TIME ZONE,
    returned_at TIMESTAMP WITH TIME ZONE,
    deposit_amount NUMERIC(18,2) NOT NULL DEFAULT 0 CHECK (deposit_amount >= 0),
    return_status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' CHECK (return_status IN ('ACTIVE', 'RETURN_DUE', 'RETURNED')),
    CHECK (returned_at IS NULL OR received_at IS NULL OR returned_at >= received_at)
);
CREATE INDEX idx_equipment_loans_student ON equipment_loans(student_id, return_status);

CREATE TABLE equipment_incidents (
    id UUID PRIMARY KEY,
    asset_id UUID NOT NULL REFERENCES equipment_assets(id),
    reported_by BIGINT NOT NULL REFERENCES m_user(user_id),
    description VARCHAR(1000) NOT NULL,
    status VARCHAR(20) NOT NULL CHECK (status IN ('OPEN', 'IN_PROGRESS', 'RESOLVED')),
    compensation_due NUMERIC(18,2) NOT NULL DEFAULT 0 CHECK (compensation_due >= 0),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE consultation_followups (
    id UUID PRIMARY KEY,
    consultation_id UUID NOT NULL UNIQUE REFERENCES consultation_registrations(id),
    assigned_to BIGINT REFERENCES m_user(user_id),
    status VARCHAR(20) NOT NULL DEFAULT 'NEW' CHECK (status IN ('NEW', 'CONTACTED', 'APPOINTMENT', 'CONTRACTED', 'CLOSED')),
    appointment_at TIMESTAMP WITH TIME ZONE,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);
