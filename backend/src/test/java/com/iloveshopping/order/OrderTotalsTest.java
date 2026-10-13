// Unit tests for the order's money: line totals, the VAT inside the total, and rounding.
package com.iloveshopping.order;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** What delivery costs is the delivery option's rule (delivery/DeliveryOptionTest); here it is a given amount. */
class OrderTotalsTest {

    private static BigDecimal eur(String value) {
        return new BigDecimal(value);
    }

    private static OrderTotals totals(String shipping, BigDecimal... lineTotals) {
        return OrderTotals.of(OrderTotals.subtotal(List.of(lineTotals)), eur(shipping));
    }

    @Test
    void theStep3Prediction_twoAt24AndOneAt65_withFreeShipping_has21_87Vat() {
        OrderTotals t = totals("0.00", OrderTotals.lineTotal(eur("24.00"), 2), OrderTotals.lineTotal(eur("65.00"), 1));
        assertThat(t.subtotal()).isEqualByComparingTo("113.00");
        assertThat(t.total()).isEqualByComparingTo("113.00");
        // 113 x 24 / 124 = 21.8709..., inside the total, not added to it
        assertThat(t.vat()).isEqualByComparingTo("21.87");
    }

    @Test
    void shippingIsAddedToTheTotal_andTheVatIsTakenFromBoth() {
        OrderTotals t = totals("4.90", eur("99.99"));
        assertThat(t.total()).isEqualByComparingTo("104.89");
        assertThat(t.vat()).isEqualByComparingTo("20.30");
    }

    @Test
    void vatRoundsToTheNearestCent_includingUp() {
        // 10.00 x 24 / 124 = 1.9354... -> 1.94
        assertThat(totals("4.90", eur("5.10")).vat()).isEqualByComparingTo("1.94");
    }

    @Test
    void sumsExactly_whereDoublesWouldDrift() {
        // ten lines of 0.10 are 1.00 exactly; in double arithmetic they are 0.9999999999999999
        BigDecimal[] dimes = new BigDecimal[10];
        java.util.Arrays.fill(dimes, eur("0.10"));
        assertThat(totals("3.90", dimes).subtotal()).isEqualByComparingTo("1.00");
    }

    @Test
    void everyAmountHasTwoDecimals_soTheDatabaseAndStripeAgree() {
        OrderTotals t = totals("0.00", eur("120.00"));
        assertThat(List.of(t.subtotal(), t.shipping(), t.total(), t.vat())).allMatch(v -> v.scale() == 2);
    }
}
