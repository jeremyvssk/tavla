// What one delivery option costs on one order, and what the order keeps of it: its id, name and days.
package com.iloveshopping.delivery;

import java.math.BigDecimal;

/** Handed to the order domain at checkout; the order copies the name and days, so later edits don't rewrite it. */
public record DeliveryQuote(String optionId, String name, BigDecimal cost, int daysMin, int daysMax) {
}
