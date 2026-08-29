package com.karvya.store.application.identity.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** The three password-related request bodies, grouped since they travel together. */
public final class PasswordRequests {

    private PasswordRequests() {
    }

    public record ChangePassword(
            @NotBlank(message = "Enter your current password")
            String currentPassword,

            @NotBlank(message = "Choose a new password")
            @Size(min = 10, max = 128, message = "Use at least 10 characters")
            String newPassword
    ) {
    }

    public record Forgot(
            @NotBlank(message = "Enter your email address")
            @Email(message = "Enter a valid email address")
            @Size(max = 255)
            String email
    ) {
    }

    public record Reset(
            @NotBlank(message = "The reset link is incomplete")
            String token,

            @NotBlank(message = "Choose a new password")
            @Size(min = 10, max = 128, message = "Use at least 10 characters")
            String newPassword
    ) {
    }
}
