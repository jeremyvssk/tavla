// Shared slug rule for category and brand requests, so the two can't drift apart.
package com.iloveshopping.catalog.dto;

final class Slugs {

    static final String PATTERN = "^[a-z0-9]+(-[a-z0-9]+)*$";
    static final String MESSAGE = "must be lowercase letters, digits and single hyphens";

    private Slugs() {
    }
}
