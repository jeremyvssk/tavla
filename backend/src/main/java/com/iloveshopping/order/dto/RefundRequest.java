// Body of POST /staff/orders/{id}/refunds: which lines (and how many of each), the shipping or not, and why.
package com.iloveshopping.order.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

/** No amount: the server works it out from the order's own prices, so nobody can refund more than was paid. */
public record RefundRequest(
        @NotNull @Size(max = 50) List<@Valid @NotNull Line> lines,
        @NotNull Boolean shipping,
        @NotBlank @Size(max = 200) String reason) {

    public record Line(@NotNull UUID productId, @NotNull @Min(1) @Max(10) Integer quantity) {
    }
}
