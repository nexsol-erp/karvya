package com.karvya.store.domain;

/**
 * A requested resource does not exist, or exists but is not visible to the
 * caller. Both cases deliberately produce the same result so that an identifier
 * cannot be probed for existence.
 */
public class NotFoundException extends DomainException {

    private final String code;

    public NotFoundException(String resource, String identifier) {
        super(resource + " not found: " + identifier);
        this.code = toSlug(resource) + "-not-found";
    }

    @Override
    public String code() {
        return code;
    }

    private static String toSlug(String resource) {
        return resource.trim().toLowerCase().replaceAll("[^a-z0-9]+", "-");
    }
}
