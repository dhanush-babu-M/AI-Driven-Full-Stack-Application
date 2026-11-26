package com.pathpilot.auth.dto;

import java.time.Instant;

/**
 * Public user representation returned by auth endpoints. Never includes password hashes.
 */
public record UserDto(
        Long id,
        String fullName,
        String email,
        String phone,
        String role,
        String timeZone,
        Instant createdAtUtc
) {
}
