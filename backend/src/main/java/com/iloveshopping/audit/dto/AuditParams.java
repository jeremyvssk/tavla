// Query parameters of GET /staff/audit: who, what kind, how far back, and the page.
package com.iloveshopping.audit.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;

import java.util.UUID;

/** Each value is checked against a fixed list, so nothing from the request reaches a query as text. */
public record AuditParams(
        UUID actor,
        @Pattern(regexp = "orders|products|reviews|access", message = "must be orders, products, reviews or access") String kind,
        @Pattern(regexp = "today|7d|30d", message = "must be today, 7d or 30d") String since,
        @Min(0) @Max(10_000) Integer page,
        @Min(1) @Max(100) Integer size) {
}
