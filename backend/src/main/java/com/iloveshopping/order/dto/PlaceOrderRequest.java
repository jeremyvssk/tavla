// Body of POST /orders: contact, delivery address and shipping method. Never a price, a total or a product list.
package com.iloveshopping.order.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * The messages are the checkout page's own (frontend/src/validation/checkoutRules.ts), so a field the
 * browser let through still gets a sentence that says how to fix it. What is bought comes from the
 * server's cart; the money from the server's prices. Patterns accept "" so a blank field gets only the
 * NotBlank message. CheckoutRules adds the checks that need two fields (the postcode for the country).
 */
public record PlaceOrderRequest(
        @NotBlank(message = "Enter your email. The receipt goes there.")
        @Email(message = "Enter an email like name@example.com.")
        @Size(max = 255, message = "Email must be at most 255 characters.")
        String email,

        @NotBlank(message = "Enter a phone number. The courier calls it if they can't find you.")
        @Pattern(regexp = "^$|^\\+\\d[\\d\\s]{6,17}\\d$", message = "Enter the full number with the country code, like +372 5123 4567.")
        String phone,

        @NotBlank(message = "Enter your first name.") @Size(max = 100, message = "Keep it under 100 characters.")
        String firstName,

        @NotBlank(message = "Enter your last name.") @Size(max = 100, message = "Keep it under 100 characters.")
        String lastName,

        @NotBlank(message = "Enter the street and house number.") @Size(max = 100, message = "Keep it under 100 characters.")
        @Pattern(regexp = "^$|(?s).*\\d.*", message = "Add the house number, like Narva mnt 7.")
        String street,

        @Size(max = 100, message = "Keep it under 100 characters.")
        String flat,

        @NotBlank(message = "Enter the postcode.") @Size(max = 10, message = "Enter the postcode.")
        String postcode,

        @NotBlank(message = "Enter the city.") @Size(max = 100, message = "Keep it under 100 characters.")
        String city,

        @NotBlank(message = "Choose a country we deliver to.")
        @Pattern(regexp = "^$|EE|LV|LT|FI", message = "Choose a country we deliver to.")
        String country,

        @NotBlank(message = "Choose a shipping option.")
        @Pattern(regexp = "^$|standard|express|locker", message = "Choose a shipping option.")
        String shippingMethod) {
}
