// Body of PUT /staff/delivery/order: every option's id, in the order shoppers should see them.
package com.iloveshopping.delivery.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public record DeliveryOrderRequest(@NotEmpty @Size(max = 50) List<@NotNull @Size(max = 40) String> ids) {
}
