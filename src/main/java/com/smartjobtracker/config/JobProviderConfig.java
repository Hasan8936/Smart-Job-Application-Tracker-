package com.smartjobtracker.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

@ConfigurationProperties(prefix = "app.job-providers")
public class JobProviderConfig {
    private ProviderSettings greenhouse = new ProviderSettings();
    private ProviderSettings lever = new ProviderSettings();
    private ProviderSettings ashby = new ProviderSettings();
    private ApifySettings apify = new ApifySettings();
    private long minIntervalMs = 500;
    private int maxRetries = 3;

    public ProviderSettings getGreenhouse() { return greenhouse; }
    public void setGreenhouse(ProviderSettings value) { greenhouse = value; }
    public ProviderSettings getLever() { return lever; }
    public void setLever(ProviderSettings value) { lever = value; }
    public ProviderSettings getAshby() { return ashby; }
    public void setAshby(ProviderSettings value) { ashby = value; }
    public ApifySettings getApify() { return apify; }
    public void setApify(ApifySettings value) { apify = value; }
    public long getMinIntervalMs() { return minIntervalMs; }
    public void setMinIntervalMs(long value) { minIntervalMs = value; }
    public int getMaxRetries() { return maxRetries; }
    public void setMaxRetries(int value) { maxRetries = value; }

    public static class ProviderSettings {
        private boolean enabled;
        private List<String> boards = new ArrayList<>();
        private List<String> sites = new ArrayList<>();
        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean value) { enabled = value; }
        public List<String> getBoards() { return boards; }
        public void setBoards(List<String> value) { boards = value; }
        public List<String> getSites() { return sites; }
        public void setSites(List<String> value) { sites = value; }
    }

    public static class ApifySettings extends ProviderSettings {
        private String token;
        private String actor;
        public String getToken() { return token; }
        public void setToken(String value) { token = value; }
        public String getActor() { return actor; }
        public void setActor(String value) { actor = value; }
    }
}