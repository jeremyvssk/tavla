// Orchestrates registration, login, refresh, and logout over the user and token services.
package com.iloveshopping.auth;

import com.iloveshopping.auth.exception.InvalidCredentialsException;
import com.iloveshopping.auth.exception.InvalidRefreshTokenException;
import com.iloveshopping.user.User;
import com.iloveshopping.user.UserService;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;

@Service
public class AuthService {

    private final UserService userService;
    private final JwtService jwtService;
    private final TokenStoreService tokenStore;

    public AuthService(UserService userService, JwtService jwtService, TokenStoreService tokenStore) {
        this.userService = userService;
        this.jwtService = jwtService;
        this.tokenStore = tokenStore;
    }

    public User register(String email, String rawPassword, String fullName) {
        return userService.createLocalUser(email, rawPassword, fullName);
    }

    public AuthTokens login(String email, String rawPassword) {
        User user = userService.findByEmail(email)
                .orElseThrow(InvalidCredentialsException::new);
        if (!userService.passwordMatches(user, rawPassword)) {
            throw new InvalidCredentialsException();
        }
        return issueTokens(user);
    }

    /**
     * Validates the presented refresh token, rotates it (single-use), and issues a fresh
     * access token. The user is reloaded so the new access token reflects the current role
     * and a since-deleted account is rejected.
     */
    public AuthTokens refresh(String rawRefreshToken) {
        UUID userId = tokenStore.findUserIdByRefreshToken(rawRefreshToken)
                .orElseThrow(InvalidRefreshTokenException::new);
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

    private AuthTokens issueTokens(User user) {
        String accessToken = jwtService.generateAccessToken(user.getId(), user.getRole());
        String refreshToken = tokenStore.issueRefreshToken(user.getId());
        return new AuthTokens(accessToken, refreshToken);
    }
}
