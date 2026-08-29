package com.karvya.store.application.identity.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LoginRequest(
        @NotBlank(message = "Enter your email address")
        @Size(max = 255)
        String email,

        @NotBlank(message = "Enter your password")
        @Size(max = 128)
        String password
) {
}
