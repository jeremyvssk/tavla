// Orchestrates registration, login, refresh, and logout over the user and token services.
package com.iloveshopping.auth;

import com.iloveshopping.audit.AuditEvent;
import com.iloveshopping.audit.AuditKind;
import com.iloveshopping.audit.AuditLog;
import com.iloveshopping.audit.AuditTarget;
import com.iloveshopping.auth.dto.TwoFactorEnableResponse;
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
import java.util.List;
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
    private final AccountThrottle accountThrottle;
    private final AuditLog auditLog;

    public AuthService(UserService userService, JwtService jwtService, TokenStoreService tokenStore,
                       GoogleTokenVerifier googleTokenVerifier, TwoFactorService twoFactorService,
                       AccountThrottle accountThrottle, AuditLog auditLog) {
        this.userService = userService;
        this.jwtService = jwtService;
        this.tokenStore = tokenStore;
        this.googleTokenVerifier = googleTokenVerifier;
        this.twoFactorService = twoFactorService;
        this.accountThrottle = accountThrottle;
        this.auditLog = auditLog;
    }

    public User register(String email, String rawPassword, String fullName) {
        return userService.createLocalUser(email, rawPassword, fullName);
    }

    /**
     * Verifies the password. If 2FA is enabled, no tokens are issued yet — a short-lived challenge is
     * returned and the caller must complete {@link #twoFactorLogin}. Otherwise tokens are issued.
     * The attempt is counted against the account before the password is looked at.
     */
    public LoginResult login(String email, String rawPassword, String clientIp) {
        accountThrottle.checkLogin(email, clientIp);
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
            refusedStaffSignIn(user, "Wrong password", false);
            throw new InvalidCredentialsException();
        }
        accountThrottle.loginSucceeded(email, clientIp);
        if (user.isTwoFactorEnabled()) {
            return LoginResult.twoFactorRequired(twoFactorService.startChallenge(user));
        }
        return LoginResult.authenticated(issueTokens(user, false));
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
            // the password was right, so this one is worth a staff member's attention
            refusedStaffSignIn(user, "Wrong two-factor code", true);
            throw new InvalidTwoFactorCodeException();
        }
        twoFactorService.consumeChallenge(challenge);
        return issueTokens(user, true);
    }

    /**
     * Verifies a Google ID token, finds or creates the matching user, and issues our own tokens. Never a
     * second factor: a Google account has no password to set 2FA up with, which is also why it can't be
     * given a staff role (StaffUserService).
     */
    public AuthTokens oauthLoginGoogle(String idToken) {
        OAuthUserInfo info = googleTokenVerifier.verify(idToken);
        User user = userService.findOrCreateOAuthUser(info);
        return issueTokens(user, false);
    }

    /**
     * Validates the presented refresh token, rotates it (single-use), and issues a fresh
     * access token. The user is reloaded so the new access token reflects the current role
     * and a since-deleted account is rejected. The session keeps its second factor, if it had one.
     */
    public AuthTokens refresh(String rawRefreshToken) {
        TokenStoreService.Session session = tokenStore.findSessionByRefreshToken(rawRefreshToken)
                .orElseGet(() -> {
                    detectReuse(rawRefreshToken);
                    throw new InvalidRefreshTokenException();
                });
        String newRefreshToken = tokenStore.rotateRefreshToken(session, rawRefreshToken);
        User user = userService.getById(session.userId());
        String accessToken = jwtService.generateAccessToken(user.getId(), user.getRole(), session.mfa());
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

    /**
     * Switches 2FA on with the first code from the app (setup already re-checked the password). That code
     * is a second factor, so the session it was typed in has now passed one: the refresh token, when sent,
     * is marked, and the answer carries a fresh access token that says so.
     */
    public TwoFactorEnableResponse enableTwoFactor(UUID userId, String code, String rawRefreshToken) {
        User user = userService.getById(userId);
        List<String> backupCodes = twoFactorService.enable(user, code);
        if (rawRefreshToken != null) {
            tokenStore.markSecondFactor(userId, rawRefreshToken);
        }
        if (user.getRole().isStaff()) {
            auditLog.recordFor(userId, user.getRole(), AuditEvent.of(AuditKind.ACCESS, "Turned on two-factor"));
        }
        return new TwoFactorEnableResponse(backupCodes, jwtService.generateAccessToken(userId, user.getRole(), true));
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

    /** A sign-in that succeeded: tokens for a new session, the sign-in time, and a log entry for staff. */
    private AuthTokens issueTokens(User user, boolean secondFactor) {
        String accessToken = jwtService.generateAccessToken(user.getId(), user.getRole(), secondFactor);
        String refreshToken = tokenStore.issueRefreshToken(new TokenStoreService.Session(user.getId(), secondFactor));
        userService.recordSignIn(user.getId());
        if (user.getRole().isStaff()) {
            AuditEvent signedIn = AuditEvent.of(AuditKind.ACCESS, "Signed in");
            auditLog.recordFor(user.getId(), user.getRole(), secondFactor ? signedIn.detail("Password and two-factor code")
                    : signedIn.flag("Two-factor not set up yet", false));
        }
        return new AuthTokens(accessToken, refreshToken);
    }

    /** A staff account someone failed to sign in to: who tried isn't known, so the account is the target. */
    private void refusedStaffSignIn(User user, String why, boolean serious) {
        if (user.getRole().isStaff()) {
            auditLog.recordFor(null, null, AuditEvent.of(AuditKind.ACCESS, "Sign-in to {target} refused")
                    .target(AuditTarget.USER, user.getId(), null).flag(why, serious));
        }
    }
}
