-- Copy the existing selection into a new column so the old code-only CHECK
-- constraint is removed together with interest on both PostgreSQL and H2.
ALTER TABLE consultation_registrations ADD COLUMN program_name VARCHAR(255);

UPDATE consultation_registrations
SET program_name = CASE interest
    WHEN 'not-sure' THEN 'Tôi cần tư vấn ngành nghề và lộ trình học'
    WHEN 'safety' THEN 'An toàn trong môi trường sản xuất'
    WHEN 'electric' THEN 'Điện công nghiệp nền tảng'
    WHEN 'quality' THEN 'Kiểm tra chất lượng cơ bản'
    WHEN 'career-electric' THEN 'Điện công nghiệp'
    WHEN 'career-mechanical' THEN 'Cơ khí & lắp ráp'
    WHEN 'career-welding' THEN 'Kỹ thuật hàn cơ bản'
    WHEN 'career-production' THEN 'Vận hành & lắp ráp sản xuất'
    WHEN 'career-sewing' THEN 'May công nghiệp'
    WHEN 'career-quality' THEN 'Kiểm tra chất lượng sản phẩm'
    ELSE interest
END;

ALTER TABLE consultation_registrations ALTER COLUMN program_name SET NOT NULL;
ALTER TABLE consultation_registrations DROP COLUMN interest;
ALTER TABLE consultation_registrations DROP COLUMN kind;
ALTER TABLE consultation_registrations DROP COLUMN consent;
ALTER TABLE consultation_registrations RENAME COLUMN message TO description;
ALTER TABLE consultation_registrations RENAME COLUMN created_at TO created_date;

ALTER INDEX idx_consultation_registrations_created_at RENAME TO idx_consultation_registrations_created_date;
