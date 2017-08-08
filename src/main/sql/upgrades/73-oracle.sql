-- Add column 'lock_type' to table 'vortex_lock'

ALTER TABLE vortex_lock ADD lock_type VARCHAR2(64) DEFAULT 'EXCLUSIVE' NOT null;

commit;
exit;
