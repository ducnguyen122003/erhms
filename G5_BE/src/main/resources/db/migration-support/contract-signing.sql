-- Wet signatures are collected by Admissions and reviewed before provisioning.
ALTER TABLE training_contracts ADD CONSTRAINT training_contracts_status_check
    CHECK (status IN ('DRAFT','SENT_FOR_SIGNATURE','SIGNATURE_REVIEW','SIGNED','ACTIVE','COMPLETED','CANCELLED'));
ALTER TABLE training_contracts DROP CONSTRAINT ck_contract_account_before_signature;
ALTER TABLE training_contracts ADD CONSTRAINT ck_contract_account_before_activation
    CHECK (student_id IS NOT NULL OR status NOT IN ('ACTIVE','COMPLETED'));

ALTER TABLE workflow_files ADD COLUMN upload_screen VARCHAR(80);
ALTER TABLE workflow_files ADD CONSTRAINT workflow_files_file_size_check
    CHECK (file_size>0 AND file_size<=10485760);
ALTER TABLE training_contracts ADD COLUMN signature_admissions_id BIGINT REFERENCES m_user(user_id);
ALTER TABLE training_contracts ADD COLUMN signature_sent_at TIMESTAMP WITH TIME ZONE;
ALTER TABLE training_contracts ADD COLUMN print_file_id UUID REFERENCES workflow_files(id);
ALTER TABLE training_contracts ADD COLUMN signed_scan_file_id UUID REFERENCES workflow_files(id);
ALTER TABLE training_contracts ADD COLUMN signature_submitted_at TIMESTAMP WITH TIME ZONE;
ALTER TABLE training_contracts ADD COLUMN signature_submitted_by BIGINT REFERENCES m_user(user_id);
ALTER TABLE training_contracts ADD COLUMN signature_approved_at TIMESTAMP WITH TIME ZONE;
ALTER TABLE training_contracts ADD COLUMN signature_approved_by BIGINT REFERENCES m_user(user_id);
ALTER TABLE training_contracts ADD COLUMN signature_review_note VARCHAR(1000);

-- Existing signed contracts keep their previously completed approval and accounts.
UPDATE training_contracts SET signature_approved_at=COALESCE(handed_over_at,created_at),
    signature_approved_by=COALESCE(manager_id,created_by)
WHERE status IN ('SIGNED','ACTIVE','COMPLETED');

CREATE TABLE contract_signing_history (
    id UUID PRIMARY KEY,
    contract_id UUID NOT NULL REFERENCES training_contracts(id),
    actor_id BIGINT NOT NULL REFERENCES m_user(user_id),
    action VARCHAR(20) NOT NULL CHECK (action IN ('SENT','SUBMITTED','APPROVED','REJECTED')),
    file_id UUID REFERENCES workflow_files(id),
    note VARCHAR(1000),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_contract_signing_history ON contract_signing_history(contract_id,created_at);
CREATE UNIQUE INDEX uq_signed_scan_submission ON contract_signing_history(file_id,action);
CREATE INDEX idx_contract_admissions_signing ON training_contracts(signature_admissions_id,status);

INSERT INTO m_function(function_code,screen_code,name,path,sort_order)
VALUES ('AdmissionContractSigning','AdmissionContractSigning','In & ký hợp đồng','/screens/AdmissionContractSigning',129);
-- Preserve a prior revocation of Admissions intake access.
INSERT INTO role_function(role_id,function_code)
SELECT role_id,'AdmissionContractSigning' FROM role_function
WHERE role_id=7 AND function_code='ListAdmissionApplication';
UPDATE m_function SET name='Xác nhận hợp đồng đã ký' WHERE function_code='ContractSignature';
UPDATE permission_revision SET version=version+1 WHERE id=1;
