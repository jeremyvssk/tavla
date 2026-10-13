// Reading uploaded CSV: quoting, line endings, blank lines, and the spreadsheet row each record starts on.
package com.iloveshopping.csv;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CsvReaderTest {

    @Test
    void plainRowsSplitOnCommas_andAnyLineEndingWorks() {
        List<CsvReader.Record> records = CsvReader.read("sku,name\r\nA1,Board\nB2,Clock\rC3,Set");

        assertThat(records).extracting(CsvReader.Record::cells).containsExactly(
                List.of("sku", "name"), List.of("A1", "Board"), List.of("B2", "Clock"), List.of("C3", "Set"));
        assertThat(records).extracting(CsvReader.Record::row).containsExactly(1, 2, 3, 4);
    }

    @Test
    void quotedCellsHoldCommasQuotesAndLineBreaks_andLaterRowsKeepTheirSpreadsheetNumber() {
        List<CsvReader.Record> records = CsvReader.read("""
                sku,description
                A1,"Walnut, ""hand made""
                second line"
                B2,plain
                """);

        assertThat(records.get(1).cells()).containsExactly("A1", "Walnut, \"hand made\"\nsecond line");
        assertThat(records.get(2).row()).isEqualTo(4);
    }

    @Test
    void theByteOrderMarkBlankLinesAndEmptyCellsAreHandled() {
        List<CsvReader.Record> records = CsvReader.read("\uFEFFsku,name\n\nA1,\n,\n");

        assertThat(records.getFirst().cells()).containsExactly("sku", "name");
        assertThat(records.get(1).cells()).containsExactly("A1", "");
        assertThat(records.get(2).cells()).containsExactly("", "");
        assertThat(records.get(1).row()).isEqualTo(3);
    }

    @Test
    void aQuoteInsideAnUnquotedCellIsJustACharacter() {
        assertThat(CsvReader.read("12\" board,x").getFirst().cells()).containsExactly("12\" board", "x");
    }

    @Test
    void anUnclosedQuoteOrTextAfterAClosingQuoteIsRefused() {
        assertThatThrownBy(() -> CsvReader.read("sku\n\"A1\nB2")).isInstanceOf(CsvReader.MalformedCsvException.class)
                .hasMessageContaining("row 2");
        assertThatThrownBy(() -> CsvReader.read("\"A1\"x,y")).isInstanceOf(CsvReader.MalformedCsvException.class);
    }
}
