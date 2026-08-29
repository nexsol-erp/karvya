package com.karvya.store.application.admin.dto;

import com.karvya.store.domain.model.ProductAttribute;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.Instant;

/** Fields an administrator decided a product should have. Back office only. */
public final class AdminAttributeDtos {

    private AdminAttributeDtos() {
    }

    public record Row(
            Long id,
            String label,
            String slug,
            Long categoryId,
            String categoryName,
            String helpText,
            int displayOrder,
            boolean active,
            long productCount
    ) {
        public static Row from(ProductAttribute a, long productCount) {
            return new Row(a.getId(), a.getLabel(), a.getSlug(),
                    a.getCategory() == null ? null : a.getCategory().getId(),
                    a.getCategory() == null ? null : a.getCategory().getName(),
                    a.getHelpText(), a.getDisplayOrder(), a.isActive(), productCount);
        }
    }

    public record Detail(
            Long id,
            String label,
            String slug,
            Long categoryId,
            String categoryName,
            String helpText,
            int displayOrder,
            boolean active,
            long productCount,
            Instant updatedAt,
            String updatedBy
    ) {
        public static Detail from(ProductAttribute a, long productCount) {
            return new Detail(a.getId(), a.getLabel(), a.getSlug(),
                    a.getCategory() == null ? null : a.getCategory().getId(),
                    a.getCategory() == null ? null : a.getCategory().getName(),
                    a.getHelpText(), a.getDisplayOrder(), a.isActive(), productCount,
                    a.getUpdatedAt(), a.getUpdatedBy());
        }
    }

    public record Upsert(
            @NotBlank(message = "Enter a label")
            @Size(max = 80)
            String label,

            /**
             * Optional on create, where it is derived from the label. Never
             * changed afterwards: it is what the recorded values are keyed by,
             * so editing it would orphan every answer already given.
             */
            @Size(max = 80)
            @Pattern(regexp = "^$|^[a-z0-9-]{2,80}$",
                    message = "A slug may use lower-case letters, numbers and hyphens only")
            String slug,

            /** Null applies it to every product, whatever the category. */
            Long categoryId,

            @Size(max = 255)
            String helpText,

            int displayOrder,

            boolean active
    ) {
    }
}
