-- ============================================================
-- 发票管家 — 数据库 Schema (SQLite)
-- 版本: 3.0 — 多账号支持
-- ============================================================

-- 用户账号
CREATE TABLE IF NOT EXISTS users (
  id           TEXT PRIMARY KEY,                               -- 用户 ID（UUID）
  username     TEXT NOT NULL UNIQUE,                           -- 登录名
  password     TEXT NOT NULL,                                  -- SHA-256 哈希
  role         TEXT NOT NULL DEFAULT 'user',                   -- admin | user
  token        TEXT,                                           -- 当前登录 token
  token_created_at TEXT,                                       -- token 创建时间（用于过期检查）
  created_at   TEXT NOT NULL DEFAULT (datetime('now','localtime'))
);

-- 发票类别
CREATE TABLE IF NOT EXISTS categories (
  id          TEXT PRIMARY KEY,
  name        TEXT NOT NULL,
  icon        TEXT,
  sort_order  INTEGER DEFAULT 0
);

-- 发票主表
CREATE TABLE IF NOT EXISTS invoices (
  id              TEXT PRIMARY KEY,                             -- INV-2026-0001
  invoice_date    TEXT NOT NULL,                                -- 2026-06-18
  seller          TEXT NOT NULL,                                -- 销售方全称
  buyer           TEXT NOT NULL DEFAULT '广州共生纪元云科技有限公司',
  type            TEXT NOT NULL DEFAULT '增值税电子普通发票',     -- 增值税专用发票 | 增值税电子普通发票 | ...
  category_id     TEXT NOT NULL REFERENCES categories(id),
  amount          REAL NOT NULL DEFAULT 0,                      -- 不含税金额
  tax_rate        REAL NOT NULL DEFAULT 0,                      -- 税率（%）
  tax_amount      REAL NOT NULL DEFAULT 0,                      -- 税额
  total_amount    REAL NOT NULL DEFAULT 0,                      -- 含税总额
  status          TEXT NOT NULL DEFAULT 'pending'               -- pending | reimbursed | rejected
                  CHECK (status IN ('pending','reimbursed','rejected')),
  notes           TEXT DEFAULT '',
  invoice_code    TEXT DEFAULT '',                              -- 发票代码（12位）
  invoice_number  TEXT DEFAULT '',                              -- 发票号码（8位）
  image_path      TEXT,                                         -- 发票图片路径
  user_id         TEXT NOT NULL DEFAULT '',                     -- 所属用户
  created_at      TEXT NOT NULL DEFAULT (datetime('now','localtime')),
  updated_at      TEXT NOT NULL DEFAULT (datetime('now','localtime'))
);

-- 操作审计日志
CREATE TABLE IF NOT EXISTS audit_log (
  id          INTEGER PRIMARY KEY AUTOINCREMENT,
  entity_id    TEXT,                                               -- 关联实体 ID（发票号或凭证号）
  action      TEXT NOT NULL,                                    -- create | status_change | delete
  old_status  TEXT,
  new_status  TEXT,
  detail      TEXT,                                             -- JSON 格式的操作详情
  user_id     TEXT DEFAULT '',                                 -- 操作人用户 ID
  changed_at  TEXT NOT NULL DEFAULT (datetime('now','localtime')),
  operator    TEXT DEFAULT '系统'
);

-- ═══ 索引 ═══
CREATE INDEX IF NOT EXISTS idx_invoices_date     ON invoices(invoice_date);
CREATE INDEX IF NOT EXISTS idx_invoices_status   ON invoices(status);
CREATE INDEX IF NOT EXISTS idx_invoices_category ON invoices(category_id);
CREATE INDEX IF NOT EXISTS idx_invoices_seller   ON invoices(seller);
CREATE INDEX IF NOT EXISTS idx_invoices_user     ON invoices(user_id);
CREATE INDEX IF NOT EXISTS idx_audit_entity  ON audit_log(entity_id);
CREATE INDEX IF NOT EXISTS idx_audit_date        ON audit_log(changed_at);

-- ═══ 预置类别 ═══
INSERT OR IGNORE INTO categories (id, name, icon, sort_order) VALUES
  ('office',    '办公用品', 'printer',         1),
  ('travel',    '差旅交通', 'plane',           2),
  ('catering',  '餐饮招待', 'utensils',        3),
  ('utility',   '水电物业', 'zap',             4),
  ('service',   '技术服务', 'code',            5),
  ('logistics', '物流快递', 'truck',           6),
  ('rental',    '房租租赁', 'building',        7),
  ('other',     '其他',     'more-horizontal', 8);

