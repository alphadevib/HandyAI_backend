package com.handyai.build.dto;

public record AuthResponse(String token, long expiresInSeconds, UserResponse user) {
}
