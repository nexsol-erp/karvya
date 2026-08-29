package com.karvya.store.domain.model;

import jakarta.persistence.*;
import java.time.Instant;

/**
 * One photograph of a product.
 *
 * <p>{@code storageKey} is the base key only. Responsive derivatives are
 * addressed as {@code {key}-{width}.{format}} and are never enumerated in the
 * database, so adding a width or a format is a pipeline change rather than a
 * migration.
 */
@Entity
@Table(name = "product_image")
public class ProductImage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(name = "storage_key", nullable = false, length = 255)
    private String storageKey;

    @Column(name = "alt_text", nullable = false, length = 255)
    private String altText;

    @Column(name = "content_type", nullable = false, length = 64)
    private String contentType;

    private Integer width;

    private Integer height;

    private Long bytes;

    @Column(name = "display_order", nullable = false)
    private int displayOrder;

    @Column(name = "is_primary", nullable = false)
    private boolean primary;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        this.createdAt = Instant.now();
    }

    protected ProductImage() {
    }

    public static ProductImage of(Product product, String storageKey, String altText,
                                  String contentType, int width, int height, long bytes) {
        ProductImage image = new ProductImage();
        image.product = product;
        image.storageKey = storageKey;
        image.altText = altText;
        image.contentType = contentType;
        image.width = width;
        image.height = height;
        image.bytes = bytes;
        return image;
    }

    public Long getId() { return id; }
    public Product getProduct() { return product; }
    public void setProduct(Product product) { this.product = product; }
    public String getStorageKey() { return storageKey; }
    public void setStorageKey(String storageKey) { this.storageKey = storageKey; }
    public String getAltText() { return altText; }
    public void setAltText(String altText) { this.altText = altText; }
    public String getContentType() { return contentType; }
    public void setContentType(String contentType) { this.contentType = contentType; }
    public Integer getWidth() { return width; }
    public void setWidth(Integer width) { this.width = width; }
    public Integer getHeight() { return height; }
    public void setHeight(Integer height) { this.height = height; }
    public Long getBytes() { return bytes; }
    public void setBytes(Long bytes) { this.bytes = bytes; }
    public int getDisplayOrder() { return displayOrder; }
    public void setDisplayOrder(int displayOrder) { this.displayOrder = displayOrder; }
    public boolean isPrimary() { return primary; }
    public void setPrimary(boolean primary) { this.primary = primary; }
    public Instant getCreatedAt() { return createdAt; }
}
