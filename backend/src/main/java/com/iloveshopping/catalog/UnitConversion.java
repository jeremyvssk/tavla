// Metric to imperial conversion for product weight and dimensions.
package com.iloveshopping.catalog;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Only metric is accepted from clients; imperial is computed here on every write. Storing both
 * as independent inputs would let a product weigh 2 kg and 9 lbs at the same time.
 */
final class UnitConversion {

    private static final BigDecimal LBS_PER_KG = new BigDecimal("2.20462262");
    private static final BigDecimal CM_PER_INCH = new BigDecimal("2.54");

    private UnitConversion() {
    }

    static BigDecimal kgToLbs(BigDecimal kg) {
        return kg == null ? null : kg.multiply(LBS_PER_KG).setScale(2, RoundingMode.HALF_UP);
    }

    static BigDecimal cmToInches(BigDecimal cm) {
        return cm == null ? null : cm.divide(CM_PER_INCH, 2, RoundingMode.HALF_UP);
    }
}
