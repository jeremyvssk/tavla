// The bulk upload: check a file without changing anything, show what it would do, then import it all or not at all.
package com.iloveshopping.catalog.imports;

import com.iloveshopping.auth.AuthPrincipal;
import com.iloveshopping.auth.CurrentUser;
import com.iloveshopping.catalog.dto.ImportJobResponse;
import com.iloveshopping.catalog.exception.CatalogNotFoundException;
import com.iloveshopping.catalog.exception.ImportFileException;
import com.iloveshopping.csv.Csv;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import tools.jackson.databind.JsonNode;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Synchronous on purpose. Five thousand rows check and import in seconds inside one request, and one
 * transaction is what makes "all or nothing" true: a background job would need its own way to undo half
 * an import. The upload is the check (a dry run); nothing in the shop changes until {@link #confirm}.
 */
@Service
public class ImportService {

    private static final Logger log = LoggerFactory.getLogger(ImportService.class);

    private final ImportFileReader reader;
    private final ImportChecker checker;
    private final ImportJobRepository jobs;
    private final ImportJobs json;
    private final ImportRunner runner;

    public ImportService(ImportFileReader reader, ImportChecker checker, ImportJobRepository jobs, ImportJobs json,
                         ImportRunner runner) {
        this.reader = reader;
        this.checker = checker;
        this.jobs = jobs;
        this.json = json;
        this.runner = runner;
    }

    /** A file the shop can't read at all is refused here (400); one with bad rows becomes a job listing them. */
    @Transactional
    public ImportJobResponse check(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ImportFileException("The file is empty.");
        }
        if (file.getSize() > ImportFileReader.MAX_BYTES) {
            throw new ImportFileException("The file is over 10 MB. Split it into smaller files.");
        }
        String name = fileName(file.getOriginalFilename());
        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException e) {
            throw new ImportFileException("The upload didn't arrive whole. Try again.");
        }
        ImportFileReader.RawFile raw = reader.read(name, bytes);
        ImportChecker.Plan plan = checker.check(raw);

        ImportJob job = new ImportJob();
        job.setId(UUID.randomUUID());
        job.setFileName(name);
        job.setSizeBytes(bytes.length);
        AuthPrincipal by = CurrentUser.principal();
        job.setUploadedBy(by == null ? null : by.userId());
        job.setUploadedAt(Instant.now());
        job.setStatus("CHECKED");
        job.setOnMatch("UPDATE");
        json.storeFile(job, raw);
        json.store(job, plan);
        return json.view(jobs.save(job));
    }

    @Transactional(readOnly = true)
    public ImportJobResponse get(UUID id) {
        return json.view(find(id));
    }

    /**
     * Refused (409) while the check lists errors: the file is fixed and uploaded again, never half imported.
     * A database failure while writing (a SKU taken at the last moment) rolls every row back, and the job
     * ends FAILED with a reason the page shows.
     */
    public ImportJobResponse confirm(UUID id, boolean updateMatches) {
        try {
            return json.view(runner.run(id, updateMatches));
        } catch (DataAccessException e) {
            log.warn("Import {} rolled back", id, e);
            return json.view(runner.fail(id, "Another change reached the same products while this file imported, so "
                    + "nothing was imported. Upload the file again to check it against the shop as it is now."));
        }
    }

    /** The rows with errors, as they were in the file, to fix and upload again: a CSV for a CSV, JSON for JSON. */
    @Transactional(readOnly = true)
    public ErrorRows errorRows(UUID id) {
        ImportJob job = find(id);
        if (job.getPlanned() == null) {
            throw new CatalogNotFoundException("import_rows_gone");
        }
        ImportFileReader.RawFile file = json.file(job);
        Set<Integer> lines = checker.check(file).errorLines();
        List<ImportFileReader.RawRow> refused = file.rows().stream().filter(r -> lines.contains(r.line())).toList();
        String base = "rows-with-errors-" + job.getFileName().replaceFirst("\\.[^.]*$", "");
        if ("CSV".equals(job.getFormat())) {
            List<List<?>> rows = new ArrayList<>();
            for (ImportFileReader.RawRow r : refused) {
                rows.add(r.raw().values().stream().map(JsonNode::asString).toList());
            }
            return new ErrorRows(Csv.file(file.header(), rows), base + ".csv", "text/csv");
        }
        return new ErrorRows(json.bytes(refused.stream().map(ImportFileReader.RawRow::raw).toList()), base + ".json", "application/json");
    }

    public record ErrorRows(byte[] body, String fileName, String contentType) {
    }

    private ImportJob find(UUID id) {
        return jobs.findById(id).orElseThrow(() -> new CatalogNotFoundException("import_not_found"));
    }

    /** The name without any folder a browser may send, without control characters, and at most 255 long. */
    static String fileName(String original) {
        String name = original == null ? "" : original.replaceAll(".*[/\\\\]", "").replaceAll("\\p{Cntrl}", "").strip();
        if (name.isEmpty()) {
            throw new ImportFileException("Choose a .csv or .json file.");
        }
        return name.length() > 255 ? name.substring(name.length() - 255) : name;
    }
}
