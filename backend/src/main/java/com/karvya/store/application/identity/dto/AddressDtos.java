package com.karvya.store.application.identity.dto;

import com.karvya.store.domain.model.CustomerAddress;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public final class AddressDtos {

    private AddressDtos() {
    }

    public record Request(
            @Size(max = 64) String label,

            @NotBlank(message = "Enter the recipient name")
            @Size(max = 160) String recipientName,

            @NotBlank(message = "Enter a contact number")
            @Pattern(regexp = "^[0-9+()\\-\\s]{7,32}$", message = "Enter a valid phone number")
            String phone,

            @NotBlank(message = "Enter the address")
            @Size(max = 255) String line1,

            @Size(max = 255) String line2,

            @NotBlank(message = "Enter the city")
            @Size(max = 120) String city,

            @NotBlank(message = "Enter the state")
            @Size(max = 120) String state,

            @NotBlank(message = "Enter the postal code")
            @Size(max = 24) String postalCode,

            boolean makeDefault
    ) {
    }

    public record Response(
            Long id,
            String label,
            String recipientName,
            String phone,
            String line1,
            String line2,
            String city,
            String state,
            String postalCode,
            boolean isDefault
    ) {
        public static Response from(CustomerAddress a) {
            return new Response(
                    a.getId(), a.getLabel(), a.getRecipientName(), a.getPhone(),
                    a.getLine1(), a.getLine2(), a.getCity(), a.getState(),
                    a.getPostalCode(), a.isDefaultAddress());
        }
    }
}
