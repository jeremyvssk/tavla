// Public representation of a review.
package com.iloveshopping.catalog.dto;

import java.time.Instant;
import java.util.UUID;

public record ReviewResponse(
        UUID id, UUID productId, int rating, String title, String body, boolean verifiedPurchase, Instant createdAt) {
}
