// One node of the public category tree, children nested.
package com.iloveshopping.catalog.dto;

import java.util.List;

public record CategoryNode(Integer id, String name, String slug, List<CategoryNode> children) {
}
