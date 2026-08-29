package com.karvya.store.infrastructure.media;

import com.karvya.store.application.media.StorageService;
import com.karvya.store.infrastructure.config.AppProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.*;
import java.util.UUID;

/**
 * Stores uploads on disk under the configured media directory.
 *
 * <p>Filenames are generated here, never taken from the upload. A client-chosen
 * name is the shortest path to a traversal bug or an executable dropped into a
 * served directory; a UUID plus a validated extension cannot be either.
 *
 * <p>Every resolved path is checked to be inside the root before anything is
 * written, so even a malformed key cannot escape the directory.
 */
@Component
public class LocalFileSystemStorage implements StorageService {

    private static final Logger log = LoggerFactory.getLogger(LocalFileSystemStorage.class);

    private final Path root;

    public LocalFileSystemStorage(AppProperties properties) {
        this.root = Paths.get(properties.storageDir()).toAbsolutePath().normalize();
        try {
            Files.createDirectories(root);
        } catch (IOException e) {
            throw new IllegalStateException("Could not create the media directory at " + root, e);
        }

        // Creating the root can succeed while writing into it cannot: a named
        // volume keeps the ownership it was first populated with, so a volume
        // seeded by a root container leaves this directory unwritable to the
        // non-root user the application runs as. Left unchecked that surfaces
        // much later, as a failed upload by an administrator who has no way to
        // read the cause. Fail at boot instead, where the message is visible.
        if (!Files.isWritable(root)) {
            throw new IllegalStateException(
                    "The media directory at " + root + " is not writable by this process (user "
                            + System.getProperty("user.name") + "). Uploads would fail. If it is a "
                            + "Docker volume, correct its ownership, e.g. "
                            + "docker run --rm -u 0 -v <volume>:/m alpine chown -R 100:101 /m");
        }

        log.info("Media directory: {}", root);
    }

    @Override
    public String store(String keyPrefix, String extension, InputStream content) {
        String key = sanitisePrefix(keyPrefix) + "/" + UUID.randomUUID() + "." + extension;
        Path destination = resolveWithinRoot(key);

        try {
            Files.createDirectories(destination.getParent());
            Files.copy(content, destination, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new IllegalStateException("Could not store the uploaded file", e);
        }
        return key;
    }

    @Override
    public void storeAt(String key, InputStream content) {
        Path destination = resolveWithinRoot(key);
        try {
            Files.createDirectories(destination.getParent());
            Files.copy(content, destination, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new IllegalStateException("Could not store " + key, e);
        }
    }

    @Override
    public void delete(String key) {
        try {
            Files.deleteIfExists(resolveWithinRoot(key));
        } catch (IOException e) {
            // a file that will not delete is worth knowing about but must not
            // fail the request that was removing its database row
            log.warn("Could not delete media {}", key, e);
        }
    }

    @Override
    public boolean exists(String key) {
        return Files.exists(resolveWithinRoot(key));
    }

    /** Strips anything that could climb out of the root. */
    private String sanitisePrefix(String keyPrefix) {
        String cleaned = keyPrefix == null ? "" : keyPrefix.replaceAll("[^A-Za-z0-9/_-]", "");
        cleaned = cleaned.replace("..", "").replaceAll("/{2,}", "/");
        cleaned = cleaned.startsWith("/") ? cleaned.substring(1) : cleaned;
        return cleaned.isBlank() ? "uploads" : cleaned;
    }

    /**
     * Resolves a key against the root and refuses anything that lands outside
     * it. Belt and braces alongside {@link #sanitisePrefix}, because path
     * traversal is the one bug here that turns a file upload into arbitrary
     * file write.
     */
    private Path resolveWithinRoot(String key) {
        Path resolved = root.resolve(key).normalize();
        if (!resolved.startsWith(root)) {
            throw new IllegalArgumentException("Refusing a media key that escapes the storage root");
        }
        return resolved;
    }
}
