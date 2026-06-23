/**
 * 发票管家 — 系统路由（关机 + 空闲退出）
 */
const { requireAuth } = require('../middleware/auth');

let idleTimer;

function resetIdle() {
  clearTimeout(idleTimer);
  idleTimer = setTimeout(() => {
    console.log('⏰ 已空闲 2 分钟，自动退出…');
    process.exit(0);
  }, 2 * 60 * 1000);
}

function register(app) {
  // ── POST /api/shutdown ──
  app.post('/api/shutdown', requireAuth, (_req, res) => {
    res.json({ ok: true, message: '服务正在关闭…' });
    setTimeout(() => { console.log('🛑 收到关闭指令，正在退出…'); process.exit(0); }, 500);
  });

  // 空闲计时器
  app.use((req, _res, next) => { resetIdle(); next(); });
  resetIdle();
}

module.exports = { register };
