// One shelf of the staff's category tree: hidden ones too, with how many products sit directly on it.
package com.iloveshopping.catalog.dto;

import java.util.List;

/** productCount: products on this shelf itself, archived ones left out; the shelves below count their own. */
public record StaffCategoryNode(Integer id, String name, String slug, Integer parentId, boolean active, long productCount,
                                List<StaffCategoryNode> children) {
}
