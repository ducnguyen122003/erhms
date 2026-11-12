-- Only administrators manage the shared profession and course catalogues.
-- Keep course owners and all existing training records intact.
DELETE FROM role_function WHERE role_id<>1 AND function_code IN
 ('ListProfession','AddProfession','EditProfession','ListCourse','AddCourse','EditCourse','CourseDetail');

INSERT INTO role_function(role_id,function_code)
 SELECT 1,function_code FROM m_function f
 WHERE function_code IN ('ListProfession','AddProfession','EditProfession','ListCourse','AddCourse','EditCourse','CourseDetail')
 AND NOT EXISTS(SELECT 1 FROM role_function rf WHERE rf.role_id=1 AND rf.function_code=f.function_code);

UPDATE permission_revision SET version=version+1 WHERE id=1;
