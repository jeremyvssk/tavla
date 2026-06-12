// Application service for user persistence and lookups.
package com.iloveshopping.user;

import com.iloveshopping.user.exception.EmailAlreadyExistsException;
import com.iloveshopping.user.exception.EmailRegisteredWithPasswordException;
import com.iloveshopping.user.exception.UserNotFoundException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.Normalizer;
import java.util.Optional;
import java.util.UUID;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public UserService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    /**
     * Creates a LOCAL user. The password is NFC-normalized before BCrypt so that
     * accented characters hash consistently across register and login.
     * The existsByEmail pre-check gives a friendly error; the DataIntegrityViolation
     * catch closes the TOCTOU race against the DB UNIQUE constraint.
     */
    @Transactional
    public User createLocalUser(String email, String rawPassword, String fullName) {
        if (userRepository.existsByEmail(email)) {
            throw new EmailAlreadyExistsException();
        }
        User user = new User();
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode(normalizePassword(rawPassword)));
        user.setFullName(fullName);
        try {
            return userRepository.save(user);
        } catch (DataIntegrityViolationException e) {
            throw new EmailAlreadyExistsException();
        }
    }

    /**
     * Returns the existing user for this OAuth identity, or creates one. We never link OAuth
     * login to a pre-existing account that shares the email — that's an account-takeover vector —
     * so a clashing email is rejected with {@link EmailRegisteredWithPasswordException}.
     */
    @Transactional
    public User findOrCreateOAuthUser(OAuthUserInfo info) {
        return userRepository.findByAuthProviderAndOauthProviderId(info.provider(), info.providerId())
                .orElseGet(() -> createOAuthUser(info));
    }

    private User createOAuthUser(OAuthUserInfo info) {
        if (userRepository.existsByEmail(info.email())) {
            throw new EmailRegisteredWithPasswordException();
        }
        User user = new User();
        user.setEmail(info.email());
        user.setFullName(info.fullName());
        user.setAvatarUrl(info.avatarUrl());
        user.setAuthProvider(info.provider());
        user.setOauthProviderId(info.providerId());
        user.setEmailVerified(info.emailVerified());
        try {
            return userRepository.save(user);
        } catch (DataIntegrityViolationException e) {
            throw new EmailRegisteredWithPasswordException();
        }
    }

    @Transactional(readOnly = true)
    public User getById(UUID id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new UserNotFoundException(id));
    }

    @Transactional(readOnly = true)
    public Optional<User> findByEmail(String email) {
        return userRepository.findByEmail(email);
    }

    /**
     * Verifies a raw password against a user's stored hash. The password is NFC-normalized
     * the same way it was at registration so accented characters match consistently.
     */
    public boolean passwordMatches(User user, String rawPassword) {
        return passwordEncoder.matches(normalizePassword(rawPassword), user.getPasswordHash());
    }

    /**
     * Sets a new password for a user, NFC-normalized and BCrypted the same way as registration.
     * Used by the password-reset flow.
     */
    @Transactional
    public void updatePassword(UUID userId, String rawPassword) {
        User user = getById(userId);
        user.setPasswordHash(passwordEncoder.encode(normalizePassword(rawPassword)));
        userRepository.save(user);
    }

    /** Stores a pending TOTP secret. 2FA stays disabled until the user verifies a code. */
    @Transactional
    public void setTwoFactorSecret(UUID userId, String secret) {
        User user = getById(userId);
        user.setTwoFactorSecret(secret);
        userRepository.save(user);
    }

    /** Turns 2FA on and stores the (already hashed) backup codes. */
    @Transactional
    public void enableTwoFactor(UUID userId, String hashedBackupCodes) {
        User user = getById(userId);
        user.setTwoFactorEnabled(true);
        user.setTwoFactorBackupCodes(hashedBackupCodes);
        userRepository.save(user);
    }

    /** Turns 2FA off and clears the secret and backup codes. */
    @Transactional
    public void disableTwoFactor(UUID userId) {
        User user = getById(userId);
        user.setTwoFactorEnabled(false);
        user.setTwoFactorSecret(null);
        user.setTwoFactorBackupCodes(null);
        userRepository.save(user);
    }

    /** Overwrites the stored backup codes, e.g. after one is consumed at login. */
    @Transactional
    public void replaceBackupCodes(UUID userId, String hashedBackupCodes) {
        User user = getById(userId);
        user.setTwoFactorBackupCodes(hashedBackupCodes);
        userRepository.save(user);
    }

    static String normalizePassword(String raw) {
        return Normalizer.normalize(raw, Normalizer.Form.NFC);
    }
}
