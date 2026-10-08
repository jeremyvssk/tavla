// Spring Data repository for payments: one statement per change, keyed by the order (one payment per order).
package com.iloveshopping.payment;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Native SQL bypasses the Encrypted converter: PaymentService passes these statements values it has
 * already encrypted (the PaymentIntent id, the card, the decline code).
 */
public interface PaymentRepository extends JpaRepository<Payment, UUID> {

    Optional<Payment> findByOrderId(UUID orderId);

    /** Two requests that both started the order's payment got the same PaymentIntent back; one row is kept. */
    @Modifying
    @Query(value = """
            INSERT INTO payments (id, order_id, provider, provider_payment_id, amount, currency, status)
            VALUES (:id, :orderId, 'stripe', :intentId, :amount, :currency, 'STARTED')
            ON CONFLICT (order_id) DO NOTHING""", nativeQuery = true)
    void insertStarted(UUID id, UUID orderId, String intentId, BigDecimal amount, String currency);

    @Modifying
    @Query(value = """
            UPDATE payments SET status = 'SUCCEEDED', card_brand = :brand, card_last4 = :last4, failure_code = NULL,
                   updated_at = now()
            WHERE order_id = :orderId""", nativeQuery = true)
    int markSucceeded(UUID orderId, String brand, String last4);

    /** A decline never overwrites a success: once the money moved, a late failure event is about an earlier attempt. */
    @Modifying
    @Query(value = """
            UPDATE payments SET status = 'FAILED', failure_code = :failureCode, updated_at = now()
            WHERE order_id = :orderId AND status <> 'SUCCEEDED'""", nativeQuery = true)
    int markFailed(UUID orderId, String failureCode);

    @Modifying
    @Query(value = """
            UPDATE payments SET status = 'STARTED', failure_code = NULL, updated_at = now()
            WHERE order_id = :orderId AND status = 'FAILED'""", nativeQuery = true)
    int markRestarted(UUID orderId);

    @Modifying
    @Query(value = """
            UPDATE payments SET status = 'CANCELED', updated_at = now()
            WHERE order_id = :orderId AND status IN ('STARTED', 'FAILED')""", nativeQuery = true)
    int markCanceled(UUID orderId);

    /** A paid order was cancelled: its money is owed back until Stripe accepts the refund. */
    @Modifying
    @Query(value = """
            UPDATE payments SET status = 'REFUND_DUE', updated_at = now()
            WHERE order_id = :orderId AND status = 'SUCCEEDED'""", nativeQuery = true)
    int markRefundDue(UUID orderId);

    @Modifying
    @Query(value = """
            UPDATE payments SET status = 'REFUNDING', updated_at = now()
            WHERE order_id = :orderId AND status = 'REFUND_DUE'""", nativeQuery = true)
    int markRefunding(UUID orderId);

    @Modifying
    @Query(value = """
            UPDATE payments SET status = 'REFUNDED', updated_at = now()
            WHERE order_id = :orderId AND status IN ('REFUND_DUE', 'REFUNDING')""", nativeQuery = true)
    int markRefunded(UUID orderId);

    /**
     * Refunds Stripe hasn't accepted yet, older than {@code ageSeconds}. The age is worked out in SQL,
     * against the same clock that wrote updated_at, so the database's time zone can't skew it.
     */
    @Query(value = """
            SELECT order_id FROM payments
            WHERE status = 'REFUND_DUE' AND updated_at <= now() - make_interval(secs => :ageSeconds)
            ORDER BY updated_at LIMIT :limit""", nativeQuery = true)
    List<UUID> findRefundsDue(long ageSeconds, int limit);
}
