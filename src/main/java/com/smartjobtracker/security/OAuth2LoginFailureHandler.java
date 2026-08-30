package com.smartjobtracker.security;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Without this, a failure during the Google authorization-code exchange (bad/expired code,
 * redirect_uri mismatch, provider error) falls back to Spring Security's default behavior:
 * a redirect to this backend's own auto-generated {@code /login?error} page. For a stateless
 * JWT-based SPA that page is a dead end the user can't do anything with, and it's easy to
 * mistake for "nothing happened" since it isn't the React app.
 *
 * This handler instead logs the failure (visible in Render logs) and redirects to the
 * frontend's login page with a specific, diagnosable error code.
 */
@Component
public class OAuth2LoginFailureHandler implements AuthenticationFailureHandler {
    private static final Logger log = LoggerFactory.getLogger(OAuth2LoginFailureHandler.class);

    private final String frontendUrl;

    public OAuth2LoginFailureHandler(@Value("${app.frontend-url:http://localhost:5173}") String frontendUrl) {
        this.frontendUrl = frontendUrl;
    }

    @Override
    public void onAuthenticationFailure(HttpServletRequest request, HttpServletResponse response,
                                         AuthenticationException exception) throws IOException, ServletException {
        log.error("Google OAuth2 authorization/token exchange failed: {}", exception.getMessage(), exception);
        response.sendRedirect(frontendUrl + "/login?error=google-authorization-failed");
    }
}
