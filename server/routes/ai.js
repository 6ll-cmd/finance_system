/**
 * 发票管家 — AI 路由（OCR + 对话 + 连接测试）
 */
const db = require('../database');
const { requireAuth } = require('../middleware/auth');
const {
  OCR_PROMPT, AI_SYSTEM_PROMPT,
  callOpenAI, callClaude,
  callOpenAIChat, callClaudeChat,
  httpRequest, friendlyError
} = require('../utils/ai-clients');

function register(app, config) {
  // ── POST /api/ocr/recognize ──
  app.post('/api/ocr/recognize', requireAuth, async (req, res) => {
    const { image } = req.body;
    if (!image) return res.status(400).json({ error: '请提供发票图片' });

    const ai = config.ai;
    if (!ai.apiKey) return res.status(400).json({ error: 'AI 接口未配置，请在 config.json 中设置 apiKey' });

    const base64 = image.replace(/^data:image\/\w+;base64,/, '');

    try {
      let result;
      if (ai.provider === 'anthropic') {
        result = await callClaude(ai, base64);
      } else {
        result = await callOpenAI(ai, base64);
      }

      let fields;
      try {
        const json = result.replace(/```json\s*|\s*```/g, '').trim();
        fields = JSON.parse(json);
      } catch (_) {
        const match = result.match(/\{[\s\S]*\}/);
        fields = match ? JSON.parse(match[0]) : {};
      }

      res.json({
        type: fields.type || '',
        number: fields.number || '',
        date: fields.date || '',
        seller: fields.seller || '',
        buyer: fields.buyer || '',
        amount: String(fields.amount || ''),
        taxRate: String(fields.taxRate || ''),
        taxAmount: String(fields.taxAmount || ''),
        totalAmount: String(fields.totalAmount || ''),
      });
    } catch (err) {
      res.status(500).json({ error: 'AI 识别失败: ' + err.message });
    }
  });

  // ── POST /api/ai/chat ──
  app.post('/api/ai/chat', requireAuth, async (req, res) => {
    const { messages } = req.body;
    if (!messages || !messages.length) return res.status(400).json({ error: '请提供对话内容' });
    const ai = config.ai;
    if (!ai.apiKey) return res.status(400).json({ error: 'AI 未配置，请在 config.json 中设置 apiKey' });

    const uid = req.user.id;
    const stats = {};
    try {
      const t = db.prepare('SELECT COUNT(*) AS c, COALESCE(SUM(total_amount),0) AS a FROM invoices WHERE user_id=?').get(uid);
      const now = new Date(), m = `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}`;
      const mo = db.prepare("SELECT COUNT(*) AS c, COALESCE(SUM(total_amount),0) AS a FROM invoices WHERE user_id=? AND invoice_date LIKE ?").get(uid, `${m}%`);
      const p = db.prepare("SELECT COUNT(*) AS c, COALESCE(SUM(total_amount),0) AS a FROM invoices WHERE user_id=? AND status='pending'").get(uid);
      Object.assign(stats, { totalInvoices: t.c, totalAmount: t.a, thisMonth: mo.c, thisMonthAmount: mo.a, pendingReimburse: p.c, pendingAmount: p.a });
    } catch (e) { console.error('AI 对话统计上下文构建失败:', e.message); }

    const recentInvoices = db.prepare('SELECT seller, total_amount, status, invoice_date FROM invoices WHERE user_id=? ORDER BY created_at DESC LIMIT 5').all(uid);

    const contextMsg = `当前用户数据：发票总数 ${stats.totalInvoices} 条，含税总额 ¥${(stats.totalAmount || 0).toFixed(2)}，本月 ${stats.thisMonth} 条共 ¥${(stats.thisMonthAmount || 0).toFixed(2)}，待报销 ${stats.pendingReimburse} 条。最近发票：${JSON.stringify(recentInvoices)}。当前日期：${new Date().toISOString().split('T')[0]}。`;

    const fullMessages = [
      { role: 'system', content: AI_SYSTEM_PROMPT },
      { role: 'system', content: contextMsg },
      ...messages.slice(-10)
    ];

    try {
      let result;
      if (ai.provider === 'anthropic') {
        result = await callClaudeChat(ai, fullMessages);
      } else {
        result = await callOpenAIChat(ai, fullMessages);
      }

      let reply = result, action = null, data = null;
      const jsonMatch = result.match(/\{[\s\S]*"action"[\s\S]*\}/);
      if (jsonMatch) {
        try {
          const parsed = JSON.parse(jsonMatch[0]);
          if (parsed.action) {
            action = parsed.action;
            data = parsed.data || {};
            reply = parsed.reply || result.replace(jsonMatch[0], '').trim();

            if (action === 'create_invoice' && data.seller && (data.amount || data.total_amount)) {
              const amt = parseFloat(data.amount || data.total_amount) || 0;
              const rate = parseFloat(data.taxRate || data.tax_rate) || 0;
              const taxAmt = Math.round(amt * rate / 100 * 100) / 100;
              const total = parseFloat(data.totalAmount || data.total_amount) || (amt + taxAmt);
              const now = new Date(), year = now.getFullYear();
              const last = db.prepare('SELECT id FROM invoices WHERE id LIKE ? ORDER BY id DESC LIMIT 1').get(`INV-${year}-%`);
              let n = 1; if (last) { const p = parseInt(last.id.split('-')[2]); if (!isNaN(p)) n = p + 1; }
              const newId = `INV-${year}-${String(n).padStart(4, '0')}`;
              db.prepare("INSERT INTO invoices (id,invoice_date,seller,buyer,type,category_id,amount,tax_rate,tax_amount,total_amount,status,notes,user_id) VALUES (?,?,?,?,'增值税电子普通发票','other',?,?,?,?,'pending',?,?)").run(newId, data.date || data.invoice_date || now.toISOString().split('T')[0], data.seller, data.buyer || '广州共生纪元云科技有限公司', amt, rate, taxAmt, total, data.notes || data.remark || '', uid);
              reply = `✅ 已录入发票 ${newId}：${data.seller}，¥${amt.toFixed(2)}`;
            }
            if (action === 'get_stats') {
              reply = `📊 本月统计：${stats.thisMonth} 条发票，共 ¥${(stats.thisMonthAmount || 0).toFixed(2)}。待报销 ${stats.pendingReimburse} 条，累计 ¥${(stats.pendingAmount || 0).toFixed(2)}。`;
            }
          }
        } catch (e) { console.error('AI Chat JSON 解析失败:', e.message); }
      }
      res.json({ reply, action, data });
    } catch (err) {
      res.status(500).json({ error: 'AI 服务异常: ' + err.message });
    }
  });

  // ── POST /api/ai/test ──
  app.post('/api/ai/test', requireAuth, async (req, res) => {
    const { baseUrl, apiKey, model } = req.body;
    if (!apiKey) return res.status(400).json({ error: '请提供 API Key' });
    const testModel = model || 'gpt-4o';
    let lastError = '';

    // 尝试 1: OpenAI 格式
    try {
      const url = (baseUrl || 'https://api.openai.com/v1').replace(/\/$/, '') + '/chat/completions';
      const result = await httpRequest(url, apiKey, JSON.stringify({ model: testModel, messages: [{ role: 'user', content: 'hi' }], max_tokens: 10 }));
      if (result.choices) return res.json({ ok: true, reply: result.choices[0].message.content, protocol: 'openai' });
      if (result.error) lastError = result.error.message || JSON.stringify(result.error);
    } catch (e) { lastError = e.message; }

    // 尝试 2: Anthropic 格式
    try {
      const result = await httpRequest('https://api.anthropic.com/v1/messages', apiKey,
        JSON.stringify({ model: testModel, max_tokens: 10, messages: [{ role: 'user', content: 'hi' }] }),
        { 'anthropic-version': '2023-06-01', 'x-api-key': apiKey });
      if (result.content) return res.json({ ok: true, reply: result.content[0].text, protocol: 'anthropic' });
      if (result.error) lastError = result.error.message || JSON.stringify(result.error);
    } catch (e) { /* fall through */ }

    // 尝试 3: 自定义地址 Anthropic 格式
    if (baseUrl && !baseUrl.includes('api.openai.com')) {
      try {
        const aUrl = baseUrl.replace(/\/$/, '') + '/v1/messages';
        const result = await httpRequest(aUrl, apiKey,
          JSON.stringify({ model: testModel, max_tokens: 10, messages: [{ role: 'user', content: 'hi' }] }),
          { 'anthropic-version': '2023-06-01', 'x-api-key': apiKey });
        if (result.content) return res.json({ ok: true, reply: result.content[0].text, protocol: 'anthropic-custom' });
      } catch (e) { /* fall through */ }
    }

    // 尝试 4: 自定义地址无 /v1 后缀
    if (baseUrl) {
      try {
        const rawUrl = baseUrl.replace(/\/$/, '') + '/chat/completions';
        const result = await httpRequest(rawUrl, apiKey, JSON.stringify({ model: testModel, messages: [{ role: 'user', content: 'hi' }], max_tokens: 10 }));
        if (result.choices) return res.json({ ok: true, reply: result.choices[0].message.content, protocol: 'openai-raw' });
      } catch (e) { /* fall through */ }
    }

    res.json({ ok: false, error: friendlyError(lastError) });
  });
}

module.exports = { register };
