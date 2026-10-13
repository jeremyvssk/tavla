// Spring Data JPA repository for the User aggregate.
package com.iloveshopping.user;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID>, JpaSpecificationExecutor<User> {

    /** By crypto/BlindIndex's hash of the normalised email: the email column itself is ciphertext. */
    Optional<User> findByEmailHash(String emailHash);

    Optional<User> findByAuthProviderAndOauthProviderId(AuthProvider authProvider, String oauthProviderId);

    boolean existsByEmailHash(String emailHash);

    List<User> findByIdIn(Collection<UUID> ids);

    /** Rows of (role, count), for the users page's tabs. */
    @Query(value = "SELECT role, count(*) FROM users GROUP BY role", nativeQuery = true)
    List<Object[]> countByRole();

    /** Row-locked until the transaction ends: an account's changes that must count its rows (addresses) queue here. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT u FROM User u WHERE u.id = :id")
    Optional<User> lockById(UUID id);

    @Query(value = "SELECT count(*) FROM users WHERE role <> 'CUSTOMER' AND NOT two_factor_enabled", nativeQuery = true)
    long countStaffWithoutTwoFactor();

    /** One statement, so a sign-in never rewrites the rest of the row or bumps updated_at. */
    @Modifying
    @Query(value = "UPDATE users SET last_sign_in_at = now() WHERE id = :id", nativeQuery = true)
    int touchSignIn(UUID id);
}
