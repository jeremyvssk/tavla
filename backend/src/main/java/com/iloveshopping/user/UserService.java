// Application service for user persistence and lookups.
package com.iloveshopping.user;

import com.iloveshopping.user.exception.EmailAlreadyExistsException;
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

    @Transactional(readOnly = true)
    public User getById(UUID id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new UserNotFoundException(id));
    }

    @Transactional(readOnly = true)
    public Optional<User> findByEmail(String email) {
        return userRepository.findByEmail(email);
    }

    static String normalizePassword(String raw) {
        return Normalizer.normalize(raw, Normalizer.Form.NFC);
    }
}
