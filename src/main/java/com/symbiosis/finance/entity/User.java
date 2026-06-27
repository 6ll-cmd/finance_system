package com.symbiosis.finance.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import java.time.OffsetDateTime;
import java.util.UUID;

@TableName("users")
public class User {

    private UUID id;                   // 主键 UUID
    private String username;
    private String password;
    private String role;
    private String token;              // refresh token
    private String operationPassword;  // operation password for sensitive actions
    private String operationPasswordHint;  // reversible-encrypted op password (for reveal)
    private OffsetDateTime tokenCreatedAt;
    private OffsetDateTime createdAt;

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }

    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }

    public String getOperationPassword() { return operationPassword; }
    public void setOperationPassword(String operationPassword) { this.operationPassword = operationPassword; }

    public String getOperationPasswordHint() { return operationPasswordHint; }
    public void setOperationPasswordHint(String operationPasswordHint) { this.operationPasswordHint = operationPasswordHint; }

    public String getToken() { return token; }
    public void setToken(String token) { this.token = token; }

    public OffsetDateTime getTokenCreatedAt() { return tokenCreatedAt; }
    public void setTokenCreatedAt(OffsetDateTime tokenCreatedAt) { this.tokenCreatedAt = tokenCreatedAt; }

    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
}
