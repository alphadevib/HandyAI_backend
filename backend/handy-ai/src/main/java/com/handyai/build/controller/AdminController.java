package com.handyai.build.controller;

import com.handyai.build.dto.UserResponse;
import com.handyai.build.security.CurrentUserProvider;
import com.handyai.build.service.AdminService;
import com.handyai.build.service.AdminStatsService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final AdminService adminService;
    private final AdminStatsService statsService;
    private final CurrentUserProvider currentUserProvider;

    public AdminController(AdminService adminService, AdminStatsService statsService,
                           CurrentUserProvider currentUserProvider) {
        this.adminService = adminService;
        this.statsService = statsService;
        this.currentUserProvider = currentUserProvider;
    }

    /** Live users, registrations, purchase redirects and tracked plans; the dashboard polls it. */
    @GetMapping("/stats")
    public AdminStatsService.Stats stats() {
        requireAdmin();
        return statsService.stats();
    }

    @GetMapping("/organisations")
    public List<UserResponse> list(@RequestParam(required = false) String status) {
        requireAdmin();
        return adminService.organisations(status);
    }

    @PostMapping("/organisations/{userId}/approve")
    public UserResponse approve(@PathVariable Long userId) {
        requireAdmin();
        return adminService.approve(userId);
    }

    @PostMapping("/organisations/{userId}/reject")
    public UserResponse reject(@PathVariable Long userId,
                               @Valid @RequestBody RejectRequest request) {
        requireAdmin();
        return adminService.reject(userId, request.reason());
    }

    private void requireAdmin() {
        statsService.requireAdmin(currentUserProvider.requireUserId());
    }

    public record RejectRequest(@Size(max = 300) String reason) {
    }
}
