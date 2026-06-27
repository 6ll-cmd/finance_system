package com.symbiosis.finance.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.symbiosis.finance.config.AiConfig;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class AiSettingsService {

    private final AiConfig config;
    private final ObjectMapper objectMapper;
    private final Path configPath;

    public AiSettingsService(AiConfig config) {
        this.config = config;
        this.objectMapper = new ObjectMapper();
        this.configPath = Path.of(System.getProperty("user.dir"), "data", "ai-config.json");
    }

    @PostConstruct
    public void load() {
        if (!Files.exists(configPath)) return;
        try {
            Settings saved = objectMapper.readValue(configPath.toFile(), Settings.class);
            apply(saved, false);
        } catch (IOException ignored) {
            // Keep environment/application.yml values when the local config cannot be read.
        }
    }

    public synchronized Map<String, Object> publicConfig() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("provider", config.getProvider());
        m.put("baseUrl", config.getBaseUrl());
        m.put("model", config.getModel());
        m.put("configured", config.isConfigured());
        return m;
    }

    public synchronized Map<String, Object> save(Settings incoming) throws IOException {
        Settings merged = new Settings();
        merged.provider = valueOr(incoming.provider, config.getProvider());
        merged.baseUrl = valueOr(incoming.baseUrl, config.getBaseUrl());
        merged.model = valueOr(incoming.model, config.getModel());
        merged.apiKey = valueOr(incoming.apiKey, config.getApiKey());

        apply(merged, true);
        Files.createDirectories(configPath.getParent());
        objectMapper.writerWithDefaultPrettyPrinter().writeValue(configPath.toFile(), merged);
        return publicConfig();
    }

    private void apply(Settings settings, boolean preserveExistingKey) {
        config.setProvider(valueOr(settings.provider, config.getProvider()));
        config.setBaseUrl(valueOr(settings.baseUrl, config.getBaseUrl()));
        config.setModel(valueOr(settings.model, config.getModel()));
        if (settings.apiKey != null && !settings.apiKey.isBlank()) {
            config.setApiKey(settings.apiKey.trim());
        } else if (!preserveExistingKey) {
            config.setApiKey(valueOr(settings.apiKey, config.getApiKey()));
        }
    }

    private String valueOr(String value, String fallback) {
        return value != null && !value.isBlank() ? value.trim() : fallback;
    }

    public static class Settings {
        public String provider;
        public String baseUrl;
        public String model;
        public String apiKey;
    }
}
