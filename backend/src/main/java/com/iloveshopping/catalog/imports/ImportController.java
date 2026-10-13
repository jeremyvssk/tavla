// Bulk upload endpoints: upload and check a file, read the job, import it, and download the rows with errors.
package com.iloveshopping.catalog.imports;

import com.iloveshopping.catalog.dto.ImportConfirmRequest;
import com.iloveshopping.catalog.dto.ImportJobResponse;
import jakarta.validation.Valid;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

/** Sales and admins only (SecurityConfig, StaffArea.CATALOG). */
@RestController
@RequestMapping("/staff/imports")
public class ImportController {

    private final ImportService importService;

    public ImportController(ImportService importService) {
        this.importService = importService;
    }

    @PostMapping
    public ResponseEntity<ImportJobResponse> check(@RequestParam("file") MultipartFile file) {
        return ResponseEntity.status(HttpStatus.CREATED).body(importService.check(file));
    }

    @GetMapping("/{id}")
    public ImportJobResponse job(@PathVariable UUID id) {
        return importService.get(id);
    }

    @PostMapping("/{id}/confirm")
    public ImportJobResponse confirm(@PathVariable UUID id, @Valid @RequestBody ImportConfirmRequest request) {
        return importService.confirm(id, "update".equals(request.onMatch()));
    }

    @GetMapping("/{id}/errors")
    public ResponseEntity<byte[]> errors(@PathVariable UUID id) {
        ImportService.ErrorRows rows = importService.errorRows(id);
        return ResponseEntity.ok()
                .contentType(new MediaType(MediaType.parseMediaType(rows.contentType()), StandardCharsets.UTF_8))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(rows.fileName()).build().toString())
                .body(rows.body());
    }
}
