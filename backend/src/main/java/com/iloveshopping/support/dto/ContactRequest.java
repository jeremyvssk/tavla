// Body of POST /support/messages: the contact form, with the same limits as the page's own rules.
package com.iloveshopping.support.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * The name and email go into an email's headers (the reply-to and the subject), so a line break in either
 * is refused here: it would let the sender add headers of their own. frontend/src/validation/contactRules.ts
 * is the page's copy of these rules.
 */
public record ContactRequest(
        @NotBlank @Size(max = 100) @Pattern(regexp = "[^\\r\\n]*", message = "must be one line") String name,
        @NotBlank @Email @Size(max = 255) @Pattern(regexp = "[^\\r\\n]*", message = "must be one line") String email,
        @NotBlank @Pattern(regexp = "order|return|product|payment|other", message = "must be order, return, product, payment or other") String topic,
        @Pattern(regexp = "^$|TV-\\d{5}", message = "Order numbers are TV- and 5 digits, like TV-24851.") String orderNumber,
        @NotBlank @Size(min = 20, max = 2000) String message,
        @Size(max = 4000) String captchaToken) {
}
