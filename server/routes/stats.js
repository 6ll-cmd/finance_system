/**
 * 发票管家 — 统计/报表/类别路由
 */
const db = require('../database');
const { requireAuth } = require('../middleware/auth');

function register(app) {
  // ── GET /api/stats ──
  app.get('/api/stats', requireAuth, (req, res) => {
    const uid = req.user.id;
    const now = new Date(), m = `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}`;

    const total = db.prepare('SELECT COUNT(*) AS c, COALESCE(SUM(total_amount),0) AS a FROM invoices WHERE user_id=?').get(uid);
    const month = db.prepare("SELECT COUNT(*) AS c, COALESCE(SUM(total_amount),0) AS a FROM invoices WHERE user_id=? AND invoice_date LIKE ?").get(uid, `${m}%`);
    const p = db.prepare("SELECT COUNT(*) AS c, COALESCE(SUM(total_amount),0) AS a FROM invoices WHERE user_id=? AND status='pending'").get(uid);
    const re = db.prepare("SELECT COUNT(*) AS c FROM invoices WHERE user_id=? AND status='reimbursed'").get(uid);
    const rj = db.prepare("SELECT COUNT(*) AS c FROM invoices WHERE user_id=? AND status='rejected'").get(uid);

    res.json({
      totalInvoices: total.c, totalAmount: Math.round(total.a * 100) / 100,
      thisMonth: month.c, thisMonthAmount: Math.round(month.a * 100) / 100,
      pendingReimburse: p.c, pendingAmount: Math.round(p.a * 100) / 100,
      reimbursed: re.c, rejected: rj.c
    });
  });

  // ── GET /api/reports ──
  app.get('/api/reports', requireAuth, (req, res) => {
    const uid = req.user.id;
    const monthly = db.prepare("SELECT substr(invoice_date,1,7) AS month, COUNT(*) AS count, ROUND(SUM(amount),2) AS amount, ROUND(SUM(tax_amount),2) AS taxAmount, ROUND(SUM(total_amount),2) AS totalAmount FROM invoices WHERE user_id=? GROUP BY month ORDER BY month").all(uid);
    const categories = db.prepare("SELECT c.id AS category, c.name, COUNT(i.id) AS count, ROUND(COALESCE(SUM(i.amount),0),2) AS amount, ROUND(COALESCE(SUM(i.total_amount),0),2) AS totalAmount FROM categories c LEFT JOIN invoices i ON i.category_id=c.id AND i.user_id=? GROUP BY c.id ORDER BY c.sort_order").all(uid);
    const sr = db.prepare('SELECT status, COUNT(*) AS count FROM invoices WHERE user_id=? GROUP BY status').all(uid);
    const statusCounts = { pending: 0, reimbursed: 0, rejected: 0 };
    sr.forEach(r => { statusCounts[r.status] = r.count; });
    res.json({ monthly, categories, statusCounts });
  });

  // ── GET /api/categories ──
  app.get('/api/categories', (_req, res) => {
    res.json(db.prepare('SELECT * FROM categories ORDER BY sort_order').all());
  });
}

module.exports = { register };
