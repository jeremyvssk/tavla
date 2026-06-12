// TOTP two-factor: secret/QR setup, enable with backup codes, disable, and login-challenge handling.
package com.iloveshopping.auth;

import com.iloveshopping.auth.dto.TwoFactorSetupResponse;
import com.iloveshopping.auth.exception.InvalidCredentialsException;
import com.iloveshopping.auth.exception.InvalidTwoFactorCodeException;
import com.iloveshopping.user.User;
import com.iloveshopping.user.UserService;
import dev.samstevens.totp.code.CodeVerifier;
import dev.samstevens.totp.code.DefaultCodeGenerator;
import dev.samstevens.totp.code.DefaultCodeVerifier;
import dev.samstevens.totp.code.HashingAlgorithm;
import dev.samstevens.totp.qr.QrData;
import dev.samstevens.totp.recovery.RecoveryCodeGenerator;
import dev.samstevens.totp.secret.DefaultSecretGenerator;
import dev.samstevens.totp.secret.SecretGenerator;
import dev.samstevens.totp.time.SystemTimeProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 2FA is enrolled in two steps: {@link #setup} stores a secret but leaves 2FA off; {@link #enable}
 * verifies the first code, flips it on, and returns one-time backup codes. At login, a user with 2FA
 * on gets a short-lived Redis challenge ({@code 2fa_pending:{hash}}, 5-min TTL) instead of tokens;
 * the second step verifies a TOTP or backup code before real tokens are issued. Backup codes are
 * stored only as SHA-256 hashes (comma-separated) and removed as they are used.
 */
@Service
public class TwoFactorService {

    private static final String PENDING_KEY = "2fa_pending:";
    private static final Duration CHALLENGE_TTL = Duration.ofMinutes(5);
    private static final int BACKUP_CODE_COUNT = 8;

    private final StringRedisTemplate redis;
    private final UserService userService;
    private final String issuer;

    private final SecretGenerator secretGenerator = new DefaultSecretGenerator();
    private final RecoveryCodeGenerator recoveryCodeGenerator = new RecoveryCodeGenerator();
    private final CodeVerifier codeVerifier =
            new DefaultCodeVerifier(new DefaultCodeGenerator(), new SystemTimeProvider());
    private final SecureRandom random = new SecureRandom();

    public TwoFactorService(StringRedisTemplate redis, UserService userService,
                            @Value("${app.two-factor.issuer}") String issuer) {
        this.redis = redis;
        this.userService = userService;
        this.issuer = issuer;
    }

    /** Generates a fresh secret (2FA stays disabled until verified) and returns the otpauth:// URI. */
    public TwoFactorSetupResponse setup(User user) {
        String secret = secretGenerator.generate();
        userService.setTwoFactorSecret(user.getId(), secret);
        return new TwoFactorSetupResponse(secret, otpauthUri(user.getEmail(), secret));
    }

    /**
     * Verifies the first TOTP code, enables 2FA, and returns the plaintext backup codes (shown once).
     * @throws InvalidTwoFactorCodeException if setup wasn't done or the code is wrong.
     */
    public List<String> enable(User user, String code) {
        if (user.getTwoFactorSecret() == null || !codeVerifier.isValidCode(user.getTwoFactorSecret(), code)) {
            throw new InvalidTwoFactorCodeException();
        }
        List<String> backupCodes = List.of(recoveryCodeGenerator.generateCodes(BACKUP_CODE_COUNT));
        String hashed = backupCodes.stream().map(TwoFactorService::sha256).collect(Collectors.joining(","));
        userService.enableTwoFactor(user.getId(), hashed);
        return backupCodes;
    }

    /**
     * Disables 2FA after re-confirming the account password.
     * @throws InvalidCredentialsException if the password is wrong.
     */
    public void disable(User user, String password) {
        if (!userService.passwordMatches(user, password)) {
            throw new InvalidCredentialsException();
        }
        userService.disableTwoFactor(user.getId());
    }

    /** Stores a single-use login challenge and returns the raw token to hand back to the client. */
    public String startChallenge(User user) {
        String token = randomToken();
        redis.opsForValue().set(PENDING_KEY + sha256(token), user.getId().toString(), CHALLENGE_TTL);
        return token;
    }

    /** Resolves the user behind a challenge token without consuming it (so a wrong code can be retried). */
    public Optional<UUID> peekChallenge(String token) {
        String userId = redis.opsForValue().get(PENDING_KEY + sha256(token));
        return Optional.ofNullable(userId).map(UUID::fromString);
    }

    /** Consumes (deletes) a challenge token so it can't be reused. */
    public void consumeChallenge(String token) {
        redis.delete(PENDING_KEY + sha256(token));
    }

    /** True if the code is the current TOTP or an unused backup code (which is then consumed). */
    public boolean verifyCode(User user, String code) {
        if (user.getTwoFactorSecret() != null && codeVerifier.isValidCode(user.getTwoFactorSecret(), code)) {
            return true;
        }
        return consumeBackupCode(user, code);
    }

    private boolean consumeBackupCode(User user, String code) {
        String stored = user.getTwoFactorBackupCodes();
        if (stored == null || stored.isBlank()) {
            return false;
        }
        List<String> hashes = new ArrayList<>(Arrays.asList(stored.split(",")));
        if (!hashes.remove(sha256(code))) {
            return false;
        }
        userService.replaceBackupCodes(user.getId(), String.join(",", hashes));
        return true;
    }

    private String otpauthUri(String email, String secret) {
        return new QrData.Builder()
                .label(email)
                .secret(secret)
                .issuer(issuer)
                .algorithm(HashingAlgorithm.SHA1)
                .digits(6)
                .period(30)
                .build()
                .getUri();
    }

    private String randomToken() {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}
