// Unit tests for FieldCipher: round trips, a fresh IV each time, tampering and a wrong key detected, bad keys refused.
package com.iloveshopping.crypto;

import org.junit.jupiter.api.Test;

import java.security.SecureRandom;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FieldCipherTest {

    private static String newKey() {
        byte[] key = new byte[32];
        new SecureRandom().nextBytes(key);
        return Base64.getEncoder().encodeToString(key);
    }

    private final FieldCipher cipher = new FieldCipher(newKey());

    @Test
    void whatGoesIn_comesBackOut_includingAccentsAndTheEmptyString() {
        for (String value : new String[]{"Pärnu mnt 22", "mart.kask@example.com", "Rīga, Brīvības iela 1", "", "4242"}) {
            String sealed = cipher.encrypt(value);
            assertThat(sealed).startsWith("v1:").doesNotContain(value.isEmpty() ? "\u0000" : value);
            assertThat(cipher.decrypt(sealed)).isEqualTo(value);
        }
    }

    @Test
    void theSameValueTwice_looksDifferentEachTime() {
        assertThat(cipher.encrypt("10141")).isNotEqualTo(cipher.encrypt("10141"));
    }

    @Test
    void nullStaysNull() {
        assertThat(cipher.encrypt(null)).isNull();
        assertThat(cipher.decrypt(null)).isNull();
    }

    @Test
    void aChangedByte_failsToDecrypt_ratherThanDecryptingToSomethingElse() {
        String sealed = cipher.encrypt("Pärnu mnt 22");
        byte[] packed = Base64.getDecoder().decode(sealed.substring(3));
        packed[packed.length - 1] ^= 1;
        String tampered = "v1:" + Base64.getEncoder().encodeToString(packed);
        assertThatThrownBy(() -> cipher.decrypt(tampered))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageNotContaining("Pärnu");
    }

    @Test
    void anotherKey_cantRead_it() {
        String sealed = cipher.encrypt("Pärnu mnt 22");
        assertThatThrownBy(() -> new FieldCipher(newKey()).decrypt(sealed)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void plainTextInAnEncryptedColumn_isRefused_notPassedThrough() {
        assertThatThrownBy(() -> cipher.decrypt("Pärnu mnt 22")).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> cipher.decrypt("v1:AAAA")).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void aKeyThatIsntAES256_isRefusedAtStartup() {
        assertThatThrownBy(() -> new FieldCipher(Base64.getEncoder().encodeToString(new byte[16])))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("32 bytes");
        assertThatThrownBy(() -> new FieldCipher("not base64 at all!"))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("base64");
        assertThatThrownBy(() -> new FieldCipher("")).isInstanceOf(IllegalStateException.class);
    }
}
