package com.karvya.store.application.identity.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * A new customer account.
 *
 * <p>The minimum length is 10 rather than the more common 8. Length is the
 * only property that reliably resists offline cracking, and composition rules
 * (a symbol, a digit) mostly push people towards predictable substitutions, so
 * none are imposed here.
 */
public record RegisterRequest(
        @NotBlank(message = "Enter your name")
        @Size(max = 160, message = "Name is too long")
        String fullName,

        @NotBlank(message = "Enter your email address")
        @Email(message = "Enter a valid email address")
        @Size(max = 255, message = "Email address is too long")
        String email,

        @Pattern(regexp = "^$|^[0-9+()\\-\\s]{7,32}$", message = "Enter a valid phone number")
        String phone,

        @NotBlank(message = "Choose a password")
        @Size(min = 10, max = 128, message = "Use at least 10 characters")
        String password
) {
}
