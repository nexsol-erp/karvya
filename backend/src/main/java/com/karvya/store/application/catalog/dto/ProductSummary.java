package com.karvya.store.application.catalog.dto;

import com.karvya.store.domain.model.Product;
import java.math.BigDecimal;

/** A product as it appears on a catalogue card. */
public record ProductSummary(
        Long id,
        String sku,
        String slug,
        String name,
        String shortDescription,
        BigDecimal price,
        boolean inStock,
        int stockQuantity,
        boolean featured,
        String categorySlug,
        String categoryName,
        ImageRef image
) {
    public static ProductSummary from(Product p) {
        return new ProductSummary(
                p.getId(),
                p.getSku(),
                p.getSlug(),
                p.getName(),
                p.getShortDescription(),
                p.getPrice(),
                p.isInStock(),
                p.getStockQuantity(),
                p.isFeatured(),
                p.getCategory().getSlug(),
                p.getCategory().getName(),
                p.primaryImage().map(ImageRef::from).orElse(null)
        );
    }
}
