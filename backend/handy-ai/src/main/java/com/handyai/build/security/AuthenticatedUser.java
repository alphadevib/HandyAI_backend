package com.handyai.build.security;

/** The caller behind a verified token; {@code tokenVersion} must match the user's current one. */
public record AuthenticatedUser(Long userId, String email, String role, int tokenVersion) {

    public boolean isAdmin() {
        return "ADMIN".equals(role);
    }
}
