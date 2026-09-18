// Admin request body for creating a category.
package com.iloveshopping.catalog.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record CategoryRequest(
        @NotBlank @Size(max = 255) String name,
        @NotBlank @Size(max = 255) @Pattern(regexp = Slugs.PATTERN, message = Slugs.MESSAGE) String slug,
        // Null creates a top-level category.
        @Positive Integer parentId) {
}
