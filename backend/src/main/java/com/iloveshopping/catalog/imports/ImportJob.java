// JPA entity mapping "import_jobs": one uploaded file between its check and its import, then a record of how it went.
package com.iloveshopping.catalog.imports;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

/** The JSONB columns are kept as JSON text and read and written with the app's own ObjectMapper (ImportService). */
@Entity
@Table(name = "import_jobs")
@Getter
@Setter
public class ImportJob {

    @Id
    private UUID id;

    @Column(name = "file_name", nullable = false)
    private String fileName;

    /** JSON or CSV */
    @Column(nullable = false)
    private String format;

    @Column(name = "size_bytes", nullable = false)
    private int sizeBytes;

    @Column(name = "uploaded_by")
    private UUID uploadedBy;

    @Column(name = "uploaded_at", nullable = false)
    private Instant uploadedAt;

    /** CHECKED, DONE or FAILED */
    @Column(nullable = false)
    private String status;

    @Column(name = "row_count", nullable = false)
    private int rowCount;

    /** UPDATE or SKIP */
    @Column(name = "on_match", nullable = false)
    private String onMatch;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private String counts;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private String errors;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private String changes;

    /** The file's rows as they were in it, until the job is done or failed. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private String planned;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private String header;

    @Column
    private String failure;

    @Column(name = "finished_at")
    private Instant finishedAt;
}
