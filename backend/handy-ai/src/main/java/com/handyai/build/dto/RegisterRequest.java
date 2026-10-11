package com.handyai.build.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Sign-up for a person or an organisation. The organisation fields are only read when
 * {@code accountType} is ORGANISATION, and are then checked by the organisation verifier.
 */
public record RegisterRequest(
        @NotBlank(message = "Name is required")
        @Size(min = 2, max = 80, message = "Name must be between 2 and 80 characters")
        String name,

        @NotBlank(message = "Email is required")
        @Email(message = "Enter a valid email address")
        @Size(max = 160)
        String email,

        @NotBlank(message = "Password is required")
        @Size(min = 8, max = 72, message = "Password must be at least 8 characters")
        String password,

        @NotBlank(message = "Choose your profession so we can suggest the right tools")
        @Size(max = 120)
        String profession,

        String accountType,

        @Size(max = 160)
        String organisationName,

        @Size(max = 200)
        String organisationWebsite,

        @Size(max = 40)
        String organisationRegistrationId) {

    /** The short form used by tests and older clients: an individual account. */
    public RegisterRequest(String name, String email, String password, String profession) {
        this(name, email, password, profession, null, null, null, null);
    }
}
