// What the catalog asks the order domain before a review: has this account received this product?
package com.iloveshopping.catalog;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Implemented by the order domain (order/OrderedProducts), so reviews never read order tables. Only a
 * delivered order counts: paid-but-not-arrived is a review of the courier, not of the product.
 */
public interface PurchaseHistory {

    /** The account's latest delivered order that holds the product. */
    Optional<Purchase> delivered(UUID userId, UUID productId);

    record Purchase(String orderNumber, Instant placedAt) {
    }
}
