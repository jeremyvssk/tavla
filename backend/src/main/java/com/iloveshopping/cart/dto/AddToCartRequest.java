// Body of POST /cart/items: which product and how many. Never a price; the server reads that itself.
package com.iloveshopping.cart.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record AddToCartRequest(
        @NotNull UUID productId,
        // mirrors the cart_items quantity CHECK; the sum with what is already in the cart is checked too
        @NotNull @Min(1) @Max(10) Integer quantity) {
}
