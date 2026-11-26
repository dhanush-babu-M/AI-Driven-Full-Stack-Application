package com.pathpilot.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * Single configuration source for application identity, CORS, JWT, and rate-limit settings.
 */
@ConfigurationProperties(prefix = "app")
public record AppProperties(
        String name,
        String tagline,
        CorsProperties cors,
        JwtProperties jwt,
        RateLimitProperties rateLimit
) {
    public record CorsProperties(List<String> allowedOrigins) {
        public CorsProperties {
            if (allowedOrigins == null || allowedOrigins.isEmpty()) {
                allowedOrigins = List.of("http://localhost:5173");
            }
        }
    }

    public record JwtProperties(
            String secret,
            long expirationMinutes,
            String cookieName,
            boolean cookieSecure,
            String cookieSameSite
    ) {
        public JwtProperties {
            if (expirationMinutes <= 0) {
                expirationMinutes = 30;
            }
            if (cookieName == null || cookieName.isBlank()) {
                cookieName = "pathpilot_token";
            }
            if (cookieSameSite == null || cookieSameSite.isBlank()) {
                cookieSameSite = "Lax";
            }
        }
    }

    public record RateLimitProperties(
            int authMaxAttempts,
            long authWindowSeconds
    ) {
        public RateLimitProperties {
            if (authMaxAttempts <= 0) {
                authMaxAttempts = 5;
            }
            if (authWindowSeconds <= 0) {
                authWindowSeconds = 60;
            }
        }
    }
}
