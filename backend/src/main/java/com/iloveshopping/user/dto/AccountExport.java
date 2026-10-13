// The account's data export: everything the shop keeps about the shopper, as one JSON file (GDPR access and portability).
package com.iloveshopping.user.dto;

import com.iloveshopping.catalog.dto.ReviewResponse;
import com.iloveshopping.order.dto.OrderDetailResponse;

import java.time.Instant;
import java.util.List;

/** No password hash, 2FA secret or backup codes: those are the shop's means of checking, not data about the person. */
public record AccountExport(Instant exportedAt, Profile profile, List<AddressResponse> addresses, List<OrderDetailResponse> orders,
                            List<ReviewResponse> reviews) {

    public record Profile(String fullName, String email, String phone, String role, String signIn, boolean twoFactorEnabled,
                          Instant createdAt, Instant lastSignInAt) {
    }
}
