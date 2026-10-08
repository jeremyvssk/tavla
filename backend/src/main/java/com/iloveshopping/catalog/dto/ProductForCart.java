// What a cart line needs from the catalog, read fresh on every cart read: the current price and stock.
package com.iloveshopping.catalog.dto;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * {@code options} is the variant's choices as one line ("Colour: Green, Size: Large"), or null for a
 * product with no variants. Inactive products are included with {@code active} false, so a cart can
 * say a line is no longer sold instead of dropping it without a word.
 */
public record ProductForCart(
        UUID id,
        String name,
        BigDecimal price,
        int stockQuantity,
        boolean active,
        String brandName,
        String options,
        String imageUrl,
        ImageFraming imageFraming) {
}
