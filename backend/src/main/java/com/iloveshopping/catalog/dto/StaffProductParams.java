// Query parameters of GET /staff/products and its CSV export: which products, in what order, which page.
package com.iloveshopping.catalog.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** {@code q} is part of a name or the start of a SKU; {@code category} includes the shelves below it. */
public record StaffProductParams(
        @Size(max = 100) String q,
        @Pattern(regexp = "all|active|hidden|out|low|archived", message = "must be all, active, hidden, out, low or archived") String state,
        @Size(max = 255) String category,
        @Size(max = 255) String brand,
        @Pattern(regexp = "price_desc|price_asc|stock_asc|stock_desc|rating|newest",
                message = "must be price_desc, price_asc, stock_asc, stock_desc, rating or newest") String sort,
        @Min(0) @Max(10_000) Integer page,
        @Min(1) @Max(100) Integer size) {
}
