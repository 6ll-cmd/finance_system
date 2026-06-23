/**
 * 发票管家 — 审计日志路由
 */
const db = require('../database');
const { requireAuth } = require('../middleware/auth');

function register(app) {
  app.get('/api/audit-log', requireAuth, (req, res) => {
    const limit = Math.min(parseInt(req.query.limit) || 100, 500);
    res.json(db.prepare('SELECT * FROM audit_log ORDER BY changed_at DESC LIMIT ?').all(limit));
  });
}

module.exports = { register };
