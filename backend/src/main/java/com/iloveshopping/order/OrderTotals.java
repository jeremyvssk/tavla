// An order's money: subtotal, shipping, total, and the VAT that is already inside the total.
package com.iloveshopping.order;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Collection;

/**
 * BigDecimal throughout, never double: 0.1 + 0.2 is not 0.3 in floating point. EU shop prices
 * include VAT, so the VAT is the part of the total that is tax (total x 24 / 124), not an addition.
 */
public record OrderTotals(BigDecimal subtotal, BigDecimal shipping, BigDecimal total, BigDecimal vat) {

    /** Estonia's standard rate since July 2025. */
    static final BigDecimal VAT_RATE = new BigDecimal("0.24");

    /** {@code shipping} is the delivery option's cost on this subtotal (delivery/DeliveryService.quote). */
    public static OrderTotals of(BigDecimal subtotal, BigDecimal shipping) {
        BigDecimal total = subtotal.add(shipping);
        BigDecimal vat = total.multiply(VAT_RATE).divide(BigDecimal.ONE.add(VAT_RATE), 2, RoundingMode.HALF_UP);
        return new OrderTotals(subtotal, shipping, total, vat);
    }

    public static BigDecimal subtotal(Collection<BigDecimal> lineTotals) {
        return lineTotals.stream().reduce(BigDecimal.ZERO, BigDecimal::add).setScale(2, RoundingMode.UNNECESSARY);
    }

    public static BigDecimal lineTotal(BigDecimal unitPrice, int quantity) {
        return unitPrice.multiply(BigDecimal.valueOf(quantity));
    }

    /** What Stripe is asked for: whole cents, so €155.00 is 15500. Throws rather than round a fraction of a cent. */
    public static long cents(BigDecimal amount) {
        return amount.movePointRight(2).longValueExact();
    }
}
