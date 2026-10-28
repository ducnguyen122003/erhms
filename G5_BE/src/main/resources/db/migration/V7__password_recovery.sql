ALTER TABLE m_user ADD COLUMN credentials_version BIGINT NOT NULL DEFAULT 0;

CREATE TABLE password_reset_challenges (
    email_key VARCHAR(64) PRIMARY KEY,
    user_id BIGINT REFERENCES m_user(user_id) ON DELETE CASCADE,
    challenge_id UUID,
    code_hash VARCHAR(100),
    code_expires_at TIMESTAMP WITH TIME ZONE,
    requested_at TIMESTAMP WITH TIME ZONE,
    request_window_at TIMESTAMP WITH TIME ZONE,
    request_count INTEGER NOT NULL DEFAULT 0,
    attempts INTEGER NOT NULL DEFAULT 0,
    token_hash VARCHAR(64),
    token_expires_at TIMESTAMP WITH TIME ZONE,
    password_snapshot VARCHAR(100),
    credentials_version BIGINT,
    consumed BOOLEAN NOT NULL DEFAULT FALSE
);
CREATE INDEX idx_password_reset_user ON password_reset_challenges(user_id);

CREATE TABLE password_reset_rate_limits (
    client_key VARCHAR(64) PRIMARY KEY,
    window_at TIMESTAMP WITH TIME ZONE NOT NULL,
    request_count INTEGER NOT NULL DEFAULT 0
);
