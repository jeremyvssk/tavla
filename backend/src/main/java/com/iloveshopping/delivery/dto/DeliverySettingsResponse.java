// The free-shipping limit and the delivery options, in the order shoppers see them.
package com.iloveshopping.delivery.dto;

import java.math.BigDecimal;
import java.util.List;

/** For staff every option; for the checkout (GET /delivery-options) only the active ones. */
public record DeliverySettingsResponse(FreeShipping freeShipping, List<DeliveryOptionResponse> options) {

    public record FreeShipping(BigDecimal from, boolean on) {
    }
}
