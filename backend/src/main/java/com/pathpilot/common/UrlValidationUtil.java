package com.pathpilot.common;

import org.springframework.http.HttpStatus;

import java.net.URI;
import java.util.Map;

/**
 * Shared server-side URL validator so profile and project links follow strict scheme rules.
 */
public final class UrlValidationUtil {

    private UrlValidationUtil() {
    }

    public static String validateOptionalHttpUrl(String raw, String field) {
        return validateOptionalUrl(raw, field, false);
    }

    public static String validateOptionalHttpsUrl(String raw, String field) {
        return validateOptionalUrl(raw, field, true);
    }

    private static String validateOptionalUrl(String raw, String field, boolean httpsOnly) {
        String trimmed = trimToNull(raw);
        if (trimmed == null) {
            return null;
        }
        try {
            URI uri = URI.create(trimmed);
            String scheme = uri.getScheme();
            boolean allowed = httpsOnly
                    ? "https".equalsIgnoreCase(scheme)
                    : ("https".equalsIgnoreCase(scheme) || "http".equalsIgnoreCase(scheme));
            if (scheme == null || !allowed) {
                String msg = httpsOnly
                        ? "URL must start with https://."
                        : "URL must start with http:// or https://.";
                throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", msg, Map.of(field, msg));
            }
            if (uri.getHost() == null || uri.getHost().isBlank()) {
                throw new ApiException(
                        HttpStatus.BAD_REQUEST,
                        "VALIDATION_ERROR",
                        "Enter a valid URL.",
                        Map.of(field, "Enter a valid URL.")
                );
            }
            return trimmed;
        } catch (IllegalArgumentException ex) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "VALIDATION_ERROR",
                    "Enter a valid URL.",
                    Map.of(field, "Enter a valid URL.")
            );
        }
    }

    public static String trimToNull(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
