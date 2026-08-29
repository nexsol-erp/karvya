package com.karvya.store.application.catalog.dto;

import com.karvya.store.domain.model.Product;
import java.math.BigDecimal;
import java.util.List;

/**
 * A product as it appears on its own page.
 *
 * <p>{@code placeholderContent} is exposed so the storefront can flag copy the
 * business has not yet reviewed. It is honest rather than hidden: seeded text
 * should be visibly provisional until someone replaces it.
 */
public record ProductDetail(
        Long id,
        String sku,
        String slug,
        String name,
        String shortDescription,
        String description,
        BigDecimal price,
        /** Null for anything that is not written by someone. */
        String author,
        /** What to call it here - "Author", "Artist" - or null to hide it. */
        String authorLabel,
        /** Whatever the administrator decided this kind of product has. */
        List<Attribute> attributes,
        boolean inStock,
        int stockQuantity,
        boolean featured,
        boolean placeholderContent,
        String categorySlug,
        String categoryName,
        List<ImageRef> images
) {
    /** One label and value, already in the order it should be shown. */
    public record Attribute(String label, String value) {
    }

    public static ProductDetail from(Product p, List<Attribute> attributes) {
        return new ProductDetail(
                p.getId(),
                p.getSku(),
                p.getSlug(),
                p.getName(),
                p.getShortDescription(),
                p.getDescription(),
                p.getPrice(),
                p.getAuthor(),
                p.getCategory().getAuthorLabel(),
                attributes,
                p.isInStock(),
                p.getStockQuantity(),
                p.isFeatured(),
                p.isPlaceholderContent(),
                p.getCategory().getSlug(),
                p.getCategory().getName(),
                p.getImages().stream().map(ImageRef::from).toList()
        );
    }
}
