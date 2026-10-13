// The order domain's answers to the catalog's questions: which products has anyone ordered, and did this account receive one?
package com.iloveshopping.order;

import com.iloveshopping.catalog.ProductUsage;
import com.iloveshopping.catalog.PurchaseHistory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Component
class OrderedProducts implements ProductUsage, PurchaseHistory {

    private final OrderRepository orders;

    OrderedProducts(OrderRepository orders) {
        this.orders = orders;
    }

    @Override
    @Transactional(readOnly = true)
    public Set<UUID> ordered(Collection<UUID> productIds) {
        return productIds.isEmpty() ? Set.of() : new HashSet<>(orders.orderedProductIds(productIds));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Purchase> delivered(UUID userId, UUID productId) {
        return orders.latestDelivered(userId, productId).stream().findFirst()
                .map(row -> new Purchase((String) row[0], instant(row[1])));
    }

    /** A native TIMESTAMP column comes back as whatever the driver picks; times are stored in UTC (hibernate.jdbc.time_zone). */
    private static Instant instant(Object value) {
        return switch (value) {
            case Timestamp t -> t.toInstant();
            case LocalDateTime t -> t.toInstant(ZoneOffset.UTC);
            case Instant t -> t;
            default -> throw new IllegalStateException("unexpected timestamp type " + value.getClass());
        };
    }
}
