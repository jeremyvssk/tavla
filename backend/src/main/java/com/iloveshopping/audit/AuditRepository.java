// Spring Data repository for the audit log: appends, filtered pages, and the nightly retention delete.
package com.iloveshopping.audit;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

public interface AuditRepository extends JpaRepository<AuditEntry, Long>, JpaSpecificationExecutor<AuditEntry> {

    @Query(value = "SELECT DISTINCT actor_id FROM audit_log WHERE actor_id IS NOT NULL", nativeQuery = true)
    List<UUID> findActorIds();

    /** Past the retention only; the V11 trigger refuses anything younger, so a wrong interval here fails loudly. */
    @Modifying
    @Query(value = "DELETE FROM audit_log WHERE at < now() - INTERVAL '12 months'", nativeQuery = true)
    int deleteExpired();
}
