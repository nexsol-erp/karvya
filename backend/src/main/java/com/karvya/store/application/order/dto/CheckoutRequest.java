package com.karvya.store.application.order.dto;

import com.karvya.store.application.cart.dto.CartDtos;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * A checkout submission.
 *
 * <p>Carries no prices and no totals. Line items are product ids and
 * quantities; everything monetary is derived server-side from the catalogue at
 * the moment the order is written.
 *
 * <p>Email is optional, because a guest ordering cash-on-delivery has no need
 * of one. When it is absent no confirmation email is queued, and the
 * confirmation page says so rather than implying one was sent.
 */
public record CheckoutRequest(

        @Valid
        @NotEmpty(message = "Your cart is empty")
        @Size(max = 50, message = "Too many different products in one order")
        List<CartDtos.LineRequest> items,

        /** For a signed-in customer choosing a saved address instead of typing one. */
        Long savedAddressId,

        @NotBlank(message = "Enter the name for delivery")
        @Size(max = 160)
        String deliveryName,

        @NotBlank(message = "Enter a mobile number we can reach you on")
        @Pattern(regexp = "^[0-9+()\\-\\s]{7,32}$", message = "Enter a valid mobile number")
        String deliveryPhone,

        @Email(message = "Enter a valid email address")
        @Size(max = 255)
        String deliveryEmail,

        @NotBlank(message = "Enter the delivery address")
        @Size(max = 255)
        String addressLine1,

        @Size(max = 255)
        String addressLine2,

        @NotBlank(message = "Enter the city")
        @Size(max = 120)
        String city,

        @NotBlank(message = "Enter the state")
        @Size(max = 120)
        String state,

        @NotBlank(message = "Enter the postal code")
        @Size(max = 24)
        String postalCode,

        @Size(max = 2000)
        String deliveryNotes,

        @Size(max = 2000)
        String customerComments,

        @NotBlank(message = "Choose how you would like to pay")
        @Size(max = 48)
        String paymentMethodCode
) {
        public List<CartDtos.LineRequest> items() {
                return items == null ? List.of() : items;
        }
}
