// A bulk upload as the import page shows it: the file, what the check found, and how the import went.
package com.iloveshopping.catalog.dto;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * status: CHECKED (waiting for Import), DONE or FAILED. counts: new, update, error (rows with at least one
 * problem), photos and done. {@code errors} and {@code changes} are cut at a thousand entries each; the
 * counts are always whole.
 */
public record ImportJobResponse(UUID id, String fileName, String format, int sizeBytes, Instant uploadedAt, int rows,
                                String status, String onMatch, Map<String, Integer> counts, List<ImportIssue> errors,
                                List<ImportChange> changes, String failure) {
}
