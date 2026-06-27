package com.symbiosis.finance.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

@TableName("invoices")
public class Invoice {

    private String id;
    private LocalDate invoiceDate;
    private String seller;
    private String buyer;
    private String buyerTaxNo;
    private String buyerAddressPhone;
    private String buyerBankAccount;
    private String sellerTaxNo;
    private String sellerAddressPhone;
    private String sellerBankAccount;
    private String type;
    private String categoryId;
    private String itemName;
    private String itemSpec;
    private String itemUnit;
    private BigDecimal itemQuantity;
    private BigDecimal itemUnitPrice;
    private BigDecimal amount;
    private BigDecimal taxRate;
    private BigDecimal taxAmount;
    private BigDecimal totalAmount;
    private String totalAmountCn;
    private String status;
    private String notes;
    private String invoiceCode;
    private String invoiceNumber;
    private String imagePath;
    private UUID userId;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
    private Integer deleted;

    private transient String categoryName;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public LocalDate getInvoiceDate() { return invoiceDate; }
    public void setInvoiceDate(LocalDate invoiceDate) { this.invoiceDate = invoiceDate; }

    public String getSeller() { return seller; }
    public void setSeller(String seller) { this.seller = seller; }

    public String getBuyer() { return buyer; }
    public void setBuyer(String buyer) { this.buyer = buyer; }

    public String getBuyerTaxNo() { return buyerTaxNo; }
    public void setBuyerTaxNo(String buyerTaxNo) { this.buyerTaxNo = buyerTaxNo; }

    public String getBuyerAddressPhone() { return buyerAddressPhone; }
    public void setBuyerAddressPhone(String buyerAddressPhone) { this.buyerAddressPhone = buyerAddressPhone; }

    public String getBuyerBankAccount() { return buyerBankAccount; }
    public void setBuyerBankAccount(String buyerBankAccount) { this.buyerBankAccount = buyerBankAccount; }

    public String getSellerTaxNo() { return sellerTaxNo; }
    public void setSellerTaxNo(String sellerTaxNo) { this.sellerTaxNo = sellerTaxNo; }

    public String getSellerAddressPhone() { return sellerAddressPhone; }
    public void setSellerAddressPhone(String sellerAddressPhone) { this.sellerAddressPhone = sellerAddressPhone; }

    public String getSellerBankAccount() { return sellerBankAccount; }
    public void setSellerBankAccount(String sellerBankAccount) { this.sellerBankAccount = sellerBankAccount; }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public String getCategoryId() { return categoryId; }
    public void setCategoryId(String categoryId) { this.categoryId = categoryId; }

    public String getItemName() { return itemName; }
    public void setItemName(String itemName) { this.itemName = itemName; }

    public String getItemSpec() { return itemSpec; }
    public void setItemSpec(String itemSpec) { this.itemSpec = itemSpec; }

    public String getItemUnit() { return itemUnit; }
    public void setItemUnit(String itemUnit) { this.itemUnit = itemUnit; }

    public BigDecimal getItemQuantity() { return itemQuantity; }
    public void setItemQuantity(BigDecimal itemQuantity) { this.itemQuantity = itemQuantity; }

    public BigDecimal getItemUnitPrice() { return itemUnitPrice; }
    public void setItemUnitPrice(BigDecimal itemUnitPrice) { this.itemUnitPrice = itemUnitPrice; }

    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }

    public BigDecimal getTaxRate() { return taxRate; }
    public void setTaxRate(BigDecimal taxRate) { this.taxRate = taxRate; }

    public BigDecimal getTaxAmount() { return taxAmount; }
    public void setTaxAmount(BigDecimal taxAmount) { this.taxAmount = taxAmount; }

    public BigDecimal getTotalAmount() { return totalAmount; }
    public void setTotalAmount(BigDecimal totalAmount) { this.totalAmount = totalAmount; }

    public String getTotalAmountCn() { return totalAmountCn; }
    public void setTotalAmountCn(String totalAmountCn) { this.totalAmountCn = totalAmountCn; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }

    public String getInvoiceCode() { return invoiceCode; }
    public void setInvoiceCode(String invoiceCode) { this.invoiceCode = invoiceCode; }

    public String getInvoiceNumber() { return invoiceNumber; }
    public void setInvoiceNumber(String invoiceNumber) { this.invoiceNumber = invoiceNumber; }

    public String getImagePath() { return imagePath; }
    public void setImagePath(String imagePath) { this.imagePath = imagePath; }

    public UUID getUserId() { return userId; }
    public void setUserId(UUID userId) { this.userId = userId; }

    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }

    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(OffsetDateTime updatedAt) { this.updatedAt = updatedAt; }

    public Integer getDeleted() { return deleted; }
    public void setDeleted(Integer deleted) { this.deleted = deleted; }

    public String getCategoryName() { return categoryName; }
    public void setCategoryName(String categoryName) { this.categoryName = categoryName; }
}
