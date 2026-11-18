-- A confirmed admission program is sufficient to create a contract.
-- Operational class/session configuration can be linked separately later.
ALTER TABLE training_contracts ADD COLUMN registered_course_name VARCHAR(255);
ALTER TABLE training_contracts ADD COLUMN registered_duration_months INTEGER CHECK (registered_duration_months > 0);

-- Preserve the actual course on existing contracts, including negotiated legacy cases.
UPDATE training_contracts SET
    registered_course_name=(SELECT name FROM training_courses WHERE id=training_contracts.course_id),
    registered_duration_months=(SELECT duration_months FROM training_courses WHERE id=training_contracts.course_id)
WHERE course_id IS NOT NULL;

CREATE INDEX idx_contract_registered_course ON training_contracts(consultation_id,registered_course_name);
