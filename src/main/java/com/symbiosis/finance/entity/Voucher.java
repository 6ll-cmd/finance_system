package com.symbiosis.finance.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@TableName("vouchers")
public class Voucher {

    private String id;               // VCH-2026-0001
    private LocalDate voucherDate;
    private String description;
    private BigDecimal totalAmount;
    private String status;           // draft | posted | cancelled
    private String notes;
    private String responsiblePerson;
    private UUID userId;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
    private Integer deleted;

    // ── 关联分录（非表字段） ──
    private transient List<VoucherEntry> entries;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public LocalDate getVoucherDate() { return voucherDate; }
    public void setVoucherDate(LocalDate voucherDate) { this.voucherDate = voucherDate; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public BigDecimal getTotalAmount() { return totalAmount; }
    public void setTotalAmount(BigDecimal totalAmount) { this.totalAmount = totalAmount; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }

    public String getResponsiblePerson() { return responsiblePerson; }
    public void setResponsiblePerson(String responsiblePerson) { this.responsiblePerson = responsiblePerson; }

    public UUID getUserId() { return userId; }
    public void setUserId(UUID userId) { this.userId = userId; }

    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }

    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(OffsetDateTime updatedAt) { this.updatedAt = updatedAt; }

    public Integer getDeleted() { return deleted; }
    public void setDeleted(Integer deleted) { this.deleted = deleted; }

    public List<VoucherEntry> getEntries() { return entries; }
    public void setEntries(List<VoucherEntry> entries) { this.entries = entries; }
}
