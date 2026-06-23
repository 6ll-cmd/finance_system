/**
 * 发票管家 — 内存频率限制中间件
 * 无需外部依赖，使用 Map 存储计数器
 */

/**
 * 创建频率限制中间件
 * @param {number} windowMs - 时间窗口（毫秒）
 * @param {number} maxAttempts - 窗口内最大请求数
 */
function createRateLimiter(windowMs, maxAttempts) {
  const attempts = new Map();

  return function rateLimiter(req, res, next) {
    const key = req.ip || '127.0.0.1';
    const now = Date.now();
    const record = attempts.get(key);

    if (record && now < record.resetTime && record.count >= maxAttempts) {
      return res.status(429).json({ error: '请求过于频繁，请稍后再试' });
    }

    if (!record || now >= record.resetTime) {
      attempts.set(key, { count: 1, resetTime: now + windowMs });
    } else {
      record.count++;
    }

    // 概率性清理过期条目（约 1% 概率触发）
    if (Math.random() < 0.01) {
      for (const [k, v] of attempts) {
        if (now >= v.resetTime) attempts.delete(k);
      }
    }

    next();
  };
}

module.exports = { createRateLimiter };
