/**
 * 发票管家 — 系统配置路由
 */
const fs = require('fs');
const path = require('path');
const { requireAuth, adminOnly } = require('../middleware/auth');
const { ROOT } = require('../config');

function register(app, config) {
  // ── GET /api/config ──
  app.get('/api/config', requireAuth, (_req, res) => {
    res.json({
      ai: {
        provider: config.ai.provider,
        baseUrl: config.ai.baseUrl,
        apiKey: config.ai.apiKey ? '***' + config.ai.apiKey.slice(-4) : '',
        model: config.ai.model
      }
    });
  });

  // ── PUT /api/config ──
  app.put('/api/config', requireAuth, adminOnly, (req, res) => {
    const { ai } = req.body;
    if (!ai) return res.status(400).json({ error: '无效配置' });
    config.ai = { ...config.ai, ...ai };
    const cfgPath = path.join(ROOT, 'config.json');
    try {
      fs.writeFileSync(cfgPath, JSON.stringify({ ai: config.ai }, null, 2), 'utf-8');
      res.json({ ok: true });
    } catch (e) {
      res.status(500).json({ error: '写入失败: ' + e.message });
    }
  });
}

module.exports = { register };
