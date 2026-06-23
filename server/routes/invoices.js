/**
 * 发票管家 — 发票路由
 */
const crypto = require('crypto');
const fs = require('fs');
const path = require('path');
const db = require('../database');
const { requireAuth } = require('../middleware/auth');
const { mapInvoice, logAudit } = require('../utils/helpers');
const { ROOT } = require('../config');

function register(app) {
  // ── GET /api/invoices ──
  app.get('/api/invoices', requireAuth, (req, res) => {
    const { search, status, category, sort, page = 1, limit = 15 } = req.query;
    let where = 'WHERE i.user_id = ?';
    const params = [req.user.id];
    if (search) { where += ' AND (i.id LIKE ? OR i.seller LIKE ? OR i.notes LIKE ? OR i.buyer LIKE ?)'; const q = `%${search}%`; params.push(q, q, q, q); }
    if (status && status !== 'all') { where += ' AND i.status = ?'; params.push(status); }
    if (category && category !== 'all') { where += ' AND i.category_id = ?'; params.push(category); }
    let orderBy = 'ORDER BY i.created_at DESC';
    if (sort === 'date-asc') orderBy = 'ORDER BY i.invoice_date ASC';
    else if (sort === 'date-desc') orderBy = 'ORDER BY i.invoice_date DESC';
    else if (sort === 'amount-desc') orderBy = 'ORDER BY i.total_amount DESC';
    else if (sort === 'amount-asc') orderBy = 'ORDER BY i.total_amount ASC';

    const { total } = db.prepare(`SELECT COUNT(*) AS total FROM invoices i ${where}`).get(...params);
    const p = Math.max(1, parseInt(page)), lim = Math.max(1, Math.min(100, parseInt(limit)));
    const rows = db.prepare(`SELECT i.*, c.name AS category_name FROM invoices i LEFT JOIN categories c ON i.category_id=c.id ${where} ${orderBy} LIMIT ? OFFSET ?`).all(...params, lim, (p - 1) * lim);
    res.json({ total, page: p, limit: lim, totalPages: Math.ceil(total / lim), data: rows.map(mapInvoice) });
  });

  // ── GET /api/invoices/:id ──
  app.get('/api/invoices/:id', requireAuth, (req, res) => {
    const row = db.prepare('SELECT i.*, c.name AS category_name FROM invoices i LEFT JOIN categories c ON i.category_id=c.id WHERE i.id=? AND i.user_id=?').get(req.params.id, req.user.id);
    if (!row) return res.status(404).json({ error: '发票不存在' });
    res.json(mapInvoice(row));
  });

  // ── POST /api/invoices ──
  app.post('/api/invoices', requireAuth, (req, res) => {
    const { seller, buyer, type, category, amount, taxRate, taxAmount, totalAmount, notes, invoiceCode, invoiceNumber, date } = req.body;
    if (!seller || amount == null) return res.status(400).json({ error: '销售方和金额为必填项' });
    const now = new Date(), year = now.getFullYear();
    const last = db.prepare('SELECT id FROM invoices WHERE id LIKE ? ORDER BY id DESC LIMIT 1').get(`INV-${year}-%`);
    let n = 1; if (last) { const p = parseInt(last.id.split('-')[2]); if (!isNaN(p)) n = p + 1; }
    const newId = `INV-${year}-${String(n).padStart(4, '0')}`;
    db.prepare('INSERT INTO invoices (id,invoice_date,seller,buyer,type,category_id,amount,tax_rate,tax_amount,total_amount,status,notes,invoice_code,invoice_number,user_id) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)').run(newId, date || now.toISOString().split('T')[0], seller, buyer || '广州共生纪元云科技有限公司', type || '增值税电子普通发票', category || 'other', parseFloat(amount) || 0, parseFloat(taxRate) || 0, parseFloat(taxAmount) || 0, parseFloat(totalAmount) || parseFloat(amount) || 0, 'pending', notes || '', invoiceCode || '', invoiceNumber || '', req.user.id);
    logAudit(db, newId, 'create', null, 'pending', JSON.stringify({ seller, amount }), req.user.id);
    const created = db.prepare('SELECT * FROM invoices WHERE id=?').get(newId);
    res.status(201).json(mapInvoice(created));
  });

  // ── PATCH /api/invoices/:id/status ──
  app.patch('/api/invoices/:id/status', requireAuth, (req, res) => {
    const { status } = req.body;
    if (!['pending', 'reimbursed', 'rejected'].includes(status)) return res.status(400).json({ error: '无效状态' });
    const old = db.prepare('SELECT * FROM invoices WHERE id=? AND user_id=?').get(req.params.id, req.user.id);
    if (!old) return res.status(404).json({ error: '发票不存在' });
    db.prepare("UPDATE invoices SET status=?, updated_at=datetime('now','localtime') WHERE id=?").run(status, req.params.id);
    logAudit(db, req.params.id, 'status_change', old.status, status, null, req.user.id);
    res.json(mapInvoice(db.prepare('SELECT * FROM invoices WHERE id=?').get(req.params.id)));
  });

  // ── PATCH /api/invoices/batch-status ──
  app.patch('/api/invoices/batch-status', requireAuth, (req, res) => {
    const { ids, status } = req.body;
    if (!Array.isArray(ids) || !['pending', 'reimbursed', 'rejected'].includes(status)) return res.status(400).json({ error: '参数无效' });
    const stmt = db.prepare("UPDATE invoices SET status=?, updated_at=datetime('now','localtime') WHERE id=? AND user_id=?");
    db.exec('BEGIN');
    try {
      for (const id of ids) {
        const inv = db.prepare('SELECT status FROM invoices WHERE id=? AND user_id=?').get(id, req.user.id);
        if (inv) { stmt.run(status, id, req.user.id); logAudit(db, id, 'status_change', inv.status, status, null, req.user.id); }
      }
      db.exec('COMMIT');
    } catch (e) { try { db.exec('ROLLBACK'); } catch (_) {} throw e; }
    res.json({ updated: ids.length });
  });

  // ── GET /api/invoices/export — CSV 导出 ──
  app.get('/api/invoices/export', requireAuth, (req, res) => {
    const rows = db.prepare(`SELECT i.*, c.name AS category_name FROM invoices i
      LEFT JOIN categories c ON i.category_id=c.id WHERE i.user_id=? ORDER BY i.created_at DESC`).all(req.user.id);
    const header = '发票号,日期,销售方,购买方,类型,类别,不含税金额,税率%,税额,含税总额,状态,备注\n';
    const csv = header + rows.map(r => [
      r.id, r.invoice_date, `"${r.seller}"`, `"${r.buyer}"`, r.type,
      r.category_name, r.amount, r.tax_rate, r.tax_amount, r.total_amount,
      r.status === 'pending' ? '待报销' : r.status === 'reimbursed' ? '已报销' : '已退回',
      `"${(r.notes || '').replace(/"/g, '""')}"`
    ].join(',')).join('\n');
    res.setHeader('Content-Type', 'text/csv; charset=utf-8');
    res.setHeader('Content-Disposition', 'attachment; filename=invoices.csv');
    res.send('﻿' + csv);
  });

  // ── POST /api/upload — 图片上传 ──
  app.post('/api/upload', requireAuth, (req, res) => {
    const { image } = req.body;
    if (!image) return res.status(400).json({ error: '请提供图片数据' });
    const base64 = image.replace(/^data:image\/\w+;base64,/, '');
    const ext = image.includes('png') ? 'png' : 'jpg';
    const filename = `${Date.now()}-${crypto.randomBytes(4).toString('hex')}.${ext}`;
    const dir = path.join(ROOT, 'data', 'uploads');
    try { fs.mkdirSync(dir, { recursive: true }); } catch (_) {}
    fs.writeFileSync(path.join(dir, filename), Buffer.from(base64, 'base64'));
    res.json({ path: `data/uploads/${filename}` });
  });
}

module.exports = { register };
