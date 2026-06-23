/**
 * 发票管家 — 科目表路由
 */
const db = require('../database');
const { requireAuth } = require('../middleware/auth');

function register(app) {
  app.get('/api/accounts', requireAuth, (_req, res) => {
    res.json(db.prepare('SELECT * FROM accounts ORDER BY id').all());
  });
}

module.exports = { register };
