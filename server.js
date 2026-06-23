/**
 * 发票管家 — 后端服务入口
 * Express REST API + SQLite + Token 认证
 *
 * 启动:  node server.js
 * 默认账号: admin / admin123
 */
const express = require('express');
const path = require('path');
const { config, PORT } = require('./server/config');
const db = require('./server/database');
const serveStatic = require('./server/middleware/serveStatic');

const app = express();
app.use(express.json());
app.use(serveStatic);

// ── 注册所有路由模块 ──
require('./server/routes/auth').register(app);
require('./server/routes/invoices').register(app);
require('./server/routes/vouchers').register(app);
require('./server/routes/stats').register(app);
require('./server/routes/accounts').register(app);
require('./server/routes/audit').register(app);
require('./server/routes/config').register(app, config);
require('./server/routes/ai').register(app, config);
require('./server/routes/system').register(app);

// ── 404 处理 ──
app.use('/api', (_req, res) => res.status(404).json({ error: 'API 端点不存在' }));

// ── 启动服务 ──
app.listen(PORT, () => {
  const { total } = db.prepare('SELECT COUNT(*) AS total FROM invoices').get();
  const users = db.prepare('SELECT COUNT(*) AS c FROM users').get().c;
  console.log(`\n🧾 发票管家已启动 (多账号)`);
  console.log(`   地址: http://localhost:${PORT}`);
  console.log(`   用户: ${users} 个 | 发票: ${total} 条`);
  console.log(`   默认: admin / admin123\n`);
});
