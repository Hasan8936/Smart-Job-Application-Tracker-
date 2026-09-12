package com.smartjobtracker.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

@ConfigurationProperties(prefix = "app.job-providers")
public class JobProviderConfig {
    private ProviderSettings greenhouse = new ProviderSettings();
    private ProviderSettings lever = new ProviderSettings();
    private ProviderSettings ashby = new ProviderSettings();
    private JobSpySettings jobspy = new JobSpySettings();
    private TelegramSettings telegram = new TelegramSettings();
    private long minIntervalMs = 500;
    private int maxRetries = 3;

    public ProviderSettings getGreenhouse() { return greenhouse; }
    public void setGreenhouse(ProviderSettings value) { greenhouse = value; }
    public ProviderSettings getLever() { return lever; }
    public void setLever(ProviderSettings value) { lever = value; }
    public ProviderSettings getAshby() { return ashby; }
    public void setAshby(ProviderSettings value) { ashby = value; }
    public JobSpySettings getJobspy() { return jobspy; }
    public void setJobspy(JobSpySettings value) { jobspy = value; }
    public TelegramSettings getTelegram() { return telegram; }
    public void setTelegram(TelegramSettings value) { telegram = value; }
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

    public static class JobSpySettings {
        private boolean enabled;
        private String serviceUrl;
        private List<String> siteNames = List.of("linkedin", "indeed", "glassdoor", "google");
        private int resultsWanted = 20;
        private int hoursOld = 168;

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean value) { enabled = value; }
        public String getServiceUrl() { return serviceUrl; }
        public void setServiceUrl(String value) { serviceUrl = value; }
        public List<String> getSiteNames() { return siteNames; }
        public void setSiteNames(List<String> value) { siteNames = value; }
        public int getResultsWanted() { return resultsWanted; }
        public void setResultsWanted(int value) { resultsWanted = value; }
        public int getHoursOld() { return hoursOld; }
        public void setHoursOld(int value) { hoursOld = value; }
    }

    public static class TelegramSettings {
        private boolean enabled;
        private List<String> channels = new ArrayList<>();
        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean value) { enabled = value; }
        public List<String> getChannels() { return channels; }
        public void setChannels(List<String> value) { channels = value; }
    }
}