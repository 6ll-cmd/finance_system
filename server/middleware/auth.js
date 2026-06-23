/**
 * 发票管家 — 认证中间件
 * Token 认证 + 7天过期检查
 */
const db = require('../database');

const TOKEN_EXPIRY_MS = 7 * 24 * 60 * 60 * 1000; // 7 天

function requireAuth(req, res, next) {
  const token = (req.headers.authorization || '').replace('Bearer ', '');
  if (!token) return res.status(401).json({ error: '未登录' });

  const user = db.prepare('SELECT id, username, role, token_created_at FROM users WHERE token = ?').get(token);
  if (!user) return res.status(401).json({ error: '登录已过期，请重新登录' });

  // 检查 Token 是否过期（7天）
  if (user.token_created_at) {
    const created = new Date(user.token_created_at.replace(' ', 'T')).getTime();
    if (Date.now() - created > TOKEN_EXPIRY_MS) {
      db.prepare('UPDATE users SET token=NULL, token_created_at=NULL WHERE id=?').run(user.id);
      return res.status(401).json({ error: '登录已过期，请重新登录' });
    }
  }

  req.user = { id: user.id, username: user.username, role: user.role };
  next();
}

function adminOnly(req, res, next) {
  if (req.user.role !== 'admin') return res.status(403).json({ error: '需要管理员权限' });
  next();
}

module.exports = { requireAuth, adminOnly };
