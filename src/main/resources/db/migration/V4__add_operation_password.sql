-- V4: add per-user operation password for sensitive actions (delete/void/edit-posted)
ALTER TABLE users ADD COLUMN operation_password VARCHAR(256);
