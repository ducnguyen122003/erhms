-- Contracts retain the supplied email before a student login exists.
ALTER TABLE training_contracts ALTER COLUMN student_id DROP NOT NULL;
ALTER TABLE training_contracts ADD COLUMN student_email VARCHAR(190);
ALTER TABLE training_contracts ADD COLUMN applicant_name VARCHAR(160);
ALTER TABLE training_contracts ADD COLUMN applicant_phone VARCHAR(30);

UPDATE training_contracts SET
    student_email=(SELECT LOWER(TRIM(u.email)) FROM m_user u WHERE u.user_id=training_contracts.student_id),
    applicant_name=(SELECT u.full_name FROM m_user u WHERE u.user_id=training_contracts.student_id),
    applicant_phone=(SELECT u.phone FROM m_user u WHERE u.user_id=training_contracts.student_id);

ALTER TABLE training_contracts ADD CONSTRAINT ck_contract_applicant
    CHECK (student_id IS NOT NULL OR (student_email IS NOT NULL AND applicant_name IS NOT NULL));
ALTER TABLE training_contracts ADD CONSTRAINT ck_contract_account_before_signature
    CHECK (student_id IS NOT NULL OR status IN ('DRAFT','CANCELLED'));
CREATE INDEX idx_contract_student_email ON training_contracts(student_email);
