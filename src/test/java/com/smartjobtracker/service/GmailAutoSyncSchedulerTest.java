package com.smartjobtracker.service;

import com.smartjobtracker.config.GmailConfig;
import com.smartjobtracker.model.GmailConnection;
import com.smartjobtracker.repository.GmailConnectionRepository;
import org.junit.jupiter.api.Test;

import java.util.List;

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
}
