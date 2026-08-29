package com.karvya.store.application.enquiry.dto;

import com.karvya.store.domain.model.ContactEnquiry;
import com.karvya.store.domain.model.EnquiryStatus;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.Instant;

public final class EnquiryDtos {

    private EnquiryDtos() {
    }

    /**
     * The contact form.
     *
     * <p>{@code website} is a honeypot: hidden from people, irresistible to
     * the simplest bots. A submission that fills it in is accepted with a
     * normal-looking success response and quietly discarded, because telling a
     * bot it was detected only teaches it to try again differently.
     */
    public record Submit(
            @NotBlank(message = "Enter your name")
            @Size(max = 160)
            String name,

            @NotBlank(message = "Enter your email address")
            @Email(message = "Enter a valid email address")
            @Size(max = 255)
            String email,

            @Pattern(regexp = "^$|^[0-9+()\\-\\s]{7,32}$", message = "Enter a valid phone number")
            String phone,

            @NotBlank(message = "Enter a subject")
            @Size(max = 200)
            String subject,

            @NotBlank(message = "Enter your message")
            @Size(max = 5000, message = "That message is too long")
            String message,

            /** Honeypot. Must stay empty. */
            String website
    ) {
        public boolean looksAutomated() {
            return website != null && !website.isBlank();
        }
    }

    public record View(
            Long id,
            String name,
            String email,
            String phone,
            String subject,
            String message,
            EnquiryStatus status,
            String internalNote,
            String handledBy,
            Instant createdAt,
            Instant updatedAt
    ) {
        public static View from(ContactEnquiry enquiry) {
            return new View(
                    enquiry.getId(), enquiry.getName(), enquiry.getEmail(), enquiry.getPhone(),
                    enquiry.getSubject(), enquiry.getMessage(), enquiry.getStatus(),
                    enquiry.getInternalNote(), enquiry.getHandledBy(),
                    enquiry.getCreatedAt(), enquiry.getUpdatedAt());
        }
    }

    public record StatusChange(
            @NotNull(message = "Choose a status")
            EnquiryStatus status,
            @Size(max = 2000)
            String internalNote
    ) {
    }
}
