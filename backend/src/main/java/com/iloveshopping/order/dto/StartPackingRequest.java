// Body of POST /staff/orders/start-packing: the ticked orders to move from Payment successful to Processing.
package com.iloveshopping.order.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

public record StartPackingRequest(@NotEmpty @Size(max = 100) List<@NotNull UUID> ids) {

    /** moved: now Processing; skipped: not in Payment successful any more (cancelled, or someone was quicker). */
    public record Result(int moved, int skipped) {
    }
}
