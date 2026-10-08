// Who moved an order to a new status; written into each order_status_history row.
package com.iloveshopping.order;

public enum OrderActor {
    /** the person who placed the order */
    SHOPPER,
    /** Stripe, through its webhook or a status check against its API */
    STRIPE,
    /** a scheduled job, such as OrderExpiry releasing unpaid orders */
    TIMER,
    /** shop staff (packing, shipping, a cancellation on the shopper's behalf) */
    ADMIN
}
