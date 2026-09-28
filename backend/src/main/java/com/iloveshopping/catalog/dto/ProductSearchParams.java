// Query parameters accepted by GET /products, bound and validated before any SQL is built.
package com.iloveshopping.catalog.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.List;

/**
 * Every field is optional. {@code sort} is checked against a fixed list here and mapped to SQL
 * through an allowlist in the search service, so the request string never reaches ORDER BY.
 * {@code minPrice} is inclusive and {@code maxPrice} exclusive, the same rule as the price facet.
 */
public record ProductSearchParams(
        @Size(max = 200) String q,
        @Size(max = 255) @Pattern(regexp = "^[a-z0-9-]+$", message = "must be a category slug") String category,
        @Size(max = 20) List<@Pattern(regexp = "^[a-z0-9-]{1,255}$", message = "must be a brand slug") String> brand,
        @DecimalMin("0") @Digits(integer = 8, fraction = 2) BigDecimal minPrice,
        @DecimalMin("0") @Digits(integer = 8, fraction = 2) BigDecimal maxPrice,
        @DecimalMin("1") @DecimalMax("5") BigDecimal minRating,
        @Pattern(regexp = "^(featured|relevance|price_asc|price_desc|rating|newest)$",
                message = "must be one of featured, relevance, price_asc, price_desc, rating, newest") String sort,
        @Min(0) @Max(1000) Integer page,
        @Min(1) @Max(48) Integer size) {
}
