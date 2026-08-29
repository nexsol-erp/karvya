package com.karvya.store.domain.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/** Money actually received against an order, recorded by an administrator. */
@Entity
@Table(name = "offline_payment")
public class OfflinePayment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false)
    private CustomerOrder order;

    @Column(name = "method_code", nullable = false, length = 48)
    private String methodCode;

    /** A UPI reference, a bank transaction id, or a receipt number. */
    @Column(length = 160)
    private String reference;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal amount;

    @Column(name = "received_on", nullable = false)
    private LocalDate receivedOn;

    @Column(columnDefinition = "text")
    private String note;

    @Column(name = "recorded_by", nullable = false, length = 160)
    private String recordedBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        this.createdAt = Instant.now();
    }

    protected OfflinePayment() {
    }

    public static OfflinePayment record(CustomerOrder order, String methodCode, String reference,
                                        BigDecimal amount, LocalDate receivedOn,
                                        String note, String recordedBy) {
        OfflinePayment payment = new OfflinePayment();
        payment.order = order;
        payment.methodCode = methodCode;
        payment.reference = reference;
        payment.amount = amount;
        payment.receivedOn = receivedOn;
        payment.note = note;
        payment.recordedBy = recordedBy;
        return payment;
    }

    public Long getId() { return id; }
    public CustomerOrder getOrder() { return order; }
    public String getMethodCode() { return methodCode; }
    public String getReference() { return reference; }
    public BigDecimal getAmount() { return amount; }
    public LocalDate getReceivedOn() { return receivedOn; }
    public String getNote() { return note; }
    public String getRecordedBy() { return recordedBy; }
    public Instant getCreatedAt() { return createdAt; }
}
