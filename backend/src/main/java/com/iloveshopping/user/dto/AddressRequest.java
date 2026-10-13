// Body of POST and PUT /users/me/addresses: an address to save, with the same rules as the checkout's.
package com.iloveshopping.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Defaults are set through PUT /users/me/addresses/{id}/default, never here. AddressService adds the postcode-for-country check. */
public record AddressRequest(
        @NotBlank @Size(max = 40) String label,
        @NotBlank @Size(max = 100) String firstName,
        @NotBlank @Size(max = 100) String lastName,
        @NotBlank @Size(max = 100) @Pattern(regexp = "(?s).*\\d.*", message = "Add the house number, like Narva mnt 7.") String street,
        @Size(max = 100) String flat,
        @NotBlank @Size(max = 10) String postcode,
        @NotBlank @Size(max = 100) String city,
        @NotBlank @Pattern(regexp = "EE|LV|LT|FI", message = "Choose a country we deliver to.") String country,
        @NotBlank @Pattern(regexp = "\\+\\d[\\d\\s]{6,17}\\d", message = "Enter the full number with the country code, like +372 5123 4567.")
        String phone) {
}
