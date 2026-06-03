// Unit tests for TokenStoreService Redis wiring (mocked StringRedisTemplate).
package com.iloveshopping.auth;

import com.iloveshopping.auth.exception.InvalidRefreshTokenException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.script.RedisScript;

import java.time.Duration;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TokenStoreServiceTest {

    @Mock StringRedisTemplate redis;
    @Mock ValueOperations<String, String> valueOps;
    @Mock SetOperations<String, String> setOps;

    TokenStoreService service;

    @BeforeEach
    void setUp() {
        lenient().when(redis.opsForValue()).thenReturn(valueOps);
        lenient().when(redis.opsForSet()).thenReturn(setOps);
        service = new TokenStoreService(redis, "P7D");
    }

    @Test
    void issueRefreshToken_storesHashedTokenWithTtlAndTracksInUserSet() {
        UUID userId = UUID.randomUUID();

        String raw = service.issueRefreshToken(userId);

        // The raw token is returned to the client but never stored as-is.
        String expectedHash = TokenStoreService.sha256(raw);
        verify(valueOps).set("refresh_token:" + expectedHash, userId.toString(), Duration.ofDays(7));
        verify(setOps).add("refresh_tokens_user:" + userId, expectedHash);
    }

    @Test
    void rotateRefreshToken_returnsNewTokenWhenScriptSucceeds() {
        UUID userId = UUID.randomUUID();
        when(redis.execute(any(RedisScript.class), any(List.class), any(), any(), any(), any()))
                .thenReturn(1L);

        String newRaw = service.rotateRefreshToken(userId, "old-raw-token");

        assertThat(newRaw).isNotBlank().isNotEqualTo("old-raw-token");
    }

    @Test
    void rotateRefreshToken_rejectsReplayedOrUnknownToken() {
        UUID userId = UUID.randomUUID();
        when(redis.execute(any(RedisScript.class), any(List.class), any(), any(), any(), any()))
                .thenReturn(0L);

        assertThatThrownBy(() -> service.rotateRefreshToken(userId, "stale-token"))
                .isInstanceOf(InvalidRefreshTokenException.class);
    }

    @Test
    void revokeAllRefreshTokens_deletesEveryTokenAndTheUserSet() {
        UUID userId = UUID.randomUUID();
        String setKey = "refresh_tokens_user:" + userId;
        when(setOps.members(setKey)).thenReturn(Set.of("hashA", "hashB"));

        service.revokeAllRefreshTokens(userId);

        // Set iteration order is unspecified, so assert membership rather than order.
        ArgumentCaptor<Collection<String>> captor = ArgumentCaptor.forClass(Collection.class);
        verify(redis).delete(captor.capture());
        assertThat(captor.getValue())
                .containsExactlyInAnyOrder("refresh_token:hashA", "refresh_token:hashB");
        verify(redis).delete(setKey);
    }

    @Test
    void blocklistAccessToken_setsJtiKeyWithRemainingTtl() {
        service.blocklistAccessToken("jti-123", Duration.ofMinutes(10));

        verify(valueOps).set("token_blocklist:jti-123", "1", Duration.ofMinutes(10));
    }

    @Test
    void isAccessTokenBlocklisted_reflectsKeyPresence() {
        when(redis.hasKey("token_blocklist:live")).thenReturn(true);
        when(redis.hasKey("token_blocklist:gone")).thenReturn(false);

        assertThat(service.isAccessTokenBlocklisted("live")).isTrue();
        assertThat(service.isAccessTokenBlocklisted("gone")).isFalse();
    }

    @Test
    void sha256_isDeterministicAndUrlSafe() {
        String a = TokenStoreService.sha256("token");
        String b = TokenStoreService.sha256("token");

        assertThat(a).isEqualTo(b).doesNotContain("+", "/", "=");
    }
}
