// A category reduced to what a link needs; used in breadcrumbs and product detail.
package com.iloveshopping.catalog.dto;

public record CategoryRef(Integer id, String name, String slug) {
}
