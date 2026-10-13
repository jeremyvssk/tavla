// Body of POST /staff/orders/{id}/status: the next status, with the courier and parcel number for Shipped and a reason for Cancelled.
package com.iloveshopping.order.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Which of the optional fields a move needs is checked by StaffOrderService. */
public record StatusMoveRequest(
        @NotNull @Pattern(regexp = "PROCESSING|SHIPPED|DELIVERED|CANCELLED", message = "must be PROCESSING, SHIPPED, DELIVERED or CANCELLED")
        String to,
        @Pattern(regexp = "Omniva|DPD", message = "must be Omniva or DPD") String carrier,
        @Size(max = 40) @Pattern(regexp = "[A-Za-z0-9-]*", message = "letters, digits and - only") String trackingNumber,
        @Size(max = 200) String note) {
}
