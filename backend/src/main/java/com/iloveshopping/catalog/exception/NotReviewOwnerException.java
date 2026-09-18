// Thrown when a user tries to delete a review that belongs to someone else; maps to 403.
package com.iloveshopping.catalog.exception;

public class NotReviewOwnerException extends RuntimeException {

    public NotReviewOwnerException() {
        super("not the review owner");
    }
}
