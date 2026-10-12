package com.handyai.build;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.handyai.build.domain.User;
import com.handyai.build.dto.LoginRequest;
import com.handyai.build.dto.RegisterRequest;
import com.handyai.build.exception.BadRequestException;
import com.handyai.build.repository.UserRepository;
import com.handyai.build.security.PasswordHasher;
import com.handyai.build.service.AuthService;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/** Spring Security over real HTTP: @PreAuthorize on the admin API and BCrypt password storage. */
@SpringBootTest(properties = {
        "handyai.admin.email=owner@handyai.test",
        "handyai.admin.password=owner-secret-1"
})
@AutoConfigureMockMvc
class HandyAIAuthorizationTests {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private AuthService authService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordHasher passwordHasher;

    @Test
    void adminEndpointsAreGuardedByRole() throws Exception {
        mvc.perform(get("/api/admin/stats"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Sign in to continue"));

        String userToken = authService.register(new RegisterRequest("Plain User",
                "plain@handyai.test", "supersecret", "Teacher")).token();
        mvc.perform(get("/api/admin/stats").header("Authorization", "Bearer " + userToken))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/admin/suggestions").header("Authorization", "Bearer " + userToken))
                .andExpect(status().isForbidden());

        String adminToken = authService.login(new LoginRequest("owner@handyai.test",
                "owner-secret-1"), "192.0.2.60").token();
        mvc.perform(get("/api/admin/stats").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());
        mvc.perform(get("/api/admin/suggestions").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());
    }

    @Test
    void publicEndpointsStayOpenWithoutCsrfTokens() throws Exception {
        mvc.perform(get("/api/categories")).andExpect(status().isOk());
        // The browser's CORS preflight still reaches WebConfig's CORS rules.
        mvc.perform(options("/api/auth/login")
                        .header("Origin", "http://localhost:5173")
                        .header("Access-Control-Request-Method", "POST"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:5173"));
        mvc.perform(post("/api/suggestions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"More video tools please\"}"))
                .andExpect(status().isCreated());
    }

    @Test
    void newPasswordsAreBcryptAndOldHashesUpgradeAtSignIn() throws Exception {
        Long id = authService.register(new RegisterRequest("Hash Owner", "hasher@handyai.test",
                "supersecret", "Teacher")).user().id();
        assertThat(userRepository.findById(id).orElseThrow().getPasswordHash()).startsWith("$2a$10$");

        // An account created before the switch still holds a PBKDF2 hash.
        User legacy = userRepository.findById(id).orElseThrow();
        legacy.setPasswordHash(pbkdf2("supersecret"));
        userRepository.save(legacy);

        assertThat(authService.login(new LoginRequest("hasher@handyai.test", "supersecret"),
                "192.0.2.61").token()).isNotBlank();
        String upgraded = userRepository.findById(id).orElseThrow().getPasswordHash();
        assertThat(upgraded).startsWith("$2a$");
        assertThat(passwordHasher.matches("supersecret", upgraded)).isTrue();
        assertThat(passwordHasher.matches("not-the-password", upgraded)).isFalse();
    }

    @Test
    void refusesPasswordsBcryptWouldTruncate() {
        // 30 emoji are 60 UTF-16 characters but 120 bytes, past BCrypt's 72-byte limit.
        String longPassword = "😀".repeat(30);
        assertThatThrownBy(() -> passwordHasher.hash(longPassword))
                .isInstanceOf(BadRequestException.class);
    }

    private static String pbkdf2(String password) throws Exception {
        byte[] salt = new byte[16];
        new SecureRandom().nextBytes(salt);
        PBEKeySpec spec = new PBEKeySpec(password.toCharArray(), salt, 1000, 256);
        byte[] hash = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec)
                .getEncoded();
        return "pbkdf2$1000$" + Base64.getEncoder().encodeToString(salt) + "$"
                + Base64.getEncoder().encodeToString(hash);
    }
}
