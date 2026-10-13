// Encrypts users rows still in plain text right after Flyway runs: rows from before V16, and the seed's reviewers.
package com.iloveshopping.user;

import com.iloveshopping.crypto.BlindIndex;
import com.iloveshopping.crypto.FieldCipher;
import org.flywaydb.core.api.callback.Callback;
import org.flywaydb.core.api.callback.Context;
import org.flywaydb.core.api.callback.Event;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;

/**
 * A Flyway callback rather than a startup job: it runs inside the migrate step, before the app serves a single
 * request, so nothing ever reads a plain row through the Encrypted converter (which refuses one). It runs after
 * every migrate, because the repeatable seed can insert its reviewers again on a fresh database.
 *
 * <p>Idempotent: the "v1:" prefix tells encrypted from plain, so a value is never encrypted twice. One pass
 * over the users table at each start; fine at this size, and a no-op once everything is encrypted.
 */
@Component
public class UserEncryptionBackfill implements Callback {

    private static final Logger log = LoggerFactory.getLogger(UserEncryptionBackfill.class);
    private static final String PREFIX = "v1:";

    private final FieldCipher cipher;
    private final BlindIndex blindIndex;

    public UserEncryptionBackfill(FieldCipher cipher, BlindIndex blindIndex) {
        this.cipher = cipher;
        this.blindIndex = blindIndex;
    }

    @Override
    public boolean supports(Event event, Context context) {
        return event == Event.AFTER_MIGRATE;
    }

    @Override
    public boolean canHandleInTransaction(Event event, Context context) {
        return true;
    }

    @Override
    public void handle(Event event, Context context) {
        Connection connection = context.getConnection();
        int done = 0;
        try (PreparedStatement select = connection.prepareStatement("""
                SELECT id, email, full_name, avatar_url, two_factor_secret FROM users
                WHERE email_hash IS NULL OR full_name NOT LIKE 'v1:%' OR avatar_url NOT LIKE 'v1:%'
                   OR two_factor_secret NOT LIKE 'v1:%'""");
             PreparedStatement update = connection.prepareStatement("""
                UPDATE users SET email = ?, email_hash = ?, full_name = ?, avatar_url = ?, two_factor_secret = ?
                WHERE id = ?""");
             ResultSet rows = select.executeQuery()) {
            while (rows.next()) {
                String email = plain(rows.getString("email"));
                update.setString(1, cipher.encrypt(email));
                update.setString(2, blindIndex.of(UserService.normalizeEmail(email)));
                update.setString(3, sealed(rows.getString("full_name")));
                update.setString(4, sealed(rows.getString("avatar_url")));
                update.setString(5, sealed(rows.getString("two_factor_secret")));
                update.setObject(6, rows.getObject("id", UUID.class));
                update.executeUpdate();
                done++;
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Could not encrypt the users table", e);
        }
        if (done > 0) {
            log.info("Encrypted the personal data of {} users rows", done);
        }
    }

    @Override
    public String getCallbackName() {
        return "user-encryption-backfill";
    }

    private String plain(String value) {
        return value != null && value.startsWith(PREFIX) ? cipher.decrypt(value) : value;
    }

    private String sealed(String value) {
        return value == null || value.startsWith(PREFIX) ? value : cipher.encrypt(value);
    }
}
