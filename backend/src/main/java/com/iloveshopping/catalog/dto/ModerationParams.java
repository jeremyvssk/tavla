// Query parameters of GET /staff/reviews: which queue, which stars, in what order, which page.
package com.iloveshopping.catalog.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;

/** state todo: new and reported reviews nobody has looked at. rating: low 1-2, mid 3, high 4-5. */
public record ModerationParams(
        @Pattern(regexp = "todo|reported|live|hidden", message = "must be todo, reported, live or hidden") String state,
        @Pattern(regexp = "low|mid|high", message = "must be low, mid or high") String rating,
        @Pattern(regexp = "oldest|newest", message = "must be oldest or newest") String sort,
        @Min(0) @Max(10_000) Integer page,
        @Min(1) @Max(100) Integer size) {
}
