// One delivery option as the delivery page and the checkout show it.
package com.iloveshopping.delivery.dto;

import java.math.BigDecimal;
import java.util.List;

/** orderBy is "15:00": ordered before it on a working day, the parcel leaves the same day. */
public record DeliveryOptionResponse(String id, String name, BigDecimal price, String orderBy, int daysMin, int daysMax,
                                     List<String> countries, boolean active, boolean freeOverLimit) {
}
