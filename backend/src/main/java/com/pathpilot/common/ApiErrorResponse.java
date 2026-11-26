package com.pathpilot.common;

import java.time.Instant;
import java.util.Map;

/**
 * Standard JSON error shape returned by all failed API requests.
 */
public record ApiErrorResponse(
        Instant timestamp,
        int status,
        String code,
        String message,
        Map<String, String> fieldErrors
) {
}
