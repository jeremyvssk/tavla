// Query parameters of GET /products/{id}/reviews: the page, the order and an optional star filter.
package com.iloveshopping.catalog.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;

public record ReviewListParams(
        @Min(0) @Max(1000) Integer page,
        @Min(1) @Max(50) Integer size,
        @Pattern(regexp = "helpful|newest|highest|lowest", message = "must be helpful, newest, highest or lowest") String sort,
        @Min(1) @Max(5) Integer rating) {
}
