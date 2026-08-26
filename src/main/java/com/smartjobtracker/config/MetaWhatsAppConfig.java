package com.smartjobtracker.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.whatsapp")
public class MetaWhatsAppConfig {
    private boolean enabled;
    private String phoneNumberId = "";
    private String accessToken = "";
    private String appSecret = "";
    private String verifyToken = "";
    private String apiVersion = "v20.0";
    private int maxRetries = 5;

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public String getPhoneNumberId() { return phoneNumberId; }
    public void setPhoneNumberId(String phoneNumberId) { this.phoneNumberId = phoneNumberId; }
    public String getAccessToken() { return accessToken; }
    public void setAccessToken(String accessToken) { this.accessToken = accessToken; }
    public String getAppSecret() { return appSecret; }
    public void setAppSecret(String appSecret) { this.appSecret = appSecret; }
    public String getVerifyToken() { return verifyToken; }
    public void setVerifyToken(String verifyToken) { this.verifyToken = verifyToken; }
    public String getApiVersion() { return apiVersion; }
    public void setApiVersion(String apiVersion) { this.apiVersion = apiVersion; }
    public int getMaxRetries() { return maxRetries; }
    public void setMaxRetries(int maxRetries) { this.maxRetries = maxRetries; }
}