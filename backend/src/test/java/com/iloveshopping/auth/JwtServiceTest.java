// Unit tests for JwtService token generation, claims, and signature/expiry validation.
package com.iloveshopping.auth;

import com.iloveshopping.user.Role;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jws;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtServiceTest {

    // HS256 requires a >=256-bit key; this 32+ char secret satisfies it.
    private static final String SECRET = "test-secret-that-is-long-enough-for-hs256-signing";

    private final JwtService jwtService = new JwtService(SECRET, "PT15M");

    @Test
    void generatedToken_carriesIdentityClaimsAndNoPii() {
        UUID userId = UUID.randomUUID();

        String token = jwtService.generateAccessToken(userId, Role.ADMIN);
        Claims claims = jwtService.parse(token).getPayload();

        assertThat(claims.getSubject()).isEqualTo(userId.toString());
        assertThat(claims.get("role", String.class)).isEqualTo("ADMIN");
        assertThat(claims.getId()).isNotBlank();           // jti present, for blocklisting
        assertThat(claims.getIssuedAt()).isNotNull();
        assertThat(claims.getExpiration()).isAfter(claims.getIssuedAt());
    }

    @Test
    void parse_rejectsTamperedSignature() {
        String token = jwtService.generateAccessToken(UUID.randomUUID(), Role.CUSTOMER);
        // Flip a character in the signature segment (after the last dot).
        String tampered = token.substring(0, token.length() - 1)
                + (token.endsWith("A") ? "B" : "A");

        assertThatThrownBy(() -> jwtService.parse(tampered))
                .isInstanceOf(JwtException.class);
    }

    @Test
    void parse_rejectsTokenSignedWithDifferentSecret() {
        JwtService other = new JwtService("a-completely-different-secret-key-value-here!!", "PT15M");
        String foreignToken = other.generateAccessToken(UUID.randomUUID(), Role.CUSTOMER);

        assertThatThrownBy(() -> jwtService.parse(foreignToken))
                .isInstanceOf(JwtException.class);
    }

    @Test
    void parse_rejectsExpiredToken() {
        // Negative TTL produces an already-expired token without sleeping.
        JwtService shortLived = new JwtService(SECRET, "PT-1M");
        String expired = shortLived.generateAccessToken(UUID.randomUUID(), Role.CUSTOMER);

        assertThatThrownBy(() -> jwtService.parse(expired))
                .isInstanceOf(ExpiredJwtException.class);
    }
}
