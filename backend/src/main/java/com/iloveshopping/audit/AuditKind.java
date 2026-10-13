// The four groups the audit log's Kind filter offers.
package com.iloveshopping.audit;

public enum AuditKind {
    /** status moves, refunds, notes */
    ORDERS,
    /** products, prices, categories, imports, delivery options */
    PRODUCTS,
    /** moderation */
    REVIEWS,
    /** sign-ins, refused sign-ins, role changes */
    ACCESS
}
