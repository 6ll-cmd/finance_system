package com.symbiosis.finance.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.symbiosis.finance.entity.Invoice;
import com.symbiosis.finance.entity.Account;
import com.symbiosis.finance.entity.User;
import com.symbiosis.finance.entity.Voucher;
import com.symbiosis.finance.entity.VoucherEntry;
import com.symbiosis.finance.mapper.AccountMapper;
import com.symbiosis.finance.mapper.InvoiceMapper;
import com.symbiosis.finance.mapper.UserMapper;
import com.symbiosis.finance.mapper.VoucherEntryMapper;
import com.symbiosis.finance.mapper.VoucherMapper;
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

@RestController
@RequestMapping("/api")
public class VoucherController {

    private final VoucherMapper voucherMapper;
    private final VoucherEntryMapper entryMapper;
    private final InvoiceMapper invoiceMapper;
    private final AccountMapper accountMapper;
    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;

    public VoucherController(VoucherMapper voucherMapper, VoucherEntryMapper entryMapper,
                             InvoiceMapper invoiceMapper, AccountMapper accountMapper,
                             UserMapper userMapper, PasswordEncoder passwordEncoder) {
        this.voucherMapper = voucherMapper;
        this.entryMapper = entryMapper;
        this.invoiceMapper = invoiceMapper;
        this.accountMapper = accountMapper;
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

    public static class EntryRequest {
        public String account;
        public BigDecimal debitAmount;
        public BigDecimal creditAmount;
        public String summary;
    }

    public static class CreateVoucherRequest {
        @NotNull public LocalDate voucherDate;
        @NotBlank public String description;
        public String notes;
        @NotEmpty public List<EntryRequest> entries;
        public String operationPassword;
    }

    public static class InvoiceVoucherRequest {
        public String invoiceId;
        public LocalDate date;
        public String seller;
        public String type;
        public String category;
        public BigDecimal amount;
        public BigDecimal taxAmount;
        public BigDecimal totalAmount;
        public String invoiceCode;
        public String invoiceNumber;
        public String notes;
    }

    public static class StatusRequest {
        @NotBlank public String status;
        public String operationPassword;
    }

    public static class DeleteRequest {
        public String operationPassword;
    }

    private Map<String, Object> toMap(Voucher v) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", v.getId());
        m.put("voucherDate", v.getVoucherDate() != null ? v.getVoucherDate().toString() : null);
        m.put("description", v.getDescription());
        m.put("totalAmount", v.getTotalAmount());
        m.put("status", v.getStatus());
        m.put("notes", v.getNotes());
        m.put("userId", v.getUserId() != null ? v.getUserId().toString() : null);
        m.put("createdAt", v.getCreatedAt() != null ? v.getCreatedAt().toString() : null);
        m.put("updatedAt", v.getUpdatedAt() != null ? v.getUpdatedAt().toString() : null);
        return m;
    }

    private Map<String, Object> toEntryMap(VoucherEntry e) {
        return toEntryMap(e, Collections.emptyMap());
    }

