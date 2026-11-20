-- An annual plan belongs to its starting year; an individual course may finish next year.
-- Existing classes, contracts and session dates remain intact.
ALTER TABLE training_contracts ADD COLUMN class_id UUID REFERENCES training_classes(id);
CREATE INDEX idx_contract_reserved_class ON training_contracts(class_id,status);
ALTER TABLE training_classes ADD COLUMN plan_year INTEGER CHECK(plan_year BETWEEN 1900 AND 9999);
ALTER TABLE training_classes ADD COLUMN weekdays VARCHAR(13);
ALTER TABLE training_classes ADD COLUMN start_time TIME;
ALTER TABLE training_classes ADD COLUMN end_time TIME;
ALTER TABLE training_classes ADD COLUMN classroom_id UUID REFERENCES m_classroom(id);
ALTER TABLE training_classes ADD COLUMN plan_fixed BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE training_classes ADD CONSTRAINT ck_class_plan_times CHECK(start_time IS NULL OR end_time IS NULL OR end_time>start_time);
ALTER TABLE training_classes ADD CONSTRAINT ck_fixed_class_plan CHECK(plan_fixed=FALSE OR
 (plan_year IS NOT NULL AND starts_on IS NOT NULL AND ends_on IS NOT NULL AND EXTRACT(YEAR FROM starts_on)=plan_year
 AND weekdays IS NOT NULL AND start_time IS NOT NULL AND end_time IS NOT NULL AND classroom_id IS NOT NULL
 AND lecturer_id IS NOT NULL AND course_id IS NOT NULL));
UPDATE training_classes SET plan_year=EXTRACT(YEAR FROM starts_on) WHERE starts_on IS NOT NULL AND EXTRACT(YEAR FROM starts_on) BETWEEN 1900 AND 9999;
CREATE INDEX idx_class_plan_year ON training_classes(plan_year,status);

-- Admin controls planning. Managers retain class visibility and student placement.
DELETE FROM role_function WHERE function_code IN ('AddClass','EditClass','TimeTable_EditSession','TimeTable_Conflict') AND role_id<>1;
INSERT INTO role_function(role_id,function_code)
 SELECT 1,f.function_code FROM m_function f
 WHERE f.function_code IN ('ListClass','ClassDetail','AddClass','EditClass','TimeTable_Manager','TimeTable_EditSession','TimeTable_Conflict')
 AND NOT EXISTS(SELECT 1 FROM role_function rf WHERE rf.role_id=1 AND rf.function_code=f.function_code);
UPDATE permission_revision SET version=version+1 WHERE id=1;
