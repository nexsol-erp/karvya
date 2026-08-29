package com.karvya.store.application.order;

import com.karvya.store.domain.repository.CustomerOrderRepository;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

/**
 * Produces order numbers of the form {@code KV-260829-7QK4}.
 *
 * <p>The date makes it human and roughly sortable; the four random characters
 * make it unguessable, so knowing one order number tells you nothing about any
 * other. A pure daily counter would fail that test - {@code KV-260829-0007}
 * invites someone to try {@code 0006}.
 *
 * <p>Short enough to read aloud, which matters when payment gets arranged over
 * the phone or WhatsApp.
 */
@Component
public class OrderNumberGenerator {

    private static final DateTimeFormatter DATE_PART = DateTimeFormatter.ofPattern("yyMMdd");

    /** Crockford-style: no I, L, O or U, so nothing is misread or spelled out. */
    private static final char[] ALPHABET = "0123456789ABCDEFGHJKMNPQRSTVWXYZ".toCharArray();
    private static final int SUFFIX_LENGTH = 4;
    private static final int MAX_ATTEMPTS = 10;

    private final SecureRandom random = new SecureRandom();
    private final CustomerOrderRepository orders;

    public OrderNumberGenerator(CustomerOrderRepository orders) {
        this.orders = orders;
    }

    /**
     * Returns an unused order number.
     *
     * <p>Checked against the table before use, but the unique constraint is
     * the real guarantee - two requests can pass this check at the same instant
     * and only the database can settle that. Retrying here just makes the
     * collision path rare enough never to be seen.
     */
    public String next() {
        String datePart = LocalDate.now(ZoneOffset.UTC).format(DATE_PART);

        for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
            String candidate = "KV-" + datePart + "-" + randomSuffix();
            if (!orders.existsByOrderNumber(candidate)) {
                return candidate;
            }
        }
        throw new IllegalStateException(
                "Could not generate an unused order number after " + MAX_ATTEMPTS + " attempts");
    }

    private String randomSuffix() {
        StringBuilder suffix = new StringBuilder(SUFFIX_LENGTH);
        for (int i = 0; i < SUFFIX_LENGTH; i++) {
            suffix.append(ALPHABET[random.nextInt(ALPHABET.length)]);
        }
        return suffix.toString();
    }
}
