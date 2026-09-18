// One price band in the price facet: min inclusive, max exclusive, null max meaning no upper bound.
package com.iloveshopping.catalog.dto;

import java.math.BigDecimal;

public record PriceBucket(BigDecimal min, BigDecimal max, long count) {
}
