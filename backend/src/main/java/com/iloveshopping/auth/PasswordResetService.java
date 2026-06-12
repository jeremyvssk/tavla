// Forgot/reset-password flow: issues single-use reset tokens (Redis) and applies new passwords.
package com.iloveshopping.auth;

import com.iloveshopping.auth.exception.InvalidResetTokenException;
import com.iloveshopping.user.UserService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.UUID;

/**
 * Reset tokens are opaque random strings; only their SHA-256 hash is stored in Redis, keyed
 * {@code pwreset:{hash}} with a 15-minute TTL. The raw token lives only in the emailed link.
 * On confirm the token is consumed (single-use) and every refresh token for the user is revoked,
 * so a reset triggered after a device theft kills the thief's existing sessions.
 */
@Service
public class PasswordResetService {

    private static final String RESET_KEY = "pwreset:";
    private static final Duration TTL = Duration.ofMinutes(15);

    private final StringRedisTemplate redis;
    private final UserService userService;
    private final EmailService emailService;
    private final TokenStoreService tokenStore;
    private final String resetUrl;
    private final SecureRandom random = new SecureRandom();

    public PasswordResetService(StringRedisTemplate redis, UserService userService,
                                EmailService emailService, TokenStoreService tokenStore,
                                @Value("${app.frontend.reset-url}") String resetUrl) {
        this.redis = redis;
        this.userService = userService;
        this.emailService = emailService;
        this.tokenStore = tokenStore;
        this.resetUrl = resetUrl;
    }

    /**
     * Starts a reset. If the email maps to a user, store a token fingerprint in Redis and email
     * the raw token. Silent on a miss so the endpoint can't be used to probe which emails exist.
     */
    public void requestReset(String email) {
        userService.findByEmail(email).ifPresent(user -> {
            String rawToken = randomToken();
            redis.opsForValue().set(RESET_KEY + sha256(rawToken), user.getId().toString(), TTL);
            emailService.sendPasswordReset(user.getEmail(), resetUrl + "?token=" + rawToken);
        });
    }

    /**
     * Completes a reset: validates the token, sets the new password, consumes the token
     * (single-use), and revokes every refresh token so existing sessions die.
     */
    public void confirmReset(String rawToken, String newPassword) {
        String key = RESET_KEY + sha256(rawToken);
        String userId = redis.opsForValue().get(key);
        if (userId == null) {
            throw new InvalidResetTokenException();
        }
        UUID uid = UUID.fromString(userId);
        userService.updatePassword(uid, newPassword);
        redis.delete(key);
        tokenStore.revokeAllRefreshTokens(uid);
    }

    private String randomToken() {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}
