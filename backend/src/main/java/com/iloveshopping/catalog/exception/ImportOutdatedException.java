// Thrown when an upload checked clean no longer does at Import (a category went, a SKU was taken); maps to 409 import_outdated.
package com.iloveshopping.catalog.exception;

/** A conflict that still commits: the job is saved with the new errors before this reaches the client. */
public class ImportOutdatedException extends CatalogConflictException {

    public ImportOutdatedException() {
        super("import_outdated");
    }
}
