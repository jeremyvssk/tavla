// Body of PATCH /users/me: the name and phone. The sign-in email can't be changed here.
package com.iloveshopping.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** phone: null or blank removes it. */
public record ProfileUpdateRequest(
        @NotBlank @Size(max = 100) String fullName,
        @Pattern(regexp = "^$|\\+\\d[\\d\\s]{6,17}\\d", message = "Enter the full number with the country code, like +372 5123 4567.")
        String phone) {
}
