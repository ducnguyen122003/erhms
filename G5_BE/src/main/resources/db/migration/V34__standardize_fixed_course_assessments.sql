-- Keep eligibility to close academic records when all earlier assessments were already published.
ALTER TABLE training_enrollments ADD COLUMN legacy_assessments_complete BOOLEAN NOT NULL DEFAULT FALSE;

UPDATE training_enrollments e
SET legacy_assessments_complete = TRUE
WHERE EXISTS (
    SELECT 1 FROM training_classes cl
    JOIN training_courses course ON course.id = cl.course_id
    WHERE cl.id = e.class_id AND course.fixed_catalog = TRUE
      AND EXISTS (SELECT 1 FROM course_assessments a WHERE a.course_id = course.id)
      AND (SELECT COUNT(*) FROM course_assessments a WHERE a.course_id = course.id AND a.title IN (
          'Điểm kiểm tra lần 1', 'Điểm kiểm tra lần 2', 'Điểm kiểm tra cuối kỳ'
      )) < 3
      AND NOT EXISTS (
          SELECT 1 FROM course_assessments a WHERE a.course_id = course.id
          AND NOT EXISTS (SELECT 1 FROM training_grades grade WHERE grade.enrollment_id = e.id
              AND grade.title = a.title AND grade.score IS NOT NULL AND grade.published_at IS NOT NULL)
      )
);

-- Grades reference individual enrollments by title, so historic scores remain in training_grades.
DELETE FROM course_assessments
WHERE course_id IN (SELECT id FROM training_courses WHERE fixed_catalog = TRUE);

INSERT INTO course_assessments(id, course_id, title, weight)
SELECT gen_random_uuid(), course.id, preset.title, preset.weight
FROM training_courses course
CROSS JOIN (VALUES
    ('Điểm kiểm tra lần 1', 30),
    ('Điểm kiểm tra lần 2', 30),
    ('Điểm kiểm tra cuối kỳ', 40)
) AS preset(title, weight)
WHERE course.fixed_catalog = TRUE;
