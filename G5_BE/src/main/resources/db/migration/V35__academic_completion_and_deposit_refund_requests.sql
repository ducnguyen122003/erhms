ALTER TABLE training_enrollments ADD COLUMN academic_completed_at TIMESTAMP WITH TIME ZONE;

CREATE TABLE deposit_refund_requests (
    id UUID PRIMARY KEY,
    contract_id UUID NOT NULL UNIQUE REFERENCES training_contracts(id),
    refund_percent INTEGER NOT NULL CHECK (refund_percent BETWEEN 0 AND 100),
    reason VARCHAR(1000) NOT NULL,
    recipient_name VARCHAR(160) NOT NULL,
    bank_name VARCHAR(120) NOT NULL,
    account_number VARCHAR(34) NOT NULL,
    deposit_amount NUMERIC(18,2) NOT NULL CHECK (deposit_amount > 0),
    refund_amount NUMERIC(18,2) NOT NULL CHECK (refund_amount >= 0 AND refund_amount <= deposit_amount),
    requested_by BIGINT NOT NULL REFERENCES m_user(user_id),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);
