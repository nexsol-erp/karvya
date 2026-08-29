package com.karvya.store.domain.model;

import jakarta.persistence.*;

import java.time.Instant;

/**
 * A field an administrator decided a product should have.
 *
 * <p>Scoped to a category, because this shop sells more than one kind of thing:
 * a book shows an author and an ISBN, a bird house shows a material, and
 * neither should be asked for the other's fields. A definition with no category
 * applies to everything.
 */
@Entity
@Table(name = "product_attribute")
public class ProductAttribute {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 80)
    private String label;

    /**
     * Stable across a rename, so relabelling "Care" to "Care instructions" does
     * not orphan every value recorded against it.
     */
    @Column(nullable = false, length = 80, unique = true)
    private String slug;

    /** Null applies it to every product, whatever the category. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id")
    private Category category;

    @Column(name = "help_text", length = 255)
    private String helpText;

    @Column(name = "display_order", nullable = false)
    private int displayOrder;

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "updated_by", length = 160)
    private String updatedBy;

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

    protected ProductAttribute() {
    }

    public static ProductAttribute of(String label, String slug) {
        ProductAttribute attribute = new ProductAttribute();
        attribute.label = label.trim();
        attribute.slug = slug;
        return attribute;
    }

    public Long getId() { return id; }
    public String getLabel() { return label; }
    public void setLabel(String label) { this.label = label == null ? null : label.trim(); }
    public String getSlug() { return slug; }
    public void setSlug(String slug) { this.slug = slug; }
    public Category getCategory() { return category; }
    public void setCategory(Category category) { this.category = category; }
    public String getHelpText() { return helpText; }
    public void setHelpText(String helpText) { this.helpText = helpText; }
    public int getDisplayOrder() { return displayOrder; }
    public void setDisplayOrder(int displayOrder) { this.displayOrder = displayOrder; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
    public Instant getUpdatedAt() { return updatedAt; }
    public String getUpdatedBy() { return updatedBy; }
    public void setUpdatedBy(String updatedBy) { this.updatedBy = updatedBy; }
}
