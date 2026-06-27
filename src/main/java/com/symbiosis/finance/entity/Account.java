package com.symbiosis.finance.entity;

import com.baomidou.mybatisplus.annotation.TableName;

@TableName("accounts")
public class Account {

    private String id;
    private String name;
    private String parentId;
    private String accountType;
    private Integer level;
    private Integer sortOrder;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getParentId() { return parentId; }
    public void setParentId(String parentId) { this.parentId = parentId; }

    public String getAccountType() { return accountType; }
    public void setAccountType(String accountType) { this.accountType = accountType; }

    public Integer getLevel() { return level; }
    public void setLevel(Integer level) { this.level = level; }

    public Integer getSortOrder() { return sortOrder; }
    public void setSortOrder(Integer sortOrder) { this.sortOrder = sortOrder; }
}
