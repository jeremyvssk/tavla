// Unit tests for AuthService orchestration: timing-equal login, 2FA gating, and refresh reuse detection.
package com.iloveshopping.auth;

import com.iloveshopping.auth.exception.InvalidCredentialsException;
import com.iloveshopping.auth.exception.InvalidRefreshTokenException;
import com.iloveshopping.auth.exception.InvalidTwoFactorCodeException;
import com.iloveshopping.user.AuthProvider;
import com.iloveshopping.user.User;
import com.iloveshopping.user.UserService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock UserService userService;
    @Mock JwtService jwtService;
    @Mock TokenStoreService tokenStore;
    @Mock GoogleTokenVerifier googleTokenVerifier;
    @Mock TwoFactorService twoFactorService;

    @InjectMocks AuthService authService;

    @Test
    void login_unknownEmail_stillRunsAPasswordComparison() {
        // The point is timing: without this, "no such account" returns in ~1ms and "wrong password"
        // in ~100ms, and the difference is a list of everyone registered here.
        when(userService.findByEmail("nobody@example.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.login("nobody@example.com", "guess"))
                .isInstanceOf(InvalidCredentialsException.class);
        verify(userService).dummyPasswordCheck("guess");
    }

    @Test
    void login_oauthAccount_stillRunsAPasswordComparison() {
        User google = new User();
        google.setId(UUID.randomUUID());
        google.setAuthProvider(AuthProvider.GOOGLE);
        when(userService.findByEmail("ada@example.com")).thenReturn(Optional.of(google));

        assertThatThrownBy(() -> authService.login("ada@example.com", "guess"))
                .isInstanceOf(InvalidCredentialsException.class);
        verify(userService).dummyPasswordCheck("guess");
        verify(userService, never()).passwordMatches(any(), anyString());
    }

    @Test
    void login_wrongPassword_comparesAgainstTheRealHashOnly() {
        User user = localUser();
        when(userService.findByEmail("ada@example.com")).thenReturn(Optional.of(user));
        when(userService.passwordMatches(user, "wrong")).thenReturn(false);

        assertThatThrownBy(() -> authService.login("ada@example.com", "wrong"))
                .isInstanceOf(InvalidCredentialsException.class);
        // One BCrypt comparison on this path too — never two, which would be slower than the miss.
        verify(userService, never()).dummyPasswordCheck(anyString());
    }

    @Test
    void refresh_replayedToken_revokesEveryTokenForThatUser() {
        // The live key is gone (it was rotated away), but the tombstone still names the owner.
        // Two parties held this token; there is no way to tell which one is calling now.
        UUID userId = UUID.randomUUID();
        when(tokenStore.findUserIdByRefreshToken("spent")).thenReturn(Optional.empty());
        when(tokenStore.findUserIdByUsedRefreshToken("spent")).thenReturn(Optional.of(userId));

        assertThatThrownBy(() -> authService.refresh("spent"))
                .isInstanceOf(InvalidRefreshTokenException.class);
        verify(tokenStore).revokeAllRefreshTokens(userId);
    }

    @Test
    void refresh_tokenThatWasNeverIssued_revokesNothing() {
        // Garbage and expired tokens are not evidence of theft, so they must not log anyone out.
        when(tokenStore.findUserIdByRefreshToken("junk")).thenReturn(Optional.empty());
        when(tokenStore.findUserIdByUsedRefreshToken("junk")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.refresh("junk"))
                .isInstanceOf(InvalidRefreshTokenException.class);
        verify(tokenStore, never()).revokeAllRefreshTokens(any());
    }

    @Test
    void twoFactorLogin_wrongCode_countsTheAttemptAndKeepsTheChallenge() {
        UUID userId = UUID.randomUUID();
        User user = localUser();
        when(twoFactorService.peekChallenge("chal")).thenReturn(Optional.of(userId));
        when(userService.getById(userId)).thenReturn(user);
        when(twoFactorService.verifyCode(user, "000000")).thenReturn(false);

        assertThatThrownBy(() -> authService.twoFactorLogin("chal", "000000"))
                .isInstanceOf(InvalidTwoFactorCodeException.class);
        verify(twoFactorService).registerFailedAttempt("chal");
        verify(twoFactorService, never()).consumeChallenge(anyString());
    }

    private User localUser() {
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setEmail("ada@example.com");
        return user;
    }
}
