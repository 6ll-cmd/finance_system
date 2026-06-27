package com.symbiosis.finance.entity;

import com.baomidou.mybatisplus.annotation.TableName;

@TableName("categories")
public class Category {

    private String id;
    private String name;
    private String icon;
    private Integer sortOrder;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getIcon() { return icon; }
    public void setIcon(String icon) { this.icon = icon; }

    public Integer getSortOrder() { return sortOrder; }
    public void setSortOrder(Integer sortOrder) { this.sortOrder = sortOrder; }
}
