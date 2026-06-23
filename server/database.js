/**
 * 发票管家 — 数据库初始化
 * 建表、迁移、默认管理员、data.json 迁移
 */
const fs = require('fs');
const path = require('path');
const crypto = require('crypto');
const { DatabaseSync } = require('node:sqlite');
const { ROOT, DB_PATH, DATA_JSON } = require('./config');

// 确保 db 目录存在
try { fs.mkdirSync(path.join(ROOT, 'db'), { recursive: true }); } catch (_) {}

// 连接数据库
const db = new DatabaseSync(DB_PATH);
db.exec('PRAGMA journal_mode=WAL; PRAGMA foreign_keys=ON;');

// 加载并执行 schema.sql（只执行一次）
let schemaSQL;
try {
  schemaSQL = fs.readFileSync(path.join(__dirname, '..', 'db', 'schema.sql'), 'utf-8');
} catch (_) {
  schemaSQL = fs.readFileSync(path.join(ROOT, 'db', 'schema.sql'), 'utf-8');
}
db.exec(schemaSQL);

// ── 兼容性迁移 ──

// 确保 users 表（兼容旧库）
db.exec('CREATE TABLE IF NOT EXISTS users (id TEXT PRIMARY KEY, username TEXT NOT NULL UNIQUE, password TEXT NOT NULL, token TEXT, role TEXT NOT NULL DEFAULT "user", created_at TEXT)');

// 添加 role 列
try { db.exec('ALTER TABLE users ADD COLUMN role TEXT NOT NULL DEFAULT "user"'); } catch (_) {}
// 修复旧管理员
db.prepare("UPDATE users SET role='admin' WHERE username='admin' AND role='user'").run();

// 添加 token_created_at 列
try { db.exec('ALTER TABLE users ADD COLUMN token_created_at TEXT'); } catch (_) {}

// 添加 user_id 列到相关表
try { db.exec('ALTER TABLE invoices ADD COLUMN user_id TEXT DEFAULT ""'); } catch (_) {}
try { db.exec('ALTER TABLE vouchers ADD COLUMN user_id TEXT DEFAULT ""'); } catch (_) {}
try { db.exec('ALTER TABLE audit_log ADD COLUMN user_id TEXT DEFAULT ""'); } catch (_) {}

// 索引
try { db.exec('CREATE INDEX IF NOT EXISTS idx_invoices_user ON invoices(user_id)'); } catch (_) {}
try { db.exec('CREATE INDEX IF NOT EXISTS idx_vouchers_user ON vouchers(user_id)'); } catch (_) {}

// ── 默认管理员 ──
const uc = db.prepare('SELECT COUNT(*) AS c FROM users').get().c;
if (uc === 0) {
  const id = crypto.randomUUID();
  const hash = crypto.createHash('sha256').update('admin123').digest('hex');
  const now = new Date().toISOString().replace('T', ' ').substring(0, 19);
  db.prepare('INSERT INTO users (id,username,password,role,created_at) VALUES (?,?,?,?,?)').run(id, 'admin', hash, 'admin', now);
  console.log('📝 默认管理员: admin / admin123');
}

// ── data.json 迁移 ──
const row = db.prepare('SELECT COUNT(*) AS cnt FROM invoices').get();
if (row.cnt === 0 && fs.existsSync(DATA_JSON)) {
  console.log('📦 检测到 data.json，正在迁移...');
  try {
    const json = JSON.parse(fs.readFileSync(DATA_JSON, 'utf-8'));
    const stmt = db.prepare('INSERT INTO invoices (id,invoice_date,seller,buyer,type,category_id,amount,tax_rate,tax_amount,total_amount,status,notes,invoice_code,invoice_number,user_id) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)');
    const adminId = db.prepare("SELECT id FROM users WHERE username='admin'").get().id;
    db.exec('BEGIN');
    for (const inv of (json.invoices || [])) {
      stmt.run(inv.id, inv.date, inv.seller, inv.buyer, inv.type, inv.category, inv.amount, inv.taxRate, inv.taxAmount, inv.totalAmount, inv.status, inv.notes || '', inv.invoiceCode || '', inv.invoiceNumber || '', adminId);
    }
    db.exec('COMMIT');
    fs.copyFileSync(DATA_JSON, DATA_JSON + '.bak');
    console.log('✅ 迁移完成');
  } catch (err) {
    console.error('data.json 迁移失败:', err.message);
    try { db.exec('ROLLBACK'); } catch (_) {}
  }
}

module.exports = db;
