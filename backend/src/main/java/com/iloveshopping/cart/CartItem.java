// JPA entity mapping the "cart_items" table: a product and its quantity in one cart.
package com.iloveshopping.cart;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;

/**
 * The product is a bare id, not a JPA link to the catalog's Product: the cart reads product facts
 * through ProductService, so the two domains don't share entities. Writes go through the
 * conditional statements in {@link CartItemRepository}.
 */
@Entity
@Table(name = "cart_items")
@Getter
public class CartItem {

    @EmbeddedId
    private Key id;

    @Column(nullable = false)
    private int quantity;

    @Column(name = "added_at", nullable = false)
    private Instant addedAt;

    public UUID getProductId() {
        return id.productId;
    }

    @Embeddable
    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @EqualsAndHashCode
    public static class Key implements Serializable {

        @Column(name = "cart_id")
        private UUID cartId;

        @Column(name = "product_id")
        private UUID productId;
    }
}
