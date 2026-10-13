// Body of PUT /users/me/addresses/{id}/default: which default this address becomes.
package com.iloveshopping.user.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public record AddressDefaultRequest(@NotNull @Pattern(regexp = "delivery|billing", message = "must be delivery or billing") String kind) {
}
