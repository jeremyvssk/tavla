// Unit tests for TwoFactorService with mocked Redis and user service; real TOTP math.
package com.iloveshopping.auth;

import com.iloveshopping.auth.dto.TwoFactorSetupResponse;
import com.iloveshopping.auth.exception.InvalidCredentialsException;
import com.iloveshopping.auth.exception.InvalidTwoFactorCodeException;
import com.iloveshopping.user.User;
import com.iloveshopping.user.UserService;
import dev.samstevens.totp.code.CodeGenerator;
import dev.samstevens.totp.code.DefaultCodeGenerator;
import dev.samstevens.totp.secret.DefaultSecretGenerator;
import dev.samstevens.totp.secret.SecretGenerator;
import dev.samstevens.totp.time.SystemTimeProvider;
import dev.samstevens.totp.time.TimeProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.util.List;
import java.util.Set;
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
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TwoFactorServiceTest {

    @Mock
    StringRedisTemplate redis;

    @Mock
    ValueOperations<String, String> valueOps;

    @Mock
    UserService userService;

    TwoFactorService service;

    private final SecretGenerator secretGenerator = new DefaultSecretGenerator();
    private final CodeGenerator codeGenerator = new DefaultCodeGenerator();
    private final TimeProvider timeProvider = new SystemTimeProvider();

    @BeforeEach
    void setUp() {
        lenient().when(redis.opsForValue()).thenReturn(valueOps);
        service = new TwoFactorService(redis, userService, "i-love-shopping");
    }

    @Test
    void setup_storesSecretAndReturnsOtpauthUri() {
        UUID id = UUID.randomUUID();
        User user = user(id);

        TwoFactorSetupResponse response = service.setup(user);

        assertThat(response.secret()).isNotBlank();
        assertThat(response.otpauthUri())
                .startsWith("otpauth://totp/")
                .contains("issuer=i-love-shopping")
                .contains("secret=" + response.secret());
        verify(userService).setTwoFactorSecret(id, response.secret());
    }

    @Test
    void enable_withValidCode_enablesAndReturnsEightHashedBackupCodes() throws Exception {
        String secret = secretGenerator.generate();
        User user = userWithSecret(secret);

        List<String> codes = service.enable(user, currentCode(secret));

        assertThat(codes).hasSize(8);
        ArgumentCaptor<String> hashed = ArgumentCaptor.forClass(String.class);
        verify(userService).enableTwoFactor(eq(user.getId()), hashed.capture());
        // The stored value is a hashed, comma-separated list — never the plaintext codes.
        assertThat(hashed.getValue().split(",")).hasSize(8);
        assertThat(hashed.getValue()).doesNotContain(codes.get(0));
    }

    @Test
    void enable_withInvalidCode_throwsAndDoesNotEnable() throws Exception {
        String secret = secretGenerator.generate();
        User user = userWithSecret(secret);

        assertThatThrownBy(() -> service.enable(user, wrongCode(secret)))
                .isInstanceOf(InvalidTwoFactorCodeException.class);
        verify(userService, never()).enableTwoFactor(any(), any());
    }

    @Test
    void enable_withoutSetup_throws() {
        User user = user(UUID.randomUUID());

        assertThatThrownBy(() -> service.enable(user, "123456"))
                .isInstanceOf(InvalidTwoFactorCodeException.class);
    }

    @Test
    void disable_wrongPassword_throwsAndKeepsEnabled() {
        User user = userWithSecret("SECRET");
        when(userService.passwordMatches(user, "wrong")).thenReturn(false);

        assertThatThrownBy(() -> service.disable(user, "wrong"))
                .isInstanceOf(InvalidCredentialsException.class);
        verify(userService, never()).disableTwoFactor(any());
    }

    @Test
    void disable_correctPassword_disables() {
        User user = userWithSecret("SECRET");
        when(userService.passwordMatches(user, "right")).thenReturn(true);

        service.disable(user, "right");

        verify(userService).disableTwoFactor(user.getId());
    }

    @Test
    void challenge_storedWithFiveMinuteTtl_thenResolvedAndConsumed() {
        UUID id = UUID.randomUUID();
        User user = user(id);

        String token = service.startChallenge(user);

        assertThat(token).isNotBlank();
        ArgumentCaptor<String> key = ArgumentCaptor.forClass(String.class);
        verify(valueOps).set(key.capture(), eq(id.toString()), eq(Duration.ofMinutes(5)));
        assertThat(key.getValue()).startsWith("2fa_pending:");

        when(valueOps.get(anyString())).thenReturn(id.toString());
        assertThat(service.peekChallenge(token)).contains(id);

        service.consumeChallenge(token);
        verify(redis).delete(startsWith("2fa_pending:"));
    }

    @Test
    void verifyCode_acceptsCurrentTotp() throws Exception {
        String secret = secretGenerator.generate();
        User user = userWithSecret(secret);

        assertThat(service.verifyCode(user, currentCode(secret))).isTrue();
    }

    @Test
    void verifyCode_rejectsWrongCodeWhenNoBackupCodes() throws Exception {
        String secret = secretGenerator.generate();
        User user = userWithSecret(secret);

        assertThat(service.verifyCode(user, wrongCode(secret))).isFalse();
    }

    private User user(UUID id) {
        User user = new User();
        user.setId(id);
        user.setEmail("u@example.com");
        return user;
    }

    private User userWithSecret(String secret) {
        User user = user(UUID.randomUUID());
        user.setTwoFactorSecret(secret);
        return user;
    }

    private String currentCode(String secret) throws Exception {
        return codeGenerator.generate(secret, Math.floorDiv(timeProvider.getTime(), 30));
    }

    // A 6-digit code guaranteed not to match the current or adjacent time windows the verifier accepts.
    private String wrongCode(String secret) throws Exception {
        long counter = Math.floorDiv(timeProvider.getTime(), 30);
        Set<String> valid = Set.of(
                codeGenerator.generate(secret, counter - 1),
                codeGenerator.generate(secret, counter),
                codeGenerator.generate(secret, counter + 1));
        for (int i = 0; i < 1_000_000; i++) {
            String candidate = String.format("%06d", i);
            if (!valid.contains(candidate)) {
                return candidate;
            }
        }
        throw new IllegalStateException("no non-matching code found");
    }
}
