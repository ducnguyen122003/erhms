CREATE TABLE consultation_registrations (
    id UUID PRIMARY KEY,
    kind VARCHAR(20) NOT NULL CHECK (kind = 'training'),
    full_name VARCHAR(120) NOT NULL,
    phone VARCHAR(20) NOT NULL,
    email VARCHAR(190),
    interest VARCHAR(60) NOT NULL CHECK (interest IN (
        'not-sure', 'safety', 'electric', 'quality',
        'career-electric', 'career-mechanical', 'career-welding',
        'career-production', 'career-sewing', 'career-quality'
    )),
    contact_time VARCHAR(20) NOT NULL CHECK (contact_time IN ('any', 'morning', 'afternoon')),
    message VARCHAR(2000),
    consent BOOLEAN NOT NULL CHECK (consent = TRUE),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX idx_consultation_registrations_created_at ON consultation_registrations(created_at);
