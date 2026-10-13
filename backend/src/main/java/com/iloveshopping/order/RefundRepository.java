// Spring Data JPA repository for part refunds.
package com.iloveshopping.order;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public interface RefundRepository extends JpaRepository<Refund, UUID> {

    List<Refund> findByOrderIdOrderByCreatedAtAsc(UUID orderId);

    @Query(value = "SELECT amount FROM refunds WHERE order_id = :orderId ORDER BY created_at DESC LIMIT 1", nativeQuery = true)
    BigDecimal latestAmount(UUID orderId);

    /** Refunds Stripe hasn't accepted yet, untouched for at least {@code ageSeconds}, oldest first. */
    @Query(value = """
            SELECT id FROM refunds WHERE status = 'DUE' AND updated_at < now() - make_interval(secs => :ageSeconds)
            ORDER BY updated_at LIMIT :limit""", nativeQuery = true)
    List<UUID> findDue(long ageSeconds, int limit);

    /** Only from DUE: a late answer can't move a refund back. */
    @Modifying
    @Query(value = "UPDATE refunds SET status = :status, updated_at = now() WHERE id = :id AND status = 'DUE'", nativeQuery = true)
    int markAccepted(UUID id, String status);
}
