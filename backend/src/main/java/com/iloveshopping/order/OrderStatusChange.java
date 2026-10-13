// JPA entity mapping the "order_status_history" table: one status move, who made it and why.
package com.iloveshopping.order;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;

import java.time.Instant;
import java.util.UUID;

/** Written only by {@link Order#place} and {@link Order#moveTo}; never changed afterwards. */
@Entity
@Table(name = "order_status_history")
@Getter
public class OrderStatusChange {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    @Enumerated(EnumType.STRING)
    @Column(name = "from_status")
    private OrderStatus fromStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "to_status", nullable = false)
    private OrderStatus toStatus;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OrderActor actor;

    /** The staff member who made the move; null for the shopper, Stripe and the timer. */
    @Column(name = "actor_user_id")
    private UUID actorUserId;

    @Column
    private String note;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected OrderStatusChange() {
    }

    OrderStatusChange(Order order, OrderStatus fromStatus, OrderStatus toStatus, OrderActor actor, UUID actorUserId, String note,
                      Instant createdAt) {
        this.order = order;
        this.fromStatus = fromStatus;
        this.toStatus = toStatus;
        this.actor = actor;
        this.actorUserId = actorUserId;
        this.note = note;
        this.createdAt = createdAt;
    }
}
