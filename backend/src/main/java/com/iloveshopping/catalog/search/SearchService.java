// The search seam: callers depend on this, so Postgres can be replaced by Elasticsearch without touching them.
package com.iloveshopping.catalog.search;

import com.iloveshopping.catalog.dto.ProductSearchParams;
import com.iloveshopping.catalog.dto.ProductSearchResponse;
import com.iloveshopping.catalog.dto.Suggestion;

import java.util.List;

public interface SearchService {

    /** Filters, ranks or sorts, and pages active products, with facet counts for the whole result set. */
    ProductSearchResponse search(ProductSearchParams params);

    /** Up to a handful of product names matching a partial word, for search-as-you-type. */
    List<Suggestion> suggest(String query);
}
