// Unit tests for the order's money: line totals, the free-shipping line, the VAT inside the total, and rounding.
package com.iloveshopping.order;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class OrderTotalsTest {

    private static BigDecimal eur(String value) {
        return new BigDecimal(value);
    }

    private static OrderTotals totals(ShippingMethod method, BigDecimal... lineTotals) {
        return OrderTotals.of(List.of(lineTotals), method);
    }

    @Test
    void theStep3Prediction_twoAt24AndOneAt65_isFreeShippingAnd21_87Vat() {
        OrderTotals t = totals(ShippingMethod.STANDARD,
                OrderTotals.lineTotal(eur("24.00"), 2), OrderTotals.lineTotal(eur("65.00"), 1));
        assertThat(t.subtotal()).isEqualByComparingTo("113.00");
        assertThat(t.shipping()).isEqualByComparingTo("0.00");
        assertThat(t.total()).isEqualByComparingTo("113.00");
        // 113 x 24 / 124 = 21.8709..., inside the total, not added to it
        assertThat(t.vat()).isEqualByComparingTo("21.87");
    }

    @Test
    void standardIsFreeFromExactly100_andCharged4_90BelowIt() {
        assertThat(totals(ShippingMethod.STANDARD, eur("100.00")).shipping()).isEqualByComparingTo("0.00");
        OrderTotals under = totals(ShippingMethod.STANDARD, eur("99.99"));
        assertThat(under.shipping()).isEqualByComparingTo("4.90");
        assertThat(under.total()).isEqualByComparingTo("104.89");
        assertThat(under.vat()).isEqualByComparingTo("20.30");
    }

    @Test
    void expressAndTheLocker_costTheirPrice_evenOver100() {
        assertThat(totals(ShippingMethod.EXPRESS, eur("150.00")).total()).isEqualByComparingTo("162.90");
        assertThat(totals(ShippingMethod.LOCKER, eur("150.00")).total()).isEqualByComparingTo("153.90");
    }

    @Test
    void vatRoundsToTheNearestCent_includingUp() {
        // 10.00 x 24 / 124 = 1.9354... -> 1.94
        assertThat(totals(ShippingMethod.STANDARD, eur("5.10")).vat()).isEqualByComparingTo("1.94");
        // 104.89 x 24 / 124 = 20.3012... -> 20.30
        assertThat(totals(ShippingMethod.STANDARD, eur("99.99")).vat()).isEqualByComparingTo("20.30");
    }

    @Test
    void sumsExactly_whereDoublesWouldDrift() {
        // ten lines of 0.10 are 1.00 exactly; in double arithmetic they are 0.9999999999999999
        BigDecimal[] dimes = new BigDecimal[10];
        java.util.Arrays.fill(dimes, eur("0.10"));
        assertThat(totals(ShippingMethod.LOCKER, dimes).subtotal()).isEqualByComparingTo("1.00");
    }

    @Test
    void everyAmountHasTwoDecimals_soTheDatabaseAndStripeAgree() {
        OrderTotals t = totals(ShippingMethod.STANDARD, eur("120.00"));
        assertThat(List.of(t.subtotal(), t.shipping(), t.total(), t.vat())).allMatch(v -> v.scale() == 2);
    }
}
