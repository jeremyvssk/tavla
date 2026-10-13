// Reads and writes an import job's JSON columns with the app's own ObjectMapper, and turns a job into its response.
package com.iloveshopping.catalog.imports;

import com.iloveshopping.catalog.dto.ImportChange;
import com.iloveshopping.catalog.dto.ImportIssue;
import com.iloveshopping.catalog.dto.ImportJobResponse;
import org.springframework.stereotype.Component;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Component
class ImportJobs {

    private static final TypeReference<Map<String, Integer>> COUNTS = new TypeReference<>() {
    };
    private static final TypeReference<List<ImportIssue>> ISSUES = new TypeReference<>() {
    };
    private static final TypeReference<List<ImportChange>> CHANGES = new TypeReference<>() {
    };
    private static final TypeReference<List<ImportFileReader.RawRow>> ROWS = new TypeReference<>() {
    };
    private static final TypeReference<List<String>> HEADER = new TypeReference<>() {
    };

    private final ObjectMapper objectMapper;

    ImportJobs(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /** Puts a check's findings on the job: counts, problems and changes. */
    void store(ImportJob job, ImportChecker.Plan plan) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        counts.put("new", plan.created());
        counts.put("update", plan.updated());
        counts.put("error", plan.errorLines().size());
        counts.put("photos", plan.photos());
        counts.put("done", 0);
        job.setCounts(write(counts));
        job.setErrors(write(plan.issues()));
        job.setChanges(write(plan.changes()));
    }

    void storeFile(ImportJob job, ImportFileReader.RawFile file) {
        job.setFormat(file.format());
        job.setRowCount(file.rows().size());
        job.setPlanned(write(file.rows()));
        job.setHeader(file.header() == null ? null : write(file.header()));
    }

    /** The file's rows as stored at upload. */
    ImportFileReader.RawFile file(ImportJob job) {
        List<String> header = job.getHeader() == null ? null : objectMapper.readValue(job.getHeader(), HEADER);
        return new ImportFileReader.RawFile(job.getFormat(), header, objectMapper.readValue(job.getPlanned(), ROWS));
    }

    Map<String, Integer> counts(ImportJob job) {
        return new LinkedHashMap<>(objectMapper.readValue(job.getCounts(), COUNTS));
    }

    String write(Object value) {
        return objectMapper.writeValueAsString(value);
    }

    byte[] bytes(Object value) {
        return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(value);
    }

    ImportJobResponse view(ImportJob job) {
        return new ImportJobResponse(job.getId(), job.getFileName(), job.getFormat().toLowerCase(Locale.ROOT), job.getSizeBytes(),
                job.getUploadedAt(), job.getRowCount(), job.getStatus(), job.getOnMatch().toLowerCase(Locale.ROOT), counts(job),
                objectMapper.readValue(job.getErrors(), ISSUES), objectMapper.readValue(job.getChanges(), CHANGES), job.getFailure());
    }
}
