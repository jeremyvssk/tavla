// A page of the moderation queue, and how many reviews each tab holds.
package com.iloveshopping.catalog.dto;

import java.util.List;
import java.util.Map;

/** counts: todo, reported, live and hidden. */
public record ModerationPageResponse(List<ModerationReviewResponse> items, int page, int size, long totalItems, int totalPages,
                                     Map<String, Long> counts) {
}
