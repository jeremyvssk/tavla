// Forgot/reset-password flow: issues single-use reset tokens (Redis) and applies new passwords.
package com.iloveshopping.auth;

import com.iloveshopping.auth.exception.InvalidResetTokenException;
import com.iloveshopping.user.UserService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
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
    private final AccountThrottle accountThrottle;
    private final String resetUrl;

    public PasswordResetService(StringRedisTemplate redis, UserService userService,
                                EmailService emailService, TokenStoreService tokenStore,
                                AccountThrottle accountThrottle,
                                @Value("${app.frontend.reset-url}") String resetUrl) {
        this.redis = redis;
        this.userService = userService;
        this.emailService = emailService;
        this.tokenStore = tokenStore;
        this.accountThrottle = accountThrottle;
        this.resetUrl = resetUrl;
    }

    /**
     * Starts a reset. If the email maps to a user, store a token fingerprint in Redis and email
     * the raw token. Silent on a miss so the endpoint can't be used to probe which emails exist.
     * Also silent over the per-address email limit, which stops the endpoint flooding someone's inbox.
     */
    public void requestReset(String email) {
        if (!accountThrottle.allowResetEmail(email)) {
            return;
        }
        userService.findByEmail(email).ifPresent(user -> {
            String rawToken = OpaqueTokens.generate();
            redis.opsForValue().set(RESET_KEY + OpaqueTokens.sha256(rawToken), user.getId().toString(), TTL);
            emailService.sendPasswordReset(user.getEmail(), resetUrl + "?token=" + rawToken);
        });
    }

    /**
     * Completes a reset: validates the token, sets the new password, consumes the token
     * (single-use), revokes every refresh token so existing sessions die, and lifts any login
     * lockout on the account: whoever holds the emailed link owns the inbox.
     */
    public void confirmReset(String rawToken, String newPassword) {
        String key = RESET_KEY + OpaqueTokens.sha256(rawToken);
        String userId = redis.opsForValue().get(key);
        if (userId == null) {
            throw new InvalidResetTokenException();
        }
        UUID uid = UUID.fromString(userId);
        userService.updatePassword(uid, newPassword);
        redis.delete(key);
        tokenStore.revokeAllRefreshTokens(uid);
        accountThrottle.clearLogin(userService.getById(uid).getEmail());
    }
}
