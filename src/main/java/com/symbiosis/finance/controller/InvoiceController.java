package com.symbiosis.finance.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.symbiosis.finance.entity.Category;
import com.symbiosis.finance.entity.User;
import com.symbiosis.finance.entity.Invoice;
import com.symbiosis.finance.mapper.CategoryMapper;
import com.symbiosis.finance.mapper.InvoiceMapper;
import com.symbiosis.finance.mapper.UserMapper;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.Year;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api")
public class InvoiceController {

    private final InvoiceMapper invoiceMapper;
    private final CategoryMapper categoryMapper;
    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;

    public InvoiceController(InvoiceMapper invoiceMapper, CategoryMapper categoryMapper,
                             UserMapper userMapper, PasswordEncoder passwordEncoder) {
        this.invoiceMapper = invoiceMapper;
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
        @NotBlank public String seller;
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
        @NotNull public BigDecimal amount;
        public BigDecimal taxRate;
        public BigDecimal taxAmount;
        public BigDecimal totalAmount;
        public String totalAmountCn;
        public String notes;
        public String invoiceCode;
        public String invoiceNumber;
        public LocalDate date;
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
        UUID uid = UUID.fromString(userId);
        LambdaQueryWrapper<Invoice> countQw = new LambdaQueryWrapper<>();
        countQw.eq(Invoice::getUserId, uid);
        if (search != null && !search.isBlank()) {
            countQw.and(w -> w.like(Invoice::getId, search).or().like(Invoice::getSeller, search).or().like(Invoice::getNotes, search).or().like(Invoice::getBuyer, search));
        }
        if (status != null && !status.equals("all")) countQw.eq(Invoice::getStatus, status);
        if (category != null && !category.equals("all")) countQw.eq(Invoice::getCategoryId, category);
        long total = invoiceMapper.selectCount(countQw);

        LambdaQueryWrapper<Invoice> qw = new LambdaQueryWrapper<>();
        qw.eq(Invoice::getUserId, uid);
        if (search != null && !search.isBlank()) {
            qw.and(w -> w.like(Invoice::getId, search).or().like(Invoice::getSeller, search).or().like(Invoice::getNotes, search).or().like(Invoice::getBuyer, search));
        }
        if (status != null && !status.equals("all")) qw.eq(Invoice::getStatus, status);
        if (category != null && !category.equals("all")) qw.eq(Invoice::getCategoryId, category);

        if ("date-asc".equals(sort)) qw.orderByAsc(Invoice::getInvoiceDate);
        else if ("date-desc".equals(sort)) qw.orderByDesc(Invoice::getInvoiceDate);
        else if ("amount-desc".equals(sort)) qw.orderByDesc(Invoice::getTotalAmount);
        else if ("amount-asc".equals(sort)) qw.orderByAsc(Invoice::getTotalAmount);
        else qw.orderByDesc(Invoice::getCreatedAt);

        int offset = (page - 1) * limit;
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
    public ResponseEntity<?> create(@AuthenticationPrincipal String userId,
                                    @Valid @RequestBody CreateInvoiceRequest req) {
        UUID uid = UUID.fromString(userId);
        int year = Year.now().getValue();

        String lastId = invoiceMapper.findLastIdByYear(year);
        int next = 1;
        if (lastId != null) {
            try {
                next = Integer.parseInt(lastId.split("-")[2]) + 1;
            } catch (Exception ignored) {}
        }
        String newId = String.format("INV-%d-%04d", year, next);

        Invoice inv = new Invoice();
        inv.setId(newId);
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
        inv.setCategoryId(req.category != null ? req.category : "other");
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
        inv.setInvoiceCode(req.invoiceCode != null ? req.invoiceCode : "");
        inv.setInvoiceNumber(req.invoiceNumber != null ? req.invoiceNumber : "");
        inv.setUserId(uid);
        inv.setCreatedAt(OffsetDateTime.now());
        inv.setUpdatedAt(OffsetDateTime.now());
        invoiceMapper.insert(inv);

        Category cat = categoryMapper.selectById(inv.getCategoryId());
        if (cat != null) inv.setCategoryName(cat.getName());

        return ResponseEntity.status(201).body(toInvoiceMap(inv));
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

        StringBuilder sb = new StringBuilder();
        sb.append("Invoice No,Date,Seller,Buyer,Type,Category,Amount(ex-tax),TaxRate%,TaxAmount,TotalAmount,Status,Notes\n");
        for (Invoice inv : list) {
            String statusEN = switch (inv.getStatus()) {
                case "pending" -> "pending";
                case "reimbursed" -> "reimbursed";
                case "rejected" -> "rejected";
                default -> inv.getStatus();
            };
            String catName = catMap.getOrDefault(inv.getCategoryId(), "");
            String notes = (inv.getNotes() != null ? inv.getNotes().replace("\"", "\"\"") : "");
            sb.append(String.format("%s,%s,\"%s\",\"%s\",%s,%s,%s,%s,%s,%s,%s,\"%s\"\n",
                    inv.getId(), inv.getInvoiceDate(), inv.getSeller(), inv.getBuyer(),
                    inv.getType(), catName, inv.getAmount(), inv.getTaxRate(),
                    inv.getTaxAmount(), inv.getTotalAmount(), statusEN, notes));
        }

        return ResponseEntity.ok()
                .header("Content-Type", "text/csv; charset=utf-8")
                .header("Content-Disposition", "attachment; filename=invoices.csv")
                .body(sb.toString());
    }
}
