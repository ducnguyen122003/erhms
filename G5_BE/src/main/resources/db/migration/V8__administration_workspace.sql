CREATE TABLE m_function (
    function_code VARCHAR(40) PRIMARY KEY,
    screen_code VARCHAR(20) NOT NULL UNIQUE,
    name VARCHAR(160) NOT NULL,
    path VARCHAR(160) NOT NULL,
    sort_order INTEGER NOT NULL
);
CREATE TABLE role_function (
    role_id BIGINT NOT NULL REFERENCES m_role(role_id),
    function_code VARCHAR(40) NOT NULL REFERENCES m_function(function_code),
    PRIMARY KEY (role_id, function_code)
);
INSERT INTO m_function(function_code,screen_code,name,path,sort_order) VALUES
('DASHBOARD','FE-01','Dashboard theo vai trò','/dashboard',1),
('USER_ACCOUNTS','FE-02','Tài khoản người dùng','/admin/users',2),
('ROLE_PERMISSIONS','FE-03','Vai trò và phân quyền','/admin/permissions',3),
('SYSTEM_CONFIG','FE-04','Cấu hình hệ thống','/admin/settings',4),
('SYSTEM_AUDIT_LOG','FE-05','Nhật ký hệ thống','/admin/audit-logs',5),
('PROFILE','FE-06','Thông tin tài khoản cá nhân','/profile',6),
('RECRUITMENT','FE-07','Nhu cầu tuyển dụng','/recruitment',7);
INSERT INTO role_function(role_id,function_code)
SELECT role_id,f.function_code FROM m_role CROSS JOIN m_function f
WHERE role_id BETWEEN 1 AND 7 AND (
    f.function_code IN ('DASHBOARD','PROFILE')
    OR (role_id=1 AND f.function_code IN ('USER_ACCOUNTS','ROLE_PERMISSIONS','SYSTEM_CONFIG','SYSTEM_AUDIT_LOG'))
    OR (role_id IN (6,7) AND f.function_code='RECRUITMENT')
);
CREATE TABLE permission_revision (
    id INTEGER PRIMARY KEY CHECK(id=1),
    version BIGINT NOT NULL DEFAULT 0
);
INSERT INTO permission_revision(id,version) VALUES(1,0);
CREATE TABLE system_settings (
    id INTEGER PRIMARY KEY CHECK(id=1),
    center_name VARCHAR(160) NOT NULL,
    notification_email VARCHAR(190) NOT NULL DEFAULT '',
    default_language VARCHAR(10) NOT NULL DEFAULT 'vi',
    version BIGINT NOT NULL DEFAULT 0,
    updated_at TIMESTAMP WITH TIME ZONE,
    updated_by BIGINT REFERENCES m_user(user_id) ON DELETE SET NULL
);
INSERT INTO system_settings(id,center_name) VALUES(1,'Trung tâm đào tạo ERHMS');
ALTER TABLE m_user ADD COLUMN admin_version BIGINT NOT NULL DEFAULT 0;
CREATE TABLE system_audit_log (
    id UUID PRIMARY KEY,
    actor_id BIGINT REFERENCES m_user(user_id) ON DELETE SET NULL,
    actor_name VARCHAR(120) NOT NULL,
    actor_role VARCHAR(40),
    action_code VARCHAR(80) NOT NULL,
    action_label VARCHAR(160) NOT NULL,
    target_id VARCHAR(100),
    request_method VARCHAR(10) NOT NULL,
    request_path VARCHAR(250) NOT NULL,
    result VARCHAR(16) NOT NULL CHECK(result IN ('SUCCESS','FAILURE')),
    status_code INTEGER NOT NULL,
    change_note VARCHAR(1000),
    occurred_at TIMESTAMP WITH TIME ZONE NOT NULL
);
CREATE INDEX idx_system_audit_time ON system_audit_log(occurred_at);
CREATE INDEX idx_system_audit_actor ON system_audit_log(actor_id);
INSERT INTO system_audit_log(id,actor_id,actor_name,actor_role,action_code,action_label,target_id,request_method,request_path,result,status_code,occurred_at)
SELECT l.id,u.user_id,u.full_name,r.code,'LOGIN','Đăng nhập hệ thống',CAST(u.user_id AS VARCHAR),'POST','/api/auth/login','SUCCESS',204,l.occurred_at
FROM user_login_events l JOIN m_user u ON u.user_id=l.user_id LEFT JOIN m_role r ON r.role_id=u.role_id;
INSERT INTO system_audit_log(id,actor_id,actor_name,actor_role,action_code,action_label,target_id,request_method,request_path,result,status_code,change_note,occurred_at)
SELECT e.id,u.user_id,u.full_name,role.code,CONCAT('RECRUITMENT_',e.action),'Thao tác yêu cầu tuyển dụng',CAST(e.request_id AS VARCHAR),'POST',
       CONCAT('/api/recruitment-requests/',CAST(e.request_id AS VARCHAR)),'SUCCESS',200,e.note,e.occurred_at
FROM request_events e JOIN m_user u ON u.user_id=e.actor_id LEFT JOIN m_role role ON role.role_id=u.role_id;
