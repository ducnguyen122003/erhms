-- Contract management belongs to Manager. Keep historical authors and contract contents intact.
DELETE FROM role_function WHERE role_id=7 AND function_code IN
 ('ListTrainingContract','AddTrainingContract','EditTrainingContract','TrainingContractDetail','ContractSignature');

INSERT INTO role_function(role_id,function_code)
 SELECT 6,function_code FROM m_function f
 WHERE function_code IN ('ListTrainingContract','AddTrainingContract','EditTrainingContract','TrainingContractDetail','ContractSignature')
 AND NOT EXISTS(SELECT 1 FROM role_function rf WHERE rf.role_id=6 AND rf.function_code=f.function_code);

-- Recover only a known course owner; do not guess an owner for unassigned legacy contracts.
UPDATE training_contracts SET manager_id=(SELECT manager_id FROM training_courses WHERE id=training_contracts.course_id)
 WHERE manager_id IS NULL AND course_id IS NOT NULL
 AND EXISTS(SELECT 1 FROM training_courses WHERE id=training_contracts.course_id);

UPDATE permission_revision SET version=version+1 WHERE id=1;
