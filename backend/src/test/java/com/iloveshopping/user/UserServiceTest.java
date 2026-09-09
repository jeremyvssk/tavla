// Unit tests for UserService with mocked repository and password encoder.
package com.iloveshopping.user;

import com.iloveshopping.user.exception.EmailAlreadyExistsException;
import com.iloveshopping.user.exception.EmailRegisteredWithPasswordException;
import com.iloveshopping.user.exception.UserNotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    UserRepository userRepository;

    @Mock
    PasswordEncoder passwordEncoder;

    @InjectMocks
    UserService userService;

    @Test
    void createLocalUser_hashesPasswordAndSavesWithDefaults() {
        when(userRepository.existsByEmail("alice@example.com")).thenReturn(false);
        when(passwordEncoder.encode(anyString())).thenReturn("hashed");
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        userService.createLocalUser("alice@example.com", "s3cret", "Alice");

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        User saved = captor.getValue();
        assertThat(saved.getEmail()).isEqualTo("alice@example.com");
        assertThat(saved.getPasswordHash()).isEqualTo("hashed");
        assertThat(saved.getFullName()).isEqualTo("Alice");
        assertThat(saved.getRole()).isEqualTo(Role.CUSTOMER);
        assertThat(saved.getAuthProvider()).isEqualTo(AuthProvider.LOCAL);
    }

    @Test
    void createLocalUser_foldsEmailToLowerCaseAndTrims() {
        // A phone keyboard capitalises the first letter; without this the user owns a second
        // account they can never log into.
        when(userRepository.existsByEmail("ada@shop.com")).thenReturn(false);
        when(passwordEncoder.encode(anyString())).thenReturn("hashed");
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        userService.createLocalUser("  Ada@Shop.COM  ", "s3cret", "Ada");

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        assertThat(captor.getValue().getEmail()).isEqualTo("ada@shop.com");
    }

    @Test
    void findByEmail_looksUpTheNormalizedAddress() {
        // Normalising on write alone would still fail every login typed with a capital.
        when(userRepository.findByEmail("ada@shop.com")).thenReturn(Optional.of(new User()));

        assertThat(userService.findByEmail("Ada@Shop.com")).isPresent();
        verify(userRepository).findByEmail("ada@shop.com");
    }

    @Test
    void dummyPasswordCheck_runsARealComparison() {
        userService.dummyPasswordCheck("guess");

        // Same work as a genuine check, so the no-such-user path costs the same time.
        verify(passwordEncoder).matches(eq("guess"), any());
    }

    @Test
    void createLocalUser_rejectsDuplicateEmailFromPreCheck() {
        when(userRepository.existsByEmail("dupe@example.com")).thenReturn(true);

        assertThatThrownBy(() -> userService.createLocalUser("dupe@example.com", "pw", "Dupe"))
                .isInstanceOf(EmailAlreadyExistsException.class);
        verify(userRepository, never()).save(any());
    }

    @Test
    void createLocalUser_mapsConstraintRaceToEmailExists() {
        when(userRepository.existsByEmail(anyString())).thenReturn(false);
        when(passwordEncoder.encode(anyString())).thenReturn("hashed");
        when(userRepository.save(any(User.class)))
                .thenThrow(new DataIntegrityViolationException("duplicate key"));

        assertThatThrownBy(() -> userService.createLocalUser("race@example.com", "pw", "Race"))
                .isInstanceOf(EmailAlreadyExistsException.class);
    }

    @Test
    void findOrCreateOAuthUser_returnsExistingByProviderId() {
        User existing = new User();
        when(userRepository.findByAuthProviderAndOauthProviderId(AuthProvider.GOOGLE, "sub-1"))
                .thenReturn(Optional.of(existing));

        OAuthUserInfo info = new OAuthUserInfo(AuthProvider.GOOGLE, "sub-1", "x@gmail.com", "X", null, true);
        assertThat(userService.findOrCreateOAuthUser(info)).isSameAs(existing);
        verify(userRepository, never()).save(any());
    }

    @Test
    void findOrCreateOAuthUser_createsNewWithoutPasswordWhenAbsent() {
        when(userRepository.findByAuthProviderAndOauthProviderId(AuthProvider.GOOGLE, "sub-2"))
                .thenReturn(Optional.empty());
        when(userRepository.existsByEmail("new@gmail.com")).thenReturn(false);
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        OAuthUserInfo info =
                new OAuthUserInfo(AuthProvider.GOOGLE, "sub-2", "new@gmail.com", "New User", "http://pic", true);
        User created = userService.findOrCreateOAuthUser(info);

        assertThat(created.getAuthProvider()).isEqualTo(AuthProvider.GOOGLE);
        assertThat(created.getOauthProviderId()).isEqualTo("sub-2");
        assertThat(created.getEmail()).isEqualTo("new@gmail.com");
        assertThat(created.getPasswordHash()).isNull();
        assertThat(created.isEmailVerified()).isTrue();
    }

    @Test
    void findOrCreateOAuthUser_rejectsWhenEmailBelongsToAnotherAccount() {
        when(userRepository.findByAuthProviderAndOauthProviderId(AuthProvider.GOOGLE, "sub-3"))
                .thenReturn(Optional.empty());
        when(userRepository.existsByEmail("taken@gmail.com")).thenReturn(true);

        OAuthUserInfo info = new OAuthUserInfo(AuthProvider.GOOGLE, "sub-3", "taken@gmail.com", "Taken", null, true);
        assertThatThrownBy(() -> userService.findOrCreateOAuthUser(info))
                .isInstanceOf(EmailRegisteredWithPasswordException.class);
        verify(userRepository, never()).save(any());
    }

    @Test
    void getById_returnsUserWhenPresent() {
        UUID id = UUID.randomUUID();
        User user = new User();
        when(userRepository.findById(id)).thenReturn(Optional.of(user));

        assertThat(userService.getById(id)).isSameAs(user);
    }

    @Test
    void getById_throwsWhenMissing() {
        UUID id = UUID.randomUUID();
        when(userRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.getById(id))
                .isInstanceOf(UserNotFoundException.class);
    }

    @Test
    void normalizePassword_appliesNfcComposition() {
        // "e" + combining acute accent (U+0301) normalizes to composed "é" (U+00E9).
        assertThat(UserService.normalizePassword("e\u0301")).isEqualTo("\u00e9");
    }
}
