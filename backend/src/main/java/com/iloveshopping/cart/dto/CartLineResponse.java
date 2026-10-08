// One cart line as the drawer and the checkout summary show it, priced and stock-checked on this read.
package com.iloveshopping.cart.dto;

import com.iloveshopping.catalog.dto.ImageFraming;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * {@code state} is "ok"; "short" when fewer are in stock than the line asks for; "sold_out"; or
 * "unavailable" when the product is no longer sold. {@code countedQuantity} is what the subtotal
 * counts: the whole line when ok, what is left when short, nothing otherwise. {@code maxQuantity} is
 * where the stepper stops: the stock or the per-line limit, whichever is lower.
 */
public record CartLineResponse(
        UUID productId,
        String name,
        String brandName,
        String options,
        String imageUrl,
        ImageFraming imageFraming,
        BigDecimal unitPrice,
        int quantity,
        int countedQuantity,
        BigDecimal lineTotal,
        int maxQuantity,
        String state) {
}
