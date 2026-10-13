// Turns an uploaded CSV or JSON file into rows of column -> text, refusing a file that can't be read as a product list at all.
package com.iloveshopping.catalog.imports;

import com.iloveshopping.catalog.dto.ImportIssue;
import com.iloveshopping.catalog.exception.ImportFileException;
import com.iloveshopping.csv.CsvReader;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.JsonNodeFactory;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Two kinds of fault, told apart on purpose. A fault in the whole file (not UTF-8, a header column the upload
 * doesn't know, too many rows) stops here with a message, because no row of it can be trusted. A fault in
 * one row (an unknown key in one JSON item, a cell past the header) becomes an issue on that row, and the
 * check carries on so the person sees every problem in one go.
 *
 * <p>Each row is kept as it was in the file (a CSV row as an array of its cells, a JSON item as itself),
 * so the rows with errors can be handed back unchanged to fix, and the check can run again at Import.
 */
@Component
public class ImportFileReader {

    public static final int MAX_BYTES = 10 * 1024 * 1024;
    public static final int MAX_ROWS = 5_000;

    /** The file's rows before they are read as products. {@code header} is null for JSON. */
    public record RawFile(String format, List<String> header, List<RawRow> rows) {
    }

    public record RawRow(int line, JsonNode raw) {
    }

    /** One row as column -> text (absent when empty), and anything wrong with its shape. */
    public record Row(int line, Map<String, String> cells, List<ImportIssue> issues) {
    }

    private final ObjectMapper objectMapper;

    public ImportFileReader(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public RawFile read(String fileName, byte[] bytes) {
        if (bytes.length == 0) {
            throw new ImportFileException("The file is empty.");
        }
        if (bytes.length > MAX_BYTES) {
            throw new ImportFileException("The file is over 10 MB. Split it into smaller files.");
        }
        String lower = fileName.toLowerCase(Locale.ROOT);
        String text = utf8(bytes);
        if (lower.endsWith(".csv")) {
            return csv(text);
        }
        if (lower.endsWith(".json")) {
            return json(text);
        }
        throw new ImportFileException("Choose a .csv or .json file.");
    }

    /** One stored row back as cells, the same way at the check and again at Import. */
    public static Row cells(RawFile file, RawRow row) {
        return "CSV".equals(file.format()) ? csvCells(file.header(), row) : jsonCells(row);
    }

    private static String utf8(byte[] bytes) {
        try {
            return StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes)).toString();
        } catch (CharacterCodingException e) {
            throw new ImportFileException("The file isn't UTF-8 text. Save it as \"CSV UTF-8\" or as UTF-8 JSON.");
        }
    }

    private static RawFile csv(String text) {
        List<CsvReader.Record> records;
        try {
            records = CsvReader.read(text);
        } catch (CsvReader.MalformedCsvException e) {
            throw new ImportFileException(e.getMessage());
        }
        if (records.isEmpty()) {
            throw new ImportFileException("The file is empty.");
        }
        List<String> header = records.getFirst().cells().stream().map(c -> c.strip().toLowerCase(Locale.ROOT)).toList();
        List<String> unknown = header.stream().filter(h -> !ImportColumns.ALL.contains(h)).toList();
        if (!unknown.isEmpty()) {
            throw new ImportFileException("Unknown " + (unknown.size() == 1 ? "column " : "columns ")
                    + unknown.stream().map(u -> u.isEmpty() ? "(empty)" : "\"" + u + "\"").collect(Collectors.joining(", "))
                    + ". The columns are: " + String.join(", ", ImportColumns.ALL) + ".");
        }
        if (new HashSet<>(header).size() < header.size()) {
            throw new ImportFileException("A column appears twice in the header row.");
        }
        if (!header.contains("sku")) {
            throw new ImportFileException("The header row has no sku column. Rows are matched on it.");
        }
        List<RawRow> rows = new ArrayList<>();
        for (CsvReader.Record r : records.subList(1, records.size())) {
            ArrayNode cells = JsonNodeFactory.instance.arrayNode();
            r.cells().forEach(cells::add);
            rows.add(new RawRow(r.row(), cells));
        }
        return new RawFile("CSV", header, checkCount(rows));
    }

    private RawFile json(String text) {
        JsonNode root;
        try {
            root = objectMapper.readTree(text);
        } catch (JacksonException e) {
            throw new ImportFileException("The file isn't valid JSON. Check it near line "
                    + (e.getLocation() == null ? "?" : e.getLocation().getLineNr()) + ".");
        }
        if (root == null || !root.isArray()) {
            throw new ImportFileException("A JSON file must be a list of products: [ {...}, {...} ].");
        }
        List<RawRow> rows = new ArrayList<>();
        int line = 0;
        for (JsonNode item : root.values()) {
            rows.add(new RawRow(++line, item));
        }
        return new RawFile("JSON", null, checkCount(rows));
    }

    private static List<RawRow> checkCount(List<RawRow> rows) {
        if (rows.isEmpty()) {
            throw new ImportFileException("The file has no products in it.");
        }
        if (rows.size() > MAX_ROWS) {
            throw new ImportFileException("The file has " + rows.size() + " rows; the most one upload takes is "
                    + MAX_ROWS + ". Split it into smaller files.");
        }
        return rows;
    }

    private static Row csvCells(List<String> header, RawRow row) {
        Map<String, String> cells = new LinkedHashMap<>();
        List<ImportIssue> issues = new ArrayList<>();
        int i = 0;
        for (JsonNode cell : row.raw().values()) {
            String value = cell.asString();
            if (i < header.size()) {
                put(cells, header.get(i), value);
            } else if (!value.isBlank()) {
                issues.add(new ImportIssue(row.line(), "", "More cells than the header row has columns", value));
            }
            i++;
        }
        return new Row(row.line(), cells, issues);
    }

    private static Row jsonCells(RawRow row) {
        Map<String, String> cells = new LinkedHashMap<>();
        List<ImportIssue> issues = new ArrayList<>();
        JsonNode item = row.raw();
        if (!item.isObject()) {
            issues.add(new ImportIssue(row.line(), "", "Each product must be an object: {\"sku\": ..., \"name\": ...}", null));
            return new Row(row.line(), cells, issues);
        }
        Set<String> seen = new HashSet<>();
        for (Map.Entry<String, JsonNode> e : item.properties()) {
            String column = e.getKey().strip().toLowerCase(Locale.ROOT);
            JsonNode value = e.getValue();
            if (!ImportColumns.ALL.contains(column) || !seen.add(column)) {
                issues.add(new ImportIssue(row.line(), e.getKey(), seen.contains(column) && ImportColumns.ALL.contains(column)
                        ? "This column appears twice" : "Not a column the upload knows", null));
            } else if (value.isNull()) {
                continue;
            } else if (value.isString()) {
                put(cells, column, value.asString());
            } else if (value.isNumber()) {
                put(cells, column, value.decimalValue().toPlainString());
            } else if (column.equals("variant") && value.isObject() && value.values().stream().allMatch(v -> v.isString() || v.isNumber())) {
                Map<String, String> options = new LinkedHashMap<>();
                value.properties().forEach(o -> options.put(o.getKey(), o.getValue().asString()));
                put(cells, column, ImportColumns.variantText(options));
            } else if ((column.equals("image_url") || column.equals("image_alt")) && value.isArray()
                    && value.values().stream().allMatch(JsonNode::isString)) {
                put(cells, column, value.values().stream().map(JsonNode::asString).collect(Collectors.joining(ImportColumns.LIST_SEPARATOR)));
            } else {
                issues.add(new ImportIssue(row.line(), column, "Must be text or a number", value.toString()));
            }
        }
        return new Row(row.line(), cells, issues);
    }

    private static void put(Map<String, String> cells, String column, String value) {
        if (!value.isBlank()) {
            cells.put(column, value);
        }
    }
}
