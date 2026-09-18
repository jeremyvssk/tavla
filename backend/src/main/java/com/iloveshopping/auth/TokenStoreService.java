// Redis-backed store for refresh tokens and the access-token revocation blocklist.
package com.iloveshopping.auth;

import com.iloveshopping.auth.exception.InvalidRefreshTokenException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Refresh tokens are opaque random strings; only their SHA-256 hash is stored, so a
 * Redis dump never exposes a usable token. Each user's live hashes are tracked in a
 * set so password reset can revoke them all at once.
 *
 * Storage map:
 *   refresh_token:{hash}        -> userId   (TTL = refresh lifetime)
 *   refresh_tokens_user:{uid}   -> Set<hash>
 *   token_blocklist:{jti}       -> "1"      (TTL = remaining access-token lifetime)
 */
@Service
public class TokenStoreService {

    private static final String REFRESH_KEY = "refresh_token:";
    private static final String USER_SET_KEY = "refresh_tokens_user:";
    private static final String BLOCKLIST_KEY = "token_blocklist:";
    private static final String USED_KEY = "used_refresh_token:";

    // Atomic single-use rotation: reject unless the old hash still maps to this user,
    // then in one step delete the old token and store the new one. A replayed old token
    // finds the key gone and fails. Returns 1 on success, 0 on rejection.
    // KEYS[4] is the tombstone: the spent hash is remembered for the rest of the refresh
    // lifetime so a later replay can still be attributed to a user (reuse detection).
    private static final RedisScript<Long> ROTATE_SCRIPT = new DefaultRedisScript<>("""
            local owner = redis.call('GET', KEYS[1])
            if not owner or owner ~= ARGV[1] then return 0 end
            redis.call('DEL', KEYS[1])
            redis.call('SREM', KEYS[3], ARGV[2])
            redis.call('SET', KEYS[4], ARGV[1], 'EX', ARGV[4])
            redis.call('SET', KEYS[2], ARGV[1], 'EX', ARGV[4])
            redis.call('SADD', KEYS[3], ARGV[3])
            return 1
            """, Long.class);

    private final StringRedisTemplate redis;
    private final Duration refreshTtl;

    public TokenStoreService(StringRedisTemplate redis,
                             @Value("${app.refresh-token.ttl}") String refreshTtl) {
        this.redis = redis;
        this.refreshTtl = Duration.parse(refreshTtl);
    }

    /** Issues a new refresh token for the user and returns the raw value to hand to the client. */
    public String issueRefreshToken(UUID userId) {
        String raw = OpaqueTokens.generate();
        String hash = OpaqueTokens.sha256(raw);
        redis.opsForValue().set(REFRESH_KEY + hash, userId.toString(), refreshTtl);
        redis.opsForSet().add(USER_SET_KEY + userId, hash);
        return raw;
    }

    /**
     * Atomically swaps a valid refresh token for a fresh one (rotation). The old token is
     * single-use: once rotated it is deleted, so a replay is rejected.
     *
     * @return the raw replacement token
     * @throws InvalidRefreshTokenException if the presented token is unknown or not owned by the user
     */
    public String rotateRefreshToken(UUID userId, String rawToken) {
        String oldHash = OpaqueTokens.sha256(rawToken);
        String newRaw = OpaqueTokens.generate();
        String newHash = OpaqueTokens.sha256(newRaw);

        Long result = redis.execute(
                ROTATE_SCRIPT,
                List.of(REFRESH_KEY + oldHash, REFRESH_KEY + newHash, USER_SET_KEY + userId,
                        USED_KEY + oldHash),
                userId.toString(), oldHash, newHash, String.valueOf(refreshTtl.toSeconds()));

        if (result == null || result == 0L) {
            throw new InvalidRefreshTokenException();
        }
        return newRaw;
    }

    /** Resolves the owning user of a refresh token, or empty if it is unknown or expired. */
    public Optional<UUID> findUserIdByRefreshToken(String rawToken) {
        String userId = redis.opsForValue().get(REFRESH_KEY + OpaqueTokens.sha256(rawToken));
        return Optional.ofNullable(userId).map(UUID::fromString);
    }

    /**
     * Resolves the owner of a refresh token that has already been rotated away. A hit means the
     * presented token was spent earlier and is being replayed — which is only possible if two
     * parties hold it, so the caller treats it as theft.
     */
    public Optional<UUID> findUserIdByUsedRefreshToken(String rawToken) {
        String userId = redis.opsForValue().get(USED_KEY + OpaqueTokens.sha256(rawToken));
        return Optional.ofNullable(userId).map(UUID::fromString);
    }

    /**
     * Revokes a single refresh token (used on logout). Only the owner may revoke it: without the
     * check, any authenticated caller could end someone else's session by presenting their token.
     * No-op if it is already gone, so logout stays idempotent.
     */
    public void revokeRefreshToken(UUID userId, String rawToken) {
        String hash = OpaqueTokens.sha256(rawToken);
        String owner = redis.opsForValue().get(REFRESH_KEY + hash);
        if (userId.toString().equals(owner)) {
            redis.delete(REFRESH_KEY + hash);
        }
        redis.opsForSet().remove(USER_SET_KEY + userId, hash);
    }

    /** Revokes every refresh token for a user (used on password reset). */
    public void revokeAllRefreshTokens(UUID userId) {
        String setKey = USER_SET_KEY + userId;
        Set<String> hashes = redis.opsForSet().members(setKey);
        if (hashes != null && !hashes.isEmpty()) {
            redis.delete(hashes.stream().map(h -> REFRESH_KEY + h).toList());
        }
        redis.delete(setKey);
    }

    /** Blocklists an access token's JTI for its remaining lifetime so it stops validating. */
    public void blocklistAccessToken(String jti, Duration remainingTtl) {
        redis.opsForValue().set(BLOCKLIST_KEY + jti, "1", remainingTtl);
    }

    public boolean isAccessTokenBlocklisted(String jti) {
        return Boolean.TRUE.equals(redis.hasKey(BLOCKLIST_KEY + jti));
    }

    /** Refresh token lifetime, used to align the client cookie's Max-Age with the Redis TTL. */
    public Duration refreshTokenTtl() {
        return refreshTtl;
    }
}
