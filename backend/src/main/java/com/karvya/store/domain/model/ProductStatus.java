package com.karvya.store.domain.model;

/**
 * Lifecycle of a catalogue entry. Only {@link #ACTIVE} products are visible to
 * the public storefront. Products are archived rather than deleted so that
 * historical orders keep resolving.
 */
public enum ProductStatus {
    DRAFT,
    ACTIVE,
    INACTIVE,
    ARCHIVED;

    public boolean isPubliclyVisible() {
        return this == ACTIVE;
    }
}
