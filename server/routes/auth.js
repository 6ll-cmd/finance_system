/**
 * 发票管家 — 认证路由（注册/登录/当前用户）
 */
const crypto = require('crypto');
const db = require('../database');
const { requireAuth } = require('../middleware/auth');
const { createRateLimiter } = require('../middleware/rateLimiter');

const loginLimiter = createRateLimiter(60 * 1000, 5);    // 5次/分钟
const registerLimiter = createRateLimiter(60 * 1000, 3);  // 3次/分钟

function register(app) {
  // ── POST /api/register ──
  app.post('/api/register', registerLimiter, (req, res) => {
    const { username, password } = req.body;
    if (!username || !password) return res.status(400).json({ error: '用户名和密码不能为空' });
    if (username.length < 2) return res.status(400).json({ error: '用户名至少2位' });
    if (password.length < 8) return res.status(400).json({ error: '密码至少8位' });
    if (!/[a-zA-Z]/.test(password) || !/\d/.test(password))
      return res.status(400).json({ error: '密码需包含字母和数字' });

    const exists = db.prepare('SELECT id FROM users WHERE username = ?').get(username);
    if (exists) return res.status(409).json({ error: '用户名已存在' });

    const id = crypto.randomUUID();
    const hash = crypto.createHash('sha256').update(password).digest('hex');
    const now = new Date().toISOString().replace('T', ' ').substring(0, 19);
    db.prepare('INSERT INTO users (id,username,password,created_at) VALUES (?,?,?,?)').run(id, username, hash, now);

    const token = crypto.randomBytes(32).toString('hex');
    db.prepare('UPDATE users SET token=?, token_created_at=? WHERE id=?').run(token, now, id);

    res.status(201).json({ token, username, id });
  });

  // ── POST /api/login ──
  app.post('/api/login', loginLimiter, (req, res) => {
    const { username, password } = req.body;
    const hash = crypto.createHash('sha256').update(password || '').digest('hex');
    const user = db.prepare('SELECT id, username FROM users WHERE username=? AND password=?').get(username, hash);
    if (!user) return res.status(401).json({ error: '用户名或密码错误' });

    const token = crypto.randomBytes(32).toString('hex');
    const now = new Date().toISOString().replace('T', ' ').substring(0, 19);
    db.prepare('UPDATE users SET token=?, token_created_at=? WHERE id=?').run(token, now, user.id);

    res.json({ token, username: user.username, id: user.id });
  });

  // ── GET /api/me ──
  app.get('/api/me', requireAuth, (req, res) => {
    res.json({ id: req.user.id, username: req.user.username, role: req.user.role });
  });
}

module.exports = { register };
