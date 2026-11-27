package com.pathpilot.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * Single configuration source for application identity, CORS, JWT, rate-limit, GitHub, and upload settings.
 */
@ConfigurationProperties(prefix = "app")
public record AppProperties(
        String name,
        String tagline,
        CorsProperties cors,
        JwtProperties jwt,
        RateLimitProperties rateLimit,
        GithubProperties github,
        UploadsProperties uploads
) {
    public AppProperties {
        if (github == null) {
            github = new GithubProperties(null, 5000, 10000, null);
        }
        if (uploads == null) {
            uploads = new UploadsProperties("./uploads", 2L * 1024 * 1024);
        }
    }

    public AppProperties(
            String name,
            String tagline,
            CorsProperties cors,
            JwtProperties jwt,
            RateLimitProperties rateLimit,
            GithubProperties github
    ) {
        this(name, tagline, cors, jwt, rateLimit, github, null);
    }

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

    public record GithubProperties(
            String apiBaseUrl,
            int connectTimeoutMs,
            int readTimeoutMs,
            String tokenEncryptionKey
    ) {
        public GithubProperties {
            if (apiBaseUrl == null || apiBaseUrl.isBlank()) {
                apiBaseUrl = "https://api.github.com";
            }
            if (connectTimeoutMs <= 0) {
                connectTimeoutMs = 5000;
            }
            if (readTimeoutMs <= 0) {
                readTimeoutMs = 10000;
            }
        }
    }

    public record UploadsProperties(
            String dir,
            long maxFileSizeBytes
    ) {
        public UploadsProperties {
            if (dir == null || dir.isBlank()) {
                dir = "./uploads";
            }
            if (maxFileSizeBytes <= 0) {
                maxFileSizeBytes = 2L * 1024 * 1024;
            }
        }
    }
}
