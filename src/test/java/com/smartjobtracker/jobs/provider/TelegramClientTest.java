package com.smartjobtracker.jobs.provider;

import com.smartjobtracker.config.JobProviderConfig;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class TelegramClientTest {

    // A trimmed-down but structurally faithful fixture of what t.me/s/<channel> actually
    // returns: each post is a div.tgme_widget_message_wrap containing a
    // div.tgme_widget_message[data-post] and a div.tgme_widget_message_text.
    private static final String FIXTURE_HTML = """
            <html><body>
            <div class="tgme_widget_message_wrap">
              <div class="tgme_widget_message" data-post="jobschannel/101">
                <div class="tgme_widget_message_text">
                  Role: Backend Engineer<br>Company: Acme Corp<br>Location: Remote<br>
                  Apply here: <a href="https://forms.gle/abc123">link</a>
                </div>
              </div>
            </div>
            <div class="tgme_widget_message_wrap">
              <div class="tgme_widget_message" data-post="jobschannel/102">
                <div class="tgme_widget_message_text">Happy Friday everyone! No jobs today.</div>
              </div>
            </div>
            </body></html>
            """;

    @Test
    void parsesPostsWithTextAndHrefLinksFromTheFixture() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("https://t.me/s/jobschannel"))
                .andRespond(withSuccess(FIXTURE_HTML, MediaType.TEXT_HTML));

        JobProviderConfig config = new JobProviderConfig();
        TelegramClient client = new TelegramClient(builder, config);

        List<TelegramClient.TelegramPost> posts = client.fetchRecentPosts("jobschannel");

        assertEquals(2, posts.size());

        TelegramClient.TelegramPost jobPost = posts.get(0);
        assertEquals("jobschannel/101", jobPost.postId());
        assertTrue(jobPost.text().contains("Role: Backend Engineer"));
        assertTrue(jobPost.text().contains("Company: Acme Corp"));
        assertEquals("https://t.me/jobschannel/101", jobPost.permalink());
        assertEquals(List.of("https://forms.gle/abc123"), jobPost.links());

        TelegramClient.TelegramPost nonJobPost = posts.get(1);
        assertEquals("jobschannel/102", nonJobPost.postId());
        assertTrue(nonJobPost.links().isEmpty());

        server.verify();
    }
}
