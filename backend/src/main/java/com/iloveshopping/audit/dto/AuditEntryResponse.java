// One audit log row as the admin's audit page and overview show it.
package com.iloveshopping.audit.dto;

import com.iloveshopping.user.Role;

import java.time.Instant;

/**
 * The shape frontend/src/api/admin.ts calls AuditEntry. {@code actor} is the person's current name, null
 * once the account is deleted or when nobody was signed in (a refused sign-in). {@code ip} is already cut
 * to its first two parts.
 */
public record AuditEntryResponse(
        String id,
        Instant at,
        String actor,
        Role actorRole,
        String action,
        String target,
        String targetLink,
        Change change,
        String detail,
        Flag flag,
        String ip) {

    public record Change(String from, String to) {
    }

    public record Flag(String text, boolean serious) {
    }
}
