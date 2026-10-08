// Query parameters of GET /orders: status group, date range, sort and page, checked before any SQL runs.
package com.iloveshopping.order.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;

/**
 * Every field is optional. {@code from} and {@code to} are days in the shop's time zone, both inclusive.
 * {@code status} and {@code sort} are checked against fixed lists here, and the sort reaches SQL only as
 * a bound parameter compared against those same names, never as text in ORDER BY.
 */
public record OrderListParams(
        @Pattern(regexp = "^(all|in_progress|delivered|cancelled|failed)$",
                message = "must be one of all, in_progress, delivered, cancelled, failed") String status,
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
        @Pattern(regexp = "^(newest|oldest|status|total_desc)$",
                message = "must be one of newest, oldest, status, total_desc") String sort,
        @Min(0) @Max(1000) Integer page,
        @Min(1) @Max(50) Integer size) {
}
