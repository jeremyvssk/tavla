// The search seam: callers depend on this, so Postgres can be replaced by Elasticsearch without touching them.
package com.iloveshopping.catalog.search;

import com.iloveshopping.catalog.dto.ProductSearchParams;
import com.iloveshopping.catalog.dto.ProductSearchResponse;
import com.iloveshopping.catalog.dto.ProductSummary;
import com.iloveshopping.catalog.dto.Suggestion;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface SearchService {

    /** Filters, ranks or sorts, and pages active products, with facet counts for the whole result set. */
    ProductSearchResponse search(ProductSearchParams params);

    /** Up to a handful of product names matching a partial word, for search-as-you-type. */
    List<Suggestion> suggest(String query);

    /**
     * In-stock products that go with the given ones: the same game (chess, Go, backgammon), sensibly
     * priced before dear, other shelves before the shelves already covered, best first. None of the
     * given products, and one variant per product name.
     */
    List<ProductSummary> complements(Collection<UUID> productIds, int limit);
}
