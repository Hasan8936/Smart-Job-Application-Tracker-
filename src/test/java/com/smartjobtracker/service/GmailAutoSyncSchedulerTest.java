package com.smartjobtracker.service;

import com.smartjobtracker.config.GmailConfig;
import com.smartjobtracker.model.GmailConnection;
import com.smartjobtracker.repository.GmailConnectionRepository;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;

import java.time.OffsetDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class GmailAutoSyncSchedulerTest {

    private static GmailConnection connection(long userId) {
        GmailConnection c = new GmailConnection();
        c.setUserId(userId);
        c.setStatus("CONNECTED");
        return c;
    }

    private static GmailConfig configured() {
        GmailConfig config = mock(GmailConfig.class);
        when(config.isEnabled()).thenReturn(true);
        when(config.configurationError()).thenReturn(null);
        return config;
    }

    @Test
    void syncsEveryConnectedUserAndOneFailureDoesNotStopTheRest() {
        GmailConnectionRepository connections = mock(GmailConnectionRepository.class);
        when(connections.findByStatus("CONNECTED")).thenReturn(List.of(connection(1), connection(2), connection(3)));
        GmailService gmail = mock(GmailService.class);
        when(gmail.sync(2L)).thenThrow(new IllegalArgumentException("Gmail token refresh failed"));

        new GmailAutoSyncScheduler(connections, gmail, configured(), true).syncConnectedAccounts();

        verify(gmail).sync(1L);
        verify(gmail).sync(2L);
        verify(gmail).sync(3L);
    }

    @Test
    void doesNothingWhenDisabledOrGmailNotConfigured() {
        GmailConnectionRepository connections = mock(GmailConnectionRepository.class);
        GmailService gmail = mock(GmailService.class);

        new GmailAutoSyncScheduler(connections, gmail, configured(), false).syncConnectedAccounts();

        GmailConfig broken = mock(GmailConfig.class);
        when(broken.isEnabled()).thenReturn(true);
        when(broken.configurationError()).thenReturn("missing GOOGLE_CLIENT_ID");
        new GmailAutoSyncScheduler(connections, gmail, broken, true).syncConnectedAccounts();

        verifyNoInteractions(gmail, connections);
    }

    @Test
    void backsOffTransientFailuresAndSkipsUntilNextAttempt() {
        GmailConnectionRepository connections = mock(GmailConnectionRepository.class);
        GmailConnection c = connection(4);
        when(connections.findByStatus("CONNECTED")).thenReturn(List.of(c));
        GmailService gmail = mock(GmailService.class);
        when(gmail.sync(4L)).thenThrow(new RuntimeException("temporary Gmail outage"));
        GmailAutoSyncScheduler scheduler = new GmailAutoSyncScheduler(connections, gmail, configured(), true);

        scheduler.syncConnectedAccounts();
        assertEquals(1, c.getAutoSyncFailures());
        assertTrue(c.getAutoSyncNextAttemptAt().isAfter(OffsetDateTime.now()));

        scheduler.syncConnectedAccounts();
        verify(gmail, times(1)).sync(4L);
        verify(connections, atLeastOnce()).save(c);
    }

    @Test
    void disconnectsAccountWhenGoogleForbidsToken() {
        GmailConnectionRepository connections = mock(GmailConnectionRepository.class);
        GmailConnection c = connection(5);
        c.setEncryptedAccessToken("access");
        c.setEncryptedRefreshToken("refresh");
        when(connections.findByStatus("CONNECTED")).thenReturn(List.of(c));
        GmailService gmail = mock(GmailService.class);
        when(gmail.sync(5L)).thenThrow(HttpClientErrorException.create(
                HttpStatus.FORBIDDEN, "Forbidden", HttpHeaders.EMPTY, new byte[0], null));

        new GmailAutoSyncScheduler(connections, gmail, configured(), true).syncConnectedAccounts();

        assertEquals("DISCONNECTED", c.getStatus());
        assertNull(c.getEncryptedAccessToken());
        assertNull(c.getEncryptedRefreshToken());
        assertEquals("GOOGLE_TOKEN_FORBIDDEN", c.getAutoSyncDisabledReason());
        verify(connections).save(c);
    }
}
