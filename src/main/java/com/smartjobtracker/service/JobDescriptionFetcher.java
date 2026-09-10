package com.smartjobtracker.service;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import java.net.URI;
import java.util.Locale;

/**
 * Fetches a job posting page and extracts its visible text, for the "paste a URL" JD source.
 * Basic SSRF guardrails only (scheme allow-list + obvious private-network host blocking) --
 * good enough for a candidate pasting a public job-board link, not a hardened fetcher.
 */
@Component
public class JobDescriptionFetcher {
    private final RestClient client;

    public JobDescriptionFetcher(RestClient.Builder builder) { this.client = builder.build(); }

    public String fetch(String url) {
        URI uri = validate(url);
        String html;
        try {
            html = client.get().uri(uri).header(HttpHeaders.USER_AGENT, "Mozilla/5.0 (compatible; SmartJobTrackerBot/1.0)")
                    .retrieve().body(String.class);
        } catch (RestClientException ex) {
            throw new IllegalStateException("Could not fetch the job posting page", ex);
        }
        if (html == null || html.isBlank()) throw new IllegalStateException("The job posting page returned no content");
        Document doc = Jsoup.parse(html);
        doc.select("script, style, nav, footer, header, noscript, svg").remove();
        String text = doc.body() == null ? doc.text() : doc.body().wholeText();
        text = text.replaceAll("[ \\t]+", " ").replaceAll("\\n{3,}", "\n\n").trim();
        if (text.isBlank()) throw new IllegalStateException("Could not extract any text from that job posting page");
        return text.substring(0, Math.min(text.length(), 20000));
    }

    private URI validate(String url) {
        if (url == null || url.isBlank()) throw new IllegalArgumentException("Job posting URL is required");
        URI uri;
        try { uri = URI.create(url.trim()); } catch (Exception ex) { throw new IllegalArgumentException("Invalid job posting URL"); }
        String scheme = uri.getScheme();
        if (scheme == null || !(scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https")))
            throw new IllegalArgumentException("Job posting URL must start with http:// or https://");
        String host = uri.getHost();
        if (host == null || host.isBlank()) throw new IllegalArgumentException("Invalid job posting URL");
        String lower = host.toLowerCase(Locale.ROOT);
        if (lower.equals("localhost") || lower.startsWith("127.") || lower.startsWith("0.") || lower.startsWith("192.168.")
                || lower.startsWith("10.") || lower.startsWith("169.254.") || lower.matches("172\\.(1[6-9]|2\\d|3[0-1])\\..*"))
            throw new IllegalArgumentException("That URL points to a private network address and can't be fetched");
        return uri;
    }
}
