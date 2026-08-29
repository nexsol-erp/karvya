package com.karvya.store.domain;

/**
 * The caller has exceeded an allowance. Carries no detail about whose limit
 * was hit or how much remains, since that is useful to an attacker probing the
 * endpoint and useless to a legitimate caller.
 */
public class TooManyRequestsException extends DomainException {

    public TooManyRequestsException() {
        super("Too many attempts. Please wait a few minutes and try again.");
    }

    @Override
    public String code() {
        return "too-many-requests";
    }
}