    private Map<String, Object> toEntryMap(VoucherEntry e, Map<String, String> accountNames) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("account", e.getAccount());
        m.put("accountName", accountNames.getOrDefault(e.getAccount(), ""));
        m.put("debitAmount", e.getDebitAmount());
        m.put("creditAmount", e.getCreditAmount());
        m.put("summary", e.getSummary());
        return m;
    }

    @GetMapping("/vouchers")
    public ResponseEntity<?> list(@AuthenticationPrincipal String userId,
                                  @RequestParam(required = false) String status,
                                  @RequestParam(required = false, defaultValue = "1") int page,
                                  @RequestParam(required = false, defaultValue = "20") int size) {
        UUID uid = UUID.fromString(userId);
        LambdaQueryWrapper<Voucher> countQw = new LambdaQueryWrapper<Voucher>().eq(Voucher::getUserId, uid);
        if (status != null && !status.equals("all")) countQw.eq(Voucher::getStatus, status);
        long count = voucherMapper.selectCount(countQw);

        LambdaQueryWrapper<Voucher> qw = new LambdaQueryWrapper<Voucher>().eq(Voucher::getUserId, uid).orderByDesc(Voucher::getCreatedAt);
        if (status != null && !status.equals("all")) qw.eq(Voucher::getStatus, status);
        qw.last("LIMIT " + size + " OFFSET " + (Math.max(0, page - 1) * size));
        List<Voucher> list = voucherMapper.selectList(qw);
        Map<String, String> accountNames = new HashMap<>();
        for (Account account : accountMapper.selectList(null)) {
            accountNames.put(account.getId(), account.getName());
        }
        List<Map<String, Object>> data = new ArrayList<>();
        for (Voucher voucher : list) {
            Map<String, Object> row = toMap(voucher);
            List<VoucherEntry> entries = entryMapper.selectList(
                    new LambdaQueryWrapper<VoucherEntry>().eq(VoucherEntry::getVoucherId, voucher.getId())
                            .orderByAsc(VoucherEntry::getSortOrder).orderByAsc(VoucherEntry::getId));
            row.put("entries", entries.stream().map(entry -> toEntryMap(entry, accountNames)).toList());
            data.add(row);
        }
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("data", data);
        resp.put("total", count);
        resp.put("page", page);
        resp.put("size", size);
        return ResponseEntity.ok(resp);
    }

    @GetMapping("/vouchers/{id}")
    public ResponseEntity<?> get(@AuthenticationPrincipal String userId, @PathVariable String id) {
        UUID uid = UUID.fromString(userId);
        Voucher v = voucherMapper.selectOne(
                new LambdaQueryWrapper<Voucher>().eq(Voucher::getId, id).eq(Voucher::getUserId, uid));
        if (v == null) return ResponseEntity.status(404).body(Map.of("error", "voucher not found"));
        Map<String, Object> resp = toMap(v);
        List<VoucherEntry> entries = entryMapper.selectList(
                new LambdaQueryWrapper<VoucherEntry>().eq(VoucherEntry::getVoucherId, id));
        Map<String, String> accountNames = new HashMap<>();
        for (Account account : accountMapper.selectList(null)) {
            accountNames.put(account.getId(), account.getName());
        }
        resp.put("entries", entries.stream().map(entry -> toEntryMap(entry, accountNames)).toList());
        return ResponseEntity.ok(resp);
    }

    private void validateBalance(List<EntryRequest> entries) {
        BigDecimal debit = BigDecimal.ZERO, credit = BigDecimal.ZERO;
        for (EntryRequest e : entries) {
            debit = debit.add(e.debitAmount != null ? e.debitAmount : BigDecimal.ZERO);
            credit = credit.add(e.creditAmount != null ? e.creditAmount : BigDecimal.ZERO);
        }
        if (debit.compareTo(credit) != 0) {
            throw new IllegalArgumentException(String.format("imbalanced: debit %s credit %s", debit, credit));
        }
    }

    private void saveEntries(String voucherId, List<EntryRequest> entries) {
        for (EntryRequest e : entries) {
            VoucherEntry entry = new VoucherEntry();
            entry.setVoucherId(voucherId);
            entry.setAccount(e.account);
            entry.setDebitAmount(e.debitAmount != null ? e.debitAmount : BigDecimal.ZERO);
            entry.setCreditAmount(e.creditAmount != null ? e.creditAmount : BigDecimal.ZERO);
            entry.setSummary(e.summary != null ? e.summary : "");
            entryMapper.insert(entry);
        }
    }

    private BigDecimal money(BigDecimal value) {
        return value != null ? value : BigDecimal.ZERO;
    }

    private String expenseAccount(String category, String type, String seller, String notes) {
        String haystack = String.join(" ",
                category != null ? category : "",
                type != null ? type : "",
                seller != null ? seller : "",
                notes != null ? notes : "").toLowerCase(Locale.ROOT);
        if (haystack.contains("travel")) return "5001002";
        if (haystack.contains("catering")) return "5001003";
        if (haystack.contains("utility")) return "5001004";
        if (haystack.contains("rental")) return "5001006";
        if (haystack.contains("service")) return "5001007";
        if (haystack.contains("logistics")) return "5001008";
        return "5001001";
    }

    private Map<String, Object> createVoucherFromInvoiceData(UUID uid, InvoiceVoucherRequest req) {
        BigDecimal amount = money(req.amount);
        BigDecimal tax = money(req.taxAmount);
        BigDecimal total = money(req.totalAmount);
        if (total.compareTo(BigDecimal.ZERO) <= 0) total = amount.add(tax);
        if (amount.compareTo(BigDecimal.ZERO) <= 0) amount = total.subtract(tax);
        if (amount.compareTo(BigDecimal.ZERO) <= 0 || total.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("invoice amount insufficient to generate voucher");
        }
        String summary = "invoice: " + (req.seller != null && !req.seller.isBlank() ? req.seller : "unknown seller");
        String expenseAccount = expenseAccount(req.category, req.type, req.seller, req.notes);

        List<EntryRequest> entries = new ArrayList<>();
        EntryRequest expense = new EntryRequest();
        expense.account = expenseAccount;
        expense.debitAmount = amount;
        expense.creditAmount = BigDecimal.ZERO;
        expense.summary = summary;
        entries.add(expense);

        if (tax.compareTo(BigDecimal.ZERO) > 0) {
            EntryRequest taxEntry = new EntryRequest();
            taxEntry.account = "2002004";
            taxEntry.debitAmount = tax;
            taxEntry.creditAmount = BigDecimal.ZERO;
            taxEntry.summary = "input tax: " + summary;
            entries.add(taxEntry);
        }

        EntryRequest payable = new EntryRequest();
        payable.account = "2002001";
        payable.debitAmount = BigDecimal.ZERO;
        payable.creditAmount = total;
        payable.summary = summary;
        entries.add(payable);

        validateBalance(entries);

        int year = Year.now().getValue();
        String lastId = voucherMapper.findLastIdByYear(year);
        int next = 1;
        if (lastId != null) {
            try { next = Integer.parseInt(lastId.split("-")[2]) + 1; } catch (Exception ignored) {}
        }
        String newId = String.format("VCH-%d-%04d", year, next);

        String number = req.invoiceNumber != null && !req.invoiceNumber.isBlank() ? " (invoice no: " + req.invoiceNumber + ")" : "";
        Voucher v = new Voucher();
        v.setId(newId);
        v.setVoucherDate(req.date != null ? req.date : LocalDate.now());
        v.setDescription("voucher from invoice: " + (req.seller != null ? req.seller : "unknown seller") + number);
        v.setTotalAmount(total);
        v.setStatus("draft");
        v.setNotes("AI OCR generated, please review manually." + (req.invoiceId != null ? " from invoice: " + req.invoiceId : ""));
        v.setUserId(uid);
        v.setCreatedAt(OffsetDateTime.now());
        v.setUpdatedAt(OffsetDateTime.now());
        voucherMapper.insert(v);
        saveEntries(newId, entries);

        Map<String, Object> resp = toMap(v);
        resp.put("entries", entries.stream().map(e -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("account", e.account);
            m.put("debitAmount", e.debitAmount);
            m.put("creditAmount", e.creditAmount);
            m.put("summary", e.summary);
            return m;
        }).toList());
        return resp;
    }

    @PostMapping("/vouchers")
    @Transactional
    public ResponseEntity<?> create(@AuthenticationPrincipal String userId,
                                    @Valid @RequestBody CreateVoucherRequest req) {
        try { validateBalance(req.entries); } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
        UUID uid = UUID.fromString(userId);
        int year = Year.now().getValue();

        String lastId = voucherMapper.findLastIdByYear(year);
        int next = 1;
        if (lastId != null) {
            try { next = Integer.parseInt(lastId.split("-")[2]) + 1; } catch (Exception ignored) {}
        }
        String newId = String.format("VCH-%d-%04d", year, next);

        BigDecimal total = req.entries.stream()
                .map(e -> e.debitAmount != null ? e.debitAmount : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        Voucher v = new Voucher();
        v.setId(newId);
        v.setVoucherDate(req.voucherDate);
        v.setDescription(req.description);
        v.setTotalAmount(total);
        v.setStatus("draft");
        v.setNotes(req.notes != null ? req.notes : "");
        v.setUserId(uid);
        v.setCreatedAt(OffsetDateTime.now());
        v.setUpdatedAt(OffsetDateTime.now());
        voucherMapper.insert(v);
        saveEntries(newId, req.entries);

        Map<String, Object> resp = toMap(v);
        resp.put("entries", req.entries.stream().map(e -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("account", e.account);
            m.put("debitAmount", e.debitAmount);
            m.put("creditAmount", e.creditAmount);
            m.put("summary", e.summary);
            return m;
        }).toList());
        return ResponseEntity.status(201).body(resp);
    }

    @PostMapping("/vouchers/from-invoice")
    @Transactional
    public ResponseEntity<?> fromInvoice(@AuthenticationPrincipal String userId,
                                         @Valid @RequestBody InvoiceVoucherRequest req) {
        UUID uid = UUID.fromString(userId);
        Map<String, Object> resp;
        try {
            resp = createVoucherFromInvoiceData(uid, req);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }

        if (req.invoiceId != null && !req.invoiceId.isBlank()) {
            Invoice inv = invoiceMapper.selectOne(
                    new LambdaQueryWrapper<Invoice>().eq(Invoice::getId, req.invoiceId).eq(Invoice::getUserId, uid));
            if (inv != null) {
                inv.setStatus("reimbursed");
                inv.setUpdatedAt(OffsetDateTime.now());
                invoiceMapper.updateById(inv);
            }
        }
        return ResponseEntity.status(201).body(resp);
    }

    @PutMapping("/vouchers/{id}")
    @Transactional
    public ResponseEntity<?> update(@AuthenticationPrincipal String userId,
                                    @PathVariable String id,
                                    @Valid @RequestBody CreateVoucherRequest req) {
        UUID uid = UUID.fromString(userId);
        Voucher v = voucherMapper.selectOne(
                new LambdaQueryWrapper<Voucher>().eq(Voucher::getId, id).eq(Voucher::getUserId, uid));
        if (v == null) return ResponseEntity.status(404).body(Map.of("error", "voucher not found"));

        if (!"draft".equals(v.getStatus())) {
            ResponseEntity<?> denied = checkOperationPassword(userId, req.operationPassword);
            if (denied != null) return denied;
        }

        try { validateBalance(req.entries); } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }

        BigDecimal total = req.entries.stream()
                .map(e -> e.debitAmount != null ? e.debitAmount : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        v.setVoucherDate(req.voucherDate);
        v.setDescription(req.description);
        v.setTotalAmount(total);
        v.setNotes(req.notes != null ? req.notes : "");
        v.setUpdatedAt(OffsetDateTime.now());
        voucherMapper.updateById(v);

        entryMapper.delete(new LambdaQueryWrapper<VoucherEntry>().eq(VoucherEntry::getVoucherId, id));
        saveEntries(id, req.entries);

        return ResponseEntity.ok(toMap(v));
    }

    @PatchMapping("/vouchers/{id}/status")
    public ResponseEntity<?> updateStatus(@AuthenticationPrincipal String userId,
                                          @PathVariable String id,
                                          @Valid @RequestBody StatusRequest req) {
        if (!Set.of("draft", "posted", "cancelled").contains(req.status)) {
            return ResponseEntity.badRequest().body(Map.of("error", "invalid status"));
        }
        UUID uid = UUID.fromString(userId);
        Voucher v = voucherMapper.selectOne(
                new LambdaQueryWrapper<Voucher>().eq(Voucher::getId, id).eq(Voucher::getUserId, uid));
        if (v == null) return ResponseEntity.status(404).body(Map.of("error", "voucher not found"));

        ResponseEntity<?> denied = checkOperationPassword(userId, req.operationPassword);
        if (denied != null) return denied;

        v.setStatus(req.status);
        v.setUpdatedAt(OffsetDateTime.now());
        voucherMapper.updateById(v);

        return ResponseEntity.ok(toMap(v));
    }

    @DeleteMapping("/vouchers/{id}")
    @Transactional
    public ResponseEntity<?> delete(@AuthenticationPrincipal String userId, @PathVariable String id,
                                    @RequestBody(required = false) DeleteRequest req) {
        UUID uid = UUID.fromString(userId);
        Voucher v = voucherMapper.selectOne(
                new LambdaQueryWrapper<Voucher>().eq(Voucher::getId, id).eq(Voucher::getUserId, uid));
        if (v == null) return ResponseEntity.status(404).body(Map.of("error", "voucher not found"));

        ResponseEntity<?> denied = checkOperationPassword(userId, req != null ? req.operationPassword : null);
        if (denied != null) return denied;

        entryMapper.delete(new LambdaQueryWrapper<VoucherEntry>().eq(VoucherEntry::getVoucherId, id));
        voucherMapper.deleteById(id);

        return ResponseEntity.ok(Map.of("ok", true));
    }
}
