// The three ways an order can be delivered, and what each costs.
package com.iloveshopping.order;

import java.math.BigDecimal;
import java.util.Locale;

/** frontend/src/lib/shipping.ts shows the same prices; this is the copy that gets charged. */
public enum ShippingMethod {
    STANDARD(new BigDecimal("4.90")),
    EXPRESS(new BigDecimal("12.90")),
    LOCKER(new BigDecimal("3.90"));

    /** Standard is free from here up; the faster and the locker options always cost their price. */
    static final BigDecimal FREE_STANDARD_FROM = new BigDecimal("100.00");

    private final BigDecimal price;

    ShippingMethod(BigDecimal price) {
        this.price = price;
    }

    public BigDecimal costFor(BigDecimal subtotal) {
        return this == STANDARD && subtotal.compareTo(FREE_STANDARD_FROM) >= 0 ? BigDecimal.ZERO.setScale(2) : price;
    }

    /** The lowercase name the API uses ("standard"). */
    public String wire() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static ShippingMethod fromWire(String value) {
        return valueOf(value.toUpperCase(Locale.ROOT));
    }
}
