// A page of accounts, and how many there are per group, for the users page's tabs.
package com.iloveshopping.user.dto;

import java.util.List;
import java.util.Map;

/** counts: all, customers, staff, and admins. */
public record StaffUserPageResponse(List<StaffUserResponse> items, int page, int size, long totalItems, int totalPages,
                                    Map<String, Long> counts) {
}
