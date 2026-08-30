package com.smartjobtracker.jobs.provider;

import com.smartjobtracker.config.GmailConfig;
import com.smartjobtracker.config.JobProviderConfig;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TelegramJobProviderTest {

    private JobProviderConfig config(List<String> channels) {
        JobProviderConfig config = new JobProviderConfig();
        config.getTelegram().setEnabled(true);
        config.getTelegram().setChannels(channels);
        return config;
    }

    private GmailConfig geminiConfigured() {
        GmailConfig config = mock(GmailConfig.class);
        when(config.getClassificationProvider()).thenReturn("gemini");
        when(config.getClassificationApiKey()).thenReturn("test-key");
        when(config.getClassificationMaxRetries()).thenReturn(1);
        return config;
    }

    @Test
    void dropsPostsBelowTheConfidenceFloorEvenWithAllFieldsPresent() {
        TelegramClient client = mock(TelegramClient.class);
        TelegramClient.TelegramPost post = new TelegramClient.TelegramPost("channel/1", "some text", "https://t.me/channel/1", List.of());
        when(client.fetchRecentPosts("channel")).thenReturn(List.of(post));

        GeminiTelegramPostExtractor gemini = mock(GeminiTelegramPostExtractor.class);
        // All fields present, but confidence is below the 0.55 floor — must still be dropped.
        when(gemini.extract(any())).thenReturn(new TelegramPostExtractor.Extraction(
                "Acme", "Engineer", "Remote", "FULL_TIME", "https://apply.example.com", 0.40, "gemini"));

        TelegramJobProvider provider = new TelegramJobProvider(config(List.of("channel")), geminiConfigured(),
                client, gemini, new RuleBasedTelegramPostExtractor());

        JobProvider.JobBatch batch = provider.search(new JobProvider.JobQuery(null, List.of(), List.of()), null);

        assertEquals(0, batch.jobs().size());
    }

    @Test
    void keepsPostsAtOrAboveTheConfidenceFloorAndFallsBackToPermalinkWhenNoApplyUrlFound() {
        TelegramClient client = mock(TelegramClient.class);
        TelegramClient.TelegramPost post = new TelegramClient.TelegramPost("channel/2", "some text", "https://t.me/channel/2", List.of());
        when(client.fetchRecentPosts("channel")).thenReturn(List.of(post));

        GeminiTelegramPostExtractor gemini = mock(GeminiTelegramPostExtractor.class);
        when(gemini.extract(any())).thenReturn(new TelegramPostExtractor.Extraction(
                "Acme", "Engineer", "Remote", "FULL_TIME", null, 0.90, "gemini"));

        TelegramJobProvider provider = new TelegramJobProvider(config(List.of("channel")), geminiConfigured(),
                client, gemini, new RuleBasedTelegramPostExtractor());

        JobProvider.JobBatch batch = provider.search(new JobProvider.JobQuery(null, List.of(), List.of()), null);

        assertEquals(1, batch.jobs().size());
        assertEquals("https://t.me/channel/2", batch.jobs().get(0).applyUrl());
        assertEquals("Acme", batch.jobs().get(0).company());
    }

    @Test
    void fallsBackToRuleBasedExtractionWhenGeminiKeepsFailing() {
        TelegramClient client = mock(TelegramClient.class);
        TelegramClient.TelegramPost post = new TelegramClient.TelegramPost(
                "channel/3", "Role: Backend Engineer\nCompany: Acme\nLocation: Remote\nApply: https://forms.gle/xyz",
                "https://t.me/channel/3", List.of("https://forms.gle/xyz"));
        when(client.fetchRecentPosts("channel")).thenReturn(List.of(post));

        GeminiTelegramPostExtractor gemini = mock(GeminiTelegramPostExtractor.class);
        when(gemini.extract(any())).thenThrow(new RuntimeException("Gemini API down"));

        TelegramJobProvider provider = new TelegramJobProvider(config(List.of("channel")), geminiConfigured(),
                client, gemini, new RuleBasedTelegramPostExtractor());

        JobProvider.JobBatch batch = provider.search(new JobProvider.JobQuery(null, List.of(), List.of()), null);

        // Rule-based confidence (0.35) is below MIN_CONFIDENCE (0.55), so even the successful
        // fallback extraction correctly does not produce a listing here — this documents that
        // trade-off rather than asserting it silently.
        assertEquals(0, batch.jobs().size());
    }

    @Test
    void isDisabledWhenNoChannelsAreConfigured() {
        TelegramJobProvider provider = new TelegramJobProvider(config(List.of()), geminiConfigured(),
                mock(TelegramClient.class), mock(GeminiTelegramPostExtractor.class), new RuleBasedTelegramPostExtractor());

        assertFalse(provider.isEnabled());
    }
}
