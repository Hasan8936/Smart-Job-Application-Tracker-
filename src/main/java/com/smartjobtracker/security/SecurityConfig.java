package com.smartjobtracker.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

@Configuration
public class SecurityConfig {

    private final JwtUtil jwtUtil;
    private final UserDetailsService userDetailsService;
    private final OAuth2LoginSuccessHandler oauth2LoginSuccessHandler;
    private final OAuth2LoginFailureHandler oauth2LoginFailureHandler;
    private final ClientRegistrationRepository clientRegistrationRepository;

    // Always-allowed origin patterns regardless of what's configured below. These cover
    // every Vercel deployment (previews and production aliases) plus local dev, so a
    // partial or stale CORS_ALLOWED_ORIGINS/CORS_ALLOWED_ORIGIN_PATTERNS list can never
    // silently lock out the real frontend the way it did before this safety net existed.
    private static final List<String> DEFAULT_ORIGIN_PATTERNS =
            List.of("https://*.vercel.app", "http://localhost:5173", "http://localhost:3000");

    // CORS_ALLOWED_ORIGIN_PATTERNS is preferred; CORS_ALLOWED_ORIGINS is accepted as a
    // backward-compatible fallback for deployments (e.g. Render) that set that name instead.
    @Value("${CORS_ALLOWED_ORIGIN_PATTERNS:}")
    private String configuredOriginPatterns;

    @Value("${CORS_ALLOWED_ORIGINS:}")
    private String legacyAllowedOrigins;

    public SecurityConfig(JwtUtil jwtUtil, UserDetailsService userDetailsService,
                          OAuth2LoginSuccessHandler oauth2LoginSuccessHandler,
                          OAuth2LoginFailureHandler oauth2LoginFailureHandler,
                          org.springframework.beans.factory.ObjectProvider<ClientRegistrationRepository> clientRegistrationRepository) {
        this.jwtUtil = jwtUtil;
        this.userDetailsService = userDetailsService;
        this.oauth2LoginSuccessHandler = oauth2LoginSuccessHandler;
        this.oauth2LoginFailureHandler = oauth2LoginFailureHandler;
        this.clientRegistrationRepository = clientRegistrationRepository.getIfAvailable();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        JwtFilter jwtFilter = new JwtFilter(jwtUtil, userDetailsService);

        http
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .csrf(csrf -> csrf.disable())
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // /actuator/health and /actuator/prometheus need to be reachable without a
                        // JWT — infra healthchecks and the Prometheus scraper don't have one — while
                        // the rest of Actuator (env, beans, etc.) stays behind auth.
                        // "/error" must be permitted: when an endpoint is missing (e.g. OAuth
                        // isn't configured yet) Spring forwards to /error, and without this the
                        // unauthenticated forward turns a 404 into a confusing 403 Access Denied.
                        .requestMatchers("/api/auth/**", "/api/gmail/callback", "/api/notifications/webhook", "/oauth2/**", "/login/**", "/error", "/api/health", "/actuator/health", "/actuator/prometheus").permitAll()
                        .anyRequest().authenticated())
                .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class);

        if (clientRegistrationRepository != null) {
            http.oauth2Login(oauth -> oauth.successHandler(oauth2LoginSuccessHandler).failureHandler(oauth2LoginFailureHandler));
        }

        return http.build();
    }

    /**
     * Without this, the browser blocks every request from a frontend hosted on a
     * different origin (e.g. your Vercel URL) to this API — set CORS_ALLOWED_ORIGINS
     * as an env var in production, comma-separated if you have more than one.
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOriginPatterns(resolveAllowedOriginPatterns());
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setExposedHeaders(List.of("Authorization"));
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }

    /**
     * CORS_ALLOWED_ORIGIN_PATTERNS wins if set; otherwise CORS_ALLOWED_ORIGINS is used as a
     * backward-compatible fallback. Either way, {@link #DEFAULT_ORIGIN_PATTERNS} is always
     * added on top — so an incomplete or stale origin list (e.g. one that lists preview
     * deployment URLs but not the actual production domain) can never fully replace CORS
     * access and silently break the live frontend.
     */
    private List<String> resolveAllowedOriginPatterns() {
        String configured = configuredOriginPatterns == null ? "" : configuredOriginPatterns.trim();
        String legacy = legacyAllowedOrigins == null ? "" : legacyAllowedOrigins.trim();
        String source = !configured.isBlank() ? configured : legacy;

        LinkedHashSet<String> patterns = new LinkedHashSet<>();
        if (!source.isBlank()) {
            for (String origin : source.split(",")) {
                String trimmed = origin.trim();
                if (!trimmed.isBlank()) patterns.add(trimmed);
            }
        }
        patterns.addAll(DEFAULT_ORIGIN_PATTERNS);
        return new ArrayList<>(patterns);
    }
}
