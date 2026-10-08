// JPA converter for columns stored encrypted: entities hold plain text, the database holds FieldCipher's output.
package com.iloveshopping.crypto;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import org.springframework.stereotype.Component;

/**
 * Used as {@code @Convert(converter = Encrypted.class)} on an entity field. A Spring bean, so it gets the
 * key: Spring Boot lets Hibernate take converters from the application context.
 *
 * <p>Only for columns nothing filters, sorts or joins on. Each encryption is different, so
 * {@code WHERE email = ?} can never match. Native SQL bypasses converters: code that writes these
 * columns with a native statement encrypts with {@link FieldCipher} itself (see PaymentService).
 */
@Component
@Converter
public class Encrypted implements AttributeConverter<String, String> {

    private final FieldCipher cipher;

    public Encrypted(FieldCipher cipher) {
        this.cipher = cipher;
    }

    @Override
    public String convertToDatabaseColumn(String attribute) {
        return cipher.encrypt(attribute);
    }

    @Override
    public String convertToEntityAttribute(String dbData) {
        return cipher.decrypt(dbData);
    }
}
