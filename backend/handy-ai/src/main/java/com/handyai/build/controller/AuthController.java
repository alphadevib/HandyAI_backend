package com.handyai.build.controller;

import com.handyai.build.dto.AuthResponse;
import com.handyai.build.dto.LoginRequest;
import com.handyai.build.dto.RegisterRequest;
import com.handyai.build.dto.UserResponse;
import com.handyai.build.security.CurrentUserProvider;
import com.handyai.build.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@Validated
public class AuthController {

    private final AuthService authService;
    private final CurrentUserProvider currentUserProvider;

    public AuthController(AuthService authService, CurrentUserProvider currentUserProvider) {
        this.authService = authService;
        this.currentUserProvider = currentUserProvider;
    }

    /** 201 with a session, or 202 when the email has to be confirmed before signing in. */
    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        AuthResponse response = authService.register(request);
        HttpStatus status = Boolean.TRUE.equals(response.verificationRequired())
                ? HttpStatus.ACCEPTED : HttpStatus.CREATED;
        return ResponseEntity.status(status).body(response);
    }

    @PostMapping("/verify-email")
    public AuthResponse verifyEmail(@Valid @RequestBody VerifyEmailRequest request) {
        return authService.verifyEmail(request.token());
    }

    /** Always 202, whether or not the address has an account waiting for confirmation. */
    @PostMapping("/resend-verification")
    public ResponseEntity<Void> resendVerification(@Valid @RequestBody ResendRequest request) {
        authService.resendVerification(request.email());
        return ResponseEntity.accepted().build();
    }

    public record VerifyEmailRequest(@NotBlank @Size(max = 200) String token) {
    }

    public record ResendRequest(@NotBlank @Email @Size(max = 160) String email) {
    }

    /** The client address comes from the proxy's X-Forwarded-For in production. */
    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest request, HttpServletRequest http) {
        return authService.login(request, http.getRemoteAddr());
    }

    /** Signs out everywhere: tokens issued before this call stop working. */
    @PostMapping("/logout")
    public ResponseEntity<Void> logout() {
        authService.logout(currentUserProvider.requireUserId());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/me")
    public UserResponse me() {
        return authService.profile(currentUserProvider.requireUserId());
    }

    @PutMapping("/me")
    public UserResponse updateMe(@Valid @RequestBody UpdateProfileRequest request) {
        return authService.updateProfile(currentUserProvider.requireUserId(), request.name(),
                request.profession(), request.organisationName(), request.organisationWebsite(),
                request.organisationRegistrationId());
    }

    /** Every field is optional; only the ones sent are changed. */
    public record UpdateProfileRequest(@Size(min = 2, max = 80) String name,
                                       @Size(max = 120) String profession,
                                       @Size(max = 160) String organisationName,
                                       @Size(max = 200) String organisationWebsite,
                                       @Size(max = 40) String organisationRegistrationId) {
    }
}
