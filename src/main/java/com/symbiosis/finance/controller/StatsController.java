package com.symbiosis.finance.controller;

import com.symbiosis.finance.entity.Category;
import com.symbiosis.finance.mapper.CategoryMapper;
import com.symbiosis.finance.mapper.InvoiceMapper;
import com.symbiosis.finance.mapper.VoucherMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.symbiosis.finance.entity.Invoice;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.*;

@RestController
@RequestMapping("/api")
public class StatsController {

    private final InvoiceMapper invoiceMapper;
    private final CategoryMapper categoryMapper;
    private final VoucherMapper voucherMapper;

    public StatsController(InvoiceMapper invoiceMapper, CategoryMapper categoryMapper, VoucherMapper voucherMapper) {
        this.invoiceMapper = invoiceMapper;
        this.categoryMapper = categoryMapper;
        this.voucherMapper = voucherMapper;
    }

    // ── GET /api/stats ──
    @GetMapping("/stats")
    public Map<String, Object> stats(@AuthenticationPrincipal String userId) {
        UUID uid = UUID.fromString(userId);
        String thisMonth = String.format("%tY-%<tm", LocalDate.now());

        LambdaQueryWrapper<Invoice> userQw = new LambdaQueryWrapper<Invoice>().eq(Invoice::getUserId, uid);
        long total = invoiceMapper.selectCount(userQw);
        double totalAmount = invoiceMapper.sumTotalAmount(uid);

        LambdaQueryWrapper<Invoice> monthQw = new LambdaQueryWrapper<Invoice>()
                .eq(Invoice::getUserId, uid)
                .apply("to_char(invoice_date, 'YYYY-MM') = {0}", thisMonth);
        long monthCount = invoiceMapper.selectCount(monthQw);
        double monthAmount = invoiceMapper.sumMonthAmount(uid.toString(), thisMonth);

        LambdaQueryWrapper<Invoice> pendingQw = new LambdaQueryWrapper<Invoice>()
                .eq(Invoice::getUserId, uid).eq(Invoice::getStatus, "pending");
        long pendingCount = invoiceMapper.selectCount(pendingQw);
        double pendingAmount = invoiceMapper.sumPendingAmount(uid);

        LambdaQueryWrapper<Invoice> reimbursedQw = new LambdaQueryWrapper<Invoice>()
                .eq(Invoice::getUserId, uid).eq(Invoice::getStatus, "reimbursed");
        long reimbursed = invoiceMapper.selectCount(reimbursedQw);

        LambdaQueryWrapper<Invoice> rejectedQw = new LambdaQueryWrapper<Invoice>()
                .eq(Invoice::getUserId, uid).eq(Invoice::getStatus, "rejected");
        long rejected = invoiceMapper.selectCount(rejectedQw);

        Map<String, Object> voucherSummary = voucherSummary(uid);

        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("totalInvoices", total);
        resp.put("totalAmount", Math.round(totalAmount * 100.0) / 100.0);
        resp.put("thisMonth", monthCount);
        resp.put("thisMonthAmount", Math.round(monthAmount * 100.0) / 100.0);
        resp.put("pendingReimburse", pendingCount);
        resp.put("pendingAmount", Math.round(pendingAmount * 100.0) / 100.0);
        resp.put("reimbursed", reimbursed);
        resp.put("rejected", rejected);
        resp.put("vouchers", voucherSummary);
        return resp;
    }

    // ── GET /api/reports ──
    @GetMapping("/reports")
    public Map<String, Object> reports(@AuthenticationPrincipal String userId) {
        UUID uid = UUID.fromString(userId);

        List<Map<String, Object>> monthly = invoiceMapper.monthlyReport(uid);
        List<Map<String, Object>> categories = invoiceMapper.categoryReport(uid);
        List<Map<String, Object>> sr = invoiceMapper.statusReport(uid);
        Map<String, Object> voucherSummary = voucherSummary(uid);

        Map<String, Long> statusCounts = new LinkedHashMap<>();
        statusCounts.put("pending", 0L);
        statusCounts.put("reimbursed", 0L);
        statusCounts.put("rejected", 0L);
        for (Map<String, Object> row : sr) {
            statusCounts.put((String) row.get("status"), ((Number) row.get("count")).longValue());
        }

        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("monthly", monthly);
        resp.put("categories", categories);
        resp.put("statusCounts", statusCounts);
        resp.put("voucherSummary", voucherSummary);
        return resp;
    }

    private Map<String, Object> voucherSummary(UUID uid) {
        Map<String, Long> counts = new LinkedHashMap<>();
        counts.put("draft", 0L);
        counts.put("posted", 0L);
        counts.put("cancelled", 0L);

        Map<String, Double> amounts = new LinkedHashMap<>();
        amounts.put("draft", 0.0);
        amounts.put("posted", 0.0);
        amounts.put("cancelled", 0.0);

        List<Map<String, Object>> rows = voucherMapper.statusReport(uid);
        for (Map<String, Object> row : rows) {
            String status = String.valueOf(row.get("status"));
            counts.put(status, ((Number) row.get("count")).longValue());
            amounts.put(status, number(row, "totalAmount", "totalamount", "total_amount").doubleValue());
        }

        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("total", voucherMapper.countActive(uid));
        resp.put("totalAmount", round(voucherMapper.sumActiveAmount(uid)));
        resp.put("postedAmount", round(voucherMapper.sumPostedAmount(uid)));
        resp.put("statusCounts", counts);
        resp.put("statusAmounts", amounts);
        return resp;
    }

    private static double round(Double value) {
        double n = value != null ? value : 0.0;
        return Math.round(n * 100.0) / 100.0;
    }

    private static Number number(Map<String, Object> row, String... keys) {
        for (String key : keys) {
            Object value = row.get(key);
            if (value instanceof Number n) return n;
        }
        return 0;
    }

    // ── GET /api/categories ──
    @GetMapping("/categories")
    public List<Category> categories() {
        return categoryMapper.selectList(
                new LambdaQueryWrapper<Category>().orderByAsc(Category::getSortOrder));
    }
}
