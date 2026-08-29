package com.karvya.store.domain.model;

import jakarta.persistence.*;
import java.math.BigDecimal;

/**
 * One purchased line, frozen at the moment of purchase.
 *
 * <p>Name, SKU, unit price and image are copied rather than read through the
 * product association. Renaming a piece or changing its price must not rewrite
 * what a customer was told they bought, and archiving it must not blank the
 * line: the product reference is nullable and set null on delete, so history
 * survives the catalogue.
 */
@Entity
@Table(name = "order_item")
public class OrderItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false)
    private CustomerOrder order;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id")
    private Product product;

    @Column(name = "product_name", nullable = false, length = 200)
    private String productName;

    @Column(name = "product_sku", nullable = false, length = 64)
    private String productSku;

    @Column(name = "unit_price", nullable = false, precision = 10, scale = 2)
    private BigDecimal unitPrice;

    @Column(nullable = false)
    private int quantity;

    @Column(name = "line_total", nullable = false, precision = 10, scale = 2)
    private BigDecimal lineTotal;

    @Column(name = "image_key", length = 255)
    private String imageKey;

    protected OrderItem() {
    }

    static OrderItem snapshotOf(CustomerOrder order, Product product, int quantity) {
        OrderItem item = new OrderItem();
        item.order = order;
        item.product = product;
        item.productName = product.getName();
        item.productSku = product.getSku();
        item.unitPrice = product.getPrice();
        item.quantity = quantity;
        item.lineTotal = product.getPrice().multiply(BigDecimal.valueOf(quantity));
        item.imageKey = product.primaryImage().map(ProductImage::getStorageKey).orElse(null);
        return item;
    }

    public Long getId() { return id; }
    public CustomerOrder getOrder() { return order; }
    public Product getProduct() { return product; }
    public String getProductName() { return productName; }
    public String getProductSku() { return productSku; }
    public BigDecimal getUnitPrice() { return unitPrice; }
    public int getQuantity() { return quantity; }
    public BigDecimal getLineTotal() { return lineTotal; }
    public String getImageKey() { return imageKey; }
}
