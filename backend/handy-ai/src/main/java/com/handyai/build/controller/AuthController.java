package com.handyai.build.controller;

import com.handyai.build.dto.AuthResponse;
import com.handyai.build.dto.LoginRequest;
import com.handyai.build.dto.RegisterRequest;
import com.handyai.build.dto.UserResponse;
import com.handyai.build.security.CurrentUserProvider;
import com.handyai.build.service.AuthService;
import jakarta.validation.Valid;
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

    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(authService.register(request));
    }

    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request);
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
