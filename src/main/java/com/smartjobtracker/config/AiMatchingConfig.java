package com.smartjobtracker.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.ai-matching")
public class AiMatchingConfig {
    private String provider = "fallback";
    private String apiKey = "";
    private String model = "gemini-embedding-001";
    private String endpoint = "https://generativelanguage.googleapis.com/v1beta/models";
    public String getProvider() { return provider; }
    public void setProvider(String value) { provider = value; }
    public String getApiKey() { return apiKey; }
    public void setApiKey(String value) { apiKey = value; }
    public String getModel() { return model; }
    public void setModel(String value) { model = value; }
    public String getEndpoint() { return endpoint; }
    public void setEndpoint(String value) { endpoint = value; }
}