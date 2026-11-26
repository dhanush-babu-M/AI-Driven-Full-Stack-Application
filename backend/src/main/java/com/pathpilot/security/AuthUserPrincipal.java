package com.pathpilot.security;

/**
 * Authenticated user identity stored inside Spring SecurityContext.
 */
public record AuthUserPrincipal(
        Long userId,
        String email,
        String role
) {
}
