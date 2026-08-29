package com.karvya.store.domain;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * One or more values were rejected, with a message for each.
 *
 * <p>Bean validation already reports per-field errors, and the interface marks
 * the offending inputs from them. Anything validated in a service rather than
 * by an annotation had no way to say the same thing: it threw a single message
 * naming the field in prose, which a form can only show as a banner, leaving
 * the reader to find the field themselves.
 *
 * <p>Carries every failure rather than the first. A form with three bad values
 * should be correctable in one pass, not three.
 */
public class FieldValidationException extends DomainException {

    private final Map<String, String> errors;

    public FieldValidationException(Map<String, String> errors) {
        super(summarise(errors));
        this.errors = Map.copyOf(errors);
    }

    private static String summarise(Map<String, String> errors) {
        if (errors.size() == 1) {
            // named, because on its own this may be shown as a banner with
            // nothing else to say which field it is about
            Map.Entry<String, String> only = errors.entrySet().iterator().next();
            return only.getKey() + ": " + only.getValue();
        }
        return errors.size() + " values could not be saved.";
    }

    /** Field name to the reason, in the order they were checked. */
    public Map<String, String> errors() {
        return new LinkedHashMap<>(errors);
    }

    @Override
    public String code() {
        return "validation-failed";
    }
}
