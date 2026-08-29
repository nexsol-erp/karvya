package com.karvya.store.domain.model;

import jakarta.persistence.*;

import java.io.Serializable;
import java.util.Objects;

/** What one product says for one attribute. */
@Entity
@Table(name = "product_attribute_value")
@IdClass(ProductAttributeValue.Key.class)
public class ProductAttributeValue {

    /**
     * The pair is the identity: a product says one thing per attribute, and the
     * database enforces that rather than trusting the application to.
     */
    public static class Key implements Serializable {
        private Long product;
        private Long attribute;

        public Key() {
        }

        public Key(Long product, Long attribute) {
            this.product = product;
            this.attribute = attribute;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof Key key)) return false;
            return Objects.equals(product, key.product) && Objects.equals(attribute, key.attribute);
        }

        @Override
        public int hashCode() {
            return Objects.hash(product, attribute);
        }
    }

    @Id
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Id
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "attribute_id", nullable = false)
    private ProductAttribute attribute;

    @Column(nullable = false, columnDefinition = "text")
    private String value;

    protected ProductAttributeValue() {
    }

    public static ProductAttributeValue of(Product product, ProductAttribute attribute, String value) {
        ProductAttributeValue entry = new ProductAttributeValue();
        entry.product = product;
        entry.attribute = attribute;
        entry.value = value;
        return entry;
    }

    public Product getProduct() { return product; }
    public ProductAttribute getAttribute() { return attribute; }
    public String getValue() { return value; }
    public void setValue(String value) { this.value = value; }
}
