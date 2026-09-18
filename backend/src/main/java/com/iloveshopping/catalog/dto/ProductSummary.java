// Compact product representation for listings: only what a product card shows.
package com.iloveshopping.catalog.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record ProductSummary(
        UUID id,
        String name,
        BigDecimal price,
        BigDecimal averageRating,
        int reviewCount,
        boolean inStock,
        String brandName,
        String categorySlug,
        String primaryImageUrl) {
}
