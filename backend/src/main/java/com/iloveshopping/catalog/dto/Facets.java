// All facet counts returned alongside a product listing.
package com.iloveshopping.catalog.dto;

import java.util.List;

public record Facets(
        List<FacetValue> categories,
        List<FacetValue> brands,
        List<PriceBucket> prices,
        List<RatingBucket> ratings) {
}
