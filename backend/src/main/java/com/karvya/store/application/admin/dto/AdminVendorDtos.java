package com.karvya.store.application.admin.dto;

import com.karvya.store.domain.model.Vendor;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.Instant;

/**
 * Suppliers, as the back office sees them.
 *
 * <p>There is deliberately no public counterpart. Everything here is internal:
 * a supplier's contact details and terms are not something a shopper has any
 * business seeing.
 */
public final class AdminVendorDtos {

    private AdminVendorDtos() {
    }

    public record Row(
            Long id,
            String name,
            String contactName,
            String email,
            String phone,
            String deliveryTime,
            boolean active,
            long productCount,
            Instant updatedAt
    ) {
    }

    public record Detail(
            Long id,
            String name,
            String contactName,
            String email,
            String phone,
            String address,
            String deliveryTime,
            String conditions,
            boolean active,
            long productCount,
            Instant createdAt,
            Instant updatedAt,
            String updatedBy
    ) {
        public static Detail from(Vendor v, long productCount) {
            return new Detail(v.getId(), v.getName(), v.getContactName(), v.getEmail(),
                    v.getPhone(), v.getAddress(), v.getDeliveryTime(), v.getConditions(),
                    v.isActive(), productCount, v.getCreatedAt(), v.getUpdatedAt(),
                    v.getUpdatedBy());
        }
    }

    public record Upsert(
            @NotBlank(message = "Enter the supplier's name")
            @Size(max = 200)
            String name,

            @Size(max = 160) String contactName,

            @Email(message = "Enter a valid email address")
            @Size(max = 255)
            String email,

            @Pattern(regexp = "^$|^[0-9+()\\-\\s]{7,32}$", message = "Enter a valid phone number")
            String phone,

            String address,

            @Size(max = 160, message = "Keep the delivery time short, e.g. '2 to 3 weeks'")
            String deliveryTime,

            String conditions,

            boolean active
    ) {
    }
}
