package com.karvya.store.application.media;

import java.io.InputStream;

/**
 * Where uploaded media lives.
 *
 * <p>A port, so the filesystem implementation used today can be swapped for
 * object storage without the product code knowing. Keys are opaque relative
 * paths; nothing above this interface builds a filesystem path.
 */
public interface StorageService {

    /**
     * Stores a file and returns the key it can be read back by.
     *
     * @param keyPrefix logical folder, e.g. {@code products/kv-bh-01}
     * @param extension without the dot
     */
    String store(String keyPrefix, String extension, InputStream content);

    /**
     * Stores a file at an exact key, replacing anything already there.
     *
     * <p>Used where the caller must control the name because something else
     * derives it - the storefront builds rendition URLs from the base key and
     * a width, so those files cannot be given generated names.
     */
    void storeAt(String key, InputStream content);

    /** Removes a stored file. Missing keys are not an error. */
    void delete(String key);

    boolean exists(String key);
}
