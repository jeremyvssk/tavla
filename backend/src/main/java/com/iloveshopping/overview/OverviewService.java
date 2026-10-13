// Gathers the staff overview from each domain's own service, keeping to what the caller's role may see.
package com.iloveshopping.overview;

import com.iloveshopping.audit.AuditLog;
import com.iloveshopping.auth.AuthPrincipal;
import com.iloveshopping.auth.StaffArea;
import com.iloveshopping.catalog.StaffProductService;
import com.iloveshopping.catalog.StaffReviewService;
import com.iloveshopping.order.StaffOrderService;
import com.iloveshopping.overview.dto.OverviewResponse;
import com.iloveshopping.user.Role;
import com.iloveshopping.user.UserService;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/** Owns no data: every number comes from the domain that owns it, through its service. */
@Service
public class OverviewService {

    private static final int RECENT = 8;

    private final StaffOrderService orders;
    private final StaffReviewService reviews;
    private final StaffProductService products;
    private final UserService users;
    private final AuditLog auditLog;

    public OverviewService(StaffOrderService orders, StaffReviewService reviews, StaffProductService products, UserService users,
                           AuditLog auditLog) {
        this.orders = orders;
        this.reviews = reviews;
        this.products = products;
        this.users = users;
        this.auditLog = auditLog;
    }

    public OverviewResponse overview(AuthPrincipal caller) {
        Role role = caller.role();
        boolean orderDesk = StaffArea.ORDERS.allows(role);
        StaffOrderService.Overview o = orders.overview();
        Map<String, Long> reviewCounts = StaffArea.REVIEWS.allows(role) ? reviews.counts() : null;
        return new OverviewResponse(o.salesToday(), o.ordersToday(), o.sales7d(), o.orders7d(), o.failedPaymentsToday(),
                o.paymentsTriedToday(),
                orderDesk ? o.toPack() : null, orderDesk ? o.oldestToPack() : null, orderDesk ? o.refundsUnconfirmed() : null,
                reviewCounts == null ? null : reviewCounts.get("todo"), reviewCounts == null ? null : reviewCounts.get("reported"),
                StaffArea.CATALOG.allows(role) ? products.lowStockCount() : null,
                StaffArea.USERS.allows(role) ? users.staffWithoutTwoFactor() : null,
                orderDesk ? o.latestOrders() : List.of(),
                auditLog.recent(role == Role.ADMIN ? null : caller.userId(), RECENT));
    }
}
