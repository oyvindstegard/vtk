-- Add column 'lock_type' to table 'vortex_lock'

ALTER TABLE vortex_lock ADD COLUMN lock_type VARCHAR(64) NOT NULL DEFAULT 'EXCLUSIVE';
