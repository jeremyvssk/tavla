// JPA entity mapping the "processed_payment_events" table: a Stripe event id that has been acted on.
package com.iloveshopping.payment;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;

import java.time.Instant;

/** Written only by {@link ProcessedPaymentEventRepository#insertIfNew}. */
@Entity
@Table(name = "processed_payment_events")
@Getter
public class ProcessedPaymentEvent {

    @Id
    @Column(name = "event_id")
    private String eventId;

    @Column(nullable = false)
    private String type;

    @Column(name = "processed_at", nullable = false)
    private Instant processedAt;
}
