// Unit tests for the CSV writer: quoting, and cells a spreadsheet would run as formulas.
package com.iloveshopping.csv;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CsvTest {

    @ParameterizedTest
    @ValueSource(strings = {"=HYPERLINK(\"http://evil\")", "+1+1", "-2+3", "@SUM(A1)", "\tcmd"})
    void aCellASpreadsheetWouldRunIsWrittenAsText(String value) {
        assertThat(Csv.cell(value)).contains("'" + value.charAt(0));
    }

    @Test
    void commasQuotesAndLineBreaksAreQuoted() {
        assertThat(Csv.cell("Chess, Go")).isEqualTo("\"Chess, Go\"");
        assertThat(Csv.cell("19\" board")).isEqualTo("\"19\"\" board\"");
        assertThat(Csv.cell("two\nlines")).isEqualTo("\"two\nlines\"");
        assertThat(Csv.cell("plain")).isEqualTo("plain");
    }

    @Test
    void aFileStartsWithTheByteOrderMarkSoExcelReadsItAsUtf8() {
        String file = new String(Csv.file(List.of("Name", "Price"), List.of(List.of("Pärnu set", "12.90"))), StandardCharsets.UTF_8);
        assertThat(file).isEqualTo("﻿Name,Price\r\nPärnu set,12.90\r\n");
    }
}
