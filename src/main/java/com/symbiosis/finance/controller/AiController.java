package com.symbiosis.finance.controller;

import com.symbiosis.finance.service.AiService;
import com.symbiosis.finance.service.AiSettingsService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@RestController
@RequestMapping("/api")
public class AiController {

    private final AiService aiService;
    private final AiSettingsService aiSettingsService;

    public AiController(AiService aiService, AiSettingsService aiSettingsService) {
        this.aiService = aiService;
        this.aiSettingsService = aiSettingsService;
    }

    public static class OcrRequest {
        @NotBlank public String image;
    }

    public static class ChatRequest {
        @NotEmpty public List<Map<String, String>> messages;
    }

    public static class TestRequest {
        public String provider;
        public String baseUrl;
        public String apiKey;
        public String model;
    }

    public static class ParseTextRequest {
        @NotBlank public String text;
    }

    @PostMapping("/ocr/recognize")
    public ResponseEntity<?> recognize(@AuthenticationPrincipal String userId,
                                       @Valid @RequestBody OcrRequest req) {
        try {
            Map<String, Object> fields = aiService.recognize(
                    req.image.replaceAll("^data:image/\\w+;base64,", ""));
            return ResponseEntity.ok(fields);
        } catch (IllegalStateException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", "AI 识别失败: " + e.getMessage()));
        }
    }

