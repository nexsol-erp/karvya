package com.karvya.store.domain.model;

import jakarta.persistence.*;
import java.time.Instant;

/**
 * A message sent through the contact form.
 *
 * <p>Saved before any attempt is made to notify anyone, for the same reason
 * orders are: a customer who has written to you must not lose their message
 * because the mail server happened to be down.
 */
@Entity
@Table(name = "contact_enquiry")
public class ContactEnquiry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 160)
    private String name;

    @Column(nullable = false, length = 255)
    private String email;

    @Column(length = 32)
    private String phone;

    @Column(nullable = false, length = 200)
    private String subject;

    @Column(nullable = false, columnDefinition = "text")
    private String message;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private EnquiryStatus status = EnquiryStatus.NEW;

    @Column(name = "internal_note", columnDefinition = "text")
    private String internalNote;

    @Column(name = "handled_by", length = 160)
    private String handledBy;

    @Column(name = "source_ip", length = 64)
    private String sourceIp;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = Instant.now();
    }

    protected ContactEnquiry() {
    }

    public static ContactEnquiry received(String name, String email, String phone,
                                          String subject, String message, String sourceIp) {
        ContactEnquiry enquiry = new ContactEnquiry();
        enquiry.name = name.trim();
        enquiry.email = email.trim();
        enquiry.phone = (phone == null || phone.isBlank()) ? null : phone.trim();
        enquiry.subject = subject.trim();
        enquiry.message = message.trim();
        enquiry.sourceIp = sourceIp;
        return enquiry;
    }

    public void moveTo(EnquiryStatus next, String actor) {
        this.status = next;
        this.handledBy = actor;
    }

    public void setInternalNote(String note, String actor) {
        this.internalNote = note;
        this.handledBy = actor;
    }

    public Long getId() { return id; }
    public String getName() { return name; }
    public String getEmail() { return email; }
    public String getPhone() { return phone; }
    public String getSubject() { return subject; }
    public String getMessage() { return message; }
    public EnquiryStatus getStatus() { return status; }
    public String getInternalNote() { return internalNote; }
    public String getHandledBy() { return handledBy; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
