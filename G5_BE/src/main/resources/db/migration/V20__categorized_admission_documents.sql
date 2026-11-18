CREATE TABLE admission_profile_documents (
    id UUID PRIMARY KEY,
    consultation_id UUID NOT NULL REFERENCES consultation_registrations(id),
    document_type VARCHAR(40) NOT NULL CHECK (document_type IN (
        'IDENTITY_CARD','CURRICULUM_VITAE','BIRTH_CERTIFICATE','ID_PHOTO','APPLICATION_FORM','LEGACY_SCAN'
    )),
    file_id UUID NOT NULL REFERENCES workflow_files(id),
    submitted_by BIGINT NOT NULL REFERENCES m_user(user_id),
    display_order INTEGER NOT NULL DEFAULT 0 CHECK (display_order>=0),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (consultation_id,file_id)
);

-- Retain older scans without guessing their document category or changing their bytes.
INSERT INTO admission_profile_documents(id,consultation_id,document_type,file_id,submitted_by,created_at)
SELECT f.id,f.consultation_id,'LEGACY_SCAN',f.scan_file_id,w.uploaded_by,COALESCE(f.profile_submitted_at,w.created_at)
FROM consultation_followups f JOIN workflow_files w ON w.id=f.scan_file_id;
