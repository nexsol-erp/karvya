package com.karvya.store.domain.model;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Duration;
import java.time.Instant;

/**
 * A queued outbound email - the transactional outbox.
 *
 * <p>Rows are written inside the business transaction that caused them and
 * sent afterwards by a worker. That ordering is the whole point: an order or a
 * password reset commits whether or not the mail server is reachable, and a
 * delivery failure becomes a retryable row rather than a lost transaction.
 */
@Entity
@Table(name = "email_notification")
public class EmailNotification {

    public static final String TYPE_PASSWORD_RESET = "PASSWORD_RESET";
    public static final String TYPE_ORDER_CUSTOMER = "ORDER_CONFIRMATION_CUSTOMER";
    public static final String TYPE_ORDER_ADMIN = "ORDER_NOTIFICATION_ADMIN";
    public static final String TYPE_ENQUIRY_ADMIN = "ENQUIRY_NOTIFICATION_ADMIN";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 48)
    private String type;

    @Column(nullable = false, length = 255)
    private String recipient;

    @Column(nullable = false, length = 255)
    private String subject;

    /** Template variables. JSONB so the payload shape can differ per type. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private String payload;

    @Column(name = "related_order_id")
    private Long relatedOrderId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private NotificationStatus status = NotificationStatus.PENDING;

    @Column(nullable = false)
    private int attempts;

    @Column(name = "last_error", columnDefinition = "text")
    private String lastError;

    @Column(name = "last_attempt_at")
    private Instant lastAttemptAt;

    @Column(name = "next_attempt_at", nullable = false)
    private Instant nextAttemptAt = Instant.now();

    @Column(name = "sent_at")
    private Instant sentAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        this.createdAt = Instant.now();
    }

    protected EmailNotification() {
    }

    public static EmailNotification queue(String type, String recipient, String subject, String payloadJson) {
        EmailNotification notification = new EmailNotification();
        notification.type = type;
        notification.recipient = recipient;
        notification.subject = subject;
        notification.payload = payloadJson;
        return notification;
    }

    /**
     * Leases this row to a worker by pushing its next attempt into the future.
     *
     * <p>Claim and send are separate steps, so between them the row must not
     * look due to anyone else. If the worker dies mid-send the lease simply
     * expires and another pass picks it up.
     */
    public void reserveUntil(Instant leaseExpiry) {
        this.nextAttemptAt = leaseExpiry;
    }

    public void markSent() {
        this.status = NotificationStatus.SENT;
        this.sentAt = Instant.now();
        this.lastAttemptAt = this.sentAt;
        this.lastError = null;
    }

    /**
     * Records a failure and schedules the next attempt with exponential
     * backoff, or gives up once the budget is spent. Backoff matters: retrying
     * a temporarily unreachable mail server every minute makes the outage
     * worse and floods the log.
     */
    public void markAttemptFailed(String error, int maxAttempts) {
        this.attempts++;
        this.lastError = error == null ? null : error.substring(0, Math.min(error.length(), 2000));
        this.lastAttemptAt = Instant.now();

        if (this.attempts >= maxAttempts) {
            this.status = NotificationStatus.FAILED;
        } else {
            long backoffMinutes = (long) Math.pow(2, Math.min(this.attempts, 8));
            this.nextAttemptAt = Instant.now().plus(Duration.ofMinutes(backoffMinutes));
        }
    }

    public Long getId() { return id; }
    public String getType() { return type; }
    public String getRecipient() { return recipient; }
    public String getSubject() { return subject; }
    public String getPayload() { return payload; }
    public Long getRelatedOrderId() { return relatedOrderId; }
    public void setRelatedOrderId(Long id) { this.relatedOrderId = id; }
    public NotificationStatus getStatus() { return status; }
    public int getAttempts() { return attempts; }
    public String getLastError() { return lastError; }
    public Instant getNextAttemptAt() { return nextAttemptAt; }
    public Instant getSentAt() { return sentAt; }
    public Instant getCreatedAt() { return createdAt; }
}
