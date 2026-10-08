// Spring Data repository for cart lines; each write is one statement that checks its own limit.
package com.iloveshopping.cart;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface CartItemRepository extends JpaRepository<CartItem, CartItem.Key> {

    List<CartItem> findByIdCartIdOrderByAddedAtAscIdProductIdAsc(UUID cartId);

    /**
     * Adds to the line, or creates it, only while the result stays at or under {@code max}. One
     * statement, so two adds racing on the same line can't both pass a check and overshoot. Returns
     * the rows written: 0 means the add was refused.
     */
    @Modifying(clearAutomatically = true)
    @Query(value = """
            INSERT INTO cart_items (cart_id, product_id, quantity)
            SELECT :cartId, :productId, :quantity WHERE :quantity <= :max
            ON CONFLICT (cart_id, product_id)
            DO UPDATE SET quantity = cart_items.quantity + EXCLUDED.quantity
            WHERE cart_items.quantity + EXCLUDED.quantity <= :max""", nativeQuery = true)
    int addUpTo(UUID cartId, UUID productId, int quantity, int max);

    @Modifying(clearAutomatically = true)
    @Query(value = "UPDATE cart_items SET quantity = :quantity WHERE cart_id = :cartId AND product_id = :productId",
            nativeQuery = true)
    int setQuantity(UUID cartId, UUID productId, int quantity);

    @Modifying(clearAutomatically = true)
    @Query(value = "DELETE FROM cart_items WHERE cart_id = :cartId AND product_id = :productId", nativeQuery = true)
    int deleteLine(UUID cartId, UUID productId);

    /** The lines an order took. No clearing here: the order being built in the same transaction must stay managed. */
    @Modifying
    @Query(value = "DELETE FROM cart_items WHERE cart_id = :cartId AND product_id IN (:productIds)", nativeQuery = true)
    int deleteLines(UUID cartId, Collection<UUID> productIds);

    /** Creates the line, or raises it to {@code quantity}; never lowers what the cart holds now. */
    @Modifying
    @Query(value = """
            INSERT INTO cart_items (cart_id, product_id, quantity) VALUES (:cartId, :productId, :quantity)
            ON CONFLICT (cart_id, product_id) DO UPDATE SET quantity = GREATEST(cart_items.quantity, EXCLUDED.quantity)""",
            nativeQuery = true)
    void restoreLine(UUID cartId, UUID productId, int quantity);

    /**
     * Every line of one cart into another in one statement; where both have the product, the larger
     * quantity wins. Both are at most 10 already (the CHECK), so the result is too.
     */
    @Modifying(clearAutomatically = true)
    @Query(value = """
            INSERT INTO cart_items (cart_id, product_id, quantity, added_at)
            SELECT :toCartId, product_id, quantity, added_at FROM cart_items WHERE cart_id = :fromCartId
            ON CONFLICT (cart_id, product_id) DO UPDATE SET quantity = GREATEST(cart_items.quantity, EXCLUDED.quantity)""",
            nativeQuery = true)
    int mergeInto(UUID fromCartId, UUID toCartId);
}
