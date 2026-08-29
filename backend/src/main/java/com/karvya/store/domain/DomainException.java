package com.karvya.store.domain;

/**
 * Base type for failures that are part of the business language rather than
 * infrastructure faults. The web layer maps these onto problem responses; they
 * never carry a stack trace to the client.
 */
public abstract class DomainException extends RuntimeException {

    protected DomainException(String message) {
        super(message);
    }

    /** Machine-readable slug used as the problem type, e.g. {@code product-not-found}. */
    public abstract String code();
}
