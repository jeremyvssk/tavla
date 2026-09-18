// StorageService writing to a local directory (the product_images_data volume), served by nginx at /images.
package com.iloveshopping.storage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
public class LocalStorageService implements StorageService {

    private static final Logger log = LoggerFactory.getLogger(LocalStorageService.class);
    private static final Pattern EXTENSION = Pattern.compile("^[a-z]{3,4}$");
    private static final String FOLDER = "products";

    private final Path root;
    private final String publicPrefix;

    public LocalStorageService(@Value("${app.storage.images-dir}") String imagesDir,
                               @Value("${app.storage.public-prefix}") String publicPrefix) {
        this.root = Path.of(imagesDir).toAbsolutePath().normalize();
        this.publicPrefix = publicPrefix;
    }

    @Override
    public String store(byte[] bytes, String extension) {
        if (!EXTENSION.matcher(extension).matches()) {
            throw new IllegalArgumentException("unexpected extension");
        }
        String relative = FOLDER + "/" + UUID.randomUUID() + "." + extension;
        Path target = resolveInsideRoot(relative);
        try {
            Files.createDirectories(target.getParent());
            // CREATE_NEW: never overwrite, even in the astronomically unlikely UUID collision.
            Files.write(target, bytes, StandardOpenOption.CREATE_NEW);
        } catch (IOException e) {
            throw new UncheckedIOException("could not store image", e);
        }
        return publicPrefix + "/" + relative;
    }

    @Override
    public void delete(String url) {
        if (url == null || !url.startsWith(publicPrefix + "/")) {
            return;
        }
        try {
            Files.deleteIfExists(resolveInsideRoot(url.substring(publicPrefix.length() + 1)));
        } catch (IOException | IllegalArgumentException e) {
            // An orphaned file is untidy, not dangerous; the database row is already gone.
            log.warn("Could not delete stored file {}", url, e);
        }
    }

    /**
     * Defence in depth: names are generated, but every path is still normalised and checked to be
     * under the root, so a crafted URL like /images/../../etc/passwd can't reach outside it.
     */
    private Path resolveInsideRoot(String relative) {
        Path resolved = root.resolve(relative).normalize();
        if (!resolved.startsWith(root)) {
            throw new IllegalArgumentException("path escapes storage root");
        }
        return resolved;
    }
}
