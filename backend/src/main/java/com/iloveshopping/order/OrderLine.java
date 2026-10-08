// JPA entity mapping the "order_items" table: one line of an order, copied from the cart when it was placed.
package com.iloveshopping.order;

import com.iloveshopping.catalog.dto.ImageFraming;
import com.iloveshopping.crypto.Encrypted;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * The name, options, photo and price are a snapshot: a later price change or rename in the catalog
 * doesn't alter what this order says was bought. The product is a bare id, like in the cart, so
 * the order and catalog domains don't share entities. What was bought (name, options, photo) is stored
 * encrypted; the money stays plain.
 */
@Entity
@Table(name = "order_items")
@Getter
public class OrderLine {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    /** Null once the product is deleted from the catalog. */
    @Column(name = "product_id")
    private UUID productId;

    @Column(nullable = false)
    private short position;

    @Convert(converter = Encrypted.class)
    @Column(name = "product_name", nullable = false)
    private String productName;

    @Convert(converter = Encrypted.class)
    @Column
    private String options;

    @Convert(converter = Encrypted.class)
    @Column(name = "image_url")
    private String imageUrl;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "image_framing", columnDefinition = "jsonb")
    private ImageFraming imageFraming;

    @Column(name = "unit_price", nullable = false, precision = 10, scale = 2)
    private BigDecimal unitPrice;

    @Column(nullable = false)
    private int quantity;

    @Column(name = "line_total", nullable = false, precision = 10, scale = 2)
    private BigDecimal lineTotal;

    protected OrderLine() {
    }

    OrderLine(Order order, int position, UUID productId, String productName, String options, String imageUrl,
              ImageFraming imageFraming, BigDecimal unitPrice, int quantity) {
        this.order = order;
        this.position = (short) position;
        this.productId = productId;
        this.productName = productName;
        this.options = options;
        this.imageUrl = imageUrl;
        this.imageFraming = imageFraming;
        this.unitPrice = unitPrice;
        this.quantity = quantity;
        this.lineTotal = OrderTotals.lineTotal(unitPrice, quantity);
    }
}
