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
        String description,
        BigDecimal price,
        int stockQuantity,
        boolean inStock,
        boolean active,
        CategoryRef category,
        List<CategoryRef> breadcrumb,
        BrandResponse brand,
        List<ProductImageResponse> images,
        Map<String, Object> attributes,
        Measurements measurements,
        BigDecimal averageRating,
        int reviewCount,
        Instant createdAt,
        Instant updatedAt) {
}
