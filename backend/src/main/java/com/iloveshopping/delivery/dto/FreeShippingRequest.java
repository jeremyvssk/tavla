// Body of PUT /staff/delivery/free-shipping: the limit, and whether it applies at all.
package com.iloveshopping.delivery.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record FreeShippingRequest(
        @NotNull @DecimalMin("0.00") @DecimalMax("99999.99") @Digits(integer = 5, fraction = 2) BigDecimal from,
        @NotNull Boolean on) {
}
