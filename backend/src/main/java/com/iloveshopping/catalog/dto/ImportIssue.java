// A problem the bulk upload's check found: the row, the column and the value at fault.
package com.iloveshopping.catalog.dto;

/** {@code line} is the spreadsheet row (the header is row 1) or, in a JSON file, the item's number from 1. */
public record ImportIssue(int line, String column, String problem, String value) {
}
