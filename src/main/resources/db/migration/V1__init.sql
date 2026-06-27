-- ============================================================
-- 发票管家 — Flyway 迁移 V1
-- SQLite → PostgreSQL 转换
-- 公司: 广州共生纪元云科技有限公司
-- ============================================================

-- ═══ 用户账号 ═══
CREATE TABLE users (
  id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  username     VARCHAR(64) NOT NULL UNIQUE,
  password     VARCHAR(128) NOT NULL,                              -- SHA-256 哈希
  role         VARCHAR(16) NOT NULL DEFAULT 'user',               -- admin | user
  token        VARCHAR(128),                                      -- JWT refresh token
  token_created_at TIMESTAMPTZ,                                   -- token 签发时间
  created_at   TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

COMMENT ON TABLE users IS '用户账号';
COMMENT ON COLUMN users.role IS 'admin | user';

-- ═══ 发票类别 ═══
CREATE TABLE categories (
  id         VARCHAR(32) PRIMARY KEY,
  name       VARCHAR(64) NOT NULL,
  icon       VARCHAR(32),
  sort_order INT DEFAULT 0
);

-- ═══ 发票主表 ═══
CREATE TABLE invoices (
  id              VARCHAR(32) PRIMARY KEY,                        -- INV-2026-0001
  invoice_date    DATE NOT NULL,
  seller          VARCHAR(256) NOT NULL,                          -- 销售方全称
  buyer           VARCHAR(256) NOT NULL DEFAULT '广州共生纪元云科技有限公司',
  type            VARCHAR(64) NOT NULL DEFAULT '增值税电子普通发票',
  category_id     VARCHAR(32) NOT NULL REFERENCES categories(id),
  amount          NUMERIC(12,2) NOT NULL DEFAULT 0,               -- 不含税金额
  tax_rate        NUMERIC(5,2) NOT NULL DEFAULT 0,                -- 税率（%）
  tax_amount      NUMERIC(12,2) NOT NULL DEFAULT 0,               -- 税额
  total_amount    NUMERIC(12,2) NOT NULL DEFAULT 0,               -- 含税总额
  status          VARCHAR(16) NOT NULL DEFAULT 'pending'          -- pending | reimbursed | rejected
                  CHECK (status IN ('pending','reimbursed','rejected')),
  notes           TEXT DEFAULT '',
  invoice_code    VARCHAR(32) DEFAULT '',                         -- 发票代码（12位）
  invoice_number  VARCHAR(32) DEFAULT '',                         -- 发票号码（8位）
  image_path      VARCHAR(512),                                   -- 发票图片路径
  user_id         UUID NOT NULL REFERENCES users(id),
  created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  deleted         SMALLINT NOT NULL DEFAULT 0                     -- 逻辑删除 (MyBatis-Plus)
);

CREATE INDEX idx_invoices_date     ON invoices(invoice_date);
CREATE INDEX idx_invoices_status   ON invoices(status);
CREATE INDEX idx_invoices_category ON invoices(category_id);
CREATE INDEX idx_invoices_seller   ON invoices(seller);
CREATE INDEX idx_invoices_user     ON invoices(user_id);

COMMENT ON COLUMN invoices.status IS 'pending | reimbursed | rejected';

-- ═══ 操作审计日志 ═══
CREATE TABLE audit_log (
  id          BIGSERIAL PRIMARY KEY,
  entity_id   VARCHAR(32),                                         -- 关联实体 ID
  action      VARCHAR(32) NOT NULL,                                -- create | status_change | delete
  old_status  VARCHAR(32),
  new_status  VARCHAR(32),
  detail      TEXT,                                                -- JSON 格式操作详情
  user_id     UUID REFERENCES users(id),                           -- 操作人
  changed_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  operator    VARCHAR(64) DEFAULT '系统'
);

CREATE INDEX idx_audit_entity ON audit_log(entity_id);
CREATE INDEX idx_audit_date   ON audit_log(changed_at);
CREATE INDEX idx_audit_user   ON audit_log(user_id);

-- ═══ 预置类别 ═══
INSERT INTO categories (id, name, icon, sort_order) VALUES
  ('office',    '办公用品', 'printer',         1),
  ('travel',    '差旅交通', 'plane',           2),
  ('catering',  '餐饮招待', 'utensils',        3),
  ('utility',   '水电物业', 'zap',             4),
  ('service',   '技术服务', 'code',            5),
  ('logistics', '物流快递', 'truck',           6),
  ('rental',    '房租租赁', 'building',        7),
  ('other',     '其他',     'more-horizontal', 8)
ON CONFLICT (id) DO NOTHING;

-- ═══ 科目表 ═══
CREATE TABLE accounts (
  id           VARCHAR(16) PRIMARY KEY,                            -- 科目编号（如 1001）
  name         VARCHAR(64) NOT NULL,
  parent_id    VARCHAR(16) REFERENCES accounts(id),                -- 上级科目
  account_type VARCHAR(16) NOT NULL DEFAULT 'expense',             -- asset/liability/equity/income/expense
  level        INT DEFAULT 0,                                      -- 层级（0=一级 1=二级）
  sort_order   INT DEFAULT 0
);

CREATE INDEX idx_accounts_parent ON accounts(parent_id);
CREATE INDEX idx_accounts_type   ON accounts(account_type);

-- 预置科目表（企业会计准则常见科目，37 个）
INSERT INTO accounts (id, name, parent_id, account_type, level, sort_order) VALUES
  ('1001','资产',NULL,'asset',0,1),
  ('1002','流动资产',NULL,'asset',0,2),
  ('1002001','银行存款','1002','asset',1,1),
  ('1002002','库存现金','1002','asset',1,2),
  ('1002003','应收账款','1002','asset',1,3),
  ('1002004','预付账款','1002','asset',1,4),
  ('1002005','其他应收款','1002','asset',1,5),
  ('1003','固定资产',NULL,'asset',0,3),
  ('1003001','办公设备','1003','asset',1,1),
  ('1003002','电子设备','1003','asset',1,2),
  ('1003003','累计折旧','1003','asset',1,3),
  ('1004','无形资产',NULL,'asset',0,4),
  ('2001','负债',NULL,'liability',0,5),
  ('2002','流动负债',NULL,'liability',0,6),
  ('2002001','应付账款','2002','liability',1,1),
  ('2002002','预收账款','2002','liability',1,2),
  ('2002003','应付职工薪酬','2002','liability',1,3),
  ('2002004','应交税费','2002','liability',1,4),
  ('2002005','其他应付款','2002','liability',1,5),
  ('3001','所有者权益',NULL,'equity',0,7),
  ('3001001','实收资本','3001','equity',1,1),
  ('3001002','未分配利润','3001','equity',1,2),
  ('4001','收入',NULL,'income',0,8),
  ('4001001','主营业务收入','4001','income',1,1),
  ('4001002','其他业务收入','4001','income',1,2),
  ('4001003','营业外收入','4001','income',1,3),
  ('5001','费用',NULL,'expense',0,9),
  ('5001001','办公费','5001','expense',1,1),
  ('5001002','差旅费','5001','expense',1,2),
  ('5001003','招待费','5001','expense',1,3),
  ('5001004','水电费','5001','expense',1,4),
  ('5001005','物业费','5001','expense',1,5),
  ('5001006','租赁费','5001','expense',1,6),
  ('5001007','技术服务费','5001','expense',1,7),
  ('5001008','物流费','5001','expense',1,8),
  ('5001009','折旧费','5001','expense',1,9),
  ('5001010','职工薪酬','5001','expense',1,10)
ON CONFLICT (id) DO NOTHING;

-- ═══ 凭证主表 ═══
CREATE TABLE vouchers (
  id            VARCHAR(32) PRIMARY KEY,                            -- VCH-2026-0001
  voucher_date  DATE NOT NULL,
  description   TEXT NOT NULL DEFAULT '',
  total_amount  NUMERIC(12,2) NOT NULL DEFAULT 0,
  status        VARCHAR(16) NOT NULL DEFAULT 'draft'               -- draft | posted | cancelled
                CHECK (status IN ('draft','posted','cancelled')),
  notes         TEXT DEFAULT '',
  user_id       UUID NOT NULL REFERENCES users(id),
  created_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  updated_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  deleted       SMALLINT NOT NULL DEFAULT 0
);

CREATE INDEX idx_vouchers_date   ON vouchers(voucher_date);
CREATE INDEX idx_vouchers_status ON vouchers(status);
CREATE INDEX idx_vouchers_user   ON vouchers(user_id);

-- ═══ 凭证明细（借贷分录） ═══
CREATE TABLE voucher_entries (
  id            BIGSERIAL PRIMARY KEY,
  voucher_id    VARCHAR(32) NOT NULL REFERENCES vouchers(id) ON DELETE CASCADE,
  account       VARCHAR(16) NOT NULL DEFAULT '',                   -- 会计科目编号
  debit_amount  NUMERIC(12,2) NOT NULL DEFAULT 0,
  credit_amount NUMERIC(12,2) NOT NULL DEFAULT 0,
  summary       TEXT DEFAULT '',
  sort_order    INT DEFAULT 0
);

CREATE INDEX idx_ventries_vid ON voucher_entries(voucher_id);

-- ═══ flyway_schema_history 由 Flyway 自动创建 ═══
