package com.karvya.store.application.identity.dto;

import com.karvya.store.domain.model.AppUser;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.Instant;

public final class ProfileDtos {

    private ProfileDtos() {
    }

    /**
     * Email is not editable here. Changing the address that identifies an
     * account is an account-recovery concern needing verification of the new
     * address, not a profile field.
     */
    public record Request(
            @NotBlank(message = "Enter your name")
            @Size(max = 160) String fullName,

            @Pattern(regexp = "^$|^[0-9+()\\-\\s]{7,32}$", message = "Enter a valid phone number")
            String phone
    ) {
    }

    public record Response(
            Long id,
            String email,
            String fullName,
            String phone,
            Instant memberSince
    ) {
        public static Response from(AppUser user) {
            return new Response(
                    user.getId(), user.getEmail(), user.getFullName(),
                    user.getPhone(), user.getCreatedAt());
        }
    }
}
