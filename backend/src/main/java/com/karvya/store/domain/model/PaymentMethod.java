package com.karvya.store.domain.model;

import jakarta.persistence.*;

/**
 * An offline way to pay, configurable by an administrator.
 *
 * <p>Orders record the {@code code} rather than a foreign key, so retiring a
 * method never orphans the orders that used it.
 */
@Entity
@Table(name = "payment_method")
public class PaymentMethod extends Auditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 48)
    private String code;

    @Column(nullable = false, length = 120)
    private String label;

    @Column(columnDefinition = "text")
    private String instructions;

    @Column(name = "display_order", nullable = false)
    private int displayOrder;

    @Column(nullable = false)
    private boolean active = true;

    protected PaymentMethod() {
    }

    public Long getId() { return id; }
    public String getCode() { return code; }
    public String getLabel() { return label; }
    public String getInstructions() { return instructions; }
    public void setInstructions(String instructions) { this.instructions = instructions; }
    public int getDisplayOrder() { return displayOrder; }
    public void setDisplayOrder(int displayOrder) { this.displayOrder = displayOrder; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
    public void setLabel(String label) { this.label = label; }
}