-- ══════════════════════════════════════════════════════════
--  科目表 (v4.0 — ERPNext 风格)
-- ══════════════════════════════════════════════════════════

CREATE TABLE IF NOT EXISTS accounts (
  id           TEXT PRIMARY KEY,                              -- 科目编号（如 1001）
  name         TEXT NOT NULL,                                 -- 科目名称
  parent_id    TEXT REFERENCES accounts(id),                  -- 上级科目
  account_type TEXT NOT NULL DEFAULT 'expense',               -- asset/liability/equity/income/expense
  level        INTEGER DEFAULT 0,                             -- 层级（0=一级 1=二级）
  sort_order   INTEGER DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_accounts_parent ON accounts(parent_id);
CREATE INDEX IF NOT EXISTS idx_accounts_type   ON accounts(account_type);

-- 预置科目表（企业会计准则常见科目）
INSERT OR IGNORE INTO accounts (id, name, parent_id, account_type, level, sort_order) VALUES
  -- 资产类
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
  -- 负债类
  ('2001','负债',NULL,'liability',0,5),
  ('2002','流动负债',NULL,'liability',0,6),
  ('2002001','应付账款','2002','liability',1,1),
  ('2002002','预收账款','2002','liability',1,2),
  ('2002003','应付职工薪酬','2002','liability',1,3),
  ('2002004','应交税费','2002','liability',1,4),
  ('2002005','其他应付款','2002','liability',1,5),
  -- 权益类
  ('3001','所有者权益',NULL,'equity',0,7),
  ('3001001','实收资本','3001','equity',1,1),
  ('3001002','未分配利润','3001','equity',1,2),
  -- 收入类
  ('4001','收入',NULL,'income',0,8),
  ('4001001','主营业务收入','4001','income',1,1),
  ('4001002','其他业务收入','4001','income',1,2),
  ('4001003','营业外收入','4001','income',1,3),
  -- 费用类
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
  ('5001010','职工薪酬','5001','expense',1,10);

-- ══════════════════════════════════════════════════════════
--  凭证模块 (v2.0)
-- ══════════════════════════════════════════════════════════

-- 凭证主表
CREATE TABLE IF NOT EXISTS vouchers (
  id            TEXT PRIMARY KEY,                              -- VCH-2026-0001
  voucher_date  TEXT NOT NULL,                                 -- 凭证日期
  description   TEXT NOT NULL DEFAULT '',                      -- 凭证摘要
  total_amount  REAL NOT NULL DEFAULT 0,                       -- 合计金额
  status        TEXT NOT NULL DEFAULT 'draft'                  -- draft | posted | cancelled
                CHECK (status IN ('draft','posted','cancelled')),
  notes         TEXT DEFAULT '',
  user_id        TEXT NOT NULL DEFAULT '',                      -- 所属用户
  created_at    TEXT NOT NULL DEFAULT (datetime('now','localtime')),
  updated_at    TEXT NOT NULL DEFAULT (datetime('now','localtime'))
);

-- 凭证明细（借贷分录）
CREATE TABLE IF NOT EXISTS voucher_entries (
  id            INTEGER PRIMARY KEY AUTOINCREMENT,
  voucher_id    TEXT NOT NULL REFERENCES vouchers(id) ON DELETE CASCADE,
  account       TEXT NOT NULL DEFAULT '',                      -- 会计科目
  debit_amount  REAL NOT NULL DEFAULT 0,                       -- 借方金额
  credit_amount REAL NOT NULL DEFAULT 0,                       -- 贷方金额
  summary       TEXT DEFAULT '',                               -- 分录摘要
  sort_order    INTEGER DEFAULT 0
);

-- 凭证索引
CREATE INDEX IF NOT EXISTS idx_vouchers_date   ON vouchers(voucher_date);
CREATE INDEX IF NOT EXISTS idx_vouchers_status ON vouchers(status);
CREATE INDEX IF NOT EXISTS idx_vouchers_user   ON vouchers(user_id);
CREATE INDEX IF NOT EXISTS idx_ventries_vid    ON voucher_entries(voucher_id);
