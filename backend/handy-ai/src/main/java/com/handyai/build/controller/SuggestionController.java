package com.handyai.build.controller;

import com.handyai.build.security.CurrentUserProvider;
import com.handyai.build.service.AdminStatsService;
import com.handyai.build.service.SuggestionService;
import com.handyai.build.service.SuggestionService.SuggestionView;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Anyone can send a suggestion; only the admin can read or triage them. */
@RestController
@RequestMapping("/api")
public class SuggestionController {

    private final SuggestionService suggestionService;
    private final AdminStatsService adminStatsService;
    private final CurrentUserProvider currentUserProvider;

    public SuggestionController(SuggestionService suggestionService,
                                AdminStatsService adminStatsService,
                                CurrentUserProvider currentUserProvider) {
        this.suggestionService = suggestionService;
        this.adminStatsService = adminStatsService;
        this.currentUserProvider = currentUserProvider;
    }

    @PostMapping("/suggestions")
    public ResponseEntity<Map<String, Object>> submit(@Valid @RequestBody SubmitRequest request,
                                                      HttpServletRequest http) {
        suggestionService.submit(request.category(), request.message(), request.name(),
                request.email(), request.page(), currentUserProvider.currentUserId().orElse(null),
                http.getRemoteAddr());
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("received", true));
    }

    @GetMapping("/admin/suggestions")
    public List<SuggestionView> list(@RequestParam(required = false) String status) {
        requireAdmin();
        return suggestionService.list(status);
    }

    @PutMapping("/admin/suggestions/{id}")
    public SuggestionView update(@PathVariable Long id, @RequestBody StatusRequest request) {
        requireAdmin();
        return suggestionService.updateStatus(id, request.status());
    }

    private void requireAdmin() {
        adminStatsService.requireAdmin(currentUserProvider.requireUserId());
    }

    public record SubmitRequest(
            String category,
            @NotBlank(message = "Write your suggestion")
            @Size(max = 1000, message = "Keep it under 1000 characters") String message,
            @Size(max = 80) String name,
            @Email(message = "Enter a valid email, or leave it empty") @Size(max = 160) String email,
            @Size(max = 120) String page) {
    }

    public record StatusRequest(String status) {
    }
}
