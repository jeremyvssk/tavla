// Abstraction over where uploaded file bytes live, so the local volume can be swapped for S3.
package com.iloveshopping.storage;

public interface StorageService {

    /**
     * Stores the bytes under a server-generated name and returns the public URL path. Nothing
     * from the client (filename, path) is ever part of the name.
     */
    String store(byte[] bytes, String extension);

    /**
     * Stores a narrower copy of a file stored with {@link #store}, at that file's URL with "-{width}" before
     * the extension (/images/products/abc.jpg at 600 → /images/products/abc-600.jpg). The frontend builds
     * srcset from that pattern.
     */
    void storeVariant(String url, int width, byte[] bytes);

    /** Removes a previously stored file and its narrower copies. Unknown or foreign URLs are ignored. */
    void delete(String url);
}
