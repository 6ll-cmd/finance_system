/**
 * 发票管家 — 配置加载
 */
const fs = require('fs');
const path = require('path');

let config = {
  ai: { provider: 'openai', apiKey: '', model: 'gpt-4o', baseUrl: 'https://api.openai.com/v1', maxTokens: 800 }
};

try {
  const cfgPath = path.join(path.dirname(process.execPath), 'config.json');
  if (fs.existsSync(cfgPath)) {
    config = { ...config, ...JSON.parse(fs.readFileSync(cfgPath, 'utf-8')) };
  } else if (fs.existsSync(path.join(__dirname, '..', 'config.json'))) {
    config = { ...config, ...JSON.parse(fs.readFileSync(path.join(__dirname, '..', 'config.json'), 'utf-8')) };
  }
} catch (e) {
  console.error('配置加载失败:', e.message);
}

const PORT = process.env.PORT || 3456;
const ROOT = path.dirname(process.execPath);
const DB_PATH = path.join(ROOT, 'db', 'invoice.db');
const DATA_JSON = path.join(ROOT, 'data.json');

module.exports = { config, PORT, ROOT, DB_PATH, DATA_JSON };
