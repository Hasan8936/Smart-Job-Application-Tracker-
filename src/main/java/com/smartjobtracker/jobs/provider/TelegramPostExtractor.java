package com.smartjobtracker.jobs.provider;

import java.util.List;

/**
 * Extracts structured job-posting fields from a single Telegram channel post's free text.
 * Mirrors com.smartjobtracker.service.EmailClassifier's shape: implementations must return
 * null for any field they aren't confident about rather than guessing — JobSyncService's
 * hasRequiredFields() check already drops any candidate missing company/title/applyUrl, so
 * an honest null here simply means "this post doesn't become a listing", not an error.
 */
public interface TelegramPostExtractor {
    Extraction extract(Input input);

    record Input(String text, List<String> links) {}

    record Extraction(String company, String title, String location, String employmentType,
                      String applyUrl, double confidence, String provider) {}
}
