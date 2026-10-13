// A page of the staff order queue, and how many orders each status tab holds.
package com.iloveshopping.order.dto;

import java.util.List;
import java.util.Map;

/** counts: all, one per OrderStatus name, and REFUND_PENDING. */
public record StaffOrderPageResponse(List<StaffOrderRow> items, int page, int size, long totalItems, int totalPages,
                                     Map<String, Long> counts) {
}
