// Query parameters of GET /staff/users: who to show, an email address to find, and the page.
package com.iloveshopping.user.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** {@code q} is a whole email address, matched exactly: emails are encrypted, so there is no prefix search. */
public record UserListParams(
        @Size(max = 255) String q,
        @Pattern(regexp = "all|customers|staff", message = "must be all, customers or staff") String group,
        @Pattern(regexp = "off", message = "must be off") String twoFactor,
        @Min(0) @Max(10_000) Integer page,
        @Min(1) @Max(100) Integer size) {
}
