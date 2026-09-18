// A product's weight and dimensions in both unit systems, as the brief requires.
package com.iloveshopping.catalog.dto;

import java.math.BigDecimal;

public record Measurements(
        BigDecimal weightKg, BigDecimal widthCm, BigDecimal heightCm, BigDecimal depthCm,
        BigDecimal weightLbs, BigDecimal widthIn, BigDecimal heightIn, BigDecimal depthIn) {
}
