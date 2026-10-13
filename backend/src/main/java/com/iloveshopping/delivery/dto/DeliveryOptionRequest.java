// Body of POST /staff/delivery/options and PUT /staff/delivery/options/{id}: every field staff may set on an option.
package com.iloveshopping.delivery.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.List;

/** {@code id} is ignored if sent: the server names a new option, and an existing one keeps its id for its orders. */
public record DeliveryOptionRequest(
        @Size(max = 40) String id,
        @NotBlank @Size(max = 80) String name,
        @NotNull @DecimalMin("0.00") @DecimalMax("999.99") @Digits(integer = 3, fraction = 2) BigDecimal price,
        @NotNull @Pattern(regexp = "([01]\\d|2[0-3]):[0-5]\\d", message = "must be a time like 15:00") String orderBy,
        @NotNull @Min(0) @Max(30) Integer daysMin,
        @NotNull @Min(0) @Max(30) Integer daysMax,
        @NotEmpty @Size(max = 4) List<@NotNull @Pattern(regexp = "EE|LV|LT|FI", message = "must be EE, LV, LT or FI") String> countries,
        @NotNull Boolean active,
        @NotNull Boolean freeOverLimit) {
}
