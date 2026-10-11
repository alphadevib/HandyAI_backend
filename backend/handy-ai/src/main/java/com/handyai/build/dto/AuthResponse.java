package com.handyai.build.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * A signed-in session ({@code token} + {@code user}), or, when the email must be confirmed first,
 * {@code verificationRequired} with the address the link went to and whether it was sent.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record AuthResponse(String token, Long expiresInSeconds, UserResponse user,
                           Boolean verificationRequired, String pendingEmail, Boolean emailSent) {

    public static AuthResponse signedIn(String token, long expiresInSeconds, UserResponse user) {
        return new AuthResponse(token, expiresInSeconds, user, null, null, null);
    }

    public static AuthResponse pending(String email, boolean emailSent) {
        return new AuthResponse(null, null, null, true, email, emailSent);
    }
}
