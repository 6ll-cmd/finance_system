package com.symbiosis.finance.controller;

import com.symbiosis.finance.config.JwtUtil;
import com.symbiosis.finance.entity.User;
import com.symbiosis.finance.mapper.UserMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;
import org.springframework.beans.factory.annotation.Value;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api")
public class AuthController {

    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;

    @Value("${jwt.secret}")
    private String jwtSecret;

    public AuthController(UserMapper userMapper, PasswordEncoder passwordEncoder, JwtUtil jwtUtil) {
        this.userMapper = userMapper;
        this.passwordEncoder = passwordEncoder;
        this.jwtUtil = jwtUtil;
    }

    // ── 请求 DTO（内联，避免多文件） ──

    public static class RegisterRequest {
        @NotBlank @Size(min = 2, message = "用户名至少2位")
        public String username;

        @NotBlank @Size(min = 8, message = "密码至少8位")
        @Pattern(regexp = "^(?=.*[a-zA-Z])(?=.*\\d).+$", message = "密码需包含字母和数字")
        public String password;
    }

    public static class LoginRequest {
        @NotBlank public String username;
        @NotBlank public String password;
    }

    // ── POST /api/register ──
    @PostMapping("/register")
    public ResponseEntity<?> register(@Valid @RequestBody RegisterRequest req) {
        // 用户名唯一性
        long count = userMapper.selectCount(
                new LambdaQueryWrapper<User>().eq(User::getUsername, req.username));
        if (count > 0) {
            return ResponseEntity.status(409).body(Map.of("error", "用户名已存在"));
        }

        User user = new User();
        user.setId(UUID.randomUUID());
        user.setUsername(req.username);
        user.setPassword(passwordEncoder.encode(req.password));
        user.setRole("user");
        user.setCreatedAt(OffsetDateTime.now());
        userMapper.insert(user);

        // 签发 token
        String userId = user.getId().toString();
        String accessToken = jwtUtil.generateAccessToken(userId, user.getUsername(), user.getRole());
        String refreshToken = jwtUtil.generateRefreshToken(userId);

        // 存储 refresh token
        user.setToken(refreshToken);
        user.setTokenCreatedAt(OffsetDateTime.now());
        userMapper.updateById(user);

        return ResponseEntity.status(201).body(Map.of(
                "token", accessToken,
                "refreshToken", refreshToken,
                "username", user.getUsername(),
                "id", userId
        ));
    }

    // ── POST /api/login ──
    @PostMapping("/login")
    public ResponseEntity<?> login(@Valid @RequestBody LoginRequest req) {
        User user = userMapper.selectOne(
                new LambdaQueryWrapper<User>().eq(User::getUsername, req.username));

        if (user == null || !passwordEncoder.matches(req.password, user.getPassword())) {
            return ResponseEntity.status(401).body(Map.of("error", "用户名或密码错误"));
        }

        String userId = user.getId().toString();
        String accessToken = jwtUtil.generateAccessToken(userId, user.getUsername(), user.getRole());
        String refreshToken = jwtUtil.generateRefreshToken(userId);

        user.setToken(refreshToken);
        user.setTokenCreatedAt(OffsetDateTime.now());
        userMapper.updateById(user);

        return ResponseEntity.ok(Map.of(
                "token", accessToken,
                "refreshToken", refreshToken,
                "username", user.getUsername(),
                "id", userId
        ));
    }

    // ── GET /api/me ──
    @GetMapping("/me")
    public ResponseEntity<?> me(@AuthenticationPrincipal String userId) {
        User user = userMapper.selectById(UUID.fromString(userId));
        if (user == null) {
            return ResponseEntity.status(401).body(Map.of("error", "用户不存在"));
        }
        return ResponseEntity.ok(Map.of(
                "id", user.getId().toString(),
                "username", user.getUsername(),
                "role", user.getRole()
        ));
    }

    // POST /api/refresh - exchange refresh token for a new access token
    public static class RefreshRequest {
        @NotBlank public String refreshToken;
    }

    @PostMapping("/refresh")
    public ResponseEntity<?> refresh(@Valid @RequestBody RefreshRequest req) {
        if (!jwtUtil.validateToken(req.refreshToken)) {
            return ResponseEntity.status(401).body(Map.of("error", "refresh token invalid or expired"));
        }
        String userId = jwtUtil.getUserId(req.refreshToken);

        User user = userMapper.selectById(UUID.fromString(userId));
        if (user == null || user.getToken() == null || !user.getToken().equals(req.refreshToken)) {
            return ResponseEntity.status(401).body(Map.of("error", "refresh token mismatch"));
        }

        String accessToken = jwtUtil.generateAccessToken(userId, user.getUsername(), user.getRole());
        return ResponseEntity.ok(Map.of(
                "token", accessToken,
                "refreshToken", req.refreshToken
        ));
    }

