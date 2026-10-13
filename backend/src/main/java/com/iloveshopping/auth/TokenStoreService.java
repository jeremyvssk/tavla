// Redis-backed store for refresh tokens and the access-token revocation blocklist.
package com.iloveshopping.auth;

import com.iloveshopping.auth.exception.InvalidRefreshTokenException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
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
 *   refresh_token:{hash}           -> userId, or "userId 2fa" once the session passed a second factor
 *                                     (TTL = refresh lifetime)
 *   refresh_tokens_user:{uid}      -> Set<hash>
 *   token_blocklist:{jti}          -> "1"      (TTL = remaining access-token lifetime)
 *   tokens_revoked_before:{uid}    -> epoch second; access tokens issued at or before it are refused
 *                                     (TTL = access-token lifetime: by then every such token has expired)
 */
@Service
public class TokenStoreService {

    private static final String REFRESH_KEY = "refresh_token:";
    private static final String USER_SET_KEY = "refresh_tokens_user:";
    private static final String BLOCKLIST_KEY = "token_blocklist:";
    private static final String USED_KEY = "used_refresh_token:";
    private static final String CUTOFF_KEY = "tokens_revoked_before:";
    private static final String MFA_SUFFIX = " 2fa";

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

    // Marks a live session as having passed a second factor, keeping its TTL. Only if the token is still
    // this user's: a token rotated away in the meantime is left alone, and nobody else's session is touched.
    private static final RedisScript<Long> UPGRADE_SCRIPT = new DefaultRedisScript<>("""
            local owner = redis.call('GET', KEYS[1])
            if not owner or (owner ~= ARGV[1] and owner ~= ARGV[2]) then return 0 end
            redis.call('SET', KEYS[1], ARGV[2], 'KEEPTTL')
            return 1
            """, Long.class);

    private final StringRedisTemplate redis;
    private final Duration refreshTtl;
    private final Duration accessTtl;

    public TokenStoreService(StringRedisTemplate redis,
                             @Value("${app.refresh-token.ttl}") String refreshTtl,
                             @Value("${app.jwt.access-token-ttl}") String accessTtl) {
        this.redis = redis;
        this.refreshTtl = Duration.parse(refreshTtl);
        this.accessTtl = Duration.parse(accessTtl);
    }

    /** Who a refresh token signs in, and whether that session passed a second factor. */
    public record Session(UUID userId, boolean mfa) {

        String encode() {
            return mfa ? userId + MFA_SUFFIX : userId.toString();
        }

        /** A value written before sessions carried the flag is a plain user id: no second factor. */
        static Session decode(String value) {
            return value.endsWith(MFA_SUFFIX)
                    ? new Session(UUID.fromString(value.substring(0, value.length() - MFA_SUFFIX.length())), true)
                    : new Session(UUID.fromString(value), false);
        }
    }

    /** Issues a new refresh token for the session and returns the raw value to hand to the client. */
    public String issueRefreshToken(Session session) {
        String raw = OpaqueTokens.generate();
        String hash = OpaqueTokens.sha256(raw);
        redis.opsForValue().set(REFRESH_KEY + hash, session.encode(), refreshTtl);
        redis.opsForSet().add(USER_SET_KEY + session.userId(), hash);
        return raw;
    }

    /**
     * Atomically swaps a valid refresh token for a fresh one (rotation). The old token is
     * single-use: once rotated it is deleted, so a replay is rejected. The new token belongs to
     * the same session, second factor and all.
     *
     * @return the raw replacement token
     * @throws InvalidRefreshTokenException if the presented token is unknown or belongs to another session
     */
    public String rotateRefreshToken(Session session, String rawToken) {
        String oldHash = OpaqueTokens.sha256(rawToken);
        String newRaw = OpaqueTokens.generate();
        String newHash = OpaqueTokens.sha256(newRaw);

        Long result = redis.execute(
                ROTATE_SCRIPT,
                List.of(REFRESH_KEY + oldHash, REFRESH_KEY + newHash, USER_SET_KEY + session.userId(),
                        USED_KEY + oldHash),
                session.encode(), oldHash, newHash, String.valueOf(refreshTtl.toSeconds()));

        if (result == null || result == 0L) {
            throw new InvalidRefreshTokenException();
        }
        return newRaw;
    }

    /** Resolves the session behind a refresh token, or empty if it is unknown or expired. */
    public Optional<Session> findSessionByRefreshToken(String rawToken) {
        String value = redis.opsForValue().get(REFRESH_KEY + OpaqueTokens.sha256(rawToken));
        return Optional.ofNullable(value).map(Session::decode);
    }

    /**
     * After the user switched 2FA on with a valid code, the session they did it in has passed the
     * second factor, so its later refreshes keep the flag. A no-op for a token that isn't theirs.
     */
    public void markSecondFactor(UUID userId, String rawToken) {
        redis.execute(UPGRADE_SCRIPT, List.of(REFRESH_KEY + OpaqueTokens.sha256(rawToken)),
                userId.toString(), new Session(userId, true).encode());
    }

    /**
     * Resolves the owner of a refresh token that has already been rotated away. A hit means the
     * presented token was spent earlier and is being replayed — which is only possible if two
     * parties hold it, so the caller treats it as theft.
     */
    public Optional<UUID> findUserIdByUsedRefreshToken(String rawToken) {
        String value = redis.opsForValue().get(USED_KEY + OpaqueTokens.sha256(rawToken));
        return Optional.ofNullable(value).map(v -> Session.decode(v).userId());
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

    /**
     * Ends every access token the user holds right now: each one issued at or before this second is
     * refused from here on. Used when a role changes, so a demoted user's token stops carrying the old
     * role at once instead of for up to its last 15 minutes. Same-second tokens are refused too, at the
     * price of one 401 (and a refresh) for a request that lands in that exact second.
     */
    public void revokeAccessTokens(UUID userId) {
        redis.opsForValue().set(CUTOFF_KEY + userId, String.valueOf(Instant.now().getEpochSecond()), accessTtl);
    }

    /** The token's own blocklist entry and its user's cut-off, read together in one round trip. */
    public boolean isAccessTokenRevoked(String jti, UUID userId, Instant issuedAt) {
        List<String> values = redis.opsForValue().multiGet(List.of(BLOCKLIST_KEY + jti, CUTOFF_KEY + userId));
        if (values == null) {
            return false;
        }
        if (values.get(0) != null) {
            return true;
        }
        String cutoff = values.get(1);
        return cutoff != null && issuedAt.getEpochSecond() <= Long.parseLong(cutoff);
    }

    /** Refresh token lifetime, used to align the client cookie's Max-Age with the Redis TTL. */
    public Duration refreshTokenTtl() {
        return refreshTtl;
    }
}
