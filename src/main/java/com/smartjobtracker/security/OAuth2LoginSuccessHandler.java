package com.smartjobtracker.security;

import com.smartjobtracker.model.User;
import com.smartjobtracker.repository.UserRepository;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.UUID;

@Component
public class OAuth2LoginSuccessHandler extends SimpleUrlAuthenticationSuccessHandler {
    private static final Logger log = LoggerFactory.getLogger(OAuth2LoginSuccessHandler.class);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final String frontendUrl;

    public OAuth2LoginSuccessHandler(UserRepository userRepository, PasswordEncoder passwordEncoder, JwtUtil jwtUtil,
                                     @Value("${app.frontend-url:http://localhost:5173}") String frontendUrl) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtUtil = jwtUtil;
        this.frontendUrl = frontendUrl;
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
            String token = jwtUtil.generateToken(user.getEmail());
            getRedirectStrategy().sendRedirect(request, response,
                    frontendUrl + "/oauth2/callback?token=" + token);
        } catch (Exception ex) {
            // Anything unexpected here (DB save failure, JWT signing failure, etc.) must never
            // surface as an unhandled exception — that would either crash the request with a
            // generic 500 on this backend's own domain (confusing, and invisible to the
            // frontend) or, worse, fail silently. Log it loudly so it's visible in Render logs,
            // and send the user back to a frontend page that can show a real error.
            log.error("Google OAuth2 login succeeded at the provider but failed while finishing sign-in", ex);
            getRedirectStrategy().sendRedirect(request, response, frontendUrl + "/login?error=google-login-failed-server");
        }
    }
}