package com.karvya.store.domain.model;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "cart_item")
public class CartItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "cart_id", nullable = false)
    private Cart cart;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(nullable = false)
    private int quantity;

    @Column(name = "added_at", nullable = false, updatable = false)
    private Instant addedAt;

    @PrePersist
    void onCreate() {
        this.addedAt = Instant.now();
    }

    protected CartItem() {
    }

    static CartItem of(Cart cart, Product product, int quantity) {
        CartItem item = new CartItem();
        item.cart = cart;
        item.product = product;
        item.quantity = quantity;
        item.addedAt = Instant.now();
        return item;
    }

    public Long getId() { return id; }
    public Cart getCart() { return cart; }
    public Product getProduct() { return product; }
    public int getQuantity() { return quantity; }
    public Instant getAddedAt() { return addedAt; }

    void setQuantity(int quantity) {
        this.quantity = quantity;
    }
}
