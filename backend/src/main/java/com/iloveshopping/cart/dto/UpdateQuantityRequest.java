// Body of PUT /cart/items/{productId}: the line's new quantity.
package com.iloveshopping.cart.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record UpdateQuantityRequest(@NotNull @Min(1) @Max(10) Integer quantity) {
}
