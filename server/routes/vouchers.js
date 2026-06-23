/**
 * 发票管家 — 凭证路由
 */
const db = require('../database');
const { requireAuth } = require('../middleware/auth');
const { mapVoucher, logAudit } = require('../utils/helpers');

function register(app) {
  // ── GET /api/vouchers ──
  app.get('/api/vouchers', requireAuth, (req, res) => {
    const { search, status, page = 1, limit = 15 } = req.query;
    let where = 'WHERE v.user_id = ?'; const params = [req.user.id];
    if (search) { where += ' AND (v.id LIKE ? OR v.description LIKE ?)'; const q = `%${search}%`; params.push(q, q); }
    if (status && status !== 'all') { where += ' AND v.status = ?'; params.push(status); }
    const { total } = db.prepare(`SELECT COUNT(*) AS total FROM vouchers v ${where}`).get(...params);
    const p = Math.max(1, parseInt(page)), lim = Math.max(1, Math.min(100, parseInt(limit)));
    const rows = db.prepare(`SELECT v.* FROM vouchers v ${where} ORDER BY v.voucher_date DESC LIMIT ? OFFSET ?`).all(...params, lim, (p - 1) * lim);
    res.json({ total, page: p, limit: lim, totalPages: Math.ceil(total / lim), data: rows.map(mapVoucher) });
  });

  // ── GET /api/vouchers/:id ──
  app.get('/api/vouchers/:id', requireAuth, (req, res) => {
    const v = db.prepare('SELECT * FROM vouchers WHERE id=? AND user_id=?').get(req.params.id, req.user.id);
    if (!v) return res.status(404).json({ error: '凭证不存在' });
    const entries = db.prepare('SELECT * FROM voucher_entries WHERE voucher_id=? ORDER BY sort_order, id').all(req.params.id);
    res.json({ ...mapVoucher(v), entries });
  });

  // ── POST /api/vouchers ──
  app.post('/api/vouchers', requireAuth, (req, res) => {
    const { voucherDate, description, notes, entries } = req.body;
    if (!voucherDate || !description) return res.status(400).json({ error: '日期和摘要为必填项' });
    if (!entries || !entries.length) return res.status(400).json({ error: '至少需要一条分录' });
    let totalDebit = 0, totalCredit = 0;
    for (const e of entries) { totalDebit += parseFloat(e.debitAmount) || 0; totalCredit += parseFloat(e.creditAmount) || 0; }
    if (Math.abs(totalDebit - totalCredit) > 0.001) return res.status(400).json({ error: `借贷不平衡：借方 ¥${totalDebit.toFixed(2)} ≠ 贷方 ¥${totalCredit.toFixed(2)}` });
    const now = new Date(), year = now.getFullYear();
    const last = db.prepare('SELECT id FROM vouchers WHERE id LIKE ? ORDER BY id DESC LIMIT 1').get(`VCH-${year}-%`);
    let n = 1; if (last) { const p = parseInt(last.id.split('-')[2]); if (!isNaN(p)) n = p + 1; }
    const newId = `VCH-${year}-${String(n).padStart(4, '0')}`;
    db.exec('BEGIN');
    try {
      db.prepare('INSERT INTO vouchers (id,voucher_date,description,total_amount,status,notes,user_id) VALUES (?,?,?,?,?,?,?)').run(newId, voucherDate, description, totalDebit, 'draft', notes || '', req.user.id);
      const ins = db.prepare('INSERT INTO voucher_entries (voucher_id,account,debit_amount,credit_amount,summary,sort_order) VALUES (?,?,?,?,?,?)');
      entries.forEach((e, i) => ins.run(newId, e.account || '', parseFloat(e.debitAmount) || 0, parseFloat(e.creditAmount) || 0, e.summary || '', i));
      db.exec('COMMIT');
      logAudit(db, newId, 'create', null, 'draft', JSON.stringify({ description, totalAmount: totalDebit, entryCount: entries.length }), req.user.id);
    } catch (err) { try { db.exec('ROLLBACK'); } catch (_) {} return res.status(500).json({ error: '创建失败: ' + err.message }); }
    res.status(201).json(mapVoucher(db.prepare('SELECT * FROM vouchers WHERE id=?').get(newId)));
  });

  // ── PATCH /api/vouchers/:id/status ──
  app.patch('/api/vouchers/:id/status', requireAuth, (req, res) => {
    const { status } = req.body;
    if (!['draft', 'posted', 'cancelled'].includes(status)) return res.status(400).json({ error: '无效状态' });
    const old = db.prepare('SELECT * FROM vouchers WHERE id=? AND user_id=?').get(req.params.id, req.user.id);
    if (!old) return res.status(404).json({ error: '凭证不存在' });
    db.prepare("UPDATE vouchers SET status=?, updated_at=datetime('now','localtime') WHERE id=?").run(status, req.params.id);
    logAudit(db, req.params.id, 'status_change', old.status, status, null, req.user.id);
    res.json(mapVoucher(db.prepare('SELECT * FROM vouchers WHERE id=?').get(req.params.id)));
  });

  // ── PUT /api/vouchers/:id — 编辑凭证 ──
  app.put('/api/vouchers/:id', requireAuth, (req, res) => {
    const old = db.prepare('SELECT * FROM vouchers WHERE id=? AND user_id=?').get(req.params.id, req.user.id);
    if (!old) return res.status(404).json({ error: '凭证不存在' });
    if (old.status !== 'draft') return res.status(400).json({ error: '只能编辑草稿状态的凭证' });
    const { voucherDate, description, notes, entries } = req.body;
    if (!voucherDate || !description) return res.status(400).json({ error: '日期和摘要为必填项' });
    if (!entries || !entries.length) return res.status(400).json({ error: '至少需要一条分录' });
    let td = 0, tc = 0;
    for (const e of entries) { td += parseFloat(e.debitAmount) || 0; tc += parseFloat(e.creditAmount) || 0; }
    if (Math.abs(td - tc) > 0.001) return res.status(400).json({ error: `借贷不平衡：借方 ¥${td.toFixed(2)} ≠ 贷方 ¥${tc.toFixed(2)}` });
    db.exec('BEGIN');
    try {
      db.prepare('UPDATE vouchers SET voucher_date=?,description=?,total_amount=?,notes=?,updated_at=datetime("now","localtime") WHERE id=?').run(voucherDate, description, td, notes || '', req.params.id);
      db.prepare('DELETE FROM voucher_entries WHERE voucher_id=?').run(req.params.id);
      const ins = db.prepare('INSERT INTO voucher_entries (voucher_id,account,debit_amount,credit_amount,summary,sort_order) VALUES (?,?,?,?,?,?)');
      entries.forEach((e, i) => ins.run(req.params.id, e.account || '', parseFloat(e.debitAmount) || 0, parseFloat(e.creditAmount) || 0, e.summary || '', i));
      db.exec('COMMIT');
      logAudit(db, req.params.id, 'update', old.status, old.status, JSON.stringify({ description, totalAmount: td }), req.user.id);
    } catch (err) { try { db.exec('ROLLBACK'); } catch (_) {} return res.status(500).json({ error: '编辑失败: ' + err.message }); }
    res.json(mapVoucher(db.prepare('SELECT * FROM vouchers WHERE id=?').get(req.params.id)));
  });

  // ── DELETE /api/vouchers/:id — 删除凭证 ──
  app.delete('/api/vouchers/:id', requireAuth, (req, res) => {
    const old = db.prepare('SELECT * FROM vouchers WHERE id=? AND user_id=?').get(req.params.id, req.user.id);
    if (!old) return res.status(404).json({ error: '凭证不存在' });
    if (old.status === 'posted') return res.status(400).json({ error: '已过账凭证不可删除' });
    db.exec('BEGIN');
    try {
      db.prepare('DELETE FROM voucher_entries WHERE voucher_id=?').run(req.params.id);
      db.prepare('DELETE FROM vouchers WHERE id=?').run(req.params.id);
      db.exec('COMMIT');
      logAudit(db, req.params.id, 'delete', old.status, null, JSON.stringify({ description: old.description }), req.user.id);
    } catch (err) { try { db.exec('ROLLBACK'); } catch (_) {} return res.status(500).json({ error: '删除失败: ' + err.message }); }
    res.json({ ok: true });
  });
}

module.exports = { register };
