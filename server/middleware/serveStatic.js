/**
 * 发票管家 — 静态文件服务中间件
 * 兼容 pkg 打包和开发模式
 */
const fs = require('fs');
const path = require('path');

const MIME = {
  '.html': 'text/html; charset=utf-8',
  '.css': 'text/css; charset=utf-8',
  '.js': 'application/javascript; charset=utf-8',
  '.json': 'application/json',
  '.png': 'image/png',
  '.jpg': 'image/jpeg',
  '.svg': 'image/svg+xml',
  '.ico': 'image/x-icon'
};

function serveStatic(req, res, next) {
  if (req.path.startsWith('/api/')) return next();

  let fp = path.join(__dirname, '..', '..', req.path === '/' ? 'index.html' : req.path);
  // 移除查询字符串
  fp = fp.split('?')[0];

  try {
    const ext = path.extname(fp).toLowerCase();
    const data = fs.readFileSync(fp);
    res.setHeader('Content-Type', MIME[ext] || 'application/octet-stream');
    res.send(data);
  } catch (e) {
    if (req.path === '/' || !path.extname(fp)) {
      // SPA fallback: 对非文件路径返回 index.html
      try {
        res.setHeader('Content-Type', 'text/html; charset=utf-8');
        res.send(fs.readFileSync(path.join(__dirname, '..', '..', 'index.html')));
      } catch (_) { next(); }
    } else {
      next();
    }
  }
}

module.exports = serveStatic;
