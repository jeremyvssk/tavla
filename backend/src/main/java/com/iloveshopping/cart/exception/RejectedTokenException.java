// Thrown when a cart request carries an access token the security filter rejected; maps to 401.
package com.iloveshopping.cart.exception;

/**
 * The cart is public, so a stale token would otherwise fall through to the guest cart and a signed-in
 * shopper would see an empty one. A 401 instead makes the client refresh the token and retry.
 */
public class RejectedTokenException extends RuntimeException {

    public RejectedTokenException() {
        super("unauthorized");
    }
}
