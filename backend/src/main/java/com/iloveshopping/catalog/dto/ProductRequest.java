// Admin request body for creating or replacing a product. Whitelists every client-writable field.
package com.iloveshopping.catalog.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.Map;

/**
 * No id, rating, review count, imperial measurements or timestamps: those are server-owned, and
 * because Jackson rejects unknown properties, a client that sends them gets a 400 rather than a
 * silent overwrite. Dimensions are metric only; ProductService derives the imperial columns.
 */
public record ProductRequest(
        @NotBlank @Size(max = 255) String name,
        @Size(max = 10_000) String description,
        @NotNull @DecimalMin("0.00") @Digits(integer = 8, fraction = 2) BigDecimal price,
        @NotNull @Min(0) Integer stockQuantity,
        @NotNull @Positive Integer categoryId,
        @Positive Integer brandId,
        @Size(max = 30) Map<String, Object> attributes,
        Boolean active,
        @DecimalMin(value = "0", inclusive = false) @Digits(integer = 6, fraction = 3) BigDecimal weightKg,
        @DecimalMin(value = "0", inclusive = false) @Digits(integer = 6, fraction = 2) BigDecimal widthCm,
        @DecimalMin(value = "0", inclusive = false) @Digits(integer = 6, fraction = 2) BigDecimal heightCm,
        @DecimalMin(value = "0", inclusive = false) @Digits(integer = 6, fraction = 2) BigDecimal depthCm) {
}
