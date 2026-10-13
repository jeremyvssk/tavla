// Token buckets in Redis, shared by every backend instance: bursts are allowed, the average is held.
package com.iloveshopping.ratelimit;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;

/**
 * Each key is a bucket holding up to {@code capacity} tokens that refills evenly, {@code capacity} tokens per
 * {@code refillPeriod}. A request takes one token; an empty bucket answers 429. A fixed window (what this
 * replaced) lets twice the limit through across a window edge; a bucket has no edge to aim at.
 *
 * <p>The refill is lazy: the hash stores the tokens and the time they were counted, and each call adds what
 * the elapsed time earned before taking one. Read, refill, take and write are one Lua script, so two
 * requests can never both spend the last token, and the clock is Redis's own TIME, so instances whose
 * clocks disagree still share one timeline.
 */
@Service
public class RateLimiter {

    private static final String KEY_PREFIX = "rate_bucket:";

    // ARGV: capacity, refill rate in tokens per millisecond. Returns {allowed (1/0), ms until a token}.
    // The key expires once a full refill has passed: an idle bucket is full anyway, so it costs no memory.
    @SuppressWarnings("rawtypes")
    private static final RedisScript<List> TAKE_SCRIPT = new DefaultRedisScript<>("""
            local capacity = tonumber(ARGV[1])
            local rate = tonumber(ARGV[2])
            local time = redis.call('TIME')
            local now = tonumber(time[1]) * 1000 + math.floor(tonumber(time[2]) / 1000)
            local state = redis.call('HMGET', KEYS[1], 'tokens', 'at')
            local tokens = tonumber(state[1])
            local at = tonumber(state[2])
            if tokens == nil or at == nil then
                tokens = capacity
                at = now
            end
            tokens = math.min(capacity, tokens + math.max(0, now - at) * rate)
            local allowed = 0
            local wait = 0
            if tokens >= 1 then
                tokens = tokens - 1
                allowed = 1
            else
                wait = math.ceil((1 - tokens) / rate)
            end
            redis.call('HSET', KEYS[1], 'tokens', tostring(tokens), 'at', tostring(now))
            redis.call('PEXPIRE', KEYS[1], math.ceil(capacity / rate))
            return {allowed, wait}
            """, List.class);

    private final StringRedisTemplate redis;
    private final int multiplier;

    public RateLimiter(StringRedisTemplate redis, @Value("${app.rate-limit.multiplier:1}") int multiplier) {
        this.redis = redis;
        // Only the load test's stack raises this (k6's virtual users all share one address); see loadtest/.
        this.multiplier = Math.max(1, multiplier);
    }

    /**
     * Takes one token from the key's bucket. The bucket holds {@code capacity} tokens and earns them back
     * evenly over {@code refillPeriod}: capacity 10 over a minute is a burst of 10, then one every 6 seconds.
     */
    public Result hit(String key, int capacity, Duration refillPeriod) {
        int scaled = capacity * multiplier;
        double perMilli = (double) scaled / refillPeriod.toMillis();
        List<?> reply = redis.execute(TAKE_SCRIPT, List.of(KEY_PREFIX + key),
                String.valueOf(scaled), String.valueOf(perMilli));
        boolean allowed = ((Number) reply.get(0)).longValue() == 1;
        long waitMillis = ((Number) reply.get(1)).longValue();
        // Rounded up, so a client that waits exactly Retry-After seconds finds a token waiting.
        return new Result(allowed, Math.max(1, (waitMillis + 999) / 1000));
    }

    public void reset(String key) {
        redis.delete(KEY_PREFIX + key);
    }

    public record Result(boolean allowed, long retryAfterSeconds) {
    }
}
