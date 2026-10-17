-- ERHMS: clean installation into an empty database.
-- Create PostgreSQL database erhms before starting the application.
-- Demo password for all three accounts: Demo@123456 (stored as BCrypt below).

CREATE SEQUENCE m_role_seq START WITH 1 INCREMENT BY 1;
CREATE SEQUENCE m_user_seq START WITH 1 INCREMENT BY 1;

CREATE TABLE m_role (
    role_id BIGINT DEFAULT nextval('m_role_seq') PRIMARY KEY,
    code VARCHAR(40) NOT NULL UNIQUE,
    name VARCHAR(100) NOT NULL
);

CREATE TABLE m_user (
    user_id BIGINT DEFAULT nextval('m_user_seq') PRIMARY KEY,
    email VARCHAR(190) NOT NULL UNIQUE,
    password_hash VARCHAR(100) NOT NULL,
    full_name VARCHAR(120) NOT NULL,
    role_id BIGINT NOT NULL,
    CONSTRAINT fk_m_user_role FOREIGN KEY (role_id) REFERENCES m_role(role_id)
);
CREATE INDEX idx_m_user_role ON m_user(role_id);

CREATE TABLE recruitment_requests (
    id UUID PRIMARY KEY,
    created_by BIGINT NOT NULL,
    title VARCHAR(160) NOT NULL,
    quantity INTEGER NOT NULL CHECK (quantity BETWEEN 1 AND 10000),
    shift VARCHAR(20) NOT NULL CHECK (shift IN ('DAY', 'NIGHT', 'ROTATING')),
    skills VARCHAR(2000) NOT NULL,
    location VARCHAR(200) NOT NULL,
    needed_by DATE NOT NULL,
    status VARCHAR(30) NOT NULL
        CHECK (status IN ('DRAFT', 'SUBMITTED', 'REVISION_REQUIRED', 'APPROVED', 'CLOSED')),
    review_note VARCHAR(1000),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_recruitment_requests_created_by
        FOREIGN KEY (created_by) REFERENCES m_user(user_id)
);
CREATE INDEX idx_request_creator_created ON recruitment_requests(created_by, created_at);
CREATE INDEX idx_request_status ON recruitment_requests(status);

CREATE TABLE request_events (
    id UUID PRIMARY KEY,
    request_id UUID NOT NULL,
    actor_id BIGINT NOT NULL,
    action VARCHAR(40) NOT NULL,
    note VARCHAR(1000),
    occurred_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_request_events_request FOREIGN KEY (request_id) REFERENCES recruitment_requests(id),
    CONSTRAINT fk_request_events_actor FOREIGN KEY (actor_id) REFERENCES m_user(user_id)
);
CREATE INDEX idx_request_events ON request_events(request_id, occurred_at);

-- Sequence defaults allocate seeded IDs and remain ready for future inserts.
INSERT INTO m_role (code, name) VALUES
    ('USER', 'Người dùng'),
    ('SERVICE_MANAGER', 'Quản lý dịch vụ');

INSERT INTO m_user (email, password_hash, full_name, role_id) VALUES
    ('user1@erhms.local', '$2a$10$6aYqwiwi52/f6G7yzarsjOrPkcRdMNrbeYkWjqp87CgSGBlqx2Odq', 'Nguyễn Văn An',
        (SELECT role_id FROM m_role WHERE code = 'USER')),
    ('user2@erhms.local', '$2a$10$6aYqwiwi52/f6G7yzarsjOrPkcRdMNrbeYkWjqp87CgSGBlqx2Odq', 'Trần Thị Bình',
        (SELECT role_id FROM m_role WHERE code = 'USER')),
    ('manager@erhms.local', '$2a$10$6aYqwiwi52/f6G7yzarsjOrPkcRdMNrbeYkWjqp87CgSGBlqx2Odq', 'Lê Minh Quản',
        (SELECT role_id FROM m_role WHERE code = 'SERVICE_MANAGER'));

-- Five demo requests cover every supported state.
INSERT INTO recruitment_requests (
    id, created_by, title, quantity, shift, skills, location, needed_by,
    status, review_note, created_at, updated_at, version
) VALUES
    ('00000000-0000-0000-0000-000000000101', (SELECT user_id FROM m_user WHERE email = 'user1@erhms.local'),
        'Nhân viên vận hành máy', 12, 'DAY', 'Vận hành máy cơ bản', 'Khu A, xưởng số 1', CURRENT_DATE + 14,
        'DRAFT', NULL, CURRENT_TIMESTAMP - INTERVAL '1' HOUR, CURRENT_TIMESTAMP - INTERVAL '1' HOUR, 0),
    ('00000000-0000-0000-0000-000000000102', (SELECT user_id FROM m_user WHERE email = 'user1@erhms.local'),
        'Nhân viên kho', 8, 'ROTATING', 'Kiểm kê và quản lý hàng hóa', 'Khu B, kho trung tâm', CURRENT_DATE + 21,
        'SUBMITTED', NULL, CURRENT_TIMESTAMP - INTERVAL '3' DAY, CURRENT_TIMESTAMP - INTERVAL '2' DAY, 1),
    ('00000000-0000-0000-0000-000000000103', (SELECT user_id FROM m_user WHERE email = 'user1@erhms.local'),
        'Kỹ thuật viên bảo trì', 4, 'DAY', 'Bảo trì thiết bị cơ điện', 'Khu A, phòng kỹ thuật', CURRENT_DATE + 30,
        'REVISION_REQUIRED', 'Bổ sung chứng chỉ và kinh nghiệm bảo trì.', CURRENT_TIMESTAMP - INTERVAL '5' DAY, CURRENT_TIMESTAMP - INTERVAL '3' DAY, 2),
    ('00000000-0000-0000-0000-000000000104', (SELECT user_id FROM m_user WHERE email = 'user2@erhms.local'),
        'Nhân viên kiểm tra chất lượng', 6, 'NIGHT', 'Kiểm tra sản phẩm và lập báo cáo', 'Khu C, dây chuyền số 2', CURRENT_DATE + 20,
        'APPROVED', NULL, CURRENT_TIMESTAMP - INTERVAL '6' DAY, CURRENT_TIMESTAMP - INTERVAL '4' DAY, 2),
    ('00000000-0000-0000-0000-000000000105', (SELECT user_id FROM m_user WHERE email = 'user2@erhms.local'),
        'Nhân viên đóng gói', 10, 'DAY', 'Đóng gói và phân loại sản phẩm', 'Khu C, xưởng đóng gói', CURRENT_DATE + 7,
        'CLOSED', NULL, CURRENT_TIMESTAMP - INTERVAL '10' DAY, CURRENT_TIMESTAMP - INTERVAL '7' DAY, 3);

