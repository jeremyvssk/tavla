// Writing CSV files the admin exports, safe to open in a spreadsheet: quoting, and formulas defused.
package com.iloveshopping.csv;

import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.stream.Collectors;

/**
 * RFC 4180 quoting, plus the OWASP rule for CSV injection: a cell that starts with = + - @ or a tab or
 * carriage return is run as a formula by Excel and LibreOffice, so a product named "=HYPERLINK(...)"
 * would become a live link in the admin's spreadsheet. Such a cell gets a leading apostrophe, which
 * spreadsheets read as "this is text". Numbers are written by the caller as plain strings and never
 * start with those characters, except a negative amount, which this shop never exports.
 */
public final class Csv {

    /** Excel opens a UTF-8 file as UTF-8 only when it starts with a byte order mark. */
    public static final String BOM = "﻿";

    private Csv() {
    }

    public static String row(List<?> cells) {
        return cells.stream().map(c -> cell(c == null ? "" : c.toString())).collect(Collectors.joining(",")) + "\r\n";
    }

    public static String cell(String value) {
        String safe = isFormula(value) ? "'" + value : value;
        if (safe.contains(",") || safe.contains("\"") || safe.contains("\n") || safe.contains("\r")) {
            return '"' + safe.replace("\"", "\"\"") + '"';
        }
        return safe;
    }

    public static boolean isFormula(String value) {
        return !value.isEmpty() && "=+-@\t\r".indexOf(value.charAt(0)) >= 0;
    }

    /** A whole file: the BOM, a header row and the rows, as bytes for a download. */
    public static byte[] file(List<String> header, List<List<?>> rows) {
        StringBuilder out = new StringBuilder(BOM).append(row(header));
        rows.forEach(r -> out.append(row(r)));
        return out.toString().getBytes(StandardCharsets.UTF_8);
    }

    /** Sent as an attachment, so the browser saves it rather than showing it. */
    public static ResponseEntity<byte[]> download(byte[] body, String fileName) {
        return ResponseEntity.ok()
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(fileName).build().toString())
                .body(body);
    }
}
