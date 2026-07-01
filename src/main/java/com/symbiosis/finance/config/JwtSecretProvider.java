package com.symbiosis.finance.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.util.Base64;

@Component
public class JwtSecretProvider {

    private static final String DEFAULT_SECRET = "change-me-in-production-use-256-bit-minimum";
    private static final int MIN_SECRET_LENGTH = 32;
    private static final Path LOCAL_SECRET_PATH = Path.of("data", "jwt-secret.key");

    private final String secret;

    public JwtSecretProvider(@Value("${jwt.secret:}") String configuredSecret) {
        this.secret = resolveSecret(configuredSecret);
    }

    public String secret() {
        return secret;
    }

    private String resolveSecret(String configuredSecret) {
        String cleanSecret = configuredSecret == null ? "" : configuredSecret.trim();
        if (!cleanSecret.isBlank() && !DEFAULT_SECRET.equals(cleanSecret)) {
            if (cleanSecret.length() < MIN_SECRET_LENGTH) {
                throw new IllegalStateException("JWT_SECRET must be at least 32 characters.");
            }
            return cleanSecret;
        }
        return localMachineSecret();
    }

    private String localMachineSecret() {
        try {
            Files.createDirectories(LOCAL_SECRET_PATH.getParent());
            if (Files.exists(LOCAL_SECRET_PATH)) {
                String existing = Files.readString(LOCAL_SECRET_PATH, StandardCharsets.UTF_8).trim();
                if (existing.length() >= MIN_SECRET_LENGTH) {
                    return existing;
                }
            }
            String generated = generateSecret();
            Files.writeString(LOCAL_SECRET_PATH, generated, StandardCharsets.UTF_8);
            return generated;
        } catch (IOException e) {
            throw new IllegalStateException("Unable to initialize local JWT secret.", e);
        }
    }

    private String generateSecret() {
        byte[] bytes = new byte[64];
        new SecureRandom().nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
