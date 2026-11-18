UPDATE m_function SET name='Tư vấn & tiếp nhận hồ sơ' WHERE function_code='ListAdmissionApplication';

INSERT INTO m_function(function_code,screen_code,name,path,sort_order)
VALUES ('ListAdmissionEnrollment','ListAdmissionEnrollment','Hoàn tất thủ tục nhập học','/screens/ListAdmissionEnrollment',128);

-- Preserve revocation: only roles that currently have the HR intake grant receive the new screen.
INSERT INTO role_function(role_id,function_code)
SELECT role_id,'ListAdmissionEnrollment' FROM role_function
WHERE role_id=7 AND function_code='ListAdmissionApplication';
UPDATE permission_revision SET version=version+1 WHERE id=1;
