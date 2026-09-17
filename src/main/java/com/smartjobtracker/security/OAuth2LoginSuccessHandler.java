package com.smartjobtracker.security;

import com.smartjobtracker.model.User;
import com.smartjobtracker.repository.UserRepository;
import com.smartjobtracker.service.GmailService;
import com.smartjobtracker.service.GoogleCalendarService;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.OAuth2RefreshToken;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

@Component
public class OAuth2LoginSuccessHandler extends SimpleUrlAuthenticationSuccessHandler {
    private static final Logger log = LoggerFactory.getLogger(OAuth2LoginSuccessHandler.class);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final String frontendUrl;
    private final OAuth2AuthorizedClientService authorizedClientService;
    private final GmailService gmailService;
    private final GoogleCalendarService calendarService;

    public OAuth2LoginSuccessHandler(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            JwtUtil jwtUtil,
            @Value("${app.frontend-url:http://localhost:5173}") String frontendUrl,
            org.springframework.beans.factory.ObjectProvider<OAuth2AuthorizedClientService> authorizedClientService,
            org.springframework.beans.factory.ObjectProvider<GmailService> gmailService,
            org.springframework.beans.factory.ObjectProvider<GoogleCalendarService> calendarService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtUtil = jwtUtil;
        this.frontendUrl = frontendUrl;
        this.authorizedClientService = authorizedClientService.getIfAvailable();
        this.gmailService = gmailService.getIfAvailable();
        this.calendarService = calendarService.getIfAvailable();
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
                                        Authentication authentication) throws IOException, ServletException {
        try {
            OAuth2User googleUser = (OAuth2User) authentication.getPrincipal();
            String email = googleUser.getAttribute("email");
            if (email == null || email.isBlank()) {
                log.warn("Google OAuth2 login succeeded but no email attribute was returned; principal attributes: {}",
                        googleUser.getAttributes().keySet());
                getRedirectStrategy().sendRedirect(request, response, frontendUrl + "/login?error=google-email-required");
                return;
            }

            User user = userRepository.findByEmail(email).orElseGet(() -> {
                User created = new User();
                created.setEmail(email);
                created.setName(googleUser.getAttribute("name"));
                created.setPasswordHash(passwordEncoder.encode(UUID.randomUUID().toString()));
                return userRepository.save(created);
            });

            // Store Gmail + Calendar tokens obtained during this sign-in authorization.
            // Wrapped in its own try-catch so a token storage failure never blocks sign-in.
            tryStoreTokens(user, email, authentication);

            String token = jwtUtil.generateToken(user.getEmail());
            getRedirectStrategy().sendRedirect(request, response,
                    frontendUrl + "/oauth2/callback?token=" + token);
        } catch (Exception ex) {
            log.error("Google OAuth2 login succeeded at the provider but failed while finishing sign-in", ex);
            getRedirectStrategy().sendRedirect(request, response, frontendUrl + "/login?error=google-login-failed-server");
        }
    }

    private void tryStoreTokens(User user, String email, Authentication authentication) {
        if (authorizedClientService == null || !(authentication instanceof OAuth2AuthenticationToken oauthToken)) return;
        try {
            OAuth2AuthorizedClient client = authorizedClientService.loadAuthorizedClient(
                    oauthToken.getAuthorizedClientRegistrationId(), oauthToken.getName());
            if (client == null) return;

            OAuth2AccessToken accessToken = client.getAccessToken();
            OAuth2RefreshToken refreshToken = client.getRefreshToken();
            if (accessToken == null) return;

            String at = accessToken.getTokenValue();
            String rt = refreshToken != null ? refreshToken.getTokenValue() : null;
            Instant expiresAt = accessToken.getExpiresAt();
            long expiresIn = (expiresAt != null)
                    ? Math.max(Duration.between(Instant.now(), expiresAt).getSeconds(), 60)
                    : 3600;

            if (gmailService != null) {
                try {
                    gmailService.storeOAuthTokens(user.getId(), email, at, rt, expiresIn);
                    log.debug("Stored Gmail tokens for user {}", user.getId());
                } catch (Exception ex) {
                    log.warn("Failed to store Gmail tokens for user {}: {}", user.getId(), ex.getMessage());
                }
            }
            if (calendarService != null) {
                try {
                    calendarService.storeOAuthTokens(user.getId(), at, rt, expiresIn);
                    log.debug("Stored Calendar tokens for user {}", user.getId());
                } catch (Exception ex) {
                    log.warn("Failed to store Calendar tokens for user {}: {}", user.getId(), ex.getMessage());
                }
            }
        } catch (Exception ex) {
            log.warn("Could not load OAuth2 authorized client for token storage: {}", ex.getMessage());
        }
    }
}
