// Fixed-window request counters in Redis, shared by every backend instance.
package com.iloveshopping.ratelimit;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;

/**
 * Fixed window rather than a sliding window or token bucket: one counter per key, one Lua call per
 * request. The known cost is a burst of up to twice the limit across a window boundary, which is
 * harmless at these limits. Keys live under {@code rate_limit:} with the window as their TTL.
 */
@Service
public class RateLimiter {

    private static final String KEY_PREFIX = "rate_limit:";

    // INCR and EXPIRE in one script. As two calls, a failure between them leaves a counter with no
    // TTL, and that key stays over its limit forever. PTTL < 0 also repairs such a key if one exists.
    @SuppressWarnings("rawtypes")
    private static final RedisScript<List> HIT_SCRIPT = new DefaultRedisScript<>("""
            local count = redis.call('INCR', KEYS[1])
            if redis.call('PTTL', KEYS[1]) < 0 then
                redis.call('PEXPIRE', KEYS[1], ARGV[1])
            end
            return {count, redis.call('PTTL', KEYS[1])}
            """, List.class);

    private final StringRedisTemplate redis;

    public RateLimiter(StringRedisTemplate redis) {
        this.redis = redis;
    }

    /** Counts one hit against the key and reports whether it is still within the limit. */
    public Result hit(String key, int limit, Duration window) {
        List<?> reply = redis.execute(HIT_SCRIPT, List.of(KEY_PREFIX + key), String.valueOf(window.toMillis()));
        long count = ((Number) reply.get(0)).longValue();
        long ttlMillis = ((Number) reply.get(1)).longValue();
        // Rounded up, so a client that waits exactly Retry-After seconds never lands a moment early.
        return new Result(count <= limit, Math.max(1, (ttlMillis + 999) / 1000));
    }

    public void reset(String key) {
        redis.delete(KEY_PREFIX + key);
    }

    public record Result(boolean allowed, long retryAfterSeconds) {
    }
}
