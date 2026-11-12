-- Direct manager intake shares account/contract workflows but is not an HR admission.
ALTER TABLE consultation_registrations ADD COLUMN intake_source VARCHAR(20) NOT NULL DEFAULT 'ADMISSION'
    CHECK (intake_source IN ('ADMISSION', 'EXCEL'));
ALTER TABLE consultation_registrations ADD COLUMN imported_by BIGINT REFERENCES m_user(user_id);

CREATE INDEX idx_consultation_registrations_intake_source ON consultation_registrations(intake_source);
