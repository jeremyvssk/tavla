// Thrown when an uploaded file can't be read as a product list at all; maps to 400 invalid_import_file.
package com.iloveshopping.catalog.exception;

/** The message is written for the person who uploaded the file, and is sent to them as fields.file. */
public class ImportFileException extends RuntimeException {

    public ImportFileException(String message) {
        super(message);
    }
}
