-- Keep the confirmed course separate from the applicant's original interest.
-- Existing profiles must select a course before their next academic handoff.
ALTER TABLE consultation_registrations ADD COLUMN enrollment_course VARCHAR(255);
ALTER TABLE consultation_registrations ADD CONSTRAINT ck_admission_enrollment_course CHECK (
    enrollment_course IS NULL OR enrollment_course IN (
        'Sửa chữa máy tính Laptop chuyên nghiệp',
        'Sửa chữa máy tính PC & máy in',
        'Sửa chữa Điện tử - Điện gia dụng',
        'Sửa chữa Điện lạnh, Điện gia dụng & Inverter'
    )
);
