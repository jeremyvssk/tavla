// One account as the admin's users page lists it.
package com.iloveshopping.user.dto;

import com.iloveshopping.user.Role;

import java.time.Instant;
import java.util.UUID;

public record StaffUserResponse(
        UUID id,
        String fullName,
        String email,
        Role role,
        boolean twoFactorEnabled,
        Instant createdAt,
        Instant lastSignInAt,
        long orderCount) {
}
