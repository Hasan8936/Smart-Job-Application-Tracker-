package com.smartjobtracker.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartjobtracker.config.GmailConfig;
import com.smartjobtracker.model.GmailConnection;
import com.smartjobtracker.repository.GmailConnectionRepository;
import com.smartjobtracker.repository.IngestedEmailRepository;
import com.smartjobtracker.repository.InterviewCandidateRepository;
import com.smartjobtracker.repository.JobApplicationRepository;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.time.OffsetDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;

/**
 * Verifies the scope-upgrade path for interview detection: a connection that was authorized
 * before calendar.readonly was added to the requested scopes (see GmailService.begin()) still
 * has a valid Gmail-only token. Calling the Calendar API with such a token returns 403, and
 * that must surface as an actionable "reconnect" message rather than an unhandled exception.
 */
class GmailServiceCalendarScopeUpgradeTest {

    @Test
    void insufficientCalendarScopeSurfacesAsActionableReconnectMessage() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();

        GmailConnectionRepository connections = mock(GmailConnectionRepository.class);
        GmailConnection connection = new GmailConnection();
        connection.setUserId(1L);
        connection.setStatus("CONNECTED");
        connection.setEncryptedAccessToken("encrypted-access-token");
        // Token still valid (not expired) — this connection was authorized under the OLD
        // Gmail-only scope, before calendar.readonly was added, and never needed a refresh.
        connection.setAccessTokenExpiresAt(OffsetDateTime.now().plusMinutes(30));
        when(connections.findByUserId(1L)).thenReturn(Optional.of(connection));

        GmailTokenCipher cipher = mock(GmailTokenCipher.class);
        when(cipher.decrypt("encrypted-access-token")).thenReturn("plain-access-token");

        server.expect(requestTo(org.hamcrest.Matchers.startsWith("https://www.googleapis.com/calendar/v3/calendars/primary/events")))
                .andRespond(withStatus(HttpStatus.FORBIDDEN).contentType(MediaType.APPLICATION_JSON)
                        .body("{\"error\":{\"message\":\"Request had insufficient authentication scopes.\"}}"));

        GmailService service = new GmailService(
                mock(GmailConfig.class), connections, mock(IngestedEmailRepository.class),
                mock(JobApplicationRepository.class), mock(JobApplicationService.class),
                cipher, builder, new ObjectMapper(),
                mock(RuleBasedEmailClassifier.class), mock(GeminiEmailClassifier.class),
                mock(EmailApplicationMatcher.class), mock(InterviewCandidateRepository.class),
                new InterviewHeuristic());

        IllegalStateException thrown = assertThrows(IllegalStateException.class, () -> service.syncCalendar(1L));
        assertTrue(thrown.getMessage().toLowerCase().contains("reconnect"),
                "Expected an actionable reconnect message, got: " + thrown.getMessage());

        server.verify();
    }
}
