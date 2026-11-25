ALTER TABLE training_enrollments ADD COLUMN academic_failed_at TIMESTAMP WITH TIME ZONE;

CREATE TABLE course_retake_offers (
    id UUID PRIMARY KEY,
    enrollment_id UUID NOT NULL UNIQUE REFERENCES training_enrollments(id),
    source_contract_id UUID NOT NULL UNIQUE REFERENCES training_contracts(id),
    student_id BIGINT NOT NULL REFERENCES m_user(user_id),
    original_tuition NUMERIC(18,2) NOT NULL CHECK (original_tuition >= 0),
    retake_tuition NUMERIC(18,2) NOT NULL CHECK (retake_tuition >= 0 AND retake_tuition <= original_tuition),
    new_contract_id UUID UNIQUE REFERENCES training_contracts(id),
    offered_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    registered_at TIMESTAMP WITH TIME ZONE,
    CHECK ((new_contract_id IS NULL AND registered_at IS NULL) OR (new_contract_id IS NOT NULL AND registered_at IS NOT NULL))
);
CREATE INDEX idx_course_retake_student ON course_retake_offers(student_id, offered_at);

INSERT INTO m_function(function_code,screen_code,name,path,sort_order)
VALUES ('StudentRetake','StudentRetake','Đăng ký học lại','/screens/StudentRetake',219);
INSERT INTO role_function(role_id,function_code) VALUES (3,'StudentRetake');
UPDATE permission_revision SET version=version+1 WHERE id=1;
