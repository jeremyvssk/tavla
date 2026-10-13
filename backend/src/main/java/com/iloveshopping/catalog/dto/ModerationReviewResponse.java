// A review as the moderation queue shows it: the review, its product, the order behind it, what the check noticed and the reports.
package com.iloveshopping.catalog.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** flags: what the automatic check noticed (a link, a phone number); serious ones are shown in red. reports: newest first. */
public record ModerationReviewResponse(
        UUID id, int rating, String title, String body, boolean verifiedPurchase, Instant createdAt, String authorName,
        int helpfulCount, UUID productId, String productName, String productImageUrl, ImageFraming productImageFraming,
        String orderNumber, List<Flag> flags, List<Report> reports, String state, String hiddenReason) {

    public record Flag(String text, boolean serious) {
    }

    public record Report(String reason, Instant at) {
    }
}
