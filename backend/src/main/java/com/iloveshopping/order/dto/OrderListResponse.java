// A page of the account's orders, and how many orders the date range holds per status group.
package com.iloveshopping.order.dto;

import com.iloveshopping.catalog.dto.ImageFraming;
import com.iloveshopping.order.OrderStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The shape frontend/src/api/orders.ts calls OrderListResponse. {@code counts} ignores the status
 * filter, so the filter pills can show what each one would hold; keys are the status group names.
 */
public record OrderListResponse(List<Summary> items, int page, int size, long totalItems, int totalPages,
                                Map<String, Long> counts) {

    /** One row of the list; {@code preview} is the first three lines, for their names and photos. */
    public record Summary(UUID id, String number, Instant placedAt, OrderStatus status, int itemCount, BigDecimal total,
                          List<Preview> preview) {
    }

    public record Preview(String name, String imageUrl, ImageFraming imageFraming) {
    }
}
