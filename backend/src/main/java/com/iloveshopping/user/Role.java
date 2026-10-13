// User authorization role: a shopper, or one of the three staff roles.
package com.iloveshopping.user;

/** What each staff role may open is in auth/StaffArea.java. */
public enum Role {
    CUSTOMER,
    /** orders, refunds up to a limit, review moderation */
    SUPPORT,
    /** products, categories, prices within a limit, bulk upload, delivery options */
    SALES,
    /** everything, including roles and the audit log; granted only from the command line */
    ADMIN;

    public boolean isStaff() {
        return this != CUSTOMER;
    }
}
