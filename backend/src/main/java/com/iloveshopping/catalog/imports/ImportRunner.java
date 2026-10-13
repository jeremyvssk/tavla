// Import itself: checks the file again under a lock and writes every row in one transaction, or none of them.
package com.iloveshopping.catalog.imports;

import com.iloveshopping.audit.AuditEvent;
import com.iloveshopping.audit.AuditKind;
import com.iloveshopping.audit.AuditLog;
import com.iloveshopping.audit.AuditTarget;
import com.iloveshopping.catalog.ProductImageService;
import com.iloveshopping.catalog.ProductRepository;
import com.iloveshopping.catalog.ProductService;
import com.iloveshopping.catalog.exception.CatalogConflictException;
import com.iloveshopping.catalog.exception.CatalogNotFoundException;
import com.iloveshopping.catalog.exception.ImportOutdatedException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * A bean of its own so ImportService calls it through Spring's proxy: {@link #run} needs its transaction to
 * end (and roll back) before ImportService marks a failed job in a fresh one.
 */
@Component
class ImportRunner {

    private final ImportJobRepository jobs;
    private final ImportJobs json;
    private final ImportChecker checker;
    private final ProductService productService;
    private final ProductImageService imageService;
    private final ProductRepository products;
    private final AuditLog auditLog;

    ImportRunner(ImportJobRepository jobs, ImportJobs json, ImportChecker checker, ProductService productService,
                 ProductImageService imageService, ProductRepository products, AuditLog auditLog) {
        this.jobs = jobs;
        this.json = json;
        this.checker = checker;
        this.productService = productService;
        this.imageService = imageService;
        this.products = products;
        this.auditLog = auditLog;
    }

    /**
     * All or nothing, as the import page promises. The job row is locked first, so a double press runs once
     * and the second finds it done. The file is checked again against the shop as it is now: a category
     * deleted or a SKU taken since the check would otherwise surface halfway through. If the check now finds
     * errors, they are saved on the job (this transaction commits that much) and nothing else is written.
     */
    @Transactional(noRollbackFor = ImportOutdatedException.class)
    public ImportJob run(UUID id, boolean updateMatches) {
        ImportJob job = jobs.findForUpdate(id).orElseThrow(() -> new CatalogNotFoundException("import_not_found"));
        if (!"CHECKED".equals(job.getStatus())) {
            throw new CatalogConflictException("import_not_ready");
        }
        if (json.counts(job).getOrDefault("error", 0) > 0) {
            throw new CatalogConflictException("import_has_errors");
        }
        ImportChecker.Plan plan = checker.check(json.file(job));
        if (!plan.errorLines().isEmpty()) {
            json.store(job, plan);
            jobs.save(job);
            throw new ImportOutdatedException();
        }
        int created = 0;
        int updated = 0;
        int skipped = 0;
        int photos = 0;
        for (ImportChecker.Step step : plan.steps()) {
            if (step.productId() != null && !updateMatches) {
                skipped++;
                continue;
            }
            UUID productId = productService.saveFromImport(step.productId(), step.request());
            for (ImportChecker.Photo photo : step.photos()) {
                imageService.addLinked(productId, photo.url(), photo.alt());
                photos++;
            }
            if (step.productId() == null) {
                created++;
            } else {
                updated++;
            }
        }
        // the unique SKU index speaks here, inside the transaction, if a SKU was taken after the check above
        products.flush();
        Map<String, Integer> counts = json.counts(job);
        counts.put("done", created + updated);
        job.setCounts(json.write(counts));
        job.setStatus("DONE");
        job.setOnMatch(updateMatches ? "UPDATE" : "SKIP");
        job.setPlanned(null);
        job.setFinishedAt(Instant.now());
        auditLog.record(AuditEvent.of(AuditKind.PRODUCTS, "Imported {target}").target(AuditTarget.IMPORT, id, job.getFileName())
                .detail(created + " new, " + updated + " updated, " + skipped + " skipped, " + photos + " photos"));
        return jobs.save(job);
    }

    /** After {@link #run} rolled back: the job ends FAILED with a reason, and its rows are let go. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public ImportJob fail(UUID id, String reason) {
        ImportJob job = jobs.findForUpdate(id).orElseThrow(() -> new CatalogNotFoundException("import_not_found"));
        job.setStatus("FAILED");
        job.setFailure(reason);
        job.setPlanned(null);
        job.setFinishedAt(Instant.now());
        auditLog.record(AuditEvent.of(AuditKind.PRODUCTS, "Import of {target} failed").target(AuditTarget.IMPORT, id, job.getFileName())
                .flag(reason, true));
        return jobs.save(job);
    }
}
