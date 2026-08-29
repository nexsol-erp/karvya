package com.karvya.store.domain.model;

import jakarta.persistence.*;
import org.hibernate.annotations.BatchSize;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * A sellable catalogue item.
 *
 * <p>{@code version} is an optimistic lock guarding administrator edits against
 * lost updates. It is deliberately <em>not</em> the mechanism protecting stock
 * during checkout: that path takes a pessimistic row lock instead, because two
 * concurrent buyers should queue rather than one of them failing outright.
 */
@Entity
@Table(name = "product")
public class Product extends Auditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 64, unique = true)
    private String sku;

    @Column(nullable = false, length = 200, unique = true)
    private String slug;

    @Column(nullable = false, length = 200)
    private String name;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "category_id", nullable = false)
    private Category category;

    /**
     * Who supplies this piece. Optional: the seeded catalogue has none, and a
     * shop may make something itself.
     *
     * <p>Lazy, and never reached from a storefront query. The supplier and the
     * price paid to them are the shop's business, not the shopper's.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "vendor_id")
    private Vendor vendor;

    /** What the shop pays for it, as opposed to what it sells for. */
    @Column(name = "vendor_price", precision = 10, scale = 2)
    private BigDecimal vendorPrice;

    /** Overrides the vendor's usual lead time when this piece differs. */
    @Column(name = "vendor_delivery_time", length = 160)
    private String vendorDeliveryTime;

    @Column(name = "short_description", length = 400)
    private String shortDescription;

    @Column(columnDefinition = "text")
    private String description;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal price;

    /**
     * Kept as a column rather than an attribute, unlike everything else that
     * varies by what is being sold.
     *
     * <p>It is what customers search and browse by, so it needs an index and a
     * place on the product card, and neither is reasonable against a generic
     * key-value table. Null for anything that does not have one.
     */
    @Column(length = 200)
    private String author;

    @Column(name = "stock_quantity", nullable = false)
    private int stockQuantity;

    @Column(name = "low_stock_threshold", nullable = false)
    private int lowStockThreshold = 3;

    @Column(nullable = false)
    private boolean featured;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private ProductStatus status = ProductStatus.DRAFT;

    /** Seeded copy that has not yet been reviewed by the business. */
    @Column(name = "placeholder_content", nullable = false)
    private boolean placeholderContent;

    @Version
    @Column(nullable = false)
    private long version;

    /**
     * Batched rather than join-fetched: a fetch join against a collection forces
     * Hibernate to paginate in memory, which defeats the point of a paged
     * catalogue. One extra query per batch of products is the cheaper trade.
     */
    @OneToMany(mappedBy = "product", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("displayOrder ASC, id ASC")
    @BatchSize(size = 32)
    private List<ProductImage> images = new ArrayList<>();

    protected Product() {
    }

    /**
     * A new product, created as a draft with a zero price so it cannot be sold
     * before somebody has finished filling it in.
     */
    public static Product createDraft(String sku, String slug, String name, Category category) {
        Product product = new Product();
        product.sku = sku;
        product.slug = slug;
        product.name = name;
        product.category = category;
        product.price = BigDecimal.ZERO;
        product.status = ProductStatus.DRAFT;
        return product;
    }

    // ---- domain behaviour -------------------------------------------------

    public boolean isInStock() {
        return stockQuantity > 0;
    }

    public boolean isLowStock() {
        return stockQuantity <= lowStockThreshold;
    }

    public boolean isPurchasable() {
        return status.isPubliclyVisible() && isInStock();
    }

    /**
     * Reduces stock by {@code quantity}, refusing to go negative. Callers must
     * already hold a write lock on this row.
     */
    public void reserveStock(int quantity) {
        if (quantity <= 0) {
            throw new IllegalArgumentException("quantity must be positive");
        }
        if (quantity > stockQuantity) {
            throw new IllegalStateException(
                    "insufficient stock for " + sku + ": requested " + quantity
                            + ", available " + stockQuantity);
        }
        this.stockQuantity -= quantity;
    }

    public void restoreStock(int quantity) {
        if (quantity <= 0) {
            throw new IllegalArgumentException("quantity must be positive");
        }
        this.stockQuantity += quantity;
    }

    public Optional<ProductImage> primaryImage() {
        return images.stream()
                .filter(ProductImage::isPrimary)
                .findFirst()
                .or(() -> images.stream().min(Comparator.comparingInt(ProductImage::getDisplayOrder)));
    }

    public void addImage(ProductImage image) {
        images.add(image);
        image.setProduct(this);
    }

    // ---- accessors --------------------------------------------------------

    public Long getId() { return id; }
    public String getSku() { return sku; }
    public void setSku(String sku) { this.sku = sku; }
    public String getSlug() { return slug; }
    public void setSlug(String slug) { this.slug = slug; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public Category getCategory() { return category; }
    public void setCategory(Category category) { this.category = category; }
    public Vendor getVendor() { return vendor; }
    public void setVendor(Vendor vendor) { this.vendor = vendor; }
    public BigDecimal getVendorPrice() { return vendorPrice; }
    public void setVendorPrice(BigDecimal vendorPrice) { this.vendorPrice = vendorPrice; }
    public String getVendorDeliveryTime() { return vendorDeliveryTime; }
    public void setVendorDeliveryTime(String v) { this.vendorDeliveryTime = v; }
    public String getShortDescription() { return shortDescription; }
    public void setShortDescription(String v) { this.shortDescription = v; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public BigDecimal getPrice() { return price; }
    public void setPrice(BigDecimal price) { this.price = price; }
    public String getAuthor() { return author; }
    public void setAuthor(String author) { this.author = author; }
    public int getStockQuantity() { return stockQuantity; }
    public void setStockQuantity(int stockQuantity) { this.stockQuantity = stockQuantity; }
    public int getLowStockThreshold() { return lowStockThreshold; }
    public void setLowStockThreshold(int v) { this.lowStockThreshold = v; }
    public boolean isFeatured() { return featured; }
    public void setFeatured(boolean featured) { this.featured = featured; }
    public ProductStatus getStatus() { return status; }
    public void setStatus(ProductStatus status) { this.status = status; }
    public boolean isPlaceholderContent() { return placeholderContent; }
    public void setPlaceholderContent(boolean v) { this.placeholderContent = v; }
    public long getVersion() { return version; }
    public List<ProductImage> getImages() { return images; }
}
