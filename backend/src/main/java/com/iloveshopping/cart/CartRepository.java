// Spring Data repository for carts: lookup by owner, race-free creation, the order's lock, and the guest expiry delete.
package com.iloveshopping.cart;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface CartRepository extends JpaRepository<Cart, UUID> {

    Optional<Cart> findByUserId(UUID userId);

    Optional<Cart> findByGuestTokenHash(String guestTokenHash);

    /** SELECT ... FOR UPDATE, held until the transaction ends. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM Cart c WHERE c.id = :id")
    Optional<Cart> lockById(UUID id);

    /** A no-op when the user already has a cart, so a double-clicked first add can't make two. */
    @Modifying
    @Query(value = "INSERT INTO carts (id, user_id) VALUES (:id, :userId) ON CONFLICT (user_id) DO NOTHING",
            nativeQuery = true)
    void insertForUser(UUID id, UUID userId);

    @Modifying
    @Query(value = "INSERT INTO carts (id, guest_token_hash) VALUES (:id, :tokenHash)", nativeQuery = true)
    void insertForGuest(UUID id, String tokenHash);

    /** Every change touches the cart, which is what keeps a guest cart from expiring while it is used. */
    @Modifying
    @Query(value = "UPDATE carts SET updated_at = now() WHERE id = :id", nativeQuery = true)
    void touch(UUID id);

    @Modifying
    @Query(value = "DELETE FROM carts WHERE user_id IS NULL AND updated_at < :cutoff", nativeQuery = true)
    int deleteGuestCartsUntouchedSince(Instant cutoff);
}
