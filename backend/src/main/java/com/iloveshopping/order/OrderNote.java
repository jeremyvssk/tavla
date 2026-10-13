// JPA entity mapping "order_notes": a line the team leaves on an order, which the customer never sees.
package com.iloveshopping.order;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "order_notes")
@Getter
public class OrderNote {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "order_id", nullable = false)
    private UUID orderId;

    @Column(name = "author_id")
    private UUID authorId;

    @Column(nullable = false)
    private String note;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected OrderNote() {
    }

    OrderNote(UUID orderId, UUID authorId, String note, Instant createdAt) {
        this.orderId = orderId;
        this.authorId = authorId;
        this.note = note;
        this.createdAt = createdAt;
    }
}
