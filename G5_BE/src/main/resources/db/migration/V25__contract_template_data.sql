-- Contract-specific snapshots for the supplied document template and appendices.
-- These values do not post payments or create equipment handover/return records.
ALTER TABLE training_contracts ADD COLUMN template_data TEXT NOT NULL DEFAULT '{}';
