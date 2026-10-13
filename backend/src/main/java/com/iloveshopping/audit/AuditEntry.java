// JPA entity mapping the "audit_log" table: one staff change or sign-in. Written once, never changed.
package com.iloveshopping.audit;

import com.iloveshopping.user.Role;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;

import java.time.Instant;
import java.util.UUID;

/** No setters, and a trigger (V11) refuses any UPDATE or a DELETE inside the 12 months the log is kept. */
@Entity
@Table(name = "audit_log")
@Getter
public class AuditEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Instant at;

    @Column(name = "actor_id")
    private UUID actorId;

    @Enumerated(EnumType.STRING)
    @Column(name = "actor_role")
    private Role actorRole;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AuditKind kind;

    @Column(nullable = false)
    private String action;

    @Enumerated(EnumType.STRING)
    @Column(name = "target_type")
    private AuditTarget targetType;

    @Column(name = "target_id")
    private String targetId;

    @Column(name = "target_label")
    private String targetLabel;

    @Column(name = "change_from")
    private String changeFrom;

    @Column(name = "change_to")
    private String changeTo;

    @Column
    private String detail;

    @Column(name = "flag_text")
    private String flagText;

    @Column(name = "flag_serious", nullable = false)
    private boolean flagSerious;

    @Column
    private String ip;

    protected AuditEntry() {
    }

    AuditEntry(UUID actorId, Role actorRole, AuditEvent e, String ip, Instant at) {
        this.at = at;
        this.actorId = actorId;
        // the column takes staff roles only: a shopper's own actions are not audited
        this.actorRole = actorRole != null && actorRole.isStaff() ? actorRole : null;
        this.kind = e.kind();
        this.action = cut(e.action(), 120);
        this.targetType = e.targetType();
        this.targetId = cut(e.targetId(), 64);
        this.targetLabel = cut(e.targetLabel(), 255);
        this.changeFrom = cut(e.changeFrom(), 255);
        this.changeTo = cut(e.changeTo(), 255);
        this.detail = cut(e.detail(), 255);
        this.flagText = cut(e.flagText(), 120);
        this.flagSerious = e.flagSerious();
        this.ip = ip;
    }

    /** A long product name must not make the change it describes fail. */
    private static String cut(String value, int max) {
        return value == null || value.length() <= max ? value : value.substring(0, max - 1) + "…";
    }
}
