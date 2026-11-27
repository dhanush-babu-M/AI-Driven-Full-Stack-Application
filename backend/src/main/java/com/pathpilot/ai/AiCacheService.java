package com.pathpilot.ai;

import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Simple per-user cache for AI responses so repeated page loads do not spend LLM quota.
 */
@Service
public class AiCacheService {

    private final ConcurrentHashMap<String, CacheEntry> cache = new ConcurrentHashMap<>();

    public <T> Optional<T> get(Long userId, String feature, String key, Class<T> type) {
        String composite = buildKey(userId, feature, key);
        CacheEntry entry = cache.get(composite);
        if (entry == null) {
            return Optional.empty();
        }
        if (Instant.now().isAfter(entry.expiresAt())) {
            cache.remove(composite);
            return Optional.empty();
        }
        if (!type.isInstance(entry.value())) {
            return Optional.empty();
        }
        return Optional.of(type.cast(entry.value()));
    }

    public void put(Long userId, String feature, String key, Object value, long ttlSeconds) {
        if (value == null) return;
        cache.put(
                buildKey(userId, feature, key),
                new CacheEntry(value, Instant.now().plusSeconds(Math.max(60, ttlSeconds)))
        );
    }

    public void evictUser(Long userId) {
        String prefix = userId + "|";
        for (Map.Entry<String, CacheEntry> e : cache.entrySet()) {
            if (e.getKey().startsWith(prefix)) {
                cache.remove(e.getKey());
            }
        }
    }

    public void clearAll() {
        cache.clear();
    }

    private String buildKey(Long userId, String feature, String key) {
        return userId + "|" + feature + "|" + (key != null ? key : "");
    }

    private record CacheEntry(Object value, Instant expiresAt) {
    }
}
