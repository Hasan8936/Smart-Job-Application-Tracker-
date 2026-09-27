package com.smartjobtracker.service;

import com.smartjobtracker.model.User;
import com.smartjobtracker.repository.UserRepository;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class SuperAdminBootstrapTest {

    private static User user(String email, String role) {
        User u = new User();
        u.setId(7L); u.setEmail(email); u.setRole(role);
        return u;
    }

    @Test
    void startupPromotesTheConfiguredExistingAccountOnce() {
        UserRepository repo = mock(UserRepository.class);
        User u = user("Owner@Example.com", "USER");
        when(repo.findByEmailIgnoreCase("owner@example.com")).thenReturn(Optional.of(u));

        SuperAdminBootstrap bootstrap = new SuperAdminBootstrap(repo, " owner@example.com ");
        bootstrap.run(null);
        assertEquals("ADMIN", u.getRole());
        verify(repo, times(1)).save(u);

        bootstrap.run(null); // idempotent: already ADMIN, nothing saved again
        verify(repo, times(1)).save(any());
    }

    @Test
    void unsetEmailMeansNoAdminAndNeverCreatesAccounts() {
        UserRepository repo = mock(UserRepository.class);
        new SuperAdminBootstrap(repo, "").run(null);
        new SuperAdminBootstrap(repo, null).run(null);
        verifyNoInteractions(repo);

        UserRepository empty = mock(UserRepository.class);
        when(empty.findByEmailIgnoreCase(any())).thenReturn(Optional.empty());
        new SuperAdminBootstrap(empty, "owner@example.com").run(null);
        verify(empty, never()).save(any());
    }

    @Test
    void otherAccountsAreNeverPromoted() {
        SuperAdminBootstrap bootstrap = new SuperAdminBootstrap(mock(UserRepository.class), "owner@example.com");
        User other = user("someone@example.com", "USER");
        assertFalse(bootstrap.promoteIfConfigured(other));
        assertEquals("USER", other.getRole());
        assertTrue(bootstrap.promoteIfConfigured(user("OWNER@example.com", "USER")));
    }
}
