// The spread behind a product's average, and what the signed-in shopper may do: review it, or read why theirs is hidden.
package com.iloveshopping.catalog.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/** distribution: shown reviews per star, index 0 = one star. me: null for a guest. */
public record ReviewSummaryResponse(BigDecimal average, int count, List<Integer> distribution, Me me) {

    /**
     * purchasedAt: when the shopper's latest delivered order with this product was placed; null if none, and
     * then they can't review it. review: their own review, shown even when hidden (published false).
     */
    public record Me(Instant purchasedAt, boolean reviewed, ReviewResponse review, String hiddenReason) {
    }
}
