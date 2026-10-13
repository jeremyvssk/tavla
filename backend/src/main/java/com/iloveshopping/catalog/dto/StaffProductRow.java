// One row of the staff product list: hidden and archived products too, with SKU and exact stock.
package com.iloveshopping.catalog.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record StaffProductRow(
        UUID id,
        String name,
        String sku,
        String brandName,
        String categorySlug,
        BigDecimal price,
        int stockQuantity,
        boolean inStock,
        BigDecimal averageRating,
        int reviewCount,
        /** ACTIVE, HIDDEN or ARCHIVED */
        String state,
        String primaryImageUrl,
        ImageFraming primaryImageFraming) {
}
