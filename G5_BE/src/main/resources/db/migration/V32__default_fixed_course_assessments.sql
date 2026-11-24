-- Give fixed courses a gradebook when they have no configured assessments.
INSERT INTO course_assessments (id, course_id, title, weight)
SELECT gen_random_uuid(), course.id, 'Điểm tổng kết', 100
FROM training_courses course
WHERE course.fixed_catalog = TRUE
  AND NOT EXISTS (
      SELECT 1 FROM course_assessments assessment WHERE assessment.course_id = course.id
  );
