// Keyed hashes of encrypted values that must still be looked up exactly: the email at sign-in.
package com.iloveshopping.crypto;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.Base64;
import java.util.HexFormat;

/**
 * FieldCipher gives the same email a different ciphertext every time, so {@code WHERE email = ?} and a unique
 * index can't work on it. The row also stores HMAC-SHA256(key, email): the same email always gives the same
 * hash, so sign-in finds the row and the unique index refuses a second account.
 *
 * <p>An HMAC and not a plain SHA-256: emails are guessable, and anyone holding the database could hash a list
 * of a million addresses and match them. Without the key they can't. The key is derived from
 * APP_ENCRYPTION_KEY with a fixed label (the HKDF idea), so it is never the cipher's key itself and needs no
 * second variable. The cost of any blind index: equality only. Staff can find a user by the whole address,
 * not by its first letters.
 */
@Component
public class BlindIndex {

    private static final String LABEL = "i-love-shopping blind index v1";

    private final SecretKeySpec key;

    public BlindIndex(@Value("${app.encryption.key}") String base64Key) {
        try {
            Mac derive = Mac.getInstance("HmacSHA256");
            derive.init(new SecretKeySpec(Base64.getDecoder().decode(base64Key.strip()), "HmacSHA256"));
            this.key = new SecretKeySpec(derive.doFinal(LABEL.getBytes(StandardCharsets.UTF_8)), "HmacSHA256");
        } catch (IllegalArgumentException | GeneralSecurityException e) {
            // FieldCipher's constructor explains a bad key; this one only refuses to start without one
            throw new IllegalStateException("APP_ENCRYPTION_KEY is missing or not base64");
        }
    }

    /** 64 hex characters. The caller normalises first (UserService.normalizeEmail), so case never matters. */
    public String of(String value) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(key);
            return HexFormat.of().formatHex(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Could not hash a value", e);
        }
    }
}
