// The one rule for what a SKU may look like, shared by the product form and the bulk upload.
package com.iloveshopping.catalog.dto;

public final class Skus {

    /**
     * Letters, digits, spaces and . _ / -, starting and ending with a letter or digit. Spaces are allowed
     * because suppliers use them ("CH135 BROWN"); a leading or trailing one would be invisible in a
     * spreadsheet and make two SKUs look the same.
     */
    public static final String PATTERN = "[A-Za-z0-9](?:[A-Za-z0-9 ._/-]{0,62}[A-Za-z0-9])?";
    public static final String MESSAGE = "letters, digits, spaces and . _ / - only, starting and ending with a letter or digit";
    public static final int MAX = 64;

    private Skus() {
    }
}
