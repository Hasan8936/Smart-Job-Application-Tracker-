package com.smartjobtracker.jobs.provider;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Reads recent posts from a PUBLIC Telegram channel via its web preview page
 * (https://t.me/s/&lt;channel&gt;), which Telegram serves with no authentication for any
 * public channel. This is a workaround, not an official API — if Telegram changes this
 * page's markup, parsing may need updating. Only works for public channels; a private
 * channel would need the Bot API with a bot token instead (not implemented here).
 */
@Component
public class TelegramClient {
    private final ProviderHttpClient http;

    public TelegramClient(RestClient.Builder builder, com.smartjobtracker.config.JobProviderConfig config) {
        http = new ProviderHttpClient(builder, config.getMinIntervalMs(), config.getMaxRetries());
    }

    public List<TelegramPost> fetchRecentPosts(String channel) {
        String html = http.getHtml("https://t.me/s/" + enc(channel));
        Document doc = Jsoup.parse(html);
        Elements wraps = doc.select("div.tgme_widget_message_wrap");
        List<TelegramPost> posts = new ArrayList<>();
        for (Element wrap : wraps) {
            Element messageDiv = wrap.selectFirst("div.tgme_widget_message");
            if (messageDiv == null) continue;
            String postId = messageDiv.attr("data-post"); // e.g. "somechannel/1234"
            if (postId == null || postId.isBlank()) continue;
            Element textDiv = wrap.selectFirst("div.tgme_widget_message_text");
            if (textDiv == null) continue;
            // Preserve line breaks: Jsoup's .text() collapses all whitespace to single spaces,
            // which loses structure job posts often rely on (Role: / Company: / Apply: on
            // separate lines). Convert <br> to a real newline, then use wholeText() (which does
            // NOT collapse whitespace) instead of text().
            String withNewlines = textDiv.html().replaceAll("(?i)<br\\s*/?>", "\n");
            String text = org.jsoup.parser.Parser.unescapeEntities(
                    Jsoup.parseBodyFragment(withNewlines).body().wholeText(), false).trim();
            if (text.isBlank()) continue;
            // Collect actual hrefs separately: a hyperlinked "Apply here" has its real URL in
            // href, not in the visible text wholeText() captured above.
            List<String> links = new ArrayList<>();
            for (Element anchor : textDiv.select("a[href]")) {
                String href = anchor.attr("abs:href");
                if (!href.isBlank()) links.add(href);
            }
            posts.add(new TelegramPost(postId, text, "https://t.me/" + postId, links));
        }
        return posts;
    }

    private String enc(String value) { return URLEncoder.encode(value, StandardCharsets.UTF_8); }

    public record TelegramPost(String postId, String text, String permalink, List<String> links) {}
}
