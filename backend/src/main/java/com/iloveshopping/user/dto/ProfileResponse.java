// The shopper's own profile on the account page.
package com.iloveshopping.user.dto;

import java.time.Instant;

public record ProfileResponse(String fullName, String email, String phone, boolean emailVerified, Instant createdAt, Instant updatedAt) {
}
