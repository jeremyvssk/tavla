// Random opaque tokens and the SHA-256 fingerprints that Redis and the database store instead of them.
package com.iloveshopping.auth;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Refresh tokens, reset tokens, 2FA challenges and backup codes are all stored only as a hash, so a
 * Redis dump or a database leak exposes nothing usable. A fast hash is right here, unlike for
 * passwords: 256 random bits can't be guessed, so there is nothing for BCrypt's cost to slow down.
 */
final class OpaqueTokens {

    private static final SecureRandom RANDOM = new SecureRandom();

    private OpaqueTokens() {
    }

    /** 256 random bits, URL-safe, safe to put in a cookie or an emailed link. */
    static String generate() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    static String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}
