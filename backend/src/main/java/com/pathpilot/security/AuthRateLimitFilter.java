package com.pathpilot.security;

import com.pathpilot.common.ApiErrorResponse;
import com.pathpilot.config.AppProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Per-IP sliding-window rate limiter protecting /api/auth/login and /api/auth/register.
 */
@Component
public class AuthRateLimitFilter extends OncePerRequestFilter {

    private final int maxAttempts;
    private final long windowMillis;
    private final ObjectMapper objectMapper;
    private final ConcurrentHashMap<String, Deque<Long>> attemptsByIp = new ConcurrentHashMap<>();

    public AuthRateLimitFilter(AppProperties appProperties, ObjectMapper objectMapper) {
        this.maxAttempts = appProperties.rateLimit().authMaxAttempts();
        this.windowMillis = appProperties.rateLimit().authWindowSeconds() * 1000L;
        this.objectMapper = objectMapper;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        if (!"POST".equalsIgnoreCase(request.getMethod())) {
            return true;
        }
        String path = request.getRequestURI();
        return !"/api/auth/login".equals(path) && !"/api/auth/register".equals(path);
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        String key = request.getRemoteAddr() + "|" + request.getRequestURI();
        long now = System.currentTimeMillis();

        Deque<Long> timestamps = attemptsByIp.computeIfAbsent(key, ignored -> new ArrayDeque<>());
        boolean allowed;
        long retryAfterSeconds = 1L;

        synchronized (timestamps) {
            long cutoff = now - windowMillis;
            while (!timestamps.isEmpty() && timestamps.peekFirst() <= cutoff) {
                timestamps.pollFirst();
            }

            if (timestamps.size() >= maxAttempts) {
                allowed = false;
                long oldest = timestamps.peekFirst();
                retryAfterSeconds = Math.max(1L, ((oldest + windowMillis) - now + 999L) / 1000L);
            } else {
                timestamps.addLast(now);
                allowed = true;
            }
        }

        if (!allowed) {
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setHeader("Retry-After", String.valueOf(retryAfterSeconds));
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            ApiErrorResponse error = new ApiErrorResponse(
                    Instant.now(),
                    HttpStatus.TOO_MANY_REQUESTS.value(),
                    "RATE_LIMIT_EXCEEDED",
                    "Too many attempts. Please wait a minute and try again.",
                    Map.of()
            );
            objectMapper.writeValue(response.getOutputStream(), error);
            return;
        }

        filterChain.doFilter(request, response);
    }

    public void clearAll() {
        attemptsByIp.clear();
    }
}
