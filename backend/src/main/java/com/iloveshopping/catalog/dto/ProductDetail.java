// Full product representation for the product page.
package com.iloveshopping.catalog.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record ProductDetail(
        UUID id,
        String name,
        String sku,
        String description,
        BigDecimal price,
        int stockQuantity,
        boolean inStock,
        boolean active,
        /** taken out of the shop for good; only staff ever see an archived product */
        boolean archived,
        CategoryRef category,
        List<CategoryRef> breadcrumb,
        BrandResponse brand,
        List<ProductImageResponse> images,
        Map<String, Object> attributes,
        Measurements measurements,
        BigDecimal averageRating,
        int reviewCount,
        Instant createdAt,
        Instant updatedAt,
        List<ProductVariant> variants) {
}
