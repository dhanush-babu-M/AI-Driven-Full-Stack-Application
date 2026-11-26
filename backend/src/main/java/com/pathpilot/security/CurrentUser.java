package com.pathpilot.security;

import com.pathpilot.common.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Reads the authenticated user strictly from Spring SecurityContext.
 * Every user-owned repository query must filter by {@link #id()}, never a user id from the client.
 */
public final class CurrentUser {

    private CurrentUser() {
    }

    public static AuthUserPrincipal principal() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "Authentication required.");
        }
        if (authentication.getPrincipal() instanceof AuthUserPrincipal principal) {
            return principal;
        }
        throw new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "Authentication required.");
    }

    public static Long id() {
        return principal().userId();
    }
}
