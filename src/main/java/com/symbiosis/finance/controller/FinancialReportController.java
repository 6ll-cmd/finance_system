package com.symbiosis.finance.controller;

import com.symbiosis.finance.service.ReportService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 三大财务报表 REST 接口。
 * 所有端点通过 @AuthenticationPrincipal String userId 获取当前用户，
 * 再转成 UUID 做用户隔离取数。
 */
@RestController
@RequestMapping("/api/financial-reports")
public class FinancialReportController {

    private final ReportService reportService;

    public FinancialReportController(ReportService reportService) {
        this.reportService = reportService;
    }

    // ── GET /api/financial-reports/balance-sheet ──
    @GetMapping("/balance-sheet")
    public Map<String, Object> balanceSheet(@AuthenticationPrincipal String userId,
                                            @RequestParam(required = false) String endDate) {
        UUID uid = UUID.fromString(userId);
        String end = (endDate == null || endDate.isBlank())
                ? LocalDate.now().toString() : endDate;

        Map<String, List<Map<String, Object>>> body = reportService.balanceSheet(uid, end);

        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("period", end);
        resp.put("assets", body.get("assets"));
        resp.put("liabilities", body.get("liabilities"));
        return resp;
    }

    // ── GET /api/financial-reports/income-statement ──
    @GetMapping("/income-statement")
    public Map<String, Object> incomeStatement(@AuthenticationPrincipal String userId,
                                               @RequestParam(required = false) String startDate,
                                               @RequestParam(required = false) String endDate) {
        UUID uid = UUID.fromString(userId);
        int year = LocalDate.now().getYear();
        String start = (startDate == null || startDate.isBlank())
                ? year + "-01-01" : startDate;
        String end = (endDate == null || endDate.isBlank())
                ? LocalDate.now().toString() : endDate;

        List<Map<String, Object>> rows = reportService.incomeStatement(uid, start, end);

        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("period", start + "~" + end);
        resp.put("rows", rows);
        return resp;
    }

    // ── GET /api/financial-reports/cash-flow ──
    @GetMapping("/cash-flow")
    public Map<String, Object> cashFlow(@AuthenticationPrincipal String userId,
                                        @RequestParam(required = false) String startDate,
                                        @RequestParam(required = false) String endDate) {
        UUID uid = UUID.fromString(userId);
        int year = LocalDate.now().getYear();
        String start = (startDate == null || startDate.isBlank())
                ? year + "-01-01" : startDate;
        String end = (endDate == null || endDate.isBlank())
                ? LocalDate.now().toString() : endDate;

        List<Map<String, Object>> rows = reportService.cashFlow(uid, start, end);

        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("period", start + "~" + end);
        resp.put("rows", rows);
        return resp;
    }
}
