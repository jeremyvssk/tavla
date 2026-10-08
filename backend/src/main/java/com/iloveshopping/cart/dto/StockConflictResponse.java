// 409 body when an add or a quantity change asks for more than the line can hold.
package com.iloveshopping.cart.dto;

import java.util.UUID;

/**
 * {@code error} is "out_of_stock" (none left), "not_enough_stock" or "line_limit". {@code maxQuantity}
 * is the most the line can hold and {@code inCart} what it holds now, so the page can say "only 2
 * left" and offer the 2.
 */
public record StockConflictResponse(String error, UUID productId, int maxQuantity, int inCart) {
}
