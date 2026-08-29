package com.karvya.store.application.admin.dto;

import com.karvya.store.domain.model.Product;
import com.karvya.store.domain.model.ProductImage;
import com.karvya.store.domain.model.ProductStatus;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

public final class AdminProductDtos {

    private AdminProductDtos() {
    }

    public record Row(
            Long id,
            String sku,
            String slug,
            String name,
            String categoryName,
            BigDecimal price,
            int stockQuantity,
            boolean lowStock,
            boolean featured,
            ProductStatus status,
            boolean placeholderContent,
            String primaryImageKey,
            Instant updatedAt
    ) {
        public static Row from(Product p) {
            return new Row(
                    p.getId(), p.getSku(), p.getSlug(), p.getName(),
                    p.getCategory().getName(), p.getPrice(), p.getStockQuantity(),
                    p.isLowStock(), p.isFeatured(), p.getStatus(), p.isPlaceholderContent(),
                    p.primaryImage().map(ProductImage::getStorageKey).orElse(null),
                    p.getUpdatedAt());
        }
    }

    /** One attribute as the admin screen shows it: what to ask for, and the answer. */
    public record AttributeValue(
            Long id,
            String slug,
            String label,
            String helpText,
            String value
    ) {
    }

    public record Image(
            Long id,
            String storageKey,
            String altText,
            Integer width,
            Integer height,
            int displayOrder,
            boolean primary,
            /** Which renditions exist; see ImageRef for why it is not inferred. */
            List<String> formats
    ) {
        public static Image from(ProductImage image) {
            return new Image(image.getId(), image.getStorageKey(), image.getAltText(),
                    image.getWidth(), image.getHeight(), image.getDisplayOrder(), image.isPrimary(),
                    java.util.Arrays.stream(image.getFormats().split(","))
                            .map(String::trim)
                            .filter(format -> !format.isEmpty())
                            .toList());
        }
    }

    public record Detail(
            Long id,
            String sku,
            String slug,
            String name,
            Long categoryId,
            String categoryName,
            String shortDescription,
            String description,
            BigDecimal price,
            String author,
            /** What this category calls that field, or null when it has none. */
            String authorLabel,
            List<AttributeValue> attributes,
            int stockQuantity,
            int lowStockThreshold,
            boolean featured,
            ProductStatus status,
            boolean placeholderContent,
            long version,
            Long vendorId,
            String vendorName,
            BigDecimal vendorPrice,
            String vendorDeliveryTime,
            List<Image> images,
            Instant createdAt,
            Instant updatedAt,
            String updatedBy
    ) {
        public static Detail from(Product p, List<AttributeValue> attributes) {
            return new Detail(
                    p.getId(), p.getSku(), p.getSlug(), p.getName(),
                    p.getCategory().getId(), p.getCategory().getName(),
                    p.getShortDescription(), p.getDescription(), p.getPrice(),
                    p.getAuthor(), p.getCategory().getAuthorLabel(), attributes,
                    p.getStockQuantity(), p.getLowStockThreshold(), p.isFeatured(),
                    p.getStatus(), p.isPlaceholderContent(), p.getVersion(),
                    p.getVendor() == null ? null : p.getVendor().getId(),
                    p.getVendor() == null ? null : p.getVendor().getName(),
                    p.getVendorPrice(),
                    p.getVendorDeliveryTime(),
                    // Sorted here rather than relying on @OrderBy, which only
                    // applies when the collection is loaded from the database.
                    // After a reorder the managed list is still in its old
                    // sequence, so serialising it directly returns an order the
                    // client would show until the next reload.
                    p.getImages().stream()
                            .sorted(java.util.Comparator
                                    .comparingInt(ProductImage::getDisplayOrder)
                                    .thenComparing(ProductImage::getId))
                            .map(Image::from)
                            .toList(),
                    p.getCreatedAt(), p.getUpdatedAt(), p.getUpdatedBy());
        }
    }

    /**
     * Create or update.
     *
     * <p>{@code version} carries the optimistic lock. Sending back the value
     * that was read is what lets the server notice that somebody else saved in
     * between, rather than silently discarding their edit.
     */
    public record Upsert(
            @NotBlank(message = "Enter a SKU")
            @Pattern(regexp = "^[A-Za-z0-9-]{2,64}$",
                    message = "A SKU may use letters, numbers and hyphens only")
            String sku,

            @NotBlank(message = "Enter a name")
            @Size(max = 200)
            String name,

            @Size(max = 200)
            @Pattern(regexp = "^$|^[a-z0-9-]{2,200}$",
                    message = "A slug may use lower-case letters, numbers and hyphens only")
            String slug,

            @NotNull(message = "Choose a category")
            Long categoryId,

            @Size(max = 400)
            String shortDescription,

            String description,

            @NotNull(message = "Enter a price")
            @DecimalMin(value = "0.00", message = "A price cannot be negative")
            @Digits(integer = 8, fraction = 2, message = "Enter a price with at most two decimals")
            BigDecimal price,

            @Size(max = 200) String author,

            /** Keyed by attribute slug; anything blank is cleared. */
            Map<String, String> attributes,

            @Min(value = 0, message = "Stock cannot be negative")
            @Max(value = 100000, message = "That stock figure is too large")
            int stockQuantity,

            @Min(0) @Max(1000)
            int lowStockThreshold,

            boolean featured,

            @NotNull(message = "Choose a status")
            ProductStatus status,

            /** Cleared once a human has reviewed the seeded copy. */
            boolean placeholderContent,

            /** Who supplies it. Null for something the shop makes itself. */
            Long vendorId,

            @DecimalMin(value = "0.00", message = "A supplier price cannot be negative")
            @Digits(integer = 8, fraction = 2, message = "Enter a price with at most two decimals")
            BigDecimal vendorPrice,

            @Size(max = 160, message = "Keep the delivery time short, e.g. '2 to 3 weeks'")
            String vendorDeliveryTime,

            Long version
    ) {
    }

    public record StatusChange(
            @NotNull(message = "Choose a status")
            ProductStatus status
    ) {
    }

    public record ImageOrder(
            @NotEmpty(message = "Send the images in their new order")
            List<Long> imageIds,
            /** Which one leads the gallery. Defaults to the first. */
            Long primaryImageId
    ) {
    }

    public record ImageMeta(
            @NotBlank(message = "Describe the photograph for screen readers")
            @Size(max = 255)
            String altText
    ) {
    }
}
