// One page of products plus the facet counts for the whole result set.
package com.iloveshopping.catalog.dto;

import java.util.List;

public record ProductSearchResponse(
        List<ProductSummary> items,
        int page,
        int size,
        long totalItems,
        int totalPages,
        String sort,
        // True when full-text search found nothing and these are near matches on the product name.
        boolean approximate,
        Facets facets) {
}
