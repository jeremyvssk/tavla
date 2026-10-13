// Integration tests for the token bucket itself against real Redis: burst, refill over time, Retry-After, and the race.
package com.iloveshopping.security;

import com.iloveshopping.ratelimit.RateLimiter;
import com.iloveshopping.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;

class TokenBucketIT extends AbstractIntegrationTest {

    @Autowired
    private RateLimiter limiter;

    @Autowired
    private StringRedisTemplate redis;

    @Test
    void aFullBucket_allowsItsCapacityAtOnce_thenRefuses() {
        String key = key();
        for (int i = 0; i < 5; i++) {
            assertThat(limiter.hit(key, 5, Duration.ofMinutes(1)).allowed()).isTrue();
        }
        RateLimiter.Result refused = limiter.hit(key, 5, Duration.ofMinutes(1));
        assertThat(refused.allowed()).isFalse();
        // 5 a minute is one token every 12 seconds, and the bucket was just emptied
        assertThat(refused.retryAfterSeconds()).isBetween(11L, 12L);
    }

    @Test
    void tokensComeBack_atTheRefillRate_notAllAtOnce() throws Exception {
        // Capacity 10 earned back over 10 seconds: one token a second. q8a on the plan, scaled down.
        String key = key();
        for (int i = 0; i < 10; i++) {
            limiter.hit(key, 10, Duration.ofSeconds(10));
        }
        assertThat(limiter.hit(key, 10, Duration.ofSeconds(10)).allowed()).isFalse();

        Thread.sleep(2_100);
        int passed = 0;
        for (int i = 0; i < 10; i++) {
            if (limiter.hit(key, 10, Duration.ofSeconds(10)).allowed()) {
                passed++;
            }
        }
        assertThat(passed).isEqualTo(2);
    }

    @Test
    void fiftyRequestsAtTheSameMoment_letExactlyTheCapacityThrough() throws Exception {
        // The read-refill-take-write is one Lua script. Done as a GET and a SET from Java, two threads
        // read the same count and both pass, and this test lets more than 20 through.
        String key = key();
        int threads = 50;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        try {
            CountDownLatch start = new CountDownLatch(1);
            List<Future<Boolean>> results = new ArrayList<>();
            for (int i = 0; i < threads; i++) {
                Callable<Boolean> take = () -> {
                    start.await();
                    return limiter.hit(key, 20, Duration.ofHours(1)).allowed();
                };
                results.add(pool.submit(take));
            }
            start.countDown();
            int allowed = 0;
            for (Future<Boolean> f : results) {
                if (f.get()) {
                    allowed++;
                }
            }
            assertThat(allowed).isEqualTo(20);
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void anIdleBucket_expiresOnceItWouldBeFullAgain() {
        String key = key();
        limiter.hit(key, 10, Duration.ofSeconds(30));
        Long ttl = redis.getExpire("rate_bucket:" + key);
        assertThat(ttl).isBetween(1L, 30L);
    }

    private static String key() {
        return "test:" + UUID.randomUUID();
    }
}
