-- Shared master data and screen permissions. Preserve existing text as a historical snapshot.
CREATE TABLE m_equipment_group (
 id UUID PRIMARY KEY, code VARCHAR(40) NOT NULL UNIQUE, name VARCHAR(120) NOT NULL,
 name_key VARCHAR(120) NOT NULL UNIQUE, description VARCHAR(500) NOT NULL DEFAULT '',
 status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' CHECK(status IN ('ACTIVE','INACTIVE')),
 version BIGINT NOT NULL DEFAULT 0, created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
 updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE TABLE m_storage_location (
 id UUID PRIMARY KEY, code VARCHAR(40) NOT NULL UNIQUE, name VARCHAR(160) NOT NULL,
 name_key VARCHAR(160) NOT NULL UNIQUE, description VARCHAR(500) NOT NULL DEFAULT '',
 status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' CHECK(status IN ('ACTIVE','INACTIVE')),
 version BIGINT NOT NULL DEFAULT 0, created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
 updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE TABLE m_profession (
 id UUID PRIMARY KEY, code VARCHAR(40) NOT NULL UNIQUE, name VARCHAR(120) NOT NULL,
 name_key VARCHAR(120) NOT NULL UNIQUE, description VARCHAR(500) NOT NULL DEFAULT '',
 status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' CHECK(status IN ('ACTIVE','INACTIVE')),
 version BIGINT NOT NULL DEFAULT 0, created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
 updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE TABLE m_classroom (
 id UUID PRIMARY KEY, code VARCHAR(40) NOT NULL UNIQUE, name VARCHAR(100) NOT NULL,
 name_key VARCHAR(100) NOT NULL UNIQUE, description VARCHAR(500) NOT NULL DEFAULT '',
 status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' CHECK(status IN ('ACTIVE','INACTIVE')),
 version BIGINT NOT NULL DEFAULT 0, created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
 updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE TABLE m_bank (
 id UUID PRIMARY KEY, code VARCHAR(40) NOT NULL UNIQUE, name VARCHAR(160) NOT NULL,
 name_key VARCHAR(160) NOT NULL UNIQUE, description VARCHAR(500) NOT NULL DEFAULT '',
 status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' CHECK(status IN ('ACTIVE','INACTIVE')),
 version BIGINT NOT NULL DEFAULT 0, created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
 updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);
ALTER TABLE equipment_assets ADD COLUMN equipment_group_id UUID REFERENCES m_equipment_group(id);
CREATE INDEX idx_equipment_assets_equipment_group_id ON equipment_assets(equipment_group_id);
ALTER TABLE equipment_assets ADD COLUMN storage_location_id UUID REFERENCES m_storage_location(id);
CREATE INDEX idx_equipment_assets_storage_location_id ON equipment_assets(storage_location_id);
ALTER TABLE equipment_requests ADD COLUMN equipment_group_id UUID REFERENCES m_equipment_group(id);
CREATE INDEX idx_equipment_requests_equipment_group_id ON equipment_requests(equipment_group_id);
ALTER TABLE training_courses ADD COLUMN profession_id UUID REFERENCES m_profession(id);
CREATE INDEX idx_training_courses_profession_id ON training_courses(profession_id);
ALTER TABLE training_sessions ADD COLUMN classroom_id UUID REFERENCES m_classroom(id);
CREATE INDEX idx_training_sessions_classroom_id ON training_sessions(classroom_id);
ALTER TABLE financial_transactions ADD COLUMN bank_id UUID REFERENCES m_bank(id);
CREATE INDEX idx_financial_transactions_bank_id ON financial_transactions(bank_id);
INSERT INTO m_bank(id,code,name,name_key) VALUES ('55000000-0000-0000-0000-000000000001','VCB','Vietcombank','vietcombank');
INSERT INTO m_bank(id,code,name,name_key) VALUES ('55000000-0000-0000-0000-000000000002','BIDV','BIDV','bidv');
INSERT INTO m_bank(id,code,name,name_key) VALUES ('55000000-0000-0000-0000-000000000003','CTG','VietinBank','vietinbank');
INSERT INTO m_bank(id,code,name,name_key) VALUES ('55000000-0000-0000-0000-000000000004','AGRIBANK','Agribank','agribank');
INSERT INTO m_bank(id,code,name,name_key) VALUES ('55000000-0000-0000-0000-000000000005','TCB','Techcombank','techcombank');
INSERT INTO m_bank(id,code,name,name_key) VALUES ('55000000-0000-0000-0000-000000000006','MB','MBBank','mbbank');
INSERT INTO m_bank(id,code,name,name_key) VALUES ('55000000-0000-0000-0000-000000000007','ACB','ACB','acb');
INSERT INTO m_bank(id,code,name,name_key) VALUES ('55000000-0000-0000-0000-000000000008','VPB','VPBank','vpbank');
INSERT INTO m_bank(id,code,name,name_key) VALUES ('55000000-0000-0000-0000-000000000009','STB','Sacombank','sacombank');
INSERT INTO m_bank(id,code,name,name_key) VALUES ('55000000-0000-0000-0000-000000000010','HDB','HDBank','hdbank');
INSERT INTO m_equipment_group(id,code,name,name_key)
 SELECT CAST('11000000-0000-0000-0000-'||LPAD(CAST(ROW_NUMBER() OVER(ORDER BY normalized) AS VARCHAR),12,'0') AS UUID),
 'LEGACY-GRP-'||CAST(ROW_NUMBER() OVER(ORDER BY normalized) AS VARCHAR),label,normalized
 FROM (SELECT MIN(TRIM(label)) AS label,LOWER(TRIM(label)) AS normalized FROM (SELECT category AS label FROM equipment_assets UNION ALL SELECT group_name AS label FROM equipment_requests) labels
 WHERE label IS NOT NULL AND TRIM(label)<>'' GROUP BY LOWER(TRIM(label))) existing_labels
 WHERE NOT EXISTS(SELECT 1 FROM m_equipment_group master WHERE master.name_key=existing_labels.normalized);
INSERT INTO m_storage_location(id,code,name,name_key)
 SELECT CAST('22000000-0000-0000-0000-'||LPAD(CAST(ROW_NUMBER() OVER(ORDER BY normalized) AS VARCHAR),12,'0') AS UUID),
 'LEGACY-LOC-'||CAST(ROW_NUMBER() OVER(ORDER BY normalized) AS VARCHAR),label,normalized
 FROM (SELECT MIN(TRIM(label)) AS label,LOWER(TRIM(label)) AS normalized FROM (SELECT location AS label FROM equipment_assets) labels
 WHERE label IS NOT NULL AND TRIM(label)<>'' GROUP BY LOWER(TRIM(label))) existing_labels
 WHERE NOT EXISTS(SELECT 1 FROM m_storage_location master WHERE master.name_key=existing_labels.normalized);
INSERT INTO m_profession(id,code,name,name_key)
 SELECT CAST('33000000-0000-0000-0000-'||LPAD(CAST(ROW_NUMBER() OVER(ORDER BY normalized) AS VARCHAR),12,'0') AS UUID),
 'LEGACY-PRO-'||CAST(ROW_NUMBER() OVER(ORDER BY normalized) AS VARCHAR),label,normalized
 FROM (SELECT MIN(TRIM(label)) AS label,LOWER(TRIM(label)) AS normalized FROM (SELECT profession AS label FROM training_courses) labels
 WHERE label IS NOT NULL AND TRIM(label)<>'' GROUP BY LOWER(TRIM(label))) existing_labels
 WHERE NOT EXISTS(SELECT 1 FROM m_profession master WHERE master.name_key=existing_labels.normalized);
INSERT INTO m_classroom(id,code,name,name_key)
 SELECT CAST('44000000-0000-0000-0000-'||LPAD(CAST(ROW_NUMBER() OVER(ORDER BY normalized) AS VARCHAR),12,'0') AS UUID),
 'LEGACY-ROOM-'||CAST(ROW_NUMBER() OVER(ORDER BY normalized) AS VARCHAR),label,normalized
 FROM (SELECT MIN(TRIM(label)) AS label,LOWER(TRIM(label)) AS normalized FROM (SELECT room AS label FROM training_sessions) labels
 WHERE label IS NOT NULL AND TRIM(label)<>'' GROUP BY LOWER(TRIM(label))) existing_labels
 WHERE NOT EXISTS(SELECT 1 FROM m_classroom master WHERE master.name_key=existing_labels.normalized);
INSERT INTO m_bank(id,code,name,name_key)
 SELECT CAST('66000000-0000-0000-0000-'||LPAD(CAST(ROW_NUMBER() OVER(ORDER BY normalized) AS VARCHAR),12,'0') AS UUID),
 'LEGACY-BANK-'||CAST(ROW_NUMBER() OVER(ORDER BY normalized) AS VARCHAR),label,normalized
 FROM (SELECT MIN(TRIM(label)) AS label,LOWER(TRIM(label)) AS normalized FROM (SELECT bank_name AS label FROM financial_transactions) labels
 WHERE label IS NOT NULL AND TRIM(label)<>'' GROUP BY LOWER(TRIM(label))) existing_labels
 WHERE NOT EXISTS(SELECT 1 FROM m_bank master WHERE master.name_key=existing_labels.normalized);
UPDATE equipment_assets SET equipment_group_id=(SELECT id FROM m_equipment_group WHERE name_key=LOWER(TRIM(equipment_assets.category))) WHERE category IS NOT NULL AND TRIM(category)<>'';
UPDATE equipment_assets SET storage_location_id=(SELECT id FROM m_storage_location WHERE name_key=LOWER(TRIM(equipment_assets.location))) WHERE location IS NOT NULL AND TRIM(location)<>'';
UPDATE equipment_requests SET equipment_group_id=(SELECT id FROM m_equipment_group WHERE name_key=LOWER(TRIM(equipment_requests.group_name))) WHERE group_name IS NOT NULL AND TRIM(group_name)<>'';
UPDATE training_courses SET profession_id=(SELECT id FROM m_profession WHERE name_key=LOWER(TRIM(training_courses.profession))) WHERE profession IS NOT NULL AND TRIM(profession)<>'';
UPDATE training_sessions SET classroom_id=(SELECT id FROM m_classroom WHERE name_key=LOWER(TRIM(training_sessions.room))) WHERE room IS NOT NULL AND TRIM(room)<>'';
UPDATE financial_transactions SET bank_id=(SELECT id FROM m_bank WHERE name_key=LOWER(TRIM(financial_transactions.bank_name))) WHERE bank_name IS NOT NULL AND TRIM(bank_name)<>'';
INSERT INTO m_function(function_code,screen_code,name,path,sort_order) VALUES ('ListEquipmentGroup','ListEquipmentGroup','Nhóm thiết bị','/screens/ListEquipmentGroup',300);
INSERT INTO role_function(role_id,function_code) VALUES (5,'ListEquipmentGroup');
INSERT INTO m_function(function_code,screen_code,name,path,sort_order) VALUES ('AddEquipmentGroup','AddEquipmentGroup','Tạo nhóm thiết bị','/screens/AddEquipmentGroup',301);
INSERT INTO role_function(role_id,function_code) VALUES (5,'AddEquipmentGroup');
INSERT INTO m_function(function_code,screen_code,name,path,sort_order) VALUES ('EditEquipmentGroup','EditEquipmentGroup','Cập nhật nhóm thiết bị','/screens/EditEquipmentGroup',302);
INSERT INTO role_function(role_id,function_code) VALUES (5,'EditEquipmentGroup');
INSERT INTO m_function(function_code,screen_code,name,path,sort_order) VALUES ('ListStorageLocation','ListStorageLocation','Vị trí kho','/screens/ListStorageLocation',303);
INSERT INTO role_function(role_id,function_code) VALUES (5,'ListStorageLocation');
INSERT INTO m_function(function_code,screen_code,name,path,sort_order) VALUES ('AddStorageLocation','AddStorageLocation','Tạo vị trí kho','/screens/AddStorageLocation',304);
INSERT INTO role_function(role_id,function_code) VALUES (5,'AddStorageLocation');
INSERT INTO m_function(function_code,screen_code,name,path,sort_order) VALUES ('EditStorageLocation','EditStorageLocation','Cập nhật vị trí kho','/screens/EditStorageLocation',305);
INSERT INTO role_function(role_id,function_code) VALUES (5,'EditStorageLocation');
INSERT INTO m_function(function_code,screen_code,name,path,sort_order) VALUES ('ListProfession','ListProfession','Ngành nghề','/screens/ListProfession',306);
INSERT INTO role_function(role_id,function_code) VALUES (6,'ListProfession');
INSERT INTO m_function(function_code,screen_code,name,path,sort_order) VALUES ('AddProfession','AddProfession','Tạo ngành nghề','/screens/AddProfession',307);
INSERT INTO role_function(role_id,function_code) VALUES (6,'AddProfession');
INSERT INTO m_function(function_code,screen_code,name,path,sort_order) VALUES ('EditProfession','EditProfession','Cập nhật ngành nghề','/screens/EditProfession',308);
INSERT INTO role_function(role_id,function_code) VALUES (6,'EditProfession');
INSERT INTO m_function(function_code,screen_code,name,path,sort_order) VALUES ('ListClassroom','ListClassroom','Phòng học','/screens/ListClassroom',309);
INSERT INTO role_function(role_id,function_code) VALUES (1,'ListClassroom');
INSERT INTO m_function(function_code,screen_code,name,path,sort_order) VALUES ('AddClassroom','AddClassroom','Tạo phòng học','/screens/AddClassroom',310);
INSERT INTO role_function(role_id,function_code) VALUES (1,'AddClassroom');
INSERT INTO m_function(function_code,screen_code,name,path,sort_order) VALUES ('EditClassroom','EditClassroom','Cập nhật phòng học','/screens/EditClassroom',311);
INSERT INTO role_function(role_id,function_code) VALUES (1,'EditClassroom');
UPDATE system_settings SET center_name='Trung tâm đào tạo BK-VTCMS',version=version+1 WHERE id=1 AND center_name='Trung tâm đào tạo ERHMS';
UPDATE permission_revision SET version=version+1 WHERE id=1;
