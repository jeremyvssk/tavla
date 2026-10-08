// The whole cart: lines in the order they were added, the item count and the subtotal.
package com.iloveshopping.cart.dto;

import java.math.BigDecimal;
import java.util.List;

/** Count and subtotal cover only what can be bought now: sold-out and unavailable lines add nothing. */
public record CartResponse(List<CartLineResponse> lines, int itemCount, BigDecimal subtotal) {
}
