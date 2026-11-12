-- Managers provision only student accounts for contacted admissions.
INSERT INTO m_function(function_code,screen_code,name,path,sort_order)
VALUES ('ListStudentAccount','ListStudentAccount','Cấp tài khoản học viên','/screens/ListStudentAccount',123);

INSERT INTO role_function(role_id,function_code) VALUES (6,'ListStudentAccount');

UPDATE permission_revision SET version=version+1 WHERE id=1;
