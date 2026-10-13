// Body of POST /staff/products/bulk: one change for many products.
package com.iloveshopping.catalog.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * {@code categoryId} goes with move; {@code mode} and {@code value} with price, where raise and lower take
 * a percentage and set takes euros. Which fields an action needs is checked by the service. Archiving
 * goes through DELETE /products/{id}, which decides per product between archiving and deleting.
 */
public record BulkProductRequest(
        @NotEmpty @Size(max = 500) List<@NotNull UUID> ids,
        @NotNull @Pattern(regexp = "show|hide|move|price", message = "must be show, hide, move or price") String action,
        @Positive Integer categoryId,
        @Pattern(regexp = "set|raise|lower", message = "must be set, raise or lower") String mode,
        @DecimalMin("0.00") @Digits(integer = 8, fraction = 2) BigDecimal value) {
}
