// Admin request body for creating a brand.
package com.iloveshopping.catalog.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record BrandRequest(
        @NotBlank @Size(max = 255) String name,
        @NotBlank @Size(max = 255) @Pattern(regexp = Slugs.PATTERN, message = Slugs.MESSAGE) String slug,
        // Rendered into an <img src> by the frontend, so only our own image path or https is
        // accepted; a javascript: or data: URL here would be stored XSS.
        @Size(max = 255) @Pattern(regexp = "^(/images/|https://)\\S+$", message = "must be an https URL or an /images/ path")
        String logoUrl) {
}
