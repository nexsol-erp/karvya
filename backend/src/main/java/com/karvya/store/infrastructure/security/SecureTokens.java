package com.karvya.store.infrastructure.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

/**
 * Generation and hashing of opaque, single-use tokens.
 *
 * <p>Tokens are hashed with SHA-256 rather than bcrypt, and that is deliberate
 * rather than an oversight. bcrypt exists to slow down guessing of a
 * low-entropy human-chosen secret; these values carry 256 bits from a
 * cryptographic source, so there is nothing to guess and a slow hash would only
 * cost latency on every lookup. What matters is that the raw value is never
 * stored, which it is not.
 */
public final class SecureTokens {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final int TOKEN_BYTES = 32;

    private SecureTokens() {
    }

    /** A URL-safe token. Returned once and never persisted in this form. */
    public static String generate() {
        byte[] bytes = new byte[TOKEN_BYTES];
        RANDOM.nextBytes(bytes);
        return ENCODER.encodeToString(bytes);
    }

    public static String hash(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 is mandated by the JLS; absence means a broken runtime
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    /**
     * Constant-time comparison, for the rare case where two token hashes are
     * compared in application code rather than by a unique-index lookup.
     */
    public static boolean matches(String rawToken, String storedHash) {
        return MessageDigest.isEqual(
                hash(rawToken).getBytes(StandardCharsets.UTF_8),
                storedHash.getBytes(StandardCharsets.UTF_8));
    }
}
