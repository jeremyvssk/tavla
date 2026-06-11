// Unit tests for GoogleTokenVerifier with a mocked JwtDecoder.
package com.iloveshopping.auth;

import com.iloveshopping.auth.exception.InvalidOAuthTokenException;
import com.iloveshopping.user.AuthProvider;
import com.iloveshopping.user.OAuthUserInfo;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GoogleTokenVerifierTest {

    private final JwtDecoder decoder = mock(JwtDecoder.class);
    private final GoogleTokenVerifier verifier = new GoogleTokenVerifier(decoder);

    @Test
    void verify_mapsClaimsToOAuthUserInfo() {
        when(decoder.decode("good")).thenReturn(googleJwt("google-sub-1", "alice@gmail.com", "Alice"));

        OAuthUserInfo info = verifier.verify("good");

        assertThat(info.provider()).isEqualTo(AuthProvider.GOOGLE);
        assertThat(info.providerId()).isEqualTo("google-sub-1");
        assertThat(info.email()).isEqualTo("alice@gmail.com");
        assertThat(info.fullName()).isEqualTo("Alice");
        assertThat(info.emailVerified()).isTrue();
    }

    @Test
    void verify_fallsBackToEmailWhenNameMissing() {
        Jwt jwt = baseJwt("google-sub-2", "noname@gmail.com").build();
        when(decoder.decode("no-name")).thenReturn(jwt);

        assertThat(verifier.verify("no-name").fullName()).isEqualTo("noname@gmail.com");
    }

    @Test
    void verify_rejectsTokenThatFailsDecoding() {
        when(decoder.decode("bad")).thenThrow(new BadJwtException("bad signature"));

        assertThatThrownBy(() -> verifier.verify("bad"))
                .isInstanceOf(InvalidOAuthTokenException.class);
    }

    @Test
    void verify_rejectsTokenWithoutEmail() {
        Jwt jwt = Jwt.withTokenValue("token").header("alg", "RS256")
                .subject("google-sub-3").claim("name", "Bob")
                .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(3600)).build();
        when(decoder.decode("no-email")).thenReturn(jwt);

        assertThatThrownBy(() -> verifier.verify("no-email"))
                .isInstanceOf(InvalidOAuthTokenException.class);
    }

    private Jwt googleJwt(String sub, String email, String name) {
        return baseJwt(sub, email).claim("name", name).build();
    }

    private Jwt.Builder baseJwt(String sub, String email) {
        return Jwt.withTokenValue("token")
                .header("alg", "RS256")
                .subject(sub)
                .claim("email", email)
                .claim("email_verified", true)
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(3600));
    }
}
