// The limit on how far Sales may change a price in one edit; an admin may change it by any amount.
package com.iloveshopping.catalog;

import com.iloveshopping.auth.CurrentUser;
import com.iloveshopping.catalog.exception.PriceChangeTooLargeException;
import com.iloveshopping.user.Role;

import java.math.BigDecimal;

/**
 * A typo is the risk this guards against: 1499 typed for 14.99 (or 1.49) on a product that sells, or a
 * bulk "lower by 90%" meant as 9. Sales may move a price by at most half of what it was, up or down, per
 * edit; anything further is an admin's change. A price of 0 can't be moved by Sales at all, since any
 * change from 0 is an unlimited share of it.
 */
public final class PriceGuard {

    static final BigDecimal MAX_SHARE = new BigDecimal("0.50");

    private PriceGuard() {
    }

    /** True when the current caller may change {@code before} to {@code after}. */
    public static boolean allows(BigDecimal before, BigDecimal after) {
        return allows(CurrentUser.role(), before, after);
    }

    static boolean allows(Role role, BigDecimal before, BigDecimal after) {
        if (role != Role.SALES || before.compareTo(after) == 0) {
            return true;
        }
        if (before.signum() == 0) {
            return false;
        }
        return after.subtract(before).abs().compareTo(before.multiply(MAX_SHARE)) <= 0;
    }

    public static void check(BigDecimal before, BigDecimal after) {
        if (!allows(before, after)) {
            throw new PriceChangeTooLargeException();
        }
    }
}
