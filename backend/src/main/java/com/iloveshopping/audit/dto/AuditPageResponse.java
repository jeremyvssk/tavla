// A page of the audit log, plus the staff who appear in it, for the Person filter.
package com.iloveshopping.audit.dto;

import java.util.List;
import java.util.UUID;

public record AuditPageResponse(List<AuditEntryResponse> items, int page, int size, long totalItems, int totalPages,
                                List<Actor> actors) {

    public record Actor(UUID id, String name) {
    }
}
