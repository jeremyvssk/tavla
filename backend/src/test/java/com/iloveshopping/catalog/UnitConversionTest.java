// Unit tests for metric to imperial conversion.
package com.iloveshopping.catalog;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class UnitConversionTest {

    @Test
    void kilogramsToPounds_roundsToTwoDecimals() {
        assertThat(UnitConversion.kgToLbs(new BigDecimal("2.0"))).isEqualByComparingTo("4.41");
        assertThat(UnitConversion.kgToLbs(new BigDecimal("5.8"))).isEqualByComparingTo("12.79");
    }

    @Test
    void centimetresToInches_roundsToTwoDecimals() {
        assertThat(UnitConversion.cmToInches(new BigDecimal("2.54"))).isEqualByComparingTo("1.00");
        assertThat(UnitConversion.cmToInches(new BigDecimal("50"))).isEqualByComparingTo("19.69");
    }

    @Test
    void missingMeasurements_stayMissing() {
        assertThat(UnitConversion.kgToLbs(null)).isNull();
        assertThat(UnitConversion.cmToInches(null)).isNull();
    }
}
