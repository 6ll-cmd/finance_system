package com.symbiosis.finance.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.symbiosis.finance.entity.Category;
import com.symbiosis.finance.entity.User;
import com.symbiosis.finance.entity.Invoice;
import com.symbiosis.finance.entity.InvoiceAttachment;
import com.symbiosis.finance.mapper.CategoryMapper;
import com.symbiosis.finance.mapper.InvoiceAttachmentMapper;
import com.symbiosis.finance.mapper.InvoiceMapper;
import com.symbiosis.finance.mapper.UserMapper;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.Year;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api")
public class InvoiceController {

    private final InvoiceMapper invoiceMapper;
    private final InvoiceAttachmentMapper invoiceAttachmentMapper;
    private final CategoryMapper categoryMapper;
    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final Object invoiceIdLock = new Object();
    private static final Set<String> ALLOWED_ATTACHMENT_TYPES = Set.of(
            MediaType.IMAGE_JPEG_VALUE,
            MediaType.IMAGE_PNG_VALUE,
            MediaType.APPLICATION_PDF_VALUE
    );

    public InvoiceController(InvoiceMapper invoiceMapper, InvoiceAttachmentMapper invoiceAttachmentMapper, CategoryMapper categoryMapper,
                             UserMapper userMapper, PasswordEncoder passwordEncoder) {
        this.invoiceMapper = invoiceMapper;
        this.invoiceAttachmentMapper = invoiceAttachmentMapper;
        this.categoryMapper = categoryMapper;
        this.userMapper = userMapper;
        this.passwordEncoder = passwordEncoder;
    }

    private ResponseEntity<?> checkOperationPassword(String userId, String opPassword) {
        User user = userMapper.selectById(UUID.fromString(userId));
        if (user == null) return ResponseEntity.status(404).body(Map.of("error", "user not found"));
        if (user.getOperationPassword() == null || user.getOperationPassword().isEmpty()) {
            return ResponseEntity.status(403).body(Map.of("error", "operation password not set"));
        }
        if (opPassword == null || !passwordEncoder.matches(opPassword, user.getOperationPassword())) {
            return ResponseEntity.status(403).body(Map.of("error", "operation password incorrect"));
        }
        return null;
    }

    public static class CreateInvoiceRequest {
        @NotBlank(message = "请填写销售方企业全称") public String seller;
        public String buyer;
        public String buyerTaxNo;
        public String buyerAddressPhone;
        public String buyerBankAccount;
        public String sellerTaxNo;
        public String sellerAddressPhone;
        public String sellerBankAccount;
        public String type;
        public String category;
        public String itemName;
        public String itemSpec;
        public String itemUnit;
        public BigDecimal itemQuantity;
        public BigDecimal itemUnitPrice;
        @NotNull(message = "请填写不含税金额")
        @DecimalMin(value = "0.01", message = "不含税金额必须大于 0")
        public BigDecimal amount;
        public BigDecimal taxRate;
        public BigDecimal taxAmount;
        public BigDecimal totalAmount;
        public String totalAmountCn;
        public String notes;
        public String invoiceCode;
        @NotBlank(message = "请填写发票号码")
        public String invoiceNumber;
        public LocalDate date;
        public AttachmentRequest attachment;
    }

    public static class AttachmentRequest {
        public String fileName;
        public String contentType;
        public String data;
    }

    public static class StatusRequest {
        @NotBlank public String status;
        public String operationPassword;
    }

    public static class BatchStatusRequest {
        public List<String> ids;
        @NotBlank public String status;
        public String operationPassword;
    }

    public static class DeleteInvoiceRequest {
        public String operationPassword;
    }

    private String csv(Object value) {
        String text = value == null ? "" : String.valueOf(value);
        return "\"" + text.replace("\"", "\"\"") + "\"";
    }

    private BigDecimal money(BigDecimal value) {
        return (value == null ? BigDecimal.ZERO : value).setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal moneyOrZero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value.setScale(2, RoundingMode.HALF_UP);
    }

