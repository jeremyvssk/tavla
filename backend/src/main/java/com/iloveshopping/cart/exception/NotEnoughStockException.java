// Thrown when a cart line would go past the stock or the per-line limit; maps to 409.
package com.iloveshopping.cart.exception;

import com.iloveshopping.cart.dto.StockConflictResponse;

import java.util.UUID;

public class NotEnoughStockException extends RuntimeException {

    private final StockConflictResponse body;

    public NotEnoughStockException(String code, UUID productId, int maxQuantity, int inCart) {
        super(code);
        this.body = new StockConflictResponse(code, productId, maxQuantity, inCart);
    }

    public StockConflictResponse getBody() {
        return body;
    }
}
