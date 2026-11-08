-- Operational schema for the supplied screen catalogue. No demonstration records are inserted.
ALTER TABLE m_function ALTER COLUMN screen_code TYPE VARCHAR(100);
CREATE TABLE training_courses (
 id UUID PRIMARY KEY, code VARCHAR(40) NOT NULL UNIQUE, name VARCHAR(160) NOT NULL,
 profession VARCHAR(120) NOT NULL, duration_months INTEGER NOT NULL CHECK(duration_months>0),
 total_sessions INTEGER NOT NULL CHECK(total_sessions>0), tuition_fee NUMERIC(18,2) NOT NULL CHECK(tuition_fee>=0),
 objectives VARCHAR(4000) NOT NULL, entry_requirements VARCHAR(2000),
 status VARCHAR(20) NOT NULL CHECK(status IN ('DRAFT','OPEN','ACTIVE','CLOSED')),
 manager_id BIGINT NOT NULL REFERENCES m_user(user_id), class_capacity INTEGER NOT NULL CHECK(class_capacity>0), version BIGINT NOT NULL DEFAULT 0,
 created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE TABLE course_assessments (
 id UUID PRIMARY KEY, course_id UUID NOT NULL REFERENCES training_courses(id), title VARCHAR(160) NOT NULL,
 weight NUMERIC(5,2) NOT NULL CHECK(weight>0 AND weight<=100), UNIQUE(course_id,title)
);
CREATE TABLE course_lecturers (
 course_id UUID NOT NULL REFERENCES training_courses(id), lecturer_id BIGINT NOT NULL REFERENCES m_user(user_id),
 PRIMARY KEY(course_id,lecturer_id)
);
CREATE TABLE student_profiles (
 user_id BIGINT PRIMARY KEY REFERENCES m_user(user_id), date_of_birth DATE, identity_number VARCHAR(40),
 address VARCHAR(500), emergency_name VARCHAR(120), emergency_phone VARCHAR(20),
 manager_id BIGINT REFERENCES m_user(user_id), version BIGINT NOT NULL DEFAULT 0
);
ALTER TABLE training_classes ADD COLUMN course_id UUID REFERENCES training_courses(id);
ALTER TABLE training_classes ADD COLUMN manager_id BIGINT REFERENCES m_user(user_id);
ALTER TABLE training_classes ADD COLUMN capacity INTEGER NOT NULL DEFAULT 20 CHECK(capacity>0);
ALTER TABLE training_classes ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE training_contracts ADD COLUMN course_id UUID REFERENCES training_courses(id);
ALTER TABLE training_contracts ADD COLUMN created_by BIGINT REFERENCES m_user(user_id);
ALTER TABLE training_contracts ADD COLUMN manager_id BIGINT REFERENCES m_user(user_id);
ALTER TABLE training_contracts ADD COLUMN accountant_id BIGINT REFERENCES m_user(user_id);
ALTER TABLE training_contracts ADD COLUMN starts_on DATE;
ALTER TABLE training_contracts ADD COLUMN payment_due_on DATE;
ALTER TABLE training_contracts ADD COLUMN payment_schedule VARCHAR(2000);
ALTER TABLE training_contracts ADD COLUMN terms VARCHAR(4000);
ALTER TABLE training_contracts ADD COLUMN signed_on DATE;
ALTER TABLE training_contracts ADD COLUMN signature_method VARCHAR(40);
ALTER TABLE training_contracts ADD COLUMN signature_evidence VARCHAR(1000);
ALTER TABLE training_contracts ADD COLUMN handover_note VARCHAR(1000);
ALTER TABLE training_contracts ADD COLUMN handed_over_at TIMESTAMP WITH TIME ZONE;
ALTER TABLE training_contracts ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE training_enrollments ADD COLUMN contract_id UUID REFERENCES training_contracts(id);
CREATE UNIQUE INDEX idx_enrollment_contract ON training_enrollments(contract_id);
ALTER TABLE training_enrollments ADD COLUMN withdrawn_on DATE;
ALTER TABLE training_enrollments ADD COLUMN withdrawal_reason VARCHAR(1000);
ALTER TABLE training_enrollments ADD COLUMN withdrawal_evidence VARCHAR(1000);
ALTER TABLE training_enrollments ADD COLUMN closed_at TIMESTAMP WITH TIME ZONE;
ALTER TABLE training_enrollments ADD COLUMN closure_outcome VARCHAR(20);
ALTER TABLE training_enrollments ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE training_sessions ADD COLUMN change_note VARCHAR(1000);
ALTER TABLE training_sessions ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE training_attendance ADD COLUMN note VARCHAR(1000);
ALTER TABLE training_attendance ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE consultation_followups ADD COLUMN channel VARCHAR(40);
ALTER TABLE consultation_followups ADD COLUMN notes VARCHAR(2000);
ALTER TABLE consultation_followups ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
CREATE TABLE consultation_notes (
 id UUID PRIMARY KEY, consultation_id UUID NOT NULL REFERENCES consultation_registrations(id),
 actor_id BIGINT NOT NULL REFERENCES m_user(user_id), channel VARCHAR(40) NOT NULL,
 outcome VARCHAR(20) NOT NULL, content VARCHAR(2000) NOT NULL,
 appointment_at TIMESTAMP WITH TIME ZONE, created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);
ALTER TABLE financial_transactions ADD COLUMN bank_reference VARCHAR(120) UNIQUE;
ALTER TABLE financial_transactions ADD COLUMN payer_name VARCHAR(160);
ALTER TABLE financial_transactions ADD COLUMN bank_name VARCHAR(160);
ALTER TABLE financial_transactions ADD COLUMN memo VARCHAR(1000);
ALTER TABLE financial_transactions ADD COLUMN evidence VARCHAR(1000);
ALTER TABLE financial_transactions ADD COLUMN confirmed_by BIGINT REFERENCES m_user(user_id);
ALTER TABLE financial_transactions ADD COLUMN confirmed_at TIMESTAMP WITH TIME ZONE;
ALTER TABLE financial_transactions ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
CREATE TABLE payment_instructions (
 contract_id UUID PRIMARY KEY REFERENCES training_contracts(id), content VARCHAR(4000) NOT NULL,
 created_by BIGINT NOT NULL REFERENCES m_user(user_id), updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
 version BIGINT NOT NULL DEFAULT 0
);
ALTER TABLE equipment_assets ADD COLUMN category VARCHAR(120);
ALTER TABLE equipment_assets ADD COLUMN serial_number VARCHAR(120);
ALTER TABLE equipment_assets ADD COLUMN purchased_on DATE;
ALTER TABLE equipment_assets ADD COLUMN recorded_value NUMERIC(18,2) NOT NULL DEFAULT 0 CHECK(recorded_value>=0);
ALTER TABLE equipment_assets ADD COLUMN asset_condition VARCHAR(500);
ALTER TABLE equipment_assets ADD COLUMN location VARCHAR(160);
ALTER TABLE equipment_assets ADD COLUMN accessories VARCHAR(1000);
ALTER TABLE equipment_assets ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE equipment_requests ADD COLUMN student_id BIGINT REFERENCES m_user(user_id);
ALTER TABLE equipment_requests ADD COLUMN request_type VARCHAR(40);
ALTER TABLE equipment_requests ADD COLUMN group_name VARCHAR(120);
ALTER TABLE equipment_requests ADD COLUMN needed_on DATE;
ALTER TABLE equipment_requests ADD COLUMN description VARCHAR(2000);
ALTER TABLE equipment_requests ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE equipment_loans ADD COLUMN request_id UUID REFERENCES equipment_requests(id);
ALTER TABLE equipment_loans ADD COLUMN contract_id UUID REFERENCES training_contracts(id);
ALTER TABLE equipment_loans ADD COLUMN handed_over_by BIGINT REFERENCES m_user(user_id);
ALTER TABLE equipment_loans ADD COLUMN receipt_evidence VARCHAR(1000);
ALTER TABLE equipment_loans ADD COLUMN return_condition VARCHAR(1000);
ALTER TABLE equipment_loans ADD COLUMN return_evidence VARCHAR(1000);
ALTER TABLE equipment_loans ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
CREATE TABLE asset_maintenance (
 id UUID PRIMARY KEY, asset_id UUID NOT NULL REFERENCES equipment_assets(id), reported_by BIGINT NOT NULL REFERENCES m_user(user_id),
 owner_id BIGINT NOT NULL REFERENCES m_user(user_id), source VARCHAR(120) NOT NULL, maintenance_type VARCHAR(80) NOT NULL,
 discovered_on DATE NOT NULL, description VARCHAR(2000) NOT NULL, due_on DATE, note VARCHAR(1000),
 status VARCHAR(20) NOT NULL DEFAULT 'OPEN', version BIGINT NOT NULL DEFAULT 0
);
ALTER TABLE equipment_incidents ADD COLUMN loan_id UUID REFERENCES equipment_loans(id);
ALTER TABLE equipment_incidents ADD COLUMN technical_note VARCHAR(2000);
ALTER TABLE equipment_incidents ADD COLUMN evidence VARCHAR(1000);
ALTER TABLE equipment_incidents ADD COLUMN approval_reference VARCHAR(1000);
ALTER TABLE equipment_incidents ADD COLUMN approved_amount NUMERIC(18,2) CHECK(approved_amount>=0);
ALTER TABLE equipment_incidents ADD COLUMN settled_at TIMESTAMP WITH TIME ZONE;
ALTER TABLE equipment_incidents ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE financial_transactions ADD COLUMN incident_id UUID REFERENCES equipment_incidents(id);
CREATE TABLE deposit_settlements (
 id UUID PRIMARY KEY, contract_id UUID NOT NULL UNIQUE REFERENCES training_contracts(id),
 deposit_amount NUMERIC(18,2) NOT NULL CHECK(deposit_amount>=0), deduction_amount NUMERIC(18,2) NOT NULL CHECK(deduction_amount>=0),
 refund_amount NUMERIC(18,2) NOT NULL CHECK(refund_amount>=0), recipient VARCHAR(160) NOT NULL,
 payment_method VARCHAR(40) NOT NULL, account_details VARCHAR(500) NOT NULL, bank_reference VARCHAR(120) NOT NULL UNIQUE,
 evidence VARCHAR(1000) NOT NULL, created_by BIGINT NOT NULL REFERENCES m_user(user_id),
 created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
 CHECK(deposit_amount=deduction_amount+refund_amount)
);
CREATE TABLE user_notifications (
 id UUID PRIMARY KEY, recipient_id BIGINT NOT NULL REFERENCES m_user(user_id),
 sender_id BIGINT REFERENCES m_user(user_id), title VARCHAR(160) NOT NULL, content VARCHAR(4000) NOT NULL,
 target_screen VARCHAR(60), target_id VARCHAR(100), scheduled_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
 read_at TIMESTAMP WITH TIME ZONE, created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_notifications_recipient ON user_notifications(recipient_id,scheduled_at);
CREATE INDEX idx_contract_responsibility ON training_contracts(created_by,manager_id,accountant_id);
CREATE INDEX idx_class_manager ON training_classes(manager_id,course_id);
CREATE INDEX idx_loan_contract ON equipment_loans(contract_id,return_status);
CREATE TABLE workflow_files (
 id UUID PRIMARY KEY, uploaded_by BIGINT NOT NULL REFERENCES m_user(user_id), filename VARCHAR(160) NOT NULL,
 content_type VARCHAR(80) NOT NULL, file_size BIGINT NOT NULL CHECK(file_size>0 AND file_size<=2097152),
 file_data BYTEA NOT NULL, created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);
