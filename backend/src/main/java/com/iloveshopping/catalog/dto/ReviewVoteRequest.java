// Body of PUT /products/{id}/reviews/{reviewId}/vote; "helpful" is the only vote there is.
package com.iloveshopping.catalog.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public record ReviewVoteRequest(@NotNull @Pattern(regexp = "helpful", message = "must be helpful") String vote) {
}
