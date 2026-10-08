// Spring Data repository for the ids of Stripe events already acted on.
package com.iloveshopping.payment;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface ProcessedPaymentEventRepository extends JpaRepository<ProcessedPaymentEvent, String> {

    /**
     * 1 the first time an event id is seen, 0 after that. Two deliveries racing each other both reach
     * the primary key; the second waits for the first transaction and then gets 0.
     */
    @Modifying
    @Query(value = "INSERT INTO processed_payment_events (event_id, type) VALUES (:eventId, :type) ON CONFLICT DO NOTHING",
            nativeQuery = true)
    int insertIfNew(String eventId, String type);
}
