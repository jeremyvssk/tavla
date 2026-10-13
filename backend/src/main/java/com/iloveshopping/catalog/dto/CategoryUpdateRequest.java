// Body of PUT /categories/{id}: rename a shelf, change its address, move it under another, or hide it.
package com.iloveshopping.catalog.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record CategoryUpdateRequest(
        @NotBlank @Size(max = 255) String name,
        @NotBlank @Size(max = 255) @Pattern(regexp = Slugs.PATTERN, message = Slugs.MESSAGE) String slug,
        // null: a top-level shelf
        @Positive Integer parentId,
        // false hides the shelf and every shelf below it from the shop
        @NotNull Boolean active) {
}
