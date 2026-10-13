// JPA entity mapping "delivery_options": one way a parcel can reach the shopper, its price and its days.
package com.iloveshopping.delivery;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;

/** The id is a short lowercase name ("standard"), and what an order stores as its shipping method. */
@Entity
@Table(name = "delivery_options")
@Getter
@Setter
public class DeliveryOption {

    @Id
    private String id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal price;

    /** "15:00", the shop's local time; text because a zone-less wall-clock time must not be shifted to UTC. */
    @Column(name = "order_by", nullable = false)
    private String orderBy;

    @Column(name = "days_min", nullable = false)
    private short daysMin;

    @Column(name = "days_max", nullable = false)
    private short daysMax;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(nullable = false, columnDefinition = "varchar(2)[]")
    private String[] countries;

    @Column(nullable = false)
    private boolean active;

    @Column(name = "free_over_limit", nullable = false)
    private boolean freeOverLimit;

    @Column(nullable = false)
    private int position;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    @PreUpdate
    void touch() {
        updatedAt = Instant.now();
    }

    /** What this option costs on an order of {@code subtotal}: free past the limit when the option takes part in it. */
    BigDecimal costFor(BigDecimal subtotal, ShopSettings settings) {
        boolean free = freeOverLimit && settings.isFreeShippingOn() && subtotal.compareTo(settings.getFreeShippingFrom()) >= 0;
        return free ? BigDecimal.ZERO.setScale(2) : price;
    }

    boolean serves(String country) {
        for (String c : countries) {
            if (c.equals(country)) {
                return true;
            }
        }
        return false;
    }
}
