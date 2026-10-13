// JPA entity mapping the "users" table.
package com.iloveshopping.user;

import com.iloveshopping.crypto.Encrypted;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "users")
@Getter
@Setter
public class User {

    @Id
    @GeneratedValue
    private UUID id;

    // Encrypted like every personal field (V16); sign-in finds the row by emailHash instead (crypto/BlindIndex).
    @Convert(converter = Encrypted.class)
    @Column(nullable = false)
    private String email;

    /** HMAC of the lower-cased email: the lookup and uniqueness key. UserService sets it with the email. */
    @Column(name = "email_hash", nullable = false, unique = true, length = 64)
    private String emailHash;

    // Null for OAuth users (no local password); a DB CHECK still requires it for LOCAL accounts.
    @Column(name = "password_hash")
    private String passwordHash;

    @Convert(converter = Encrypted.class)
    @Column(name = "full_name", nullable = false)
    private String fullName;

    @Convert(converter = Encrypted.class)
    @Column(name = "avatar_url")
    private String avatarUrl;

    @Enumerated(EnumType.STRING)
    @Column(name = "auth_provider", nullable = false)
    private AuthProvider authProvider = AuthProvider.LOCAL;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Role role = Role.CUSTOMER;

    @Column(name = "email_verified", nullable = false)
    private boolean emailVerified = false;

    @Column(name = "two_factor_enabled", nullable = false)
    private boolean twoFactorEnabled = false;

    // Encrypted, not hashed: the server needs it back to compute the expected code.
    @Convert(converter = Encrypted.class)
    @Column(name = "two_factor_secret")
    private String twoFactorSecret;

    @Column(name = "two_factor_backup_codes")
    private String twoFactorBackupCodes;

    @Column(name = "oauth_provider_id")
    private String oauthProviderId;

    /** Optional; encrypted like every personal field (crypto/Encrypted). */
    @Convert(converter = Encrypted.class)
    @Column
    private String phone;

    @Column(name = "last_sign_in_at")
    private Instant lastSignInAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }
}
