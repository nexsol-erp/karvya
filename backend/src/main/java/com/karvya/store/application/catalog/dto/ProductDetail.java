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
        String material,
        String colour,
        String dimensions,
        String careInstructions,
        boolean inStock,
        int stockQuantity,
        boolean featured,
        boolean placeholderContent,
        String categorySlug,
        String categoryName,
        List<ImageRef> images
) {
    public static ProductDetail from(Product p) {
        return new ProductDetail(
                p.getId(),
                p.getSku(),
                p.getSlug(),
                p.getName(),
                p.getShortDescription(),
                p.getDescription(),
                p.getPrice(),
                p.getMaterial(),
                p.getColour(),
                p.getDimensions(),
                p.getCareInstructions(),
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
