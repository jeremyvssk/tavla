// Orchestrates registration, login, refresh, and logout over the user and token services.
package com.iloveshopping.auth;

import com.iloveshopping.auth.exception.InvalidCredentialsException;
import com.iloveshopping.auth.exception.InvalidRefreshTokenException;
import com.iloveshopping.auth.exception.InvalidTwoFactorCodeException;
import com.iloveshopping.user.AuthProvider;
import com.iloveshopping.user.OAuthUserInfo;
import com.iloveshopping.user.User;
import com.iloveshopping.user.UserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private final UserService userService;
    private final JwtService jwtService;
    private final TokenStoreService tokenStore;
    private final GoogleTokenVerifier googleTokenVerifier;
    private final TwoFactorService twoFactorService;

    public AuthService(UserService userService, JwtService jwtService, TokenStoreService tokenStore,
                       GoogleTokenVerifier googleTokenVerifier, TwoFactorService twoFactorService) {
        this.userService = userService;
        this.jwtService = jwtService;
        this.tokenStore = tokenStore;
        this.googleTokenVerifier = googleTokenVerifier;
        this.twoFactorService = twoFactorService;
    }

    public User register(String email, String rawPassword, String fullName) {
        return userService.createLocalUser(email, rawPassword, fullName);
    }

    /**
     * Verifies the password. If 2FA is enabled, no tokens are issued yet — a short-lived challenge is
     * returned and the caller must complete {@link #twoFactorLogin}. Otherwise tokens are issued.
     */
    public LoginResult login(String email, String rawPassword) {
        Optional<User> found = userService.findByEmail(email);
        // OAuth accounts have no local password; reject before touching the (null) hash.
        // Both rejection paths still run a BCrypt comparison, so an unknown address costs the
        // same time as a known one and the response timing reveals nothing.
        if (found.isEmpty() || found.get().getAuthProvider() != AuthProvider.LOCAL) {
            userService.dummyPasswordCheck(rawPassword);
            throw new InvalidCredentialsException();
        }
        User user = found.get();
        if (!userService.passwordMatches(user, rawPassword)) {
            throw new InvalidCredentialsException();
        }
        if (user.isTwoFactorEnabled()) {
            return LoginResult.twoFactorRequired(twoFactorService.startChallenge(user));
        }
        return LoginResult.authenticated(issueTokens(user));
    }

    /**
     * Completes a 2FA login: resolves the pending challenge, verifies a TOTP or backup code, then
     * consumes the challenge (single-use) and issues tokens. The challenge survives a wrong code so
     * the user can retry within its TTL.
     */
    public AuthTokens twoFactorLogin(String challenge, String code) {
        UUID userId = twoFactorService.peekChallenge(challenge)
                .orElseThrow(InvalidTwoFactorCodeException::new);
        User user = userService.getById(userId);
        if (!twoFactorService.verifyCode(user, code)) {
            // A wrong code leaves the challenge usable so a typo is not fatal — but only up to a
            // point, or the five-minute window becomes an offer to guess all million codes.
            twoFactorService.registerFailedAttempt(challenge);
            throw new InvalidTwoFactorCodeException();
        }
        twoFactorService.consumeChallenge(challenge);
        return issueTokens(user);
    }

    /** Verifies a Google ID token, finds or creates the matching user, and issues our own tokens. */
    public AuthTokens oauthLoginGoogle(String idToken) {
        OAuthUserInfo info = googleTokenVerifier.verify(idToken);
        User user = userService.findOrCreateOAuthUser(info);
        return issueTokens(user);
    }

    /**
     * Validates the presented refresh token, rotates it (single-use), and issues a fresh
     * access token. The user is reloaded so the new access token reflects the current role
     * and a since-deleted account is rejected.
     */
    public AuthTokens refresh(String rawRefreshToken) {
        UUID userId = tokenStore.findUserIdByRefreshToken(rawRefreshToken)
                .orElseGet(() -> {
                    detectReuse(rawRefreshToken);
                    throw new InvalidRefreshTokenException();
                });
        String newRefreshToken = tokenStore.rotateRefreshToken(userId, rawRefreshToken);
        User user = userService.getById(userId);
        String accessToken = jwtService.generateAccessToken(user.getId(), user.getRole());
        return new AuthTokens(accessToken, newRefreshToken);
    }

    /** Revokes the current access token (via JTI blocklist) and the presented refresh token. */
    public void logout(AuthPrincipal principal, String rawRefreshToken) {
        long remaining = principal.expiresAt().getEpochSecond() - Instant.now().getEpochSecond();
        if (remaining > 0) {
            tokenStore.blocklistAccessToken(principal.jti(), java.time.Duration.ofSeconds(remaining));
        }
        if (rawRefreshToken != null) {
            tokenStore.revokeRefreshToken(principal.userId(), rawRefreshToken);
        }
    }

    public java.time.Duration refreshTokenTtl() {
        return tokenStore.refreshTokenTtl();
    }

    /**
     * A refresh token that is unknown now but was rotated away earlier is being replayed, which
     * means two parties held it and one of them stole it. There is no way to tell which caller is
     * the thief, so the whole family is revoked and both must log in with a password again.
     */
    private void detectReuse(String rawRefreshToken) {
        tokenStore.findUserIdByUsedRefreshToken(rawRefreshToken).ifPresent(userId -> {
            log.warn("Refresh token reuse detected for user {} — revoking all refresh tokens", userId);
            tokenStore.revokeAllRefreshTokens(userId);
        });
    }

    private AuthTokens issueTokens(User user) {
        String accessToken = jwtService.generateAccessToken(user.getId(), user.getRole());
        String refreshToken = tokenStore.issueRefreshToken(user.getId());
        return new AuthTokens(accessToken, refreshToken);
    }
}
