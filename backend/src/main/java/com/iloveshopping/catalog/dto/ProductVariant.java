// One colour, wood or size of a product, as listed on its siblings' product pages.
package com.iloveshopping.catalog.dto;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

public record ProductVariant(UUID id, Map<String, Object> options, BigDecimal price, boolean inStock, String imageUrl) {
}
