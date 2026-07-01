ALTER TABLE vouchers
  ADD COLUMN IF NOT EXISTS responsible_person VARCHAR(64) NOT NULL DEFAULT '';

CREATE TABLE IF NOT EXISTS voucher_responsible_people (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  name VARCHAR(64) NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  CONSTRAINT uq_voucher_responsible_people_user_name UNIQUE (user_id, name)
);

CREATE INDEX IF NOT EXISTS idx_voucher_responsible_people_user
  ON voucher_responsible_people(user_id);
