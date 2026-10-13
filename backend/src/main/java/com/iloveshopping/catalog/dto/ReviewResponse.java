// A review as the product page shows it: the review, who wrote it (first name and initial) and its helpful votes.
package com.iloveshopping.catalog.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * authorName is "Toomas R.", never the email. myVote is "helpful" when the signed-in shopper marked it so,
 * else null. published is false only on the author's own hidden review, which only its author is sent.
 */
public record ReviewResponse(
        UUID id, UUID productId, int rating, String title, String body, boolean verifiedPurchase, Instant createdAt,
        String authorName, int helpfulCount, String myVote, boolean published) {
}
