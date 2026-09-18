// Unit tests for PasswordResetService with mocked Redis, user service, email, and token store.
package com.iloveshopping.auth;

import com.iloveshopping.auth.exception.InvalidResetTokenException;
import com.iloveshopping.user.User;
import com.iloveshopping.user.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PasswordResetServiceTest {

    @Mock
    StringRedisTemplate redis;

    @Mock
    ValueOperations<String, String> valueOps;

    @Mock
    UserService userService;

    @Mock
    EmailService emailService;

    @Mock
    TokenStoreService tokenStore;

    @Mock
    AccountThrottle accountThrottle;

    PasswordResetService service;

    @BeforeEach
    void setUp() {
        lenient().when(redis.opsForValue()).thenReturn(valueOps);
        lenient().when(accountThrottle.allowResetEmail(anyString())).thenReturn(true);
        service = new PasswordResetService(redis, userService, emailService, tokenStore, accountThrottle,
                "http://localhost:5173/reset");
    }

    @Test
    void requestReset_storesTokenFingerprintAndEmailsLink_whenUserExists() {
        UUID id = UUID.randomUUID();
        User user = new User();
        user.setId(id);
        user.setEmail("alice@example.com");
        when(userService.findByEmail("alice@example.com")).thenReturn(Optional.of(user));

        service.requestReset("alice@example.com");

        ArgumentCaptor<String> key = ArgumentCaptor.forClass(String.class);
        verify(valueOps).set(key.capture(), eq(id.toString()), eq(Duration.ofMinutes(15)));
        assertThat(key.getValue()).startsWith("pwreset:");

        ArgumentCaptor<String> url = ArgumentCaptor.forClass(String.class);
        verify(emailService).sendPasswordReset(eq("alice@example.com"), url.capture());
        assertThat(url.getValue()).startsWith("http://localhost:5173/reset?token=");
    }

    @Test
    void requestReset_isSilent_whenEmailUnknown() {
        when(userService.findByEmail("nobody@example.com")).thenReturn(Optional.empty());

        service.requestReset("nobody@example.com");

        verifyNoInteractions(emailService);
        verify(valueOps, never()).set(anyString(), anyString(), any());
    }

    @Test
    void requestReset_overThePerAddressLimit_sendsNothing() {
        when(accountThrottle.allowResetEmail("alice@example.com")).thenReturn(false);

        service.requestReset("alice@example.com");

        verifyNoInteractions(emailService, userService);
    }

    @Test
    void confirmReset_updatesPassword_consumesToken_revokesAllSessions_andLiftsLoginLockout() {
        UUID id = UUID.randomUUID();
        User user = new User();
        user.setId(id);
        user.setEmail("alice@example.com");
        when(valueOps.get(anyString())).thenReturn(id.toString());
        when(userService.getById(id)).thenReturn(user);

        service.confirmReset("raw-token", "newpassword123");

        verify(userService).updatePassword(id, "newpassword123");
        verify(redis).delete(startsWith("pwreset:"));
        verify(tokenStore).revokeAllRefreshTokens(id);
        verify(accountThrottle).clearLogin("alice@example.com");
    }

    @Test
    void confirmReset_throws_whenTokenMissingOrExpired() {
        when(valueOps.get(anyString())).thenReturn(null);

        assertThatThrownBy(() -> service.confirmReset("bad-token", "newpassword123"))
                .isInstanceOf(InvalidResetTokenException.class);

        verify(userService, never()).updatePassword(any(), anyString());
        verify(tokenStore, never()).revokeAllRefreshTokens(any());
    }
}
