package com.smartjobtracker.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.net.URI;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

@ConfigurationProperties(prefix="app.gmail")
public class GmailConfig {
    private String clientId=""; private String clientSecret=""; private String redirectUri="http://localhost:8080/api/gmail/callback";
    private String encryptionKey=""; private boolean enabled=false;
    private String classificationProvider="fallback";
    private String classificationApiKey="";
    private String classificationModel="gemini-3.6-flash";
    private String classificationEndpoint="https://generativelanguage.googleapis.com/v1beta/models";
    private double classificationMinConfidence=0.80;
    private int classificationMaxRetries=2;
    public String getClientId(){return clientId;} public void setClientId(String v){clientId=v;}
    public String getClientSecret(){return clientSecret;} public void setClientSecret(String v){clientSecret=v;}
    public String getRedirectUri(){return redirectUri;} public void setRedirectUri(String v){redirectUri=v;}
    public String getEncryptionKey(){return encryptionKey;} public void setEncryptionKey(String v){encryptionKey=v;}
    public boolean isEnabled(){return enabled;} public void setEnabled(boolean v){enabled=v;}
    public String getClassificationProvider(){return classificationProvider;} public void setClassificationProvider(String v){classificationProvider=v;}
    public String getClassificationApiKey(){return classificationApiKey;} public void setClassificationApiKey(String v){classificationApiKey=v;}
    public String getClassificationModel(){return classificationModel;} public void setClassificationModel(String v){classificationModel=v;}
    public String getClassificationEndpoint(){return classificationEndpoint;} public void setClassificationEndpoint(String v){classificationEndpoint=v;}
    public double getClassificationMinConfidence(){return classificationMinConfidence;} public void setClassificationMinConfidence(double v){classificationMinConfidence=v;}
    public int getClassificationMaxRetries(){return classificationMaxRetries;} public void setClassificationMaxRetries(int v){classificationMaxRetries=v;}

    /**
     * Validates the Gmail deployment configuration and returns a safe, non-secret message
     * describing the first problem found, or {@code null} if configuration is complete and
     * usable. Never includes secret values (client secret, encryption key) in the message —
     * only the names of the settings that need attention.
     */
    public String configurationError() {
        if (!enabled) {
            return "Gmail integration is disabled. Set GMAIL_ENABLED=true to turn it on.";
        }
        List<String> missing = new ArrayList<>();
        if (clientId == null || clientId.isBlank()) missing.add("GOOGLE_CLIENT_ID");
        if (clientSecret == null || clientSecret.isBlank()) missing.add("GOOGLE_CLIENT_SECRET");
        if (redirectUri == null || redirectUri.isBlank()) missing.add("GMAIL_REDIRECT_URI");
        if (encryptionKey == null || encryptionKey.isBlank()) missing.add("GMAIL_TOKEN_ENCRYPTION_KEY");
        if (!missing.isEmpty()) {
            return "Gmail integration is missing required configuration: " + String.join(", ", missing) + ".";
        }
        if (!isHttpsOrLoopback(redirectUri)) {
            return "GMAIL_REDIRECT_URI must use https:// (loopback addresses are allowed for local development only).";
        }
        if (!isValidEncryptionKey(encryptionKey)) {
            return "GMAIL_TOKEN_ENCRYPTION_KEY must be a base64-encoded 32-byte key.";
        }
        return null;
    }

    public boolean isUsable() {
        return configurationError() == null;
    }

    private boolean isHttpsOrLoopback(String uri) {
        try {
            URI parsed = URI.create(uri);
            String host = parsed.getHost();
            boolean loopback = "localhost".equalsIgnoreCase(host) || "127.0.0.1".equals(host) || "::1".equals(host);
            return "https".equalsIgnoreCase(parsed.getScheme()) || loopback;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private boolean isValidEncryptionKey(String key) {
        try {
            return Base64.getDecoder().decode(key).length == 32;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
}
