package com.pathpilot.ai;

import com.pathpilot.common.ApiErrorResponse;
import com.pathpilot.config.AppProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
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
 * Per-user sliding-window rate limiter protecting /api/ai/** endpoints.
 */
@Component
public class AiRateLimitFilter extends OncePerRequestFilter {

    private final int maxRequests;
    private final long windowMillis;
    private final ObjectMapper objectMapper;
    private final ConcurrentHashMap<String, Deque<Long>> requestsByUser = new ConcurrentHashMap<>();

    public AiRateLimitFilter(AppProperties appProperties, ObjectMapper objectMapper) {
        this.maxRequests = appProperties.ai().rateLimitMax();
        this.windowMillis = appProperties.ai().rateLimitWindowSeconds() * 1000L;
        this.objectMapper = objectMapper;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        if (path == null || !path.startsWith("/api/ai/")) {
            return true;
        }
        // Only rate-limit generation/agent endpoints; allow reading/reviewing proposals freely.
        return path.startsWith("/api/ai/proposals");
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || auth.getPrincipal() == null) {
            filterChain.doFilter(request, response);
            return;
        }

        String userKey = String.valueOf(auth.getPrincipal());
        long now = System.currentTimeMillis();
        Deque<Long> timestamps = requestsByUser.computeIfAbsent(userKey, ignored -> new ArrayDeque<>());

        boolean allowed;
        long retryAfterSeconds = 1L;
        synchronized (timestamps) {
            long cutoff = now - windowMillis;
            while (!timestamps.isEmpty() && timestamps.peekFirst() <= cutoff) {
                timestamps.pollFirst();
            }
            if (timestamps.size() >= maxRequests) {
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
                    "AI_RATE_LIMIT_EXCEEDED",
                    "You have reached the AI request rate limit. Please wait a moment and try again.",
                    Map.of()
            );
            objectMapper.writeValue(response.getOutputStream(), error);
            return;
        }

        filterChain.doFilter(request, response);
    }

    public void clearAll() {
        requestsByUser.clear();
    }
}
