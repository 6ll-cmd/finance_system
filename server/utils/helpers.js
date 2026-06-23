/**
 * 发票管家 — 工具函数
 */

function mapInvoice(r) {
  return {
    id: r.id, date: r.invoice_date, seller: r.seller, buyer: r.buyer,
    type: r.type, category: r.category_id, categoryName: r.category_name,
    amount: r.amount, taxRate: r.tax_rate, taxAmount: r.tax_amount,
    totalAmount: r.total_amount, status: r.status, notes: r.notes,
    invoiceCode: r.invoice_code, invoiceNumber: r.invoice_number,
    image: r.image_path
  };
}

function mapVoucher(r) {
  return {
    id: r.id, voucherDate: r.voucher_date, description: r.description,
    totalAmount: r.total_amount, status: r.status, notes: r.notes,
    createdAt: r.created_at, updatedAt: r.updated_at
  };
}

/**
 * 记录审计日志
 * @param {object} db - SQLite DatabaseSync 实例
 * @param {string} entityId - 关联实体 ID
 * @param {string} action - 操作类型 (create|status_change|delete)
 * @param {string|null} oldStatus - 旧状态
 * @param {string|null} newStatus - 新状态
 * @param {string|null} detail - JSON 格式详情
 * @param {string} userId - 操作用户 ID
 */
function logAudit(db, entityId, action, oldStatus, newStatus, detail, userId) {
  db.prepare(
    'INSERT INTO audit_log (entity_id, action, old_status, new_status, detail, user_id) VALUES (?,?,?,?,?,?)'
  ).run(entityId, action, oldStatus || null, newStatus || null, detail || null, userId || '');
}

module.exports = { mapInvoice, mapVoucher, logAudit };
