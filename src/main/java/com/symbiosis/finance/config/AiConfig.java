package com.symbiosis.finance.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "ai")
public class AiConfig {

    private String provider = "openai";
    private String apiKey = "";
    private String model = "gpt-4o";
    private String baseUrl = "https://api.openai.com/v1";
    private int maxTokens = 800;

    public String getProvider() { return provider; }
    public void setProvider(String provider) { this.provider = provider; }

    public String getApiKey() { return apiKey; }
    public void setApiKey(String apiKey) { this.apiKey = apiKey; }

    public String getModel() { return model; }
    public void setModel(String model) { this.model = model; }

    public String getBaseUrl() { return baseUrl; }
    public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }

    public int getMaxTokens() { return maxTokens; }
    public void setMaxTokens(int maxTokens) { this.maxTokens = maxTokens; }

    public boolean isConfigured() {
        return apiKey != null && !apiKey.isBlank();
    }
}
