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

@RestController
@RequestMapping("/api/financial-reports")
public class FinancialReportController {

    private static final String REPORT_NOTICE =
            "本系统三大报表基于已过账凭证自动计算，属于简化会计口径，适合经营分析和内部核对；正式申报、审计或复杂会计处理请人工复核。";

    private final ReportService reportService;

    public FinancialReportController(ReportService reportService) {
        this.reportService = reportService;
    }

    @GetMapping("/meta")
    public Map<String, Object> meta() {
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("notice", REPORT_NOTICE);
        resp.put("basis", "仅统计 status='posted' 且未删除的凭证；发票统计页面仍按发票列表全量数据统计。");
        resp.put("cashFlow", "现金流量表为简化估算，精确拆分经营/投资/筹资现金流需要辅助核算。");
        return resp;
    }

    @GetMapping("/balance-sheet")
    public Map<String, Object> balanceSheet(@AuthenticationPrincipal String userId,
                                            @RequestParam(required = false) String endDate) {
        UUID uid = UUID.fromString(userId);
        String end = (endDate == null || endDate.isBlank()) ? LocalDate.now().toString() : endDate;
        Map<String, List<Map<String, Object>>> body = reportService.balanceSheet(uid, end);

        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("period", end);
        resp.put("notice", REPORT_NOTICE);
        resp.put("assets", body.get("assets"));
        resp.put("liabilities", body.get("liabilities"));
        return resp;
    }

    @GetMapping("/income-statement")
    public Map<String, Object> incomeStatement(@AuthenticationPrincipal String userId,
                                               @RequestParam(required = false) String startDate,
                                               @RequestParam(required = false) String endDate) {
        UUID uid = UUID.fromString(userId);
        int year = LocalDate.now().getYear();
        String start = (startDate == null || startDate.isBlank()) ? year + "-01-01" : startDate;
        String end = (endDate == null || endDate.isBlank()) ? LocalDate.now().toString() : endDate;
        List<Map<String, Object>> rows = reportService.incomeStatement(uid, start, end);

        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("period", start + "~" + end);
        resp.put("notice", REPORT_NOTICE);
        resp.put("rows", rows);
        return resp;
    }

    @GetMapping("/cash-flow")
    public Map<String, Object> cashFlow(@AuthenticationPrincipal String userId,
                                        @RequestParam(required = false) String startDate,
                                        @RequestParam(required = false) String endDate) {
        UUID uid = UUID.fromString(userId);
        int year = LocalDate.now().getYear();
        String start = (startDate == null || startDate.isBlank()) ? year + "-01-01" : startDate;
        String end = (endDate == null || endDate.isBlank()) ? LocalDate.now().toString() : endDate;
        List<Map<String, Object>> rows = reportService.cashFlow(uid, start, end);

        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("period", start + "~" + end);
        resp.put("notice", REPORT_NOTICE);
        resp.put("rows", rows);
        return resp;
    }
}