    @PostMapping("/ocr/parse-text")
    public ResponseEntity<?> parseText(@Valid @RequestBody ParseTextRequest req) {
        String text = req.text == null ? "" : req.text.replace('\u00A0', ' ');
        if (text.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "empty text"));
        }

        Map<String, Object> result = new LinkedHashMap<>();
        List<String> lines = normalizedLines(text);
        String joined = String.join("\n", lines);

        putFirst(result, "number", joined, "发票号码[:：]?\\s*(\\d{8,20})", "(?<![A-Z0-9])(\\d{18,20})(?![A-Z0-9])");
        Matcher date = Pattern.compile("(\\d{4})年\\s*(\\d{1,2})月\\s*(\\d{1,2})日").matcher(joined);
        if (date.find()) {
            result.put("date", date.group(1) + "-" + pad2(date.group(2)) + "-" + pad2(date.group(3)));
        }

        if (joined.contains("电子发票")) {
            result.put("type", joined.contains("专用发票") ? "电子发票（专用发票）" : "电子发票（普通发票）");
        } else if (joined.contains("增值税专用发票")) {
            result.put("type", "增值税专用发票");
        } else if (joined.contains("普通发票")) {
            result.put("type", "普通发票");
        }

        parseParties(result, lines);
        parseItemAndAmounts(result, lines, joined);
        result.putIfAbsent("category", inferCategory(asString(result.get("itemName"))));

        return ResponseEntity.ok(result);
    }

    private static void parseParties(Map<String, Object> result, List<String> lines) {
        for (String line : lines) {
            Matcher names = Pattern.compile("名称[:：]\\s*(.+?)\\s+名称[:：]\\s*(.+)").matcher(line);
            if (names.find()) {
                result.put("buyer", cleanParty(names.group(1)));
                result.put("seller", cleanParty(names.group(2)));
                break;
            }
        }

        if (!result.containsKey("buyer") || !result.containsKey("seller")) {
            List<String> companies = new ArrayList<>();
            Matcher company = Pattern.compile("([\\u4e00-\\u9fa5A-Za-z0-9（）()·]+(?:公司|集团|中心|事务所|厂|店))").matcher(String.join(" ", lines));
            while (company.find()) companies.add(cleanParty(company.group(1)));
            if (!result.containsKey("buyer") && companies.size() > 0) result.put("buyer", companies.get(0));
            if (!result.containsKey("seller") && companies.size() > 1) result.put("seller", companies.get(1));
        }

        List<String> taxNos = new ArrayList<>();
        Matcher taxNo = Pattern.compile("(?<![A-Z0-9])(?=[0-9A-Z]{15,20}\\b)(?=[0-9A-Z]*[A-Z])[0-9A-Z]{15,20}(?![A-Z0-9])").matcher(String.join(" ", lines));
        while (taxNo.find()) taxNos.add(taxNo.group());
        if (!taxNos.isEmpty()) result.put("buyerTaxNo", taxNos.get(0));
        if (taxNos.size() > 1) result.put("sellerTaxNo", taxNos.get(1));
    }

    private static void parseItemAndAmounts(Map<String, Object> result, List<String> lines, String joined) {
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            if (!line.contains("*")) continue;

            String itemLine = line;
            if (i + 1 < lines.size() && !containsMoney(lines.get(i + 1)) && !lines.get(i + 1).contains("合 计")) {
                Matcher split = Pattern.compile("^(.*?)\\s+([^\\d¥￥%\\s]+\\s+\\d+(?:\\.\\d+)?.*)$").matcher(line);
                if (split.find()) {
                    itemLine = split.group(1).trim() + lines.get(i + 1).trim() + " " + split.group(2).trim();
                } else {
                    itemLine = line + lines.get(i + 1).trim();
                }
            }

            Matcher detail = Pattern.compile("^(.*?)\\s+([^\\d¥￥%\\s]+)\\s+(\\d+(?:\\.\\d+)?)\\s+(\\d+(?:\\.\\d+)?)\\s+(\\d+(?:\\.\\d+)?)\\s+(\\d+(?:\\.\\d+)?)%\\s+(\\d+(?:\\.\\d+)?)$").matcher(itemLine);
            if (detail.find()) {
                result.put("itemName", detail.group(1).replaceAll("\\s+", ""));
                result.put("itemUnit", detail.group(2));
                result.put("itemQuantity", bd(detail.group(3)));
                result.put("itemUnitPrice", bd(detail.group(4)));
                result.put("amount", bd(detail.group(5)));
                result.put("taxRate", bd(detail.group(6)));
                result.put("taxAmount", bd(detail.group(7)));
            } else {
                result.put("itemName", itemLine.trim());
            }
            break;
        }

        Matcher sum = Pattern.compile("合\\s*计\\s*[¥￥]?\\s*(\\d+(?:\\.\\d+)?)\\s*[¥￥]?\\s*(\\d+(?:\\.\\d+)?)").matcher(joined);
        if (sum.find()) {
            result.put("amount", bd(sum.group(1)));
            result.put("taxAmount", bd(sum.group(2)));
        }

        Matcher total = Pattern.compile("价税合计（大写）\\s*(.+?)\\s*（小写）\\s*[¥￥]?\\s*(\\d+(?:\\.\\d+)?)").matcher(joined);
        if (total.find()) {
            result.put("totalAmountCn", total.group(1).trim());
            result.put("totalAmount", bd(total.group(2)));
        } else {
            Matcher small = Pattern.compile("（小写）\\s*[¥￥]?\\s*(\\d+(?:\\.\\d+)?)").matcher(joined);
            if (small.find()) result.put("totalAmount", bd(small.group(1)));
        }

        if (!result.containsKey("taxRate")) {
            putDecimal(result, "taxRate", joined, "(\\d+(?:\\.\\d+)?)%");
        }

        for (int i = 0; i < lines.size(); i++) {
            if (lines.get(i).equals("备") && i + 2 < lines.size() && lines.get(i + 1).equals("注")) {
                result.put("notes", lines.get(Math.max(0, i - 1)));
                break;
            }
            if (lines.get(i).equals("备注") && i + 1 < lines.size()) {
                result.put("notes", lines.get(i + 1));
                break;
            }
        }
    }

    @PostMapping("/ai/chat")
    public ResponseEntity<?> chat(@AuthenticationPrincipal String userId,
                                  @Valid @RequestBody ChatRequest req) {
        try {
            Map<String, Object> result = aiService.chat(UUID.fromString(userId), req.messages);
            return ResponseEntity.ok(result);
        } catch (IllegalStateException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", "AI 服务异常: " + e.getMessage()));
        }
    }

    @PostMapping("/ai/test")
    public Map<String, Object> test(@Valid @RequestBody TestRequest req) {
        return aiService.testConnection(req.baseUrl, req.apiKey, req.model);
    }

    @GetMapping("/ai/config")
    public Map<String, Object> config() {
        return aiSettingsService.publicConfig();
    }

    @PostMapping("/ai/config")
    public ResponseEntity<?> saveConfig(@RequestBody AiSettingsService.Settings req) {
        try {
            return ResponseEntity.ok(aiSettingsService.save(req));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", "AI 配置保存失败"));
        }
    }

    private static List<String> normalizedLines(String text) {
        List<String> lines = new ArrayList<>();
        for (String raw : text.split("\\R+")) {
            String line = raw.replaceAll("\\s+", " ").trim();
            if (!line.isBlank()) lines.add(line);
        }
        return lines;
    }

    private static void putFirst(Map<String, Object> result, String key, String text, String... regexes) {
        for (String regex : regexes) {
            Matcher matcher = Pattern.compile(regex).matcher(text);
            if (matcher.find()) {
                result.put(key, matcher.group(1));
                return;
            }
        }
    }

    private static void putDecimal(Map<String, Object> result, String key, String text, String regex) {
        Matcher matcher = Pattern.compile(regex).matcher(text);
        if (matcher.find()) result.put(key, bd(matcher.group(1)));
    }

    private static BigDecimal bd(String value) {
        return new BigDecimal(value.replace(",", "").trim());
    }

    private static boolean containsMoney(String line) {
        return line.contains("¥") || line.contains("￥") || line.matches(".*\\d+\\.\\d{2}.*");
    }

    private static String cleanParty(String value) {
        return value.replaceAll("^[购销买售方信息\\s]+", "").replaceAll("[购销买售方信息\\s]+$", "").trim();
    }

    private static String inferCategory(String itemName) {
        String text = itemName == null ? "" : itemName;
        if (text.contains("修理") || text.contains("修配") || text.contains("服务")) return "service";
        if (text.contains("交通") || text.contains("客运") || text.contains("运输")) return "travel";
        if (text.contains("餐饮") || text.contains("食品")) return "catering";
        if (text.contains("水") || text.contains("电") || text.contains("物业")) return "utility";
        if (text.contains("物流") || text.contains("快递")) return "logistics";
        if (text.contains("租赁") || text.contains("不动产")) return "rental";
        if (text.contains("办公")) return "office";
        return "other";
    }

    private static String asString(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private static String pad2(String value) {
        return value.length() == 1 ? "0" + value : value;
    }
}
