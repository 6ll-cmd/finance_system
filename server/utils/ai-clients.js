/**
 * 发票管家 — AI 客户端函数（OpenAI / Claude 双协议）
 */
const http = require('http');
const https = require('https');

const OCR_PROMPT = `请识别这张发票图片，提取以下字段并以 JSON 格式返回（只返回 JSON，不要其他文字）：
{
  "type": "发票类型（增值税专用发票/增值税电子普通发票/增值税普通发票等）",
  "number": "发票号码（8-12位数字）",
  "date": "开票日期（YYYY-MM-DD格式）",
  "seller": "销售方企业全称",
  "buyer": "购买方企业全称",
  "amount": "不含税金额（数字，如 1280.50）",
  "taxRate": "税率（数字，如 6 表示 6%）",
  "taxAmount": "税额（数字）",
  "totalAmount": "含税总额（数字）"
}
如果某个字段无法识别，值设为空字符串""。金额字段无法识别时设为 0。`;

const AI_SYSTEM_PROMPT = `你是发票管家 AI 助手，帮用户高效管理财务。你可以：
1. 录入发票 — 从自然语言提取销售方、金额、税率等信息
2. 查询统计 — 告诉用户本月的发票总数、金额、待报销等
3. 分析建议 — 分析数据趋势，给出财务建议
4. 凭证建议 — 根据描述建议会计借贷分录
5. 回答问题 — 解答发票、税务、会计相关问题

当用户要求执行操作时，返回 JSON：{"action":"操作名","data":{...},"reply":"回复文字"}
当用户只是聊天时，直接返回文字回复。
始终用简洁友好的中文回复，不超过 200 字。`;

// ── OpenAI OCR ──
function callOpenAI(ai, base64) {
  return new Promise((resolve, reject) => {
    const body = JSON.stringify({
      model: ai.model,
      messages: [{
        role: 'user',
        content: [
          { type: 'text', text: OCR_PROMPT },
          { type: 'image_url', image_url: { url: `data:image/png;base64,${base64}` } }
        ]
      }],
      max_tokens: ai.maxTokens || 800,
      temperature: 0
    });
    const url = new URL(ai.baseUrl.replace(/\/$/, '') + '/chat/completions');
    const opts = {
      hostname: url.hostname, port: url.port || (url.protocol === 'https:' ? 443 : 80),
      path: url.pathname, method: 'POST',
      headers: { 'Content-Type': 'application/json', 'Authorization': `Bearer ${ai.apiKey}` }
    };
    const req = (url.protocol === 'https:' ? https : http).request(opts, r => {
      let d = ''; r.on('data', c => d += c); r.on('end', () => {
        try {
          const j = JSON.parse(d);
          if (j.error) reject(new Error(j.error.message || 'API 错误'));
          else resolve(j.choices[0].message.content);
        } catch (e) { reject(new Error('解析 AI 响应失败')); }
      });
    });
    req.on('error', e => reject(e));
    req.write(body); req.end();
  });
}

// ── Claude OCR ──
function callClaude(ai, base64) {
  return new Promise((resolve, reject) => {
    const body = JSON.stringify({
      model: ai.model,
      max_tokens: ai.maxTokens || 800,
      messages: [{
        role: 'user',
        content: [
          { type: 'image', source: { type: 'base64', media_type: 'image/png', data: base64 } },
          { type: 'text', text: OCR_PROMPT }
        ]
      }]
    });
    const opts = {
      hostname: 'api.anthropic.com', port: 443, path: '/v1/messages', method: 'POST',
      headers: { 'Content-Type': 'application/json', 'x-api-key': ai.apiKey, 'anthropic-version': '2023-06-01' }
    };
    const req = https.request(opts, r => {
      let d = ''; r.on('data', c => d += c); r.on('end', () => {
        try {
          const j = JSON.parse(d);
          if (j.error) reject(new Error(j.error.message));
          else resolve(j.content[0].text);
        } catch (e) { reject(new Error('解析响应失败')); }
      });
    });
    req.on('error', e => reject(e));
    req.write(body); req.end();
  });
}

