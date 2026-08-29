package com.karvya.store.domain.model;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * A signed-in customer's saved cart. One per account, enforced by a unique
 * constraint on {@code user_id}.
 *
 * <p>Quantities only - no prices. A cart that stored prices would let a stale
 * one dictate what a customer pays; every line is re-priced from the catalogue
 * whenever the cart is read, and again when the order is placed.
 */
@Entity
@Table(name = "cart")
public class Cart {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private AppUser user;

    @OneToMany(mappedBy = "cart", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("addedAt ASC, id ASC")
    private List<CartItem> items = new ArrayList<>();

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

    protected Cart() {
    }

    public static Cart forUser(AppUser user) {
        Cart cart = new Cart();
        cart.user = user;
        return cart;
    }

    public Optional<CartItem> lineFor(Long productId) {
        return items.stream()
                .filter(item -> item.getProduct().getId().equals(productId))
                .findFirst();
    }

    /**
     * Sets the quantity for a product, adding the line if absent and removing
     * it when the quantity reaches zero.
     */
    public void setQuantity(Product product, int quantity) {
        Optional<CartItem> existing = lineFor(product.getId());

        if (quantity <= 0) {
            existing.ifPresent(items::remove);
            touch();
            return;
        }

        if (existing.isPresent()) {
            existing.get().setQuantity(quantity);
        } else {
            items.add(CartItem.of(this, product, quantity));
        }
        touch();
    }

    public void removeProduct(Long productId) {
        items.removeIf(item -> item.getProduct().getId().equals(productId));
        touch();
    }

    public void clear() {
        items.clear();
        touch();
    }

    public boolean isEmpty() {
        return items.isEmpty();
    }

    private void touch() {
        this.updatedAt = Instant.now();
    }

    public Long getId() { return id; }
    public AppUser getUser() { return user; }
    public List<CartItem> getItems() { return items; }
    public Instant getUpdatedAt() { return updatedAt; }
}
