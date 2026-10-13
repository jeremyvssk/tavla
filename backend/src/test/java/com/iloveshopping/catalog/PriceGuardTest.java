// How far each role may move a price in one edit, and the arithmetic of a bulk price change.
package com.iloveshopping.catalog;

import com.iloveshopping.user.Role;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class PriceGuardTest {

    @Test
    void salesMayMoveAPriceByHalfEitherWay_andNoFurther() {
        assertThat(PriceGuard.allows(Role.SALES, money("100.00"), money("150.00"))).isTrue();
        assertThat(PriceGuard.allows(Role.SALES, money("100.00"), money("50.00"))).isTrue();
        assertThat(PriceGuard.allows(Role.SALES, money("100.00"), money("150.01"))).isFalse();
        assertThat(PriceGuard.allows(Role.SALES, money("100.00"), money("49.99"))).isFalse();
        // the typo it is for: 1499 typed for 14.99
        assertThat(PriceGuard.allows(Role.SALES, money("14.99"), money("1499.00"))).isFalse();
    }

    @Test
    void anAdminMayChangeAnyPrice_andAnUnchangedPriceIsAlwaysFine() {
        assertThat(PriceGuard.allows(Role.ADMIN, money("100.00"), money("1.00"))).isTrue();
        assertThat(PriceGuard.allows(Role.SALES, money("0.00"), money("0.00"))).isTrue();
        assertThat(PriceGuard.allows(Role.SALES, money("0.00"), money("5.00"))).isFalse();
    }

    @Test
    void aBulkChangeRoundsHalfUpToTheCent() {
        assertThat(StaffProductService.newPrice(money("9.99"), "raise", new BigDecimal("10"))).isEqualByComparingTo("10.99");
        assertThat(StaffProductService.newPrice(money("10.05"), "lower", new BigDecimal("50"))).isEqualByComparingTo("5.03");
        assertThat(StaffProductService.newPrice(money("10.00"), "set", new BigDecimal("12.5"))).isEqualByComparingTo("12.50");
    }

    private static BigDecimal money(String amount) {
        return new BigDecimal(amount);
    }
}
