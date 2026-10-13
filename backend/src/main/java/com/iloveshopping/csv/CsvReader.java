// Reading an uploaded CSV file into records: RFC 4180 quoting, any line ending, and the spreadsheet row each record starts on.
package com.iloveshopping.csv;

import java.util.ArrayList;
import java.util.List;

/**
 * Hand-written rather than a library because the rules fit on a page and the upload needs one thing
 * libraries rarely give: the spreadsheet row each record started on, so an error can say "row 14" even
 * when an earlier cell held a line break. Quoted cells may hold commas, quotes ("") and line breaks; a
 * quote inside an unquoted cell is taken as a character, as Excel does. Blank lines are skipped.
 */
public final class CsvReader {

    /** One record and the row of the file it starts on (the header is row 1). */
    public record Record(int row, List<String> cells) {
    }

    /** Thrown for a file that can't be read as CSV at all, with the row where reading stopped. */
    public static class MalformedCsvException extends RuntimeException {
        public MalformedCsvException(String message) {
            super(message);
        }
    }

    private CsvReader() {
    }

    public static List<Record> read(String text) {
        List<Record> records = new ArrayList<>();
        List<String> cells = new ArrayList<>();
        StringBuilder cell = new StringBuilder();
        boolean quoted = false;
        boolean cellStarted = false;
        int row = 1;
        int recordRow = 1;
        int i = 0;
        int n = text.length();
        if (n > 0 && text.charAt(0) == '﻿') {
            i = 1;
        }
        while (i < n) {
            char c = text.charAt(i);
            if (quoted) {
                if (c == '"') {
                    if (i + 1 < n && text.charAt(i + 1) == '"') {
                        cell.append('"');
                        i += 2;
                        continue;
                    }
                    quoted = false;
                    i++;
                    // after a closing quote only a separator or the end of the line may follow
                    if (i < n && text.charAt(i) != ',' && text.charAt(i) != '\n' && text.charAt(i) != '\r') {
                        throw new MalformedCsvException("Row " + row + " has text after a closing quote.");
                    }
                    continue;
                }
                if (c == '\n' || (c == '\r' && (i + 1 >= n || text.charAt(i + 1) != '\n'))) {
                    row++;
                }
                cell.append(c);
                i++;
                continue;
            }
            switch (c) {
                case '"' -> {
                    if (!cellStarted) {
                        quoted = true;
                        cellStarted = true;
                    } else {
                        cell.append(c);
                    }
                    i++;
                }
                case ',' -> {
                    cells.add(cell.toString());
                    cell.setLength(0);
                    cellStarted = false;
                    i++;
                }
                case '\r', '\n' -> {
                    cells.add(cell.toString());
                    addRecord(records, recordRow, cells);
                    cells = new ArrayList<>();
                    cell.setLength(0);
                    cellStarted = false;
                    i += (c == '\r' && i + 1 < n && text.charAt(i + 1) == '\n') ? 2 : 1;
                    row++;
                    recordRow = row;
                }
                default -> {
                    cell.append(c);
                    cellStarted = true;
                    i++;
                }
            }
        }
        if (quoted) {
            throw new MalformedCsvException("A quote opened on row " + recordRow + " is never closed.");
        }
        if (cellStarted || !cells.isEmpty() || cell.length() > 0) {
            cells.add(cell.toString());
            addRecord(records, recordRow, cells);
        }
        return records;
    }

    private static void addRecord(List<Record> records, int row, List<String> cells) {
        boolean blank = cells.size() == 1 && cells.getFirst().isEmpty();
        if (!blank) {
            records.add(new Record(row, List.copyOf(cells)));
        }
    }
}
