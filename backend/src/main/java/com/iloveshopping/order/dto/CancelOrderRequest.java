// Body of POST /orders/{id}/cancel: why, if the shopper said. Optional, and only ever shown back to them.
package com.iloveshopping.order.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** The reason goes into the order's history note, so it is short and has no control characters. */
public record CancelOrderRequest(
        @Size(max = 100, message = "Keep the reason under 100 characters.")
        @Pattern(regexp = "^[^\\p{Cntrl}]*$", message = "Keep the reason to one line of text.")
        String reason) {
}
