// Abstraction over where uploaded file bytes live, so the local volume can be swapped for S3.
package com.iloveshopping.storage;

public interface StorageService {

    /**
     * Stores the bytes under a server-generated name and returns the public URL path. Nothing
     * from the client (filename, path) is ever part of the name.
     */
    String store(byte[] bytes, String extension);

    /** Removes a previously stored file. Unknown or foreign URLs are ignored. */
    void delete(String url);
}
