// Body of POST /staff/imports/{id}/confirm: what to do with rows whose SKU is already in the shop.
package com.iloveshopping.catalog.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public record ImportConfirmRequest(@NotNull @Pattern(regexp = "update|skip", message = "must be update or skip") String onMatch) {
}
