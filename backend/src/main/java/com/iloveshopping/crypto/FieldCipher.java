// Encrypts and decrypts single database values with AES-256-GCM, under the key in APP_ENCRYPTION_KEY.
package com.iloveshopping.crypto;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * GCM rather than ECB or CBC: every value gets a fresh random 12-byte IV, so the same address stored
 * twice looks different both times, and the 16-byte tag makes a changed byte fail to decrypt instead
 * of decrypting to something else. Stored as {@code v1:base64(iv || ciphertext || tag)}; the version
 * names the key, so a second key can be added later and old rows still read.
 *
 * <p>The key lives in the environment, never in git or in the database: someone with a copy of the
 * database alone reads nothing. Lose the key and the encrypted columns are gone for good.
 */
@Component
public class FieldCipher {

    private static final String PREFIX = "v1:";
    private static final int IV_BYTES = 12;
    private static final int TAG_BITS = 128;

    private final SecretKeySpec key;
    private final SecureRandom random = new SecureRandom();

    public FieldCipher(@Value("${app.encryption.key}") String base64Key) {
        byte[] bytes;
        try {
            bytes = Base64.getDecoder().decode(base64Key.strip());
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("APP_ENCRYPTION_KEY is not base64. ./start.sh writes a valid one into .env.");
        }
        if (bytes.length != 32) {
            throw new IllegalStateException("APP_ENCRYPTION_KEY must be 32 bytes (AES-256), base64-encoded. ./start.sh writes one into .env.");
        }
        this.key = new SecretKeySpec(bytes, "AES");
    }

    /** Null stays null, so an optional column (the flat) stays optional. */
    public String encrypt(String plaintext) {
        if (plaintext == null) {
            return null;
        }
        byte[] iv = new byte[IV_BYTES];
        random.nextBytes(iv);
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            byte[] sealed = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            return PREFIX + Base64.getEncoder().encodeToString(ByteBuffer.allocate(IV_BYTES + sealed.length).put(iv).put(sealed).array());
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Could not encrypt a value", e);
        }
    }

    /** Throws on a value that isn't ours, was changed, or was sealed with another key; never returns garbage. */
    public String decrypt(String stored) {
        if (stored == null) {
            return null;
        }
        if (!stored.startsWith(PREFIX)) {
            throw new IllegalStateException("A value in an encrypted column is not encrypted");
        }
        try {
            byte[] packed = Base64.getDecoder().decode(stored.substring(PREFIX.length()));
            if (packed.length < IV_BYTES + TAG_BITS / 8) {
                throw new IllegalStateException("An encrypted value is too short");
            }
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, packed, 0, IV_BYTES));
            return new String(cipher.doFinal(packed, IV_BYTES, packed.length - IV_BYTES), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException | GeneralSecurityException e) {
            // the message never includes the value or the key
            throw new IllegalStateException("Could not decrypt a value: wrong key, or the data was changed");
        }
    }
}
