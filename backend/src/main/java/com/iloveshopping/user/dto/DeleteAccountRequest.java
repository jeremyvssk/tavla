// Body of DELETE /users/me: the password again, so a session left open on a shared computer can't delete the account.
package com.iloveshopping.user.dto;

import jakarta.validation.constraints.Size;

/** password: required for a password account. A Google account has none; it confirms with its own email instead. */
public record DeleteAccountRequest(@Size(max = 128) String password, @Size(max = 255) String email) {
}
