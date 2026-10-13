// Spring Data JPA repository for bulk upload jobs.
package com.iloveshopping.catalog.imports;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;
import java.util.UUID;

public interface ImportJobRepository extends JpaRepository<ImportJob, UUID> {

    /** Row-locked until the transaction ends, so two presses of Import run one after the other, not side by side. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT j FROM ImportJob j WHERE j.id = :id")
    Optional<ImportJob> findForUpdate(UUID id);
}
