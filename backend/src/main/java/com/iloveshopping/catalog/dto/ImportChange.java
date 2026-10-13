// One value a bulk upload will change on a product already in the shop.
package com.iloveshopping.catalog.dto;

public record ImportChange(String sku, String name, String column, String from, String to) {
}
