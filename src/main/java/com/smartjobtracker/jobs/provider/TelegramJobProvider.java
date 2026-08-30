package com.smartjobtracker.jobs.provider;

import com.smartjobtracker.config.GmailConfig;
import com.smartjobtracker.config.JobProviderConfig;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Job source backed by a public Telegram channel (see TelegramClient for how posts are read).
 * Unlike Greenhouse/Lever/Ashby (structured ATS APIs), a Telegram channel is free-text and
 * often not job-related at all, so this provider is more conservative than the others:
 *  - Extraction (Gemini, or the rule-based fallback if Gemini isn't configured/fails) must
 *    return a confidence at or above MIN_CONFIDENCE, or the post is skipped entirely.
 *  - JobSyncService.hasRequiredFields() then additionally drops anything still missing
 *    company/title/applyUrl — so a post extraction can't fabricate its way past that gate
 *    with a high confidence score alone.
 * Nothing here changes any existing provider's behavior; this is purely additive.
 */
@Component
public class TelegramJobProvider implements JobProvider {
    private static final double MIN_CONFIDENCE = 0.55;

    private final JobProviderConfig.TelegramSettings config;
    private final GmailConfig aiConfig;
    private final TelegramClient client;
    private final GeminiTelegramPostExtractor gemini;
    private final RuleBasedTelegramPostExtractor rules;

    public TelegramJobProvider(JobProviderConfig config, GmailConfig aiConfig, TelegramClient client,
                               GeminiTelegramPostExtractor gemini, RuleBasedTelegramPostExtractor rules) {
        this.config = config.getTelegram();
        this.aiConfig = aiConfig;
        this.client = client;
        this.gemini = gemini;
        this.rules = rules;
    }

    @Override public String id() { return "telegram"; }

    @Override public boolean isEnabled() { return config.isEnabled() && !config.getChannels().isEmpty(); }

    @Override public Set<Capability> capabilities() { return EnumSet.noneOf(Capability.class); }

    @Override
    public JobBatch search(JobQuery query, String cursor) {
        if (!isEnabled()) throw new ProviderHttpClient.ProviderUnavailableException("Telegram provider is not configured");
        List<ProviderJob> jobs = new ArrayList<>();
        for (String channel : config.getChannels()) {
            for (TelegramClient.TelegramPost post : client.fetchRecentPosts(channel)) {
                ProviderJob job = toProviderJob(post);
                if (job != null) jobs.add(job);
            }
        }
        List<ProviderJob> results = jobs.stream().filter(job -> matches(job, query)).toList();
        return new JobBatch(results, null);
    }

    @Override
    public ProviderJob fetchJobDetails(String externalId) {
        String channel = externalId.contains("/") ? externalId.substring(0, externalId.indexOf('/')) : null;
        if (channel == null) throw new IllegalArgumentException("Job not found");
        return client.fetchRecentPosts(channel).stream()
                .filter(post -> externalId.equals(post.postId()))
                .findFirst()
                .map(this::toProviderJob)
                .orElseThrow(() -> new IllegalArgumentException("Job not found"));
    }

    private ProviderJob toProviderJob(TelegramClient.TelegramPost post) {
        TelegramPostExtractor.Extraction extraction = extract(post);
        if (extraction == null || extraction.confidence() < MIN_CONFIDENCE) return null;
        String applyUrl = extraction.applyUrl() != null ? extraction.applyUrl() : post.permalink();
        return new ProviderJob(post.postId(), extraction.company(), extraction.title(), extraction.location(),
                extraction.employmentType(), null, applyUrl, null, post.text(), null, null, null, null, post.text());
    }

    private TelegramPostExtractor.Extraction extract(TelegramClient.TelegramPost post) {
        TelegramPostExtractor.Input input = new TelegramPostExtractor.Input(post.text(), post.links());
        boolean geminiConfigured = "gemini".equalsIgnoreCase(aiConfig.getClassificationProvider())
                && aiConfig.getClassificationApiKey() != null && !aiConfig.getClassificationApiKey().isBlank();
        if (!geminiConfigured) return rules.extract(input);
        for (int attempt = 0; ; attempt++) {
            try {
                return gemini.extract(input);
            } catch (RuntimeException ex) {
                if (attempt >= aiConfig.getClassificationMaxRetries()) return rules.extract(input);
                try { Thread.sleep(200L * (attempt + 1)); } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    return rules.extract(input);
                }
            }
        }
    }

    private boolean matches(ProviderJob job, JobQuery query) {
        if (query.keywords() == null || query.keywords().isBlank()) return true;
        String text = ((job.title() == null ? "" : job.title()) + " " + (job.description() == null ? "" : job.description()))
                .toLowerCase(Locale.ROOT);
        return text.contains(query.keywords().toLowerCase(Locale.ROOT));
    }
}
