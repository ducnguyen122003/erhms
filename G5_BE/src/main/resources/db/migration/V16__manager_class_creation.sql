-- Manual class creation allocates increasing codes here.
-- Existing UUID ids and class codes remain unchanged.
CREATE SEQUENCE training_class_code_seq START WITH 1 INCREMENT BY 1;

INSERT INTO m_function(function_code,screen_code,name,path,sort_order)
VALUES ('AddClass','AddClass','Tạo lớp học','/screens/AddClass',146);
INSERT INTO role_function(role_id,function_code)
SELECT role_id,'AddClass' FROM m_role WHERE code='MANAGER';
UPDATE permission_revision SET version=version+1 WHERE id=1;
