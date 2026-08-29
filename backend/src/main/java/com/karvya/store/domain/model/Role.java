package com.karvya.store.domain.model;

import jakarta.persistence.*;
import java.time.Instant;

/**
 * A named authority. Roles live in the database rather than in an enum so an
 * administrator can be granted or revoked without a deployment.
 */
@Entity
@Table(name = "role")
public class Role {

    /** Full back-office access. */
    public static final String ADMIN = "ADMIN";
    /** Day-to-day order and enquiry handling. */
    public static final String STAFF = "STAFF";
    /** A registered shopper. */
    public static final String CUSTOMER = "CUSTOMER";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 32)
    private String code;

    @Column(nullable = false, length = 64)
    private String label;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        this.createdAt = Instant.now();
    }

    protected Role() {
    }

    public Long getId() { return id; }
    public String getCode() { return code; }
    public String getLabel() { return label; }

    /** Spring Security expects the {@code ROLE_} prefix on granted authorities. */
    public String authority() {
        return "ROLE_" + code;
    }
}
