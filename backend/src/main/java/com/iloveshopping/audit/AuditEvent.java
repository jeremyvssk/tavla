// One thing a staff member did, as the code that did it describes it; AuditLog stores it with who and from where.
package com.iloveshopping.audit;

/**
 * Built fluently: {@code AuditEvent.of(ORDERS, "Marked {target} shipped").target(ORDER, id, "TV-10023")
 * .change("Processing", "Shipped")}. {@code action} is a sentence with {target} where the target's name
 * goes, so the page can link it. Never put a password, a token, a 2FA secret or a full address in any
 * field: the log is one more place the data would live. A user target carries no label; the page looks
 * the name up when an admin reads it.
 */
public record AuditEvent(AuditKind kind, String action, AuditTarget targetType, String targetId, String targetLabel,
                         String changeFrom, String changeTo, String detail, String flagText, boolean flagSerious) {

    public static AuditEvent of(AuditKind kind, String action) {
        return new AuditEvent(kind, action, null, null, null, null, null, null, null, false);
    }

    public AuditEvent target(AuditTarget type, Object id, String label) {
        return new AuditEvent(kind, action, type, id == null ? null : id.toString(), label, changeFrom, changeTo, detail,
                flagText, flagSerious);
    }

    public AuditEvent change(String from, String to) {
        return new AuditEvent(kind, action, targetType, targetId, targetLabel, from, to, detail, flagText, flagSerious);
    }

    public AuditEvent detail(String text) {
        return new AuditEvent(kind, action, targetType, targetId, targetLabel, changeFrom, changeTo, text, flagText, flagSerious);
    }

    /** A refusal or a failure, shown in place of before and after; {@code serious} ones in red. */
    public AuditEvent flag(String text, boolean serious) {
        return new AuditEvent(kind, action, targetType, targetId, targetLabel, changeFrom, changeTo, detail, text, serious);
    }
}
