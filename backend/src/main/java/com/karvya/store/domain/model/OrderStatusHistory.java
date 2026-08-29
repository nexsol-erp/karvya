package com.karvya.store.domain.model;

import jakarta.persistence.*;
import java.time.Instant;

/**
 * An append-only record of every status move, for orders and for payments.
 *
 * <p>One table for both, distinguished by {@code field}, because the questions
 * asked of it are chronological - what happened to this order, and in what
 * order - rather than per-dimension.
 */
@Entity
@Table(name = "order_status_history")
public class OrderStatusHistory {

    public static final String FIELD_STATUS = "STATUS";
    public static final String FIELD_PAYMENT_STATUS = "PAYMENT_STATUS";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false)
    private CustomerOrder order;

    @Column(nullable = false, length = 24)
    private String field;

    @Column(name = "from_value", length = 24)
    private String fromValue;

    @Column(name = "to_value", nullable = false, length = 24)
    private String toValue;

    @Column(columnDefinition = "text")
    private String note;

    @Column(name = "changed_by", nullable = false, length = 160)
    private String changedBy;

    @Column(name = "changed_at", nullable = false, updatable = false)
    private Instant changedAt;

    @PrePersist
    void onCreate() {
        this.changedAt = Instant.now();
    }

    protected OrderStatusHistory() {
    }

    static OrderStatusHistory record(CustomerOrder order, String field,
                                     String from, String to, String note, String changedBy) {
        OrderStatusHistory entry = new OrderStatusHistory();
        entry.order = order;
        entry.field = field;
        entry.fromValue = from;
        entry.toValue = to;
        entry.note = note;
        entry.changedBy = changedBy;
        entry.changedAt = Instant.now();
        return entry;
    }

    public Long getId() { return id; }
    public String getField() { return field; }
    public String getFromValue() { return fromValue; }
    public String getToValue() { return toValue; }
    public String getNote() { return note; }
    public String getChangedBy() { return changedBy; }
    public Instant getChangedAt() { return changedAt; }
}
