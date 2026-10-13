// Admin endpoints for the audit log: a filtered page, and the same filter as a CSV file.
package com.iloveshopping.audit;

import com.iloveshopping.audit.dto.AuditPageResponse;
import com.iloveshopping.audit.dto.AuditParams;
import com.iloveshopping.csv.Csv;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Admin only, with a second factor (SecurityConfig, StaffArea.AUDIT). Read-only: no endpoint changes an entry. */
@RestController
@RequestMapping("/staff/audit")
public class AuditController {

    private final AuditLog auditLog;

    public AuditController(AuditLog auditLog) {
        this.auditLog = auditLog;
    }

    @GetMapping
    public AuditPageResponse page(@Valid @ModelAttribute AuditParams params) {
        return auditLog.page(params);
    }

    @GetMapping("/export")
    public ResponseEntity<byte[]> export(@Valid @ModelAttribute AuditParams params) {
        return Csv.download(auditLog.export(params), "tavla-audit-log.csv");
    }
}
