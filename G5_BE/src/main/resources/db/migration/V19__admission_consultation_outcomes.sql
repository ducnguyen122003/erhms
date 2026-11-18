ALTER TABLE consultation_followups DROP CONSTRAINT ck_consultation_profile_status;
ALTER TABLE consultation_followups ADD COLUMN document_due_on DATE;
ALTER TABLE consultation_followups ADD COLUMN discontinuation_reason VARCHAR(2000);
ALTER TABLE consultation_notes ADD COLUMN document_due_on DATE;
ALTER TABLE consultation_notes ADD COLUMN discontinuation_reason VARCHAR(2000);

-- Old appointments were for counselling. Preserve their callback timestamps.
-- CONTACTED remains the intake state for direct manager Excel imports only.
UPDATE consultation_followups SET discontinuation_reason=
    COALESCE(NULLIF(TRIM(notes),''),'Chưa ghi nhận lý do trong dữ liệu cũ.')
WHERE status='CLOSED';
UPDATE consultation_followups SET
    status=CASE WHEN status='CLOSED' THEN 'NOT_PROCEEDING' ELSE 'NEEDS_COUNSELING' END,
    version=version+1,updated_at=CURRENT_TIMESTAMP
WHERE status IN ('APPOINTMENT','CLOSED') OR (status='CONTACTED' AND EXISTS (
    SELECT 1 FROM consultation_registrations r WHERE r.id=consultation_followups.consultation_id AND r.intake_source='ADMISSION'
));

UPDATE consultation_notes SET discontinuation_reason=content WHERE outcome='CLOSED';
UPDATE consultation_notes SET outcome=CASE WHEN outcome='CLOSED' THEN 'NOT_PROCEEDING' ELSE 'NEEDS_COUNSELING' END
WHERE outcome IN ('APPOINTMENT','CLOSED') OR (outcome='CONTACTED' AND EXISTS (
    SELECT 1 FROM consultation_registrations r WHERE r.id=consultation_notes.consultation_id AND r.intake_source='ADMISSION'
));

ALTER TABLE consultation_followups ADD CONSTRAINT ck_consultation_profile_status CHECK (
    status IN ('NEW','CONTACTED','NEEDS_COUNSELING','DOCUMENT_APPOINTMENT','NOT_PROCEEDING','PROFILE_PENDING','PROFILE_CREATED','CONTRACTED')
);
