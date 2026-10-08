// JPA entity mapping the "carts" table: one cart per user, or per guest token.
package com.iloveshopping.cart;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;

import java.time.Instant;
import java.util.UUID;

/**
 * Read-only from Java: rows are created by the ON CONFLICT inserts in {@link CartRepository}, so two
 * requests racing to make the same user's cart can't both succeed. Exactly one of userId and
 * guestTokenHash is set (ck_carts_one_owner).
 */
@Entity
@Table(name = "carts")
@Getter
public class Cart {

    @Id
    private UUID id;

    @Column(name = "user_id")
    private UUID userId;

    @Column(name = "guest_token_hash")
    private String guestTokenHash;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
