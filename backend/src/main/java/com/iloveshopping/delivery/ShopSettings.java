// JPA entity mapping "shop_settings": its one row holds the free-shipping limit every page quotes.
package com.iloveshopping.delivery;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "shop_settings")
@Getter
@Setter
public class ShopSettings {

    /** Always 1: the table holds one row (CHECK id = 1), written by the migration. */
    static final short ID = 1;

    @Id
    private Short id;

    @Column(name = "free_shipping_from", nullable = false, precision = 10, scale = 2)
    private BigDecimal freeShippingFrom;

    @Column(name = "free_shipping_on", nullable = false)
    private boolean freeShippingOn;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    @PreUpdate
    void touch() {
        updatedAt = Instant.now();
    }
}
