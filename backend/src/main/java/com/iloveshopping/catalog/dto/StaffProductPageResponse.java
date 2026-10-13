// A page of the staff product list, and how many products each state tab holds under the same filters.
package com.iloveshopping.catalog.dto;

import java.util.List;
import java.util.Map;

/** counts: all (everything not archived), active, hidden, out, low and archived. */
public record StaffProductPageResponse(List<StaffProductRow> items, int page, int size, long totalItems, int totalPages,
                                       Map<String, Long> counts) {
}