    // ── PUT /api/operation-password ── set or change the per-user operation password
    public static class OperationPasswordRequest {
        @NotBlank public String currentPassword;   // login password, to authorize the change
        @NotBlank @Size(min = 4, message = "operation password at least 4 chars")
        public String newPassword;
    }

    @PutMapping("/operation-password")
    public ResponseEntity<?> setOperationPassword(@AuthenticationPrincipal String userId,
                                                  @Valid @RequestBody OperationPasswordRequest req) {
        User user = userMapper.selectById(UUID.fromString(userId));
        if (user == null) return ResponseEntity.status(404).body(Map.of("error", "user not found"));
        // verify login password to authorize changing the operation password
        if (!passwordEncoder.matches(req.currentPassword, user.getPassword())) {
            return ResponseEntity.status(403).body(Map.of("error", "login password incorrect"));
        }
        user.setOperationPassword(passwordEncoder.encode(req.newPassword));
        user.setOperationPasswordHint(encryptHint(req.newPassword));
        userMapper.updateById(user);
        return ResponseEntity.ok(Map.of("ok", true));
    }

    // ── POST /api/verify-operation-password ── used internally by frontend before sensitive actions
    public static class VerifyOperationPasswordRequest {
        @NotBlank public String password;
    }

    @PostMapping("/verify-operation-password")
    public ResponseEntity<?> verifyOperationPassword(@AuthenticationPrincipal String userId,
                                                     @Valid @RequestBody VerifyOperationPasswordRequest req) {
        User user = userMapper.selectById(UUID.fromString(userId));
        if (user == null) return ResponseEntity.status(404).body(Map.of("error", "user not found"));
        if (user.getOperationPassword() == null || user.getOperationPassword().isEmpty()) {
            return ResponseEntity.status(403).body(Map.of("error", "operation password not set"));
        }
        if (!passwordEncoder.matches(req.password, user.getOperationPassword())) {
            return ResponseEntity.status(403).body(Map.of("error", "operation password incorrect"));
        }
        return ResponseEntity.ok(Map.of("ok", true));
    }
    // GET /api/operation-password/status - whether the user has set an operation password
    @GetMapping("/operation-password/status")
    public ResponseEntity<?> operationPasswordStatus(@AuthenticationPrincipal String userId) {
        User user = userMapper.selectById(UUID.fromString(userId));
        if (user == null) return ResponseEntity.status(404).body(Map.of("error", "user not found"));
        boolean set = user.getOperationPassword() != null && !user.getOperationPassword().isEmpty();
        return ResponseEntity.ok(Map.of("set", set));
    }

    // POST /api/operation-password/reveal - return the plaintext op password after verifying login password
    public static class RevealRequest {
        @NotBlank public String loginPassword;
    }

    @PostMapping("/operation-password/reveal")
    public ResponseEntity<?> revealOperationPassword(@AuthenticationPrincipal String userId,
                                                     @Valid @RequestBody RevealRequest req) {
        User user = userMapper.selectById(UUID.fromString(userId));
        if (user == null) return ResponseEntity.status(404).body(Map.of("error", "user not found"));
        if (!passwordEncoder.matches(req.loginPassword, user.getPassword())) {
            return ResponseEntity.status(403).body(Map.of("error", "login password incorrect"));
        }
        if (user.getOperationPasswordHint() == null || user.getOperationPasswordHint().isEmpty()) {
            return ResponseEntity.status(404).body(Map.of("error", "operation password not set"));
        }
        String plain = decryptHint(user.getOperationPasswordHint());
        return ResponseEntity.ok(Map.of("password", plain != null ? plain : ""));
    }

    // AES encrypt/decrypt for the reversible hint. Key derived from the configured jwt secret.
    private String encryptHint(String plain) {
        try {
            javax.crypto.spec.SecretKeySpec key = hintKey();
            javax.crypto.Cipher c = javax.crypto.Cipher.getInstance("AES");
            c.init(javax.crypto.Cipher.ENCRYPT_MODE, key);
            byte[] enc = c.doFinal(plain.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return java.util.Base64.getEncoder().encodeToString(enc);
        } catch (Exception e) {
            return null;
        }
    }

    private String decryptHint(String b64) {
        try {
            javax.crypto.spec.SecretKeySpec key = hintKey();
            javax.crypto.Cipher c = javax.crypto.Cipher.getInstance("AES");
            c.init(javax.crypto.Cipher.DECRYPT_MODE, key);
            byte[] dec = c.doFinal(java.util.Base64.getDecoder().decode(b64));
            return new String(dec, java.nio.charset.StandardCharsets.UTF_8);
        } catch (Exception e) {
            return null;
        }
    }

    private javax.crypto.spec.SecretKeySpec hintKey() {
        String s = jwtSecret;
        if (s == null || s.length() < 16) s = "change-me-in-production-use-256-bit-minimum";
        byte[] k = s.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        byte[] key16 = new byte[16];
        System.arraycopy(k, 0, key16, 0, Math.min(16, k.length));
        return new javax.crypto.spec.SecretKeySpec(key16, "AES");
    }

}