// ── OpenAI Chat ──
function callOpenAIChat(ai, messages) {
  return new Promise((resolve, reject) => {
    const body = JSON.stringify({ model: ai.model, messages, max_tokens: ai.maxTokens || 800, temperature: 0.3 });
    const url = new URL(ai.baseUrl.replace(/\/$/, '') + '/chat/completions');
    const opts = {
      hostname: url.hostname, port: url.port || (url.protocol === 'https:' ? 443 : 80),
      path: url.pathname, method: 'POST',
      headers: { 'Content-Type': 'application/json', 'Authorization': `Bearer ${ai.apiKey}` }
    };
    const req = (url.protocol === 'https:' ? https : http).request(opts, r => {
      let d = ''; r.on('data', c => d += c); r.on('end', () => {
        try { const j = JSON.parse(d); if (j.error) reject(new Error(j.error.message)); else resolve(j.choices[0].message.content); }
        catch (e) { reject(new Error('解析失败')); }
      });
    });
    req.on('error', e => reject(e));
    req.write(body); req.end();
  });
}

// ── Claude Chat ──
function callClaudeChat(ai, messages) {
  return new Promise((resolve, reject) => {
    const systemContent = messages.filter(m => m.role === 'system').map(m => m.content).join('\n\n') || undefined;
    const body = JSON.stringify({
      model: ai.model, max_tokens: ai.maxTokens || 800,
      messages: messages.filter(m => m.role !== 'system').map(m => ({
        role: m.role === 'assistant' ? 'assistant' : 'user', content: m.content
      })),
      system: systemContent
    });
    const opts = {
      hostname: 'api.anthropic.com', port: 443, path: '/v1/messages', method: 'POST',
      headers: { 'Content-Type': 'application/json', 'x-api-key': ai.apiKey, 'anthropic-version': '2023-06-01' }
    };
    const req = https.request(opts, r => {
      let d = ''; r.on('data', c => d += c); r.on('end', () => {
        try { const j = JSON.parse(d); if (j.error) reject(new Error(j.error.message)); else resolve(j.content[0].text); }
        catch (e) { reject(new Error('解析失败')); }
      });
    });
    req.on('error', e => reject(e));
    req.write(body); req.end();
  });
}

// ── 通用 HTTP 请求（用于 AI 连接测试）──
function httpRequest(url, apiKey, body, extraHeaders) {
  return new Promise((resolve, reject) => {
    const u = new URL(url);
    const headers = { 'Content-Type': 'application/json', 'Authorization': 'Bearer ' + apiKey, ...(extraHeaders || {}) };
    const opts = {
      hostname: u.hostname, port: u.port || (u.protocol === 'https:' ? 443 : 80),
      path: u.pathname, method: 'POST', headers, timeout: 10000
    };
    const req = (u.protocol === 'https:' ? https : http).request(opts, r => {
      let d = ''; r.on('data', c => d += c); r.on('end', () => {
        try { resolve(JSON.parse(d)); } catch (e) { reject(new Error('响应解析失败')); }
      });
    });
    req.on('error', e => reject(e));
    req.on('timeout', () => { req.destroy(); reject(new Error('timeout')); });
    req.write(body); req.end();
  });
}

function friendlyError(msg) {
  if (!msg) return '所有协议尝试均失败，请检查地址和令牌';
  if (msg.includes('401') || msg.includes('403')) return '认证失败 — 令牌无效或已过期';
  if (msg.includes('404')) return '接口地址不存在 — 请检查 API 地址';
  if (msg.includes('ENOTFOUND') || msg.includes('ECONNREFUSED') || msg.includes('ECONNRESET')) return '无法连接服务器 — 请检查地址和网络';
  if (msg.includes('timeout') || msg.includes('ETIMEDOUT')) return '连接超时 — 请检查网络';
  return msg.substring(0, 100);
}

module.exports = {
  OCR_PROMPT, AI_SYSTEM_PROMPT,
  callOpenAI, callClaude,
  callOpenAIChat, callClaudeChat,
  httpRequest, friendlyError
};
