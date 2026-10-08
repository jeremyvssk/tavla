// Spring Data repository for orders: the locked read every status change starts with, numbers, expired holds and the account's list.
package com.iloveshopping.order;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OrderRepository extends JpaRepository<Order, UUID> {

    /**
     * SELECT ... FOR UPDATE. A status change reads the status, checks the move and writes it with side
     * effects (stock, history); the row lock makes a webhook and the expiry job touching the same order
     * take turns instead of both acting on the status they read.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT o FROM CustomerOrder o WHERE o.id = :id")
    Optional<Order> findForUpdate(UUID id);

    @Query(value = "SELECT nextval('order_number_seq')", nativeQuery = true)
    long nextNumber();

    @Query(value = """
            SELECT id FROM orders
            WHERE status IN ('PENDING_PAYMENT', 'PAYMENT_FAILED') AND hold_until <= :now
            ORDER BY hold_until LIMIT :limit""", nativeQuery = true)
    List<UUID> findExpiredHolds(Instant now, int limit);

    /**
     * One page of an account's orders. The sort is a bound parameter compared against fixed names, so
     * the request never writes SQL. "status" puts what needs the shopper first (a failed payment), then
     * the order's way from paid to delivered, then cancelled. Ties go newest first.
     */
    @Query(value = """
            SELECT id FROM orders
            WHERE user_id = :userId AND status IN (:statuses) AND placed_at >= :from AND placed_at < :to
            ORDER BY
              CASE WHEN :sort = 'status' THEN
                CASE status WHEN 'PAYMENT_FAILED' THEN 0 WHEN 'PENDING_PAYMENT' THEN 1 WHEN 'PAYMENT_SUCCESSFUL' THEN 2
                            WHEN 'PROCESSING' THEN 3 WHEN 'SHIPPED' THEN 4 WHEN 'DELIVERED' THEN 5
                            WHEN 'CANCELLED' THEN 6 ELSE 7 END
              END,
              CASE WHEN :sort = 'total_desc' THEN total END DESC,
              CASE WHEN :sort = 'oldest' THEN placed_at END ASC,
              placed_at DESC, id
            LIMIT :limit OFFSET :offset""", nativeQuery = true)
    List<UUID> findPageForUser(UUID userId, Collection<String> statuses, Instant from, Instant to, String sort,
                               int limit, long offset);

    @Query(value = """
            SELECT count(*) FROM orders
            WHERE user_id = :userId AND status IN (:statuses) AND placed_at >= :from AND placed_at < :to""", nativeQuery = true)
    long countForUser(UUID userId, Collection<String> statuses, Instant from, Instant to);

    /** Rows of (status, count) in the date range, for the filter pills' numbers. */
    @Query(value = """
            SELECT status, count(*) FROM orders
            WHERE user_id = :userId AND placed_at >= :from AND placed_at < :to
            GROUP BY status""", nativeQuery = true)
    List<Object[]> countByStatus(UUID userId, Instant from, Instant to);

    /** The account's latest order, whose contact and address prefill the next checkout. */
    Optional<Order> findFirstByUserIdOrderByPlacedAtDesc(UUID userId);
}
