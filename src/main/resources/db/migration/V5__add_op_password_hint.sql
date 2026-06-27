-- V5: add reversible-encrypted operation password storage (for reveal feature)
ALTER TABLE users ADD COLUMN operation_password_hint VARCHAR(512);
