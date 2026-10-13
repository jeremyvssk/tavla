// The staff overview: the shop's numbers, what is waiting for people, the newest orders and recent changes.
package com.iloveshopping.overview.dto;

import com.iloveshopping.audit.dto.AuditEntryResponse;
import com.iloveshopping.order.dto.StaffOrderRow;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * A number from an area the caller's role can't open is null, not zero: Sales is not told how many reviews
 * wait, and Support not how many products run low. The sales figures are for every staff member.
 * recent: everyone's changes for an admin, a staff member's own otherwise.
 */
public record OverviewResponse(
        BigDecimal salesToday, long ordersToday, BigDecimal sales7d, long orders7d, long failedPaymentsToday, long paymentsTriedToday,
        Long toPack, Instant oldestToPack, Long refundsUnconfirmed, Long reviewsToCheck, Long reviewsReported, Long lowStock,
        Long staffWithout2fa, List<StaffOrderRow> latestOrders, List<AuditEntryResponse> recent) {
}
