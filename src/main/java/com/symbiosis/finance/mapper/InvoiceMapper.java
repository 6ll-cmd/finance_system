package com.symbiosis.finance.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.symbiosis.finance.entity.Invoice;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Mapper
public interface InvoiceMapper extends BaseMapper<Invoice> {

    @Select("SELECT id FROM invoices WHERE id LIKE 'INV-' || #{year} || '-%' ORDER BY id DESC LIMIT 1")
    String findLastIdByYear(int year);

    // ── 统计查询 ──

    @Select("SELECT COALESCE(SUM(total_amount),0) FROM invoices WHERE user_id=#{uid} AND deleted=0")
    Double sumTotalAmount(UUID uid);

    @Select("SELECT COALESCE(SUM(total_amount),0) FROM invoices WHERE user_id=#{uid} AND deleted=0 AND to_char(invoice_date,'YYYY-MM')=#{month}")
    Double sumMonthAmount(String uid, String month);

    @Select("SELECT COALESCE(SUM(total_amount),0) FROM invoices WHERE user_id=#{uid} AND deleted=0 AND status='pending'")
    Double sumPendingAmount(UUID uid);

    // ── 报表查询 ──

    @Select("SELECT to_char(invoice_date,'YYYY-MM') AS month, COUNT(*) AS count, " +
            "ROUND(COALESCE(SUM(amount),0),2) AS amount, " +
            "ROUND(COALESCE(SUM(tax_amount),0),2) AS \"taxAmount\", " +
            "ROUND(COALESCE(SUM(total_amount),0),2) AS \"totalAmount\" " +
            "FROM invoices WHERE user_id=#{uid} AND deleted=0 GROUP BY month ORDER BY month")
    List<Map<String, Object>> monthlyReport(UUID uid);

    @Select("SELECT c.id AS category, c.name, COUNT(i.id) AS count, " +
            "ROUND(COALESCE(SUM(i.amount),0),2) AS amount, " +
            "ROUND(COALESCE(SUM(i.total_amount),0),2) AS \"totalAmount\" " +
            "FROM categories c LEFT JOIN invoices i ON i.category_id=c.id AND i.user_id=#{uid} AND i.deleted=0 " +
            "GROUP BY c.id, c.name ORDER BY c.sort_order")
    List<Map<String, Object>> categoryReport(UUID uid);

    @Select("SELECT status, COUNT(*) AS count FROM invoices WHERE user_id=#{uid} AND deleted=0 GROUP BY status")
    List<Map<String, Object>> statusReport(UUID uid);
}
