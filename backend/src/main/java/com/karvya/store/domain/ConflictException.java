package com.karvya.store.domain;

/** The request cannot be applied because it collides with existing state. */
public class ConflictException extends DomainException {

    private final String code;

    public ConflictException(String code, String message) {
        super(message);
        this.code = code;
    }

    @Override
    public String code() {
        return code;
    }
}
