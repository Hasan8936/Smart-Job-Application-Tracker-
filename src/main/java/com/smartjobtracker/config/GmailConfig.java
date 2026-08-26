package com.smartjobtracker.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix="app.gmail")
public class GmailConfig {
    private String clientId=""; private String clientSecret=""; private String redirectUri="http://localhost:8080/api/gmail/callback";
    private String encryptionKey=""; private boolean enabled=false;
    private String classificationProvider="fallback";
    private String classificationApiKey="";
    private String classificationModel="gemini-2.0-flash";
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
}