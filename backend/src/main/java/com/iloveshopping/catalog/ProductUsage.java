// What the catalog asks the order domain about a product before deleting it: is it in any order?
package com.iloveshopping.catalog;

import java.util.Collection;
import java.util.Set;
import java.util.UUID;

/**
 * Implemented by the order domain (order/OrderedProducts), so the catalog never reads order tables. A
 * product someone bought is archived instead of deleted, so their order keeps a link to it.
 */
public interface ProductUsage {

    /** The ids among {@code productIds} that at least one order line points at. */
    Set<UUID> ordered(Collection<UUID> productIds);
}
