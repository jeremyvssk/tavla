// Scheduled job that deletes audit log entries older than the 12 months the log is kept.
package com.iloveshopping.audit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * A stated retention period is what GDPR asks of a log that names people. 12 months covers a dispute or
 * an investigation that starts late. One DELETE, so every instance can run it at once.
 */
@Component
public class AuditRetention {

    private static final Logger log = LoggerFactory.getLogger(AuditRetention.class);

    private final AuditRepository entries;

    public AuditRetention(AuditRepository entries) {
        this.entries = entries;
    }

    @Scheduled(initialDelayString = "PT10M", fixedDelayString = "PT24H")
    @Transactional
    public void deleteExpired() {
        int deleted = entries.deleteExpired();
        if (deleted > 0) {
            log.info("Deleted {} audit log entries past the 12-month retention", deleted);
        }
    }
}
