package com.symbiosis.finance.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.symbiosis.finance.config.AiConfig;
import com.symbiosis.finance.entity.Invoice;
import com.symbiosis.finance.mapper.InvoiceMapper;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.InetAddress;
import java.net.URI;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.Year;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class AiService {

    private static final Pattern ACTION_JSON = Pattern.compile("\\{[\\s\\S]*\"action\"[\\s\\S]*}");
    private static final Pattern ANY_JSON = Pattern.compile("\\{[\\s\\S]*}");
    private static final String ANTHROPIC_DEFAULT_BASE_URL = "https://api.anthropic.com/v1";

    private final AiConfig config;
    private final InvoiceMapper invoiceMapper;
    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final Object invoiceIdLock = new Object();

    public AiService(AiConfig config, InvoiceMapper invoiceMapper) {
        this.config = config;
        this.invoiceMapper = invoiceMapper;
        this.restClient = RestClient.create();
        this.objectMapper = new ObjectMapper();
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> recognize(String base64Image) {
        ensureConfigured();
        String content = switch (provider(config.getProvider())) {
            case "anthropic" -> callAnthropic(anthropicOcrBody(base64Image), config.getBaseUrl(), config.getApiKey());
            default -> callOpenAi(openAiOcrBody(base64Image), config.getBaseUrl(), config.getApiKey());
        };
        return parseJsonObject(content);
    }

    public Map<String, Object> chat(UUID userId, List<Map<String, String>> messages) {
        ensureConfigured();
        List<Map<String, Object>> fullMessages = buildMessages(userId, messages);
        String result = switch (provider(config.getProvider())) {
            case "anthropic" -> callAnthropic(anthropicChatBody(fullMessages), config.getBaseUrl(), config.getApiKey());
            default -> callOpenAi(openAiChatBody(fullMessages), config.getBaseUrl(), config.getApiKey());
        };
        return parseChatResult(userId, result);
    }

    public Map<String, Object> testConnection(String baseUrl, String apiKey, String model) {
        String effectiveKey = valueOr(apiKey, config.getApiKey());
        if (effectiveKey.isBlank()) {
            return Map.of("ok", false, "error", "请先填写或保存 API Key");
        }
        String effectiveProvider = provider(config.getProvider());
        String effectiveModel = valueOr(model, config.getModel());
        String effectiveBaseUrl = valueOr(baseUrl, config.getBaseUrl());

        try {
            String reply;
            if ("anthropic".equals(effectiveProvider)) {
                reply = callAnthropic(Map.of(
                        "model", effectiveModel,
                        "max_tokens", 20,
                        "messages", List.of(Map.of("role", "user", "content", "请回复：连接正常"))
                ), effectiveBaseUrl, effectiveKey);
                return Map.of("ok", true, "protocol", "anthropic", "reply", reply);
            }
            reply = callOpenAi(Map.of(
                    "model", effectiveModel,
                    "messages", List.of(Map.of("role", "user", "content", "请回复：连接正常")),
                    "max_tokens", 20,
                    "temperature", 0
            ), effectiveBaseUrl, effectiveKey);
            return Map.of("ok", true, "protocol", "openai-compatible", "reply", reply);
        } catch (Exception e) {
            return Map.of("ok", false, "error", friendlyError(e.getMessage()));
        }
    }

    private void ensureConfigured() {
        if (!config.isConfigured()) {
            throw new IllegalStateException("AI 接口未配置，请先在系统设置中保存 API Key");
        }
    }

    private List<Map<String, Object>> buildMessages(UUID userId, List<Map<String, String>> messages) {
        List<Map<String, Object>> fullMessages = new ArrayList<>();
        fullMessages.add(Map.of("role", "system", "content", AiConstants.AI_SYSTEM_PROMPT));
        fullMessages.add(Map.of("role", "system", "content", buildContext(userId)));
        int start = Math.max(0, messages == null ? 0 : messages.size() - 10);
        if (messages != null) {
            for (int i = start; i < messages.size(); i++) {
                String role = "assistant".equals(messages.get(i).get("role")) ? "assistant" : "user";
                fullMessages.add(Map.of("role", role, "content", valueOr(messages.get(i).get("content"), "")));
            }
        }
        return fullMessages;
    }

    private String buildContext(UUID userId) {
        try {
            long total = invoiceMapper.selectCount(new LambdaQueryWrapper<Invoice>().eq(Invoice::getUserId, userId));
            double totalAmount = invoiceMapper.sumTotalAmount(userId);
            String month = String.format("%tY-%<tm", LocalDate.now());
            double monthAmount = invoiceMapper.sumMonthAmount(userId.toString(), month);
            long pending = invoiceMapper.selectCount(new LambdaQueryWrapper<Invoice>()
                    .eq(Invoice::getUserId, userId).eq(Invoice::getStatus, "pending"));
            double pendingAmount = invoiceMapper.sumPendingAmount(userId);
            List<Invoice> recent = invoiceMapper.selectList(new LambdaQueryWrapper<Invoice>()
                    .eq(Invoice::getUserId, userId)
                    .orderByDesc(Invoice::getCreatedAt)
                    .last("LIMIT 5"));
            return String.format(
                    "当前用户发票总数 %d 张，含税总额 %.2f。本月含税金额 %.2f。待报销 %d 张，待报销金额 %.2f。最近发票：%s。当前日期：%s。",
                    total,
                    totalAmount,
                    monthAmount,
                    pending,
                    pendingAmount,
                    recent.stream().map(i -> String.format("%s %.2f %s",
                            i.getSeller(),
                            i.getTotalAmount() == null ? BigDecimal.ZERO : i.getTotalAmount(),
                            i.getStatus())).toList(),
                    LocalDate.now()
            );
        } catch (Exception e) {
            return "当前日期：" + LocalDate.now() + "。统计数据暂时不可用。";
        }
    }

    private Map<String, Object> openAiOcrBody(String base64Image) {
        return Map.of(
                "model", config.getModel(),
                "messages", List.of(Map.of("role", "user", "content", List.of(
                        Map.of("type", "text", "text", AiConstants.OCR_PROMPT),
                        Map.of("type", "image_url", "image_url", Map.of("url", "data:image/png;base64," + base64Image))
                ))),
                "max_tokens", config.getMaxTokens(),
                "temperature", 0
        );
    }

    private Map<String, Object> anthropicOcrBody(String base64Image) {
        return Map.of(
                "model", config.getModel(),
                "max_tokens", config.getMaxTokens(),
                "messages", List.of(Map.of("role", "user", "content", List.of(
                        Map.of("type", "image", "source", Map.of("type", "base64", "media_type", "image/png", "data", base64Image)),
                        Map.of("type", "text", "text", AiConstants.OCR_PROMPT)
                )))
        );
    }

    private Map<String, Object> openAiChatBody(List<Map<String, Object>> messages) {
        return Map.of(
                "model", config.getModel(),
                "messages", messages,
                "max_tokens", config.getMaxTokens(),
                "temperature", 0.3
        );
    }

    private Map<String, Object> anthropicChatBody(List<Map<String, Object>> messages) {
        String system = messages.stream()
                .filter(m -> "system".equals(m.get("role")))
                .map(m -> String.valueOf(m.get("content")))
                .reduce("", (a, b) -> a.isBlank() ? b : a + "\n\n" + b);
        List<Map<String, Object>> nonSystem = messages.stream()
                .filter(m -> !"system".equals(m.get("role")))
                .map(m -> Map.of(
                        "role", "assistant".equals(m.get("role")) ? "assistant" : "user",
                        "content", m.get("content")
                ))
                .toList();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", config.getModel());
        body.put("max_tokens", config.getMaxTokens());
        body.put("messages", nonSystem);
        if (!system.isBlank()) body.put("system", system);
        return body;
    }

    @SuppressWarnings("unchecked")
    private String callOpenAi(Map<String, Object> body, String baseUrl, String apiKey) {
        Map<String, Object> result = restClient.post()
                .uri(openAiChatUrl(baseUrl))
                .header("Authorization", "Bearer " + apiKey)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(Map.class);
        if (result == null || result.get("choices") == null) {
            throw new RuntimeException("AI 响应为空");
        }
        List<Map<String, Object>> choices = (List<Map<String, Object>>) result.get("choices");
        if (choices.isEmpty()) throw new RuntimeException("AI 没有返回内容");
        Map<String, Object> message = (Map<String, Object>) choices.get(0).get("message");
        return String.valueOf(message.getOrDefault("content", ""));
    }

    @SuppressWarnings("unchecked")
    private String callAnthropic(Map<String, Object> body, String baseUrl, String apiKey) {
        Map<String, Object> result = restClient.post()
                .uri(anthropicMessagesUrl(baseUrl))
                .header("x-api-key", apiKey)
                .header("anthropic-version", "2023-06-01")
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(Map.class);
        if (result == null || result.get("content") == null) {
            throw new RuntimeException("Claude 响应为空");
        }
        List<Map<String, Object>> content = (List<Map<String, Object>>) result.get("content");
        if (content.isEmpty()) throw new RuntimeException("Claude 没有返回内容");
        return String.valueOf(content.get(0).getOrDefault("text", ""));
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseJsonObject(String content) {
        String cleaned = content.replaceAll("```json\\s*|```", "").trim();
        try {
            return objectMapper.readValue(cleaned, Map.class);
        } catch (JsonProcessingException e) {
            Matcher matcher = ANY_JSON.matcher(content);
            if (matcher.find()) {
                try {
                    return objectMapper.readValue(matcher.group(), Map.class);
                } catch (Exception ignored) {
                    return Map.of();
                }
            }
            return Map.of();
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseChatResult(UUID userId, String result) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("reply", result);
        response.put("action", null);
        response.put("data", null);

        Matcher matcher = ACTION_JSON.matcher(result);
        if (!matcher.find()) return response;

        try {
            Map<String, Object> parsed = objectMapper.readValue(matcher.group(), Map.class);
            String action = String.valueOf(parsed.getOrDefault("action", ""));
            Map<String, Object> data = (Map<String, Object>) parsed.getOrDefault("data", Map.of());
            String reply = String.valueOf(parsed.getOrDefault("reply", result.replace(matcher.group(), "").trim()));
            response.put("action", action);
            response.put("data", data);

            if ("create_invoice".equals(action)) {
                reply = createInvoiceFromAi(userId, data);
            } else if ("get_stats".equals(action)) {
                reply = statsReply(userId);
            }
            response.put("reply", reply);
        } catch (Exception ignored) {
            response.put("reply", result);
        }
        return response;
    }

    private String createInvoiceFromAi(UUID userId, Map<String, Object> data) {
        if (data.get("seller") == null || (data.get("amount") == null && data.get("total_amount") == null)) {
            return "缺少销售方或金额，暂时无法自动录入发票。";
        }
        BigDecimal amount = toBigDecimal(data.get("amount") != null ? data.get("amount") : data.get("total_amount"));
        BigDecimal rate = toBigDecimal(data.getOrDefault("taxRate", data.getOrDefault("tax_rate", 0)));
        BigDecimal taxAmount = amount.multiply(rate).divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);
        BigDecimal total = data.get("totalAmount") != null || data.get("total_amount") != null
                ? toBigDecimal(data.get("totalAmount") != null ? data.get("totalAmount") : data.get("total_amount"))
                : amount.add(taxAmount);

        int year = Year.now().getValue();
        Invoice inv = new Invoice();
        inv.setInvoiceDate(parseDate(data));
        inv.setSeller(String.valueOf(data.get("seller")));
        inv.setBuyer(String.valueOf(data.getOrDefault("buyer", "广州共生纪元云科技有限公司")));
        inv.setType(String.valueOf(data.getOrDefault("type", "增值税电子普通发票")));
        inv.setCategoryId(String.valueOf(data.getOrDefault("category", "other")));
        inv.setAmount(amount);
        inv.setTaxRate(rate);
        inv.setTaxAmount(taxAmount);
        inv.setTotalAmount(total);
        inv.setStatus("pending");
        inv.setNotes(String.valueOf(data.getOrDefault("notes", data.getOrDefault("remark", "AI 助手录入"))));
        inv.setUserId(userId);
        inv.setCreatedAt(OffsetDateTime.now());
        inv.setUpdatedAt(OffsetDateTime.now());
        synchronized (invoiceIdLock) {
            String lastId = invoiceMapper.findLastIdByYear(year);
            int next = 1;
            if (lastId != null) {
                try { next = Integer.parseInt(lastId.split("-")[2]) + 1; } catch (Exception ignored) {}
            }
            inv.setId(String.format("INV-%d-%04d", year, next));
            invoiceMapper.insert(inv);
        }
        return String.format("已录入发票 %s：%s，金额 %.2f。", inv.getId(), inv.getSeller(), amount);
    }

    private String statsReply(UUID userId) {
        String month = String.format("%tY-%<tm", LocalDate.now());
        long pending = invoiceMapper.selectCount(new LambdaQueryWrapper<Invoice>()
                .eq(Invoice::getUserId, userId).eq(Invoice::getStatus, "pending"));
        double monthAmount = invoiceMapper.sumMonthAmount(userId.toString(), month);
        double pendingAmount = invoiceMapper.sumPendingAmount(userId);
        return String.format("本月发票含税金额 %.2f 元；待报销 %d 张，金额 %.2f 元。", monthAmount, pending, pendingAmount);
    }

    private BigDecimal toBigDecimal(Object value) {
        if (value == null) return BigDecimal.ZERO;
        try {
            return new BigDecimal(String.valueOf(value).replace(",", "").trim());
        } catch (Exception e) {
            return BigDecimal.ZERO;
        }
    }

    private LocalDate parseDate(Map<String, Object> data) {
        Object value = data.getOrDefault("date", data.get("invoice_date"));
        if (value != null && !String.valueOf(value).isBlank()) {
            try { return LocalDate.parse(String.valueOf(value)); } catch (Exception ignored) {}
        }
        return LocalDate.now();
    }

    private String openAiChatUrl(String baseUrl) {
        String normalized = valueOr(baseUrl, "https://api.openai.com/v1").replaceAll("/+$", "");
        validatePublicHttpsBaseUrl(normalized);
        return normalized.endsWith("/chat/completions") ? normalized : normalized + "/chat/completions";
    }

    private String anthropicMessagesUrl(String baseUrl) {
        String normalized = valueOr(baseUrl, ANTHROPIC_DEFAULT_BASE_URL).replaceAll("/+$", "");
        if (normalized.contains("api.openai.com")) normalized = ANTHROPIC_DEFAULT_BASE_URL;
        validatePublicHttpsBaseUrl(normalized);
        return normalized.endsWith("/messages") ? normalized : normalized + "/messages";
    }

    private void validatePublicHttpsBaseUrl(String baseUrl) {
        URI uri;
        try {
            uri = URI.create(baseUrl);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("AI Base URL 格式不正确");
        }
        String host = uri.getHost();
        if (!"https".equalsIgnoreCase(uri.getScheme()) || host == null || host.isBlank()) {
            throw new IllegalArgumentException("AI Base URL 只允许 https 地址");
        }
        String normalizedHost = host.trim().toLowerCase();
        if ("localhost".equals(normalizedHost)
                || normalizedHost.endsWith(".localhost")
                || !normalizedHost.contains(".")) {
            throw new IllegalArgumentException("AI Base URL 不允许使用本机或内网主机名");
        }
        if (isIpLiteral(normalizedHost) && isBlockedAddress(normalizedHost)) {
            throw new IllegalArgumentException("AI Base URL 不允许使用本机或内网 IP");
        }
    }

    private boolean isIpLiteral(String host) {
        return host.matches("[0-9.]+") || host.contains(":");
    }

    private boolean isBlockedAddress(String host) {
        try {
            InetAddress address = InetAddress.getByName(host);
            return address.isAnyLocalAddress()
                    || address.isLoopbackAddress()
                    || address.isLinkLocalAddress()
                    || address.isSiteLocalAddress();
        } catch (Exception e) {
            throw new IllegalArgumentException("AI Base URL 主机地址不合法");
        }
    }

    private String provider(String provider) {
        return "anthropic".equalsIgnoreCase(provider) ? "anthropic" : "openai";
    }

    private String valueOr(String value, String fallback) {
        return value != null && !value.isBlank() ? value.trim() : (fallback == null ? "" : fallback);
    }

    private String friendlyError(String message) {
        String msg = message == null ? "" : message;
        if (msg.contains("401") || msg.contains("403")) return "认证失败，请检查 API Key 是否正确或已过期";
        if (msg.contains("404")) return "接口地址不存在，请检查 Base URL 和模型提供商";
        if (msg.contains("429")) return "调用太频繁或额度不足，请稍后再试";
        if (msg.contains("Connection refused") || msg.contains("UnknownHost")) return "无法连接 AI 服务，请检查网络和 Base URL";
        if (msg.contains("timeout") || msg.contains("Read timed out")) return "连接超时，请检查网络或稍后再试";
        if (msg.isBlank()) return "连接失败，请检查模型、Base URL 和 API Key";
        return msg.length() > 160 ? msg.substring(0, 160) : msg;
    }
}
