package com.symbiosis.finance.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.symbiosis.finance.config.AiConfig;
import com.symbiosis.finance.entity.Invoice;
import com.symbiosis.finance.mapper.InvoiceMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.Year;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class AiService {

    private final AiConfig config;
    private final InvoiceMapper invoiceMapper;
    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private static final Pattern JSON_PATTERN = Pattern.compile("\\{[\\s\\S]*\"action\"[\\s\\S]*\\}");

    public AiService(AiConfig config, InvoiceMapper invoiceMapper) {
        this.config = config;
        this.invoiceMapper = invoiceMapper;
        this.restClient = RestClient.create();
        this.objectMapper = new ObjectMapper();
    }

    // ── OCR 识别 ──

    @SuppressWarnings("unchecked")
    public Map<String, Object> recognize(String base64Image) {
        if (!config.isConfigured())
            throw new IllegalStateException("AI 接口未配置，请先在系统设置中保存 API Key");

        String content;
        if ("anthropic".equals(config.getProvider())) {
            content = callClaudeOcr(base64Image);
        } else {
            content = callOpenAiOcr(base64Image);
        }

        // 解析 JSON 响应
        try {
            String json = content.replaceAll("```json\\s*|\\s*```", "").trim();
            return objectMapper.readValue(json, Map.class);
        } catch (JsonProcessingException e) {
            Matcher m = Pattern.compile("\\{[\\s\\S]*\\}").matcher(content);
            if (m.find()) {
                try { return objectMapper.readValue(m.group(), Map.class); } catch (Exception ignored) {}
            }
            return Map.of();
        }
    }

    private String callOpenAiOcr(String base64Image) {
        Map<String, Object> body = Map.of(
            "model", config.getModel(),
            "messages", List.of(Map.of("role", "user", "content", List.of(
                Map.of("type", "text", "text", AiConstants.OCR_PROMPT),
                Map.of("type", "image_url", "image_url", Map.of("url", "data:image/png;base64," + base64Image))
            ))),
            "max_tokens", config.getMaxTokens(),
            "temperature", 0
        );
        return callOpenAi(body);
    }

    // ── AI 对话 ──

    @SuppressWarnings("unchecked")
    public Map<String, Object> chat(UUID userId, List<Map<String, String>> messages) {
        if (!config.isConfigured())
            throw new IllegalStateException("AI 未配置，请先在系统设置中保存 API Key");

        String contextMsg = buildContext(userId);

        List<Map<String, Object>> fullMessages = new ArrayList<>();
        fullMessages.add(Map.of("role", "system", "content", AiConstants.AI_SYSTEM_PROMPT));
        fullMessages.add(Map.of("role", "system", "content", contextMsg));

        int start = Math.max(0, messages.size() - 10);
        for (int i = start; i < messages.size(); i++) {
            fullMessages.add(Map.of("role", (Object) messages.get(i).get("role"),
                                    "content", messages.get(i).get("content")));
        }

        String result;
        if ("anthropic".equals(config.getProvider())) {
            result = callClaudeChat(fullMessages);
        } else {
            result = callOpenAiChat(fullMessages);
        }

        // 解析 action JSON
        return parseChatResult(userId, result);
    }

    private String buildContext(UUID userId) {
        try {
            LambdaQueryWrapper<Invoice> qw = new LambdaQueryWrapper<Invoice>().eq(Invoice::getUserId, userId);
            long total = invoiceMapper.selectCount(qw);
            double totalAmount = invoiceMapper.sumTotalAmount(userId);
            String thisMonth = String.format("%tY-%<tm", LocalDate.now());
            long monthCount = invoiceMapper.selectCount(
                    new LambdaQueryWrapper<Invoice>().eq(Invoice::getUserId, userId)
                            .likeRight(Invoice::getInvoiceDate, thisMonth));
            double monthAmount = invoiceMapper.sumMonthAmount(userId.toString(), thisMonth);
            long pending = invoiceMapper.selectCount(
                    new LambdaQueryWrapper<Invoice>().eq(Invoice::getUserId, userId).eq(Invoice::getStatus, "pending"));
            double pendingAmount = invoiceMapper.sumPendingAmount(userId);

            List<Invoice> recent = invoiceMapper.selectList(
                    new LambdaQueryWrapper<Invoice>().eq(Invoice::getUserId, userId)
                            .orderByDesc(Invoice::getCreatedAt).last("LIMIT 5"));

            return String.format(
                "当前用户数据：发票总数 %d 条，含税总额 ¥%.2f，本月 %d 条共 ¥%.2f，待报销 %d 条。最近发票：%s。当前日期：%s。",
                total, totalAmount, monthCount, monthAmount, pending,
                recent.stream().map(i -> String.format("%s ¥%s [%s]", i.getSeller(), i.getTotalAmount(), i.getStatus())).toList(),
                LocalDate.now()
            );
        } catch (Exception e) {
            return "当前日期：" + LocalDate.now() + "。（统计数据暂时不可用）";
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseChatResult(UUID userId, String result) {
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("reply", result);
        resp.put("action", null);
        resp.put("data", null);

        Matcher m = JSON_PATTERN.matcher(result);
        if (!m.find()) return resp;

        try {
            Map<String, Object> parsed = objectMapper.readValue(m.group(), Map.class);
            if (parsed.get("action") == null) return resp;

            String action = (String) parsed.get("action");
            Map<String, Object> data = (Map<String, Object>) parsed.getOrDefault("data", Map.of());
            String reply = (String) parsed.getOrDefault("reply", result.replace(m.group(), "").trim());

            resp.put("action", action);
            resp.put("data", data);

            if ("create_invoice".equals(action) && data.get("seller") != null &&
                (data.get("amount") != null || data.get("total_amount") != null)) {
                // AI 自然语言录入发票
                BigDecimal amt = toBigDecimal(data.get("amount") != null ? data.get("amount") : data.get("total_amount"));
                BigDecimal rate = toBigDecimal(data.getOrDefault("taxRate", data.getOrDefault("tax_rate", 0)));
                BigDecimal taxAmt = amt.multiply(rate).divide(new BigDecimal("100"), 2, java.math.RoundingMode.HALF_UP);
                BigDecimal total = data.get("totalAmount") != null || data.get("total_amount") != null
                        ? toBigDecimal(data.get("totalAmount") != null ? data.get("totalAmount") : data.get("total_amount"))
                        : amt.add(taxAmt);

                int year = Year.now().getValue();
                String lastId = invoiceMapper.findLastIdByYear(year);
                int next = 1;
                if (lastId != null) {
                    try { next = Integer.parseInt(lastId.split("-")[2]) + 1; } catch (Exception ignored) {}
                }
                String newId = String.format("INV-%d-%04d", year, next);

                Invoice inv = new Invoice();
                inv.setId(newId);
                inv.setInvoiceDate(parseDate(data));
                inv.setSeller((String) data.get("seller"));
                inv.setBuyer((String) data.getOrDefault("buyer", "广州共生纪元云科技有限公司"));
                inv.setType("增值税电子普通发票");
                inv.setCategoryId("other");
                inv.setAmount(amt);
                inv.setTaxRate(rate);
                inv.setTaxAmount(taxAmt);
                inv.setTotalAmount(total);
                inv.setStatus("pending");
                inv.setNotes((String) data.getOrDefault("notes", data.getOrDefault("remark", "")));
                inv.setUserId(userId);
                inv.setCreatedAt(OffsetDateTime.now());
                inv.setUpdatedAt(OffsetDateTime.now());
                invoiceMapper.insert(inv);

                reply = String.format("✅ 已录入发票 %s：%s，¥%s", newId, data.get("seller"), amt);
            }

            if ("get_stats".equals(action)) {
                try {
                    String month = String.format("%tY-%<tm", LocalDate.now());
                    long mc = invoiceMapper.selectCount(
                            new LambdaQueryWrapper<Invoice>().eq(Invoice::getUserId, userId)
                                    .likeRight(Invoice::getInvoiceDate, month));
                    double ma = invoiceMapper.sumMonthAmount(userId.toString(), month);
                    long pc = invoiceMapper.selectCount(
                            new LambdaQueryWrapper<Invoice>().eq(Invoice::getUserId, userId).eq(Invoice::getStatus, "pending"));
                    double pa = invoiceMapper.sumPendingAmount(userId);
                    reply = String.format("📊 本月统计：%d 条发票，共 ¥%.2f。待报销 %d 条，累计 ¥%.2f。", mc, ma, pc, pa);
                } catch (Exception e) {
                    reply = "📊 统计信息暂时不可用";
                }
            }

            resp.put("reply", reply);
        } catch (Exception e) {
            // JSON 解析失败，返回纯文本回复
        }
        return resp;
    }

    // ── 连接测试 ──

    public Map<String, Object> testConnection(String baseUrl, String apiKey, String model) {
        String lastError = "";
        String testModel = model != null && !model.isBlank() ? model : config.getModel();
        String effectiveUrl = baseUrl != null && !baseUrl.isBlank() ? baseUrl : config.getBaseUrl();
        String effectiveKey = apiKey != null && !apiKey.isBlank() ? apiKey : config.getApiKey();
        if (effectiveKey == null || effectiveKey.isBlank()) {
            return Map.of("ok", false, "error", "请先填写或保存 API Key");
        }

        // 尝试 1: OpenAI 格式
        try {
            Map<String, Object> body = Map.of("model", testModel, "messages",
                    List.of(Map.of("role", "user", "content", "hi")), "max_tokens", 10);
            Map<String, Object> result = restClient.post()
                    .uri(effectiveUrl.replaceAll("/$", "") + "/chat/completions")
                    .header("Authorization", "Bearer " + effectiveKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(Map.class);
            if (result != null && result.get("choices") != null) {
                List<Map<String, Object>> choices = (List<Map<String, Object>>) result.get("choices");
                return Map.of("ok", true, "reply", ((Map<String, Object>) choices.get(0).get("message")).get("content"),
                        "protocol", "openai");
            }
        } catch (Exception e) { lastError = e.getMessage(); }

        // 尝试 2: Anthropic 格式
        try {
            Map<String, Object> body = Map.of("model", testModel, "max_tokens", 10,
                    "messages", List.of(Map.of("role", "user", "content", "hi")));
            Map<String, Object> result = restClient.post()
                    .uri("https://api.anthropic.com/v1/messages")
                    .header("x-api-key", effectiveKey)
                    .header("anthropic-version", "2023-06-01")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(Map.class);
            if (result != null && result.get("content") != null) {
                List<Map<String, Object>> content = (List<Map<String, Object>>) result.get("content");
                return Map.of("ok", true, "reply", content.get(0).get("text"), "protocol", "anthropic");
            }
        } catch (Exception e) { /* fall through */ }

        return Map.of("ok", false, "error", friendlyError(lastError));
    }

    // ── 私有辅助方法 ──

    private String callOpenAi(Map<String, Object> body) {
        Map<String, Object> result = restClient.post()
                .uri(config.getBaseUrl().replaceAll("/$", "") + "/chat/completions")
                .header("Authorization", "Bearer " + config.getApiKey())
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(Map.class);
        if (result == null || result.get("choices") == null)
            throw new RuntimeException("AI 响应为空");
        List<Map<String, Object>> choices = (List<Map<String, Object>>) result.get("choices");
        return (String) ((Map<String, Object>) choices.get(0).get("message")).get("content");
    }

    private String callClaudeOcr(String base64Image) {
        Map<String, Object> body = Map.of(
            "model", config.getModel(),
            "max_tokens", config.getMaxTokens(),
            "messages", List.of(Map.of("role", "user", "content", List.of(
                Map.of("type", "image", "source", Map.of("type", "base64", "media_type", "image/png", "data", base64Image)),
                Map.of("type", "text", "text", AiConstants.OCR_PROMPT)
            )))
        );
        return callClaude(body);
    }

    private String callOpenAiChat(List<Map<String, Object>> messages) {
        Map<String, Object> body = Map.of(
            "model", config.getModel(),
            "messages", messages,
            "max_tokens", config.getMaxTokens(),
            "temperature", 0.3
        );
        return callOpenAi(body);
    }

    private String callClaudeChat(List<Map<String, Object>> messages) {
        // 提取 system 消息
        String systemContent = messages.stream()
                .filter(m -> "system".equals(m.get("role")))
                .map(m -> (String) m.get("content"))
                .reduce("", (a, b) -> a + "\n\n" + b);

        List<Map<String, Object>> nonSystem = messages.stream()
                .filter(m -> !"system".equals(m.get("role")))
                .map(m -> Map.of("role", "assistant".equals(m.get("role")) ? "assistant" : "user",
                                 "content", m.get("content")))
                .toList();

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", config.getModel());
        body.put("max_tokens", config.getMaxTokens());
        body.put("messages", nonSystem);
        if (!systemContent.isBlank()) body.put("system", systemContent.trim());

        return callClaude(body);
    }

    private String callClaude(Map<String, Object> body) {
        Map<String, Object> result = restClient.post()
                .uri("https://api.anthropic.com/v1/messages")
                .header("x-api-key", config.getApiKey())
                .header("anthropic-version", "2023-06-01")
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(Map.class);
        if (result == null || result.get("content") == null)
            throw new RuntimeException("Claude 响应为空");
        List<Map<String, Object>> content = (List<Map<String, Object>>) result.get("content");
        return (String) content.get(0).get("text");
    }

    private BigDecimal toBigDecimal(Object val) {
        if (val == null) return BigDecimal.ZERO;
        try { return new BigDecimal(val.toString()); } catch (Exception e) { return BigDecimal.ZERO; }
    }

    private LocalDate parseDate(Map<String, Object> data) {
        String d = (String) data.getOrDefault("date", data.getOrDefault("invoice_date", null));
        if (d != null && !d.isBlank()) {
            try { return LocalDate.parse(d); } catch (Exception e) { /* fall through */ }
        }
        return LocalDate.now();
    }

    private String friendlyError(String msg) {
        if (msg == null || msg.isBlank()) return "所有协议尝试均失败，请检查地址和令牌";
        if (msg.contains("401") || msg.contains("403")) return "认证失败 — 令牌无效或已过期";
        if (msg.contains("404")) return "接口地址不存在 — 请检查 API 地址";
        if (msg.contains("Connection refused") || msg.contains("UnknownHost"))
            return "无法连接服务器 — 请检查地址和网络";
        if (msg.contains("timeout") || msg.contains("Read timed out")) return "连接超时 — 请检查网络";
        if (msg.length() > 100) return msg.substring(0, 100);
        return msg;
    }
}
