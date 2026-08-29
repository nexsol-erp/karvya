package com.karvya.store.application.notification;

/**
 * Outbound email, as the application needs it.
 *
 * <p>A port rather than a direct dependency on {@code JavaMailSender}, so the
 * worker can be tested against a sender that fails on demand - which is the
 * only way to prove that a failed send leaves the order intact.
 */
public interface EmailSender {

    /**
     * Sends one message.
     *
     * @throws EmailDeliveryException when the message could not be handed to
     *                                the mail server. The caller records the
     *                                failure and retries later; it never
     *                                propagates into a business transaction.
     */
    void send(String to, String subject, String htmlBody);

    /** Thrown for any delivery failure, transient or permanent. */
    class EmailDeliveryException extends RuntimeException {
        public EmailDeliveryException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
