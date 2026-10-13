// Signs and verifies short-lived access JWTs (HS256).
package com.iloveshopping.auth;

import com.iloveshopping.user.Role;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

/**
 * Issues access tokens carrying only identity claims (sub, role, iat, exp, jti, and mfa when the
 * session passed a second factor) — no PII in the payload. Validation is pure signature + expiry
 * math; the token is never stored. TokenStoreService's revocation check (the JTI blocklist and the
 * per-user cut-off a role change sets) is the one lookup on top.
 */
@Service
public class JwtService {

    private final SecretKey key;
    private final Duration accessTokenTtl;

    public JwtService(@Value("${app.jwt.secret}") String secret,
                      @Value("${app.jwt.access-token-ttl}") String accessTokenTtl) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.accessTokenTtl = Duration.parse(accessTokenTtl);
    }

    /**
     * {@code mfa} says this session passed a second factor: a TOTP or backup code at sign-in, or the
     * code that switched 2FA on. Staff endpoints refuse a token without it (StaffArea).
     */
    public String generateAccessToken(UUID userId, Role role, boolean mfa) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(userId.toString())
                .claim("role", role.name())
                .claim("mfa", mfa ? Boolean.TRUE : null)
                .id(UUID.randomUUID().toString())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(accessTokenTtl)))
                .signWith(key)
                .compact();
    }

    /**
     * Parses and verifies the token. Throws a {@link io.jsonwebtoken.JwtException}
     * subtype on a bad signature, malformed token, or expiry — callers map that to 401.
     */
    public Jws<Claims> parse(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token);
    }
}
