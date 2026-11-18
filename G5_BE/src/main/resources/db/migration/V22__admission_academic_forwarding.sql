ALTER TABLE consultation_followups ADD COLUMN forwarded_at TIMESTAMP WITH TIME ZONE;
ALTER TABLE consultation_followups ADD COLUMN forwarded_by BIGINT REFERENCES m_user(user_id);
ALTER TABLE consultation_followups ADD CONSTRAINT ck_admission_forwarding_actor
    CHECK (forwarded_by IS NULL OR forwarded_at IS NOT NULL);
CREATE INDEX idx_admission_forwarded_at ON consultation_followups(forwarded_at);

-- Keep cases already processed by academic management available after upgrading.
-- Unprocessed PROFILE_PENDING cases remain waiting for an explicit HR handoff.
UPDATE consultation_followups SET
    forwarded_at=COALESCE(profile_submitted_at,updated_at,CURRENT_TIMESTAMP),
    forwarded_by=assigned_to
WHERE EXISTS (SELECT 1 FROM consultation_registrations r
              WHERE r.id=consultation_followups.consultation_id AND r.intake_source='ADMISSION')
  AND (status IN ('CONTRACTED','PROFILE_CREATED') OR EXISTS (
       SELECT 1 FROM training_contracts c WHERE c.consultation_id=consultation_followups.consultation_id
       AND c.status<>'CANCELLED'));