    private ResponseEntity<?> validateInvoiceRequest(CreateInvoiceRequest req) {
        String invoiceNumber = req.invoiceNumber == null ? "" : req.invoiceNumber.trim();
        if (!invoiceNumber.matches("[0-9A-Za-z\\-]{6,32}")) {
            return ResponseEntity.badRequest().body(Map.of("error", "发票号码格式不正确"));
        }
        String categoryId = req.category != null && !req.category.isBlank() ? req.category : "other";
        if (categoryMapper.selectById(categoryId) == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "发票类别不存在"));
        }

        BigDecimal amount = moneyOrZero(req.amount);
        BigDecimal tax = moneyOrZero(req.taxAmount);
        BigDecimal total = req.totalAmount != null
                ? moneyOrZero(req.totalAmount)
                : amount.add(tax).setScale(2, RoundingMode.HALF_UP);
        BigDecimal rate = req.taxRate != null ? req.taxRate : BigDecimal.ZERO;

        if (amount.compareTo(BigDecimal.ZERO) <= 0
                || tax.compareTo(BigDecimal.ZERO) < 0
                || total.compareTo(BigDecimal.ZERO) <= 0) {
            return ResponseEntity.badRequest().body(Map.of("error", "发票金额必须为非负且不含税金额、价税合计必须大于 0"));
        }
        if (rate.compareTo(BigDecimal.ZERO) < 0 || rate.compareTo(new BigDecimal("100")) > 0) {
            return ResponseEntity.badRequest().body(Map.of("error", "税率必须在 0 到 100 之间"));
        }

        BigDecimal expectedTotal = amount.add(tax).setScale(2, RoundingMode.HALF_UP);
        if (total.subtract(expectedTotal).abs().compareTo(new BigDecimal("0.05")) > 0) {
            return ResponseEntity.badRequest().body(Map.of("error", "价税合计必须等于不含税金额加税额"));
        }

        req.amount = amount;
        req.taxAmount = tax;
        req.totalAmount = expectedTotal;
        req.taxRate = rate;
        return null;
    }

    private String statusLabel(String status) {
        return switch (status) {
            case "pending" -> "待报销";
            case "reimbursed" -> "已报销";
            case "rejected" -> "已退回";
            default -> status == null ? "" : status;
        };
    }

    private InvoiceAttachment latestAttachment(String invoiceId, UUID userId) {
        return invoiceAttachmentMapper.selectOne(new LambdaQueryWrapper<InvoiceAttachment>()
                .eq(InvoiceAttachment::getInvoiceId, invoiceId)
                .eq(InvoiceAttachment::getUserId, userId)
                .orderByDesc(InvoiceAttachment::getCreatedAt)
                .last("LIMIT 1"));
    }

    private String safeAttachmentContentType(String rawContentType) {
        if (rawContentType == null) return MediaType.APPLICATION_OCTET_STREAM_VALUE;
        String clean = rawContentType.split(";", 2)[0].trim().toLowerCase(Locale.ROOT);
        if ("image/jpg".equals(clean)) clean = MediaType.IMAGE_JPEG_VALUE;
        return ALLOWED_ATTACHMENT_TYPES.contains(clean) ? clean : MediaType.APPLICATION_OCTET_STREAM_VALUE;
    }

    private void saveAttachment(String invoiceId, UUID userId, AttachmentRequest attachment) {
        if (attachment == null || attachment.data == null || attachment.data.isBlank()) return;
        String data = attachment.data.trim();
        String rawContentType = attachment.contentType != null && !attachment.contentType.isBlank()
                ? attachment.contentType.trim()
                : MediaType.APPLICATION_OCTET_STREAM_VALUE;
        int comma = data.indexOf(',');
        if (data.startsWith("data:") && comma > 0) {
            String meta = data.substring(5, comma);
            int semi = meta.indexOf(';');
            if (semi > 0) rawContentType = meta.substring(0, semi);
            data = data.substring(comma + 1);
        }
        String contentType = safeAttachmentContentType(rawContentType);

        byte[] content;
        try {
            content = Base64.getDecoder().decode(data);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("附件内容格式不正确，请重新上传");
        }
        if (content.length > 10 * 1024 * 1024) {
            throw new IllegalArgumentException("附件不能超过 10MB");
        }
        String fileName = attachment.fileName != null && !attachment.fileName.isBlank()
                ? attachment.fileName.trim()
                : "invoice-attachment";

        InvoiceAttachment record = new InvoiceAttachment();
        record.setId(UUID.randomUUID());
        record.setInvoiceId(invoiceId);
        record.setUserId(userId);
        record.setFileName(fileName);
        record.setContentType(contentType);
        record.setFileSize((long) content.length);
        record.setContent(content);
        record.setCreatedAt(OffsetDateTime.now());
        invoiceAttachmentMapper.insert(record);
    }

    private Map<String, Object> toInvoiceMap(Invoice inv) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", inv.getId());
        m.put("date", inv.getInvoiceDate() != null ? inv.getInvoiceDate().toString() : null);
        m.put("seller", inv.getSeller());
        m.put("buyer", inv.getBuyer());
        m.put("buyerTaxNo", inv.getBuyerTaxNo());
        m.put("buyerAddressPhone", inv.getBuyerAddressPhone());
        m.put("buyerBankAccount", inv.getBuyerBankAccount());
        m.put("sellerTaxNo", inv.getSellerTaxNo());
        m.put("sellerAddressPhone", inv.getSellerAddressPhone());
        m.put("sellerBankAccount", inv.getSellerBankAccount());
        m.put("type", inv.getType());
        m.put("category", inv.getCategoryId());
        m.put("categoryName", inv.getCategoryName());
        m.put("itemName", inv.getItemName());
        m.put("itemSpec", inv.getItemSpec());
        m.put("itemUnit", inv.getItemUnit());
        m.put("itemQuantity", inv.getItemQuantity());
        m.put("itemUnitPrice", inv.getItemUnitPrice());
        m.put("amount", inv.getAmount());
        m.put("taxRate", inv.getTaxRate());
        m.put("taxAmount", inv.getTaxAmount());
        m.put("totalAmount", inv.getTotalAmount());
        m.put("totalAmountCn", inv.getTotalAmountCn());
        m.put("status", inv.getStatus());
        m.put("notes", inv.getNotes());
        m.put("invoiceCode", inv.getInvoiceCode());
        m.put("invoiceNumber", inv.getInvoiceNumber());
        m.put("image", inv.getImagePath());
        if (inv.getUserId() != null) {
            InvoiceAttachment attachment = latestAttachment(inv.getId(), inv.getUserId());
            m.put("hasAttachment", attachment != null);
            if (attachment != null) {
                m.put("attachmentName", attachment.getFileName());
                m.put("attachmentContentType", attachment.getContentType());
                m.put("attachmentSize", attachment.getFileSize());
            }
        }
        return m;
    }

    @GetMapping("/invoices")
    public ResponseEntity<?> list(@AuthenticationPrincipal String userId,
                                  @RequestParam(required = false) String search,
                                  @RequestParam(required = false) String status,
                                  @RequestParam(required = false) String category,
                                  @RequestParam(required = false) String sort,
                                  @RequestParam(defaultValue = "1") int page,
                                  @RequestParam(defaultValue = "15") int limit) {
        page = Math.max(1, page);
        limit = Math.max(1, Math.min(200, limit));
        UUID uid = UUID.fromString(userId);
        LambdaQueryWrapper<Invoice> countQw = new LambdaQueryWrapper<>();
        countQw.eq(Invoice::getUserId, uid);
        if (search != null && !search.isBlank()) {
            countQw.and(w -> w.like(Invoice::getId, search)
                    .or().like(Invoice::getInvoiceNumber, search)
                    .or().like(Invoice::getSeller, search)
                    .or().like(Invoice::getNotes, search)
                    .or().like(Invoice::getBuyer, search));
        }
        if (status != null && !status.equals("all")) countQw.eq(Invoice::getStatus, status);
        if (category != null && !category.equals("all")) countQw.eq(Invoice::getCategoryId, category);
        long total = invoiceMapper.selectCount(countQw);

        LambdaQueryWrapper<Invoice> qw = new LambdaQueryWrapper<>();
        qw.eq(Invoice::getUserId, uid);
        if (search != null && !search.isBlank()) {
            qw.and(w -> w.like(Invoice::getId, search)
                    .or().like(Invoice::getInvoiceNumber, search)
                    .or().like(Invoice::getSeller, search)
                    .or().like(Invoice::getNotes, search)
                    .or().like(Invoice::getBuyer, search));
        }
        if (status != null && !status.equals("all")) qw.eq(Invoice::getStatus, status);
        if (category != null && !category.equals("all")) qw.eq(Invoice::getCategoryId, category);

        if ("date-asc".equals(sort)) qw.orderByAsc(Invoice::getInvoiceDate);
        else if ("date-desc".equals(sort)) qw.orderByDesc(Invoice::getInvoiceDate);
        else if ("amount-desc".equals(sort)) qw.orderByDesc(Invoice::getTotalAmount);
        else if ("amount-asc".equals(sort)) qw.orderByAsc(Invoice::getTotalAmount);
        else qw.orderByDesc(Invoice::getCreatedAt);

        long offset = (long) (page - 1) * limit;
        qw.last("LIMIT " + limit + " OFFSET " + offset);
        List<Invoice> records = invoiceMapper.selectList(qw);

        Map<String, String> catMap = categoryMapper.selectList(null).stream()
                .collect(Collectors.toMap(Category::getId, Category::getName));
        for (Invoice inv : records) {
            inv.setCategoryName(catMap.getOrDefault(inv.getCategoryId(), ""));
        }

        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("total", total);
        resp.put("page", page);
        resp.put("limit", limit);
        resp.put("totalPages", (int) Math.ceil((double) total / limit));
        resp.put("data", records.stream().map(this::toInvoiceMap).toList());
        return ResponseEntity.ok(resp);
    }

    @GetMapping("/invoices/{id}")
    public ResponseEntity<?> getOne(@AuthenticationPrincipal String userId, @PathVariable String id) {
        UUID uid = UUID.fromString(userId);
        Invoice inv = invoiceMapper.selectOne(
                new LambdaQueryWrapper<Invoice>().eq(Invoice::getId, id).eq(Invoice::getUserId, uid));
        if (inv == null) return ResponseEntity.status(404).body(Map.of("error", "invoice not found"));

        Category cat = categoryMapper.selectById(inv.getCategoryId());
        if (cat != null) inv.setCategoryName(cat.getName());

        return ResponseEntity.ok(toInvoiceMap(inv));
    }

    @PostMapping("/invoices")
    @Transactional
    public ResponseEntity<?> create(@AuthenticationPrincipal String userId,
                                    @Valid @RequestBody CreateInvoiceRequest req) {
        UUID uid = UUID.fromString(userId);
        int year = Year.now().getValue();
        ResponseEntity<?> invalid = validateInvoiceRequest(req);
        if (invalid != null) return invalid;
        String invoiceNumber = req.invoiceNumber == null ? "" : req.invoiceNumber.trim();
        if (invoiceNumber.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "请填写发票号码"));
        }
        long duplicateCount = invoiceMapper.selectCount(new LambdaQueryWrapper<Invoice>()
                .eq(Invoice::getUserId, uid)
                .eq(Invoice::getInvoiceNumber, invoiceNumber));
        if (duplicateCount > 0) {
            return ResponseEntity.status(409).body(Map.of("error", "该发票已录入"));
        }

        Invoice inv = new Invoice();
        inv.setInvoiceDate(req.date != null ? req.date : LocalDate.now());
        inv.setSeller(req.seller);
        inv.setBuyer(req.buyer != null ? req.buyer : "");
        inv.setBuyerTaxNo(req.buyerTaxNo != null ? req.buyerTaxNo : "");
        inv.setBuyerAddressPhone(req.buyerAddressPhone != null ? req.buyerAddressPhone : "");
        inv.setBuyerBankAccount(req.buyerBankAccount != null ? req.buyerBankAccount : "");
        inv.setSellerTaxNo(req.sellerTaxNo != null ? req.sellerTaxNo : "");
        inv.setSellerAddressPhone(req.sellerAddressPhone != null ? req.sellerAddressPhone : "");
        inv.setSellerBankAccount(req.sellerBankAccount != null ? req.sellerBankAccount : "");
        inv.setType(req.type != null ? req.type : "VAT electronic invoice");
        inv.setCategoryId(req.category != null && !req.category.isBlank() ? req.category : "other");
        inv.setItemName(req.itemName != null ? req.itemName : "");
        inv.setItemSpec(req.itemSpec != null ? req.itemSpec : "");
        inv.setItemUnit(req.itemUnit != null ? req.itemUnit : "");
        inv.setItemQuantity(req.itemQuantity != null ? req.itemQuantity : BigDecimal.ZERO);
        inv.setItemUnitPrice(req.itemUnitPrice != null ? req.itemUnitPrice : BigDecimal.ZERO);
        inv.setAmount(req.amount != null ? req.amount : BigDecimal.ZERO);
        inv.setTaxRate(req.taxRate != null ? req.taxRate : BigDecimal.ZERO);
        inv.setTaxAmount(req.taxAmount != null ? req.taxAmount : BigDecimal.ZERO);
        inv.setTotalAmount(req.totalAmount != null ? req.totalAmount : req.amount);
        inv.setTotalAmountCn(req.totalAmountCn != null ? req.totalAmountCn : "");
        inv.setStatus("pending");
        inv.setNotes(req.notes != null ? req.notes : "");
        inv.setInvoiceCode(req.invoiceCode != null ? req.invoiceCode.trim() : "");
        inv.setInvoiceNumber(invoiceNumber);
        inv.setUserId(uid);
        inv.setCreatedAt(OffsetDateTime.now());
        inv.setUpdatedAt(OffsetDateTime.now());
        synchronized (invoiceIdLock) {
            long lockedDuplicateCount = invoiceMapper.selectCount(new LambdaQueryWrapper<Invoice>()
                    .eq(Invoice::getUserId, uid)
                    .eq(Invoice::getInvoiceNumber, invoiceNumber));
            if (lockedDuplicateCount > 0) {
                return ResponseEntity.status(409).body(Map.of("error", "该发票已录入"));
            }
            String lastId = invoiceMapper.findLastIdByYear(year);
            int next = 1;
            if (lastId != null) {
                try {
                    next = Integer.parseInt(lastId.split("-")[2]) + 1;
                } catch (Exception ignored) {}
            }
            inv.setId(String.format("INV-%d-%04d", year, next));
            invoiceMapper.insert(inv);
            saveAttachment(inv.getId(), uid, req.attachment);
        }

        Category cat = categoryMapper.selectById(inv.getCategoryId());
        if (cat != null) inv.setCategoryName(cat.getName());

        return ResponseEntity.status(201).body(toInvoiceMap(inv));
    }

    @GetMapping("/invoices/{id}/attachment")
    public ResponseEntity<?> downloadAttachment(@AuthenticationPrincipal String userId, @PathVariable String id) {
        UUID uid = UUID.fromString(userId);
        Invoice inv = invoiceMapper.selectOne(
                new LambdaQueryWrapper<Invoice>().eq(Invoice::getId, id).eq(Invoice::getUserId, uid));
        if (inv == null) return ResponseEntity.status(404).body(Map.of("error", "invoice not found"));

        InvoiceAttachment attachment = latestAttachment(id, uid);
        if (attachment == null) return ResponseEntity.status(404).body(Map.of("error", "attachment not found"));

        String safeName = attachment.getFileName().replaceAll("[\\r\\n\"]", "_");
        String safeContentType = safeAttachmentContentType(attachment.getContentType());
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(safeContentType))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(safeName, StandardCharsets.UTF_8).build().toString())
                .header("X-Content-Type-Options", "nosniff")
                .body(attachment.getContent());
    }

    @PatchMapping("/invoices/{id}/status")
    public ResponseEntity<?> updateStatus(@AuthenticationPrincipal String userId,
                                          @PathVariable String id,
                                          @Valid @RequestBody StatusRequest req) {
        if (!Set.of("pending", "reimbursed", "rejected").contains(req.status)) {
            return ResponseEntity.badRequest().body(Map.of("error", "invalid status"));
        }
        UUID uid = UUID.fromString(userId);
        Invoice inv = invoiceMapper.selectOne(
                new LambdaQueryWrapper<Invoice>().eq(Invoice::getId, id).eq(Invoice::getUserId, uid));
        if (inv == null) return ResponseEntity.status(404).body(Map.of("error", "invoice not found"));

        if ("rejected".equals(req.status)) {
            ResponseEntity<?> denied = checkOperationPassword(userId, req.operationPassword);
            if (denied != null) return denied;
        }

        inv.setStatus(req.status);
        inv.setUpdatedAt(OffsetDateTime.now());
        invoiceMapper.updateById(inv);

        Category cat = categoryMapper.selectById(inv.getCategoryId());
        if (cat != null) inv.setCategoryName(cat.getName());

        return ResponseEntity.ok(toInvoiceMap(inv));
    }

    @PatchMapping("/invoices/batch-status")
    @Transactional
    public ResponseEntity<?> batchStatus(@AuthenticationPrincipal String userId,
                                         @Valid @RequestBody BatchStatusRequest req) {
        if (!Set.of("pending", "reimbursed", "rejected").contains(req.status)) {
            return ResponseEntity.badRequest().body(Map.of("error", "invalid params"));
        }
        if (req.ids == null || req.ids.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "ids required"));
        }
        if ("rejected".equals(req.status)) {
            ResponseEntity<?> denied = checkOperationPassword(userId, req.operationPassword);
            if (denied != null) return denied;
        }
        UUID uid = UUID.fromString(userId);
        int updated = 0;
        for (String id : req.ids) {
            Invoice inv = invoiceMapper.selectOne(
                    new LambdaQueryWrapper<Invoice>().eq(Invoice::getId, id).eq(Invoice::getUserId, uid));
            if (inv != null) {
                inv.setStatus(req.status);
                inv.setUpdatedAt(OffsetDateTime.now());
                invoiceMapper.updateById(inv);
                updated++;
            }
        }
        return ResponseEntity.ok(Map.of("updated", updated));
    }

    @DeleteMapping("/invoices/{id}")
    public ResponseEntity<?> deleteInvoice(@AuthenticationPrincipal String userId,
                                           @PathVariable String id,
                                           @RequestBody(required = false) DeleteInvoiceRequest req) {
        UUID uid = UUID.fromString(userId);
        Invoice inv = invoiceMapper.selectOne(
                new LambdaQueryWrapper<Invoice>().eq(Invoice::getId, id).eq(Invoice::getUserId, uid));
        if (inv == null) return ResponseEntity.status(404).body(Map.of("error", "invoice not found"));
        ResponseEntity<?> denied = checkOperationPassword(userId, req != null ? req.operationPassword : null);
        if (denied != null) return denied;
        invoiceMapper.deleteById(id);
        return ResponseEntity.ok(Map.of("ok", true));
    }

    @GetMapping("/invoices/export")
    public ResponseEntity<String> export(@AuthenticationPrincipal String userId) {
        UUID uid = UUID.fromString(userId);
        List<Invoice> list = invoiceMapper.selectList(
                new LambdaQueryWrapper<Invoice>().eq(Invoice::getUserId, uid).orderByDesc(Invoice::getCreatedAt));

        Map<String, String> catMap = categoryMapper.selectList(null).stream()
                .collect(Collectors.toMap(Category::getId, Category::getName));

        BigDecimal amountTotal = BigDecimal.ZERO;
        BigDecimal taxTotal = BigDecimal.ZERO;
        BigDecimal totalAmount = BigDecimal.ZERO;

        StringBuilder sb = new StringBuilder("\uFEFF");
        sb.append("发票编号,开票日期,发票号码,发票代码,销售方,购买方,发票类型,发票类别,不含税金额,税率(%),税额,价税合计,状态,备注\n");
        for (Invoice inv : list) {
            String catName = catMap.getOrDefault(inv.getCategoryId(), "");
            BigDecimal amount = money(inv.getAmount());
            BigDecimal taxAmount = money(inv.getTaxAmount());
            BigDecimal rowTotal = money(inv.getTotalAmount());
            amountTotal = amountTotal.add(amount);
            taxTotal = taxTotal.add(taxAmount);
            totalAmount = totalAmount.add(rowTotal);
            sb.append(String.join(",",
                    csv(inv.getId()),
                    csv(inv.getInvoiceDate()),
                    csv(inv.getInvoiceNumber()),
                    csv(inv.getInvoiceCode()),
                    csv(inv.getSeller()),
                    csv(inv.getBuyer()),
                    csv(inv.getType()),
                    csv(catName),
                    csv(amount),
                    csv(inv.getTaxRate()),
                    csv(taxAmount),
                    csv(rowTotal),
                    csv(statusLabel(inv.getStatus())),
                    csv(inv.getNotes())
            )).append("\n");
        }
        sb.append(String.join(",",
                csv("合计"),
                csv(""),
                csv(""),
                csv(""),
                csv(""),
                csv(""),
                csv(""),
                csv("共 " + list.size() + " 张"),
                csv(money(amountTotal)),
                csv(""),
                csv(money(taxTotal)),
                csv(money(totalAmount)),
                csv(""),
                csv("")
        )).append("\n");

        return ResponseEntity.ok()
                .header("Content-Type", "text/csv; charset=utf-8")
                .header("Content-Disposition", "attachment; filename=invoices.csv")
                .body(sb.toString());
    }
}
