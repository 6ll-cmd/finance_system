-- Flyway V2 — 扩展 token 和 password 字段长度
ALTER TABLE users ALTER COLUMN password TYPE VARCHAR(256);
ALTER TABLE users ALTER COLUMN token TYPE VARCHAR(512);
