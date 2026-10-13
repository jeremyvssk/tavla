// What an audit entry is about; decides where the admin page links the entry's target.
package com.iloveshopping.audit;

public enum AuditTarget {
    ORDER,
    PRODUCT,
    CATEGORY,
    REVIEW,
    USER,
    DELIVERY,
    IMPORT;

    /** The admin page that shows the target, or null when there is no page for it. */
    String link(String id) {
        return switch (this) {
            case ORDER -> "/admin/orders/" + id;
            case PRODUCT -> "/admin/products/" + id;
            case CATEGORY -> "/admin/categories";
            case REVIEW -> "/admin/reviews";
            case USER -> "/admin/users";
            case DELIVERY -> "/admin/delivery";
            case IMPORT -> "/admin/import";
        };
    }
}
