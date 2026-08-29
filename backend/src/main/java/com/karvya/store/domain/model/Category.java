package com.karvya.store.domain.model;

import jakarta.persistence.*;

@Entity
@Table(name = "category")
public class Category extends Auditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 160)
    private String name;

    @Column(nullable = false, length = 160, unique = true)
    private String slug;

    @Column(columnDefinition = "text")
    private String description;

    @Column(name = "image_key", length = 255)
    private String imageKey;

    /**
     * What the product's indexed free-text field is called for this category,
     * or null when it has no such field.
     *
     * <p>"Author" for a book, "Artist" for a record, nothing for a bird house.
     * One column, named differently - or hidden - by what is being sold.
     */
    @Column(name = "author_label", length = 40)
    private String authorLabel;

    @Column(name = "display_order", nullable = false)
    private int displayOrder;

    @Column(nullable = false)
    private boolean active = true;

    protected Category() {
    }

    public static Category create(String name, String slug) {
        Category category = new Category();
        category.name = name;
        category.slug = slug;
        return category;
    }

    public Long getId() { return id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getSlug() { return slug; }
    public void setSlug(String slug) { this.slug = slug; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getImageKey() { return imageKey; }
    public void setImageKey(String imageKey) { this.imageKey = imageKey; }
    public String getAuthorLabel() { return authorLabel; }
    public void setAuthorLabel(String v) { this.authorLabel = v; }
    public int getDisplayOrder() { return displayOrder; }
    public void setDisplayOrder(int displayOrder) { this.displayOrder = displayOrder; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
}
