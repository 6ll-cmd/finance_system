package com.symbiosis.finance.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import java.math.BigDecimal;

@TableName("voucher_entries")
public class VoucherEntry {

    private Long id;
    private String voucherId;
    private String account;           // 会计科目编号
    private BigDecimal debitAmount;
    private BigDecimal creditAmount;
    private String summary;
    private Integer sortOrder;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getVoucherId() { return voucherId; }
    public void setVoucherId(String voucherId) { this.voucherId = voucherId; }

    public String getAccount() { return account; }
    public void setAccount(String account) { this.account = account; }

    public BigDecimal getDebitAmount() { return debitAmount; }
    public void setDebitAmount(BigDecimal debitAmount) { this.debitAmount = debitAmount; }

    public BigDecimal getCreditAmount() { return creditAmount; }
    public void setCreditAmount(BigDecimal creditAmount) { this.creditAmount = creditAmount; }

    public String getSummary() { return summary; }
    public void setSummary(String summary) { this.summary = summary; }

    public Integer getSortOrder() { return sortOrder; }
    public void setSortOrder(Integer sortOrder) { this.sortOrder = sortOrder; }
}