-- Event actions match RecruitmentService and the final state/version of each request.
INSERT INTO request_events (id, request_id, actor_id, action, note, occurred_at) VALUES
    ('00000000-0000-0000-0000-000000000201', '00000000-0000-0000-0000-000000000101',
        (SELECT user_id FROM m_user WHERE email = 'user1@erhms.local'), 'CREATED', NULL, CURRENT_TIMESTAMP - INTERVAL '1' HOUR),
    ('00000000-0000-0000-0000-000000000202', '00000000-0000-0000-0000-000000000102',
        (SELECT user_id FROM m_user WHERE email = 'user1@erhms.local'), 'CREATED', NULL, CURRENT_TIMESTAMP - INTERVAL '3' DAY),
    ('00000000-0000-0000-0000-000000000203', '00000000-0000-0000-0000-000000000102',
        (SELECT user_id FROM m_user WHERE email = 'user1@erhms.local'), 'SUBMIT', NULL, CURRENT_TIMESTAMP - INTERVAL '2' DAY),
    ('00000000-0000-0000-0000-000000000204', '00000000-0000-0000-0000-000000000103',
        (SELECT user_id FROM m_user WHERE email = 'user1@erhms.local'), 'CREATED', NULL, CURRENT_TIMESTAMP - INTERVAL '5' DAY),
    ('00000000-0000-0000-0000-000000000205', '00000000-0000-0000-0000-000000000103',
        (SELECT user_id FROM m_user WHERE email = 'user1@erhms.local'), 'SUBMIT', NULL, CURRENT_TIMESTAMP - INTERVAL '4' DAY),
    ('00000000-0000-0000-0000-000000000206', '00000000-0000-0000-0000-000000000103',
        (SELECT user_id FROM m_user WHERE email = 'manager@erhms.local'), 'REQUEST-REVISION', 'Bổ sung chứng chỉ và kinh nghiệm bảo trì.', CURRENT_TIMESTAMP - INTERVAL '3' DAY),
    ('00000000-0000-0000-0000-000000000207', '00000000-0000-0000-0000-000000000104',
        (SELECT user_id FROM m_user WHERE email = 'user2@erhms.local'), 'CREATED', NULL, CURRENT_TIMESTAMP - INTERVAL '6' DAY),
    ('00000000-0000-0000-0000-000000000208', '00000000-0000-0000-0000-000000000104',
        (SELECT user_id FROM m_user WHERE email = 'user2@erhms.local'), 'SUBMIT', NULL, CURRENT_TIMESTAMP - INTERVAL '5' DAY),
    ('00000000-0000-0000-0000-000000000209', '00000000-0000-0000-0000-000000000104',
        (SELECT user_id FROM m_user WHERE email = 'manager@erhms.local'), 'APPROVE', NULL, CURRENT_TIMESTAMP - INTERVAL '4' DAY),
    ('00000000-0000-0000-0000-000000000210', '00000000-0000-0000-0000-000000000105',
        (SELECT user_id FROM m_user WHERE email = 'user2@erhms.local'), 'CREATED', NULL, CURRENT_TIMESTAMP - INTERVAL '10' DAY),
    ('00000000-0000-0000-0000-000000000211', '00000000-0000-0000-0000-000000000105',
        (SELECT user_id FROM m_user WHERE email = 'user2@erhms.local'), 'SUBMIT', NULL, CURRENT_TIMESTAMP - INTERVAL '9' DAY),
    ('00000000-0000-0000-0000-000000000212', '00000000-0000-0000-0000-000000000105',
        (SELECT user_id FROM m_user WHERE email = 'manager@erhms.local'), 'APPROVE', NULL, CURRENT_TIMESTAMP - INTERVAL '8' DAY),
    ('00000000-0000-0000-0000-000000000213', '00000000-0000-0000-0000-000000000105',
        (SELECT user_id FROM m_user WHERE email = 'manager@erhms.local'), 'CLOSE', NULL, CURRENT_TIMESTAMP - INTERVAL '7' DAY);
