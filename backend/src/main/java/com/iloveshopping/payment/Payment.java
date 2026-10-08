// JPA entity mapping the "payments" table: the Stripe PaymentIntent behind an order, and how it went.
package com.iloveshopping.payment;

import com.iloveshopping.crypto.Encrypted;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Read-only from Java: rows are written by the statements in {@link PaymentRepository}, each of which
 * changes one thing. There is no card number here and never will be; the brand and last 4 digits are
 * what Stripe hands back for a receipt. The Stripe id, card and decline code are stored encrypted; the
 * native statements bypass the converter, so PaymentService encrypts what it passes them.
 */
@Entity
@Table(name = "payments")
@Getter
public class Payment {

    @Id
    private UUID id;

    @Column(name = "order_id", nullable = false)
    private UUID orderId;

    @Column(nullable = false)
    private String provider;

    @Convert(converter = Encrypted.class)
    @Column(name = "provider_payment_id", nullable = false)
    private String providerPaymentId;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false)
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PaymentStatus status;

    @Convert(converter = Encrypted.class)
    @Column(name = "card_brand")
    private String cardBrand;

    @Convert(converter = Encrypted.class)
    @Column(name = "card_last4")
    private String cardLast4;

    @Convert(converter = Encrypted.class)
    @Column(name = "failure_code")
    private String failureCode;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
