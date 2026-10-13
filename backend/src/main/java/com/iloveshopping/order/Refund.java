// JPA entity mapping "refunds" and its lines: part of a paid order given back, as staff asked for it.
package com.iloveshopping.order;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Embeddable;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Written once by StaffOrderService and never changed but for its status: DUE until Stripe accepts it,
 * then PENDING or SUCCEEDED. The amount is worked out by the server from the order's own prices.
 */
@Entity
@Table(name = "refunds")
@Getter
public class Refund {

    @Id
    private UUID id;

    @Column(name = "order_id", nullable = false)
    private UUID orderId;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false)
    private boolean shipping;

    @Column(nullable = false)
    private String reason;

    @Column(nullable = false)
    private String status;

    @Column(name = "requested_by")
    private UUID requestedBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "refund_lines", joinColumns = @JoinColumn(name = "refund_id"))
    private List<Line> lines = new ArrayList<>();

    protected Refund() {
    }

    Refund(UUID orderId, BigDecimal amount, boolean shipping, String reason, UUID requestedBy, List<Line> lines, Instant now) {
        this.id = UUID.randomUUID();
        this.orderId = orderId;
        this.amount = amount;
        this.shipping = shipping;
        this.reason = reason;
        this.status = "DUE";
        this.requestedBy = requestedBy;
        this.lines = new ArrayList<>(lines);
        this.createdAt = now;
        this.updatedAt = now;
    }

    /** One order line given back: which (its position on the order), how many, and for how much. */
    @Embeddable
    @Getter
    public static class Line {

        @Column(nullable = false)
        private short position;

        @Column(nullable = false)
        private int quantity;

        @Column(nullable = false, precision = 10, scale = 2)
        private BigDecimal amount;

        protected Line() {
        }

        Line(int position, int quantity, BigDecimal amount) {
            this.position = (short) position;
            this.quantity = quantity;
            this.amount = amount;
        }
    }
}
