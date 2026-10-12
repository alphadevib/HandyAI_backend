package com.handyai.build.controller;

import com.handyai.build.dto.UserResponse;
import com.handyai.build.service.AdminService;
import com.handyai.build.service.AdminStatsService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** The admin dashboard. Guests get a 401 and signed-in non-admins a 403 (GlobalExceptionHandler). */
@RestController
@RequestMapping("/api/admin")
@PreAuthorize("hasRole('ADMIN')")
public class AdminController {

    private final AdminService adminService;
    private final AdminStatsService statsService;

    public AdminController(AdminService adminService, AdminStatsService statsService) {
        this.adminService = adminService;
        this.statsService = statsService;
    }

    /** Live users, registrations, purchase redirects and tracked plans; the dashboard polls it. */
    @GetMapping("/stats")
    public AdminStatsService.Stats stats() {
        return statsService.stats();
    }

    @GetMapping("/organisations")
    public List<UserResponse> list(@RequestParam(required = false) String status) {
        return adminService.organisations(status);
    }

    @PostMapping("/organisations/{userId}/approve")
    public UserResponse approve(@PathVariable Long userId) {
        return adminService.approve(userId);
    }

    @PostMapping("/organisations/{userId}/reject")
    public UserResponse reject(@PathVariable Long userId,
                               @Valid @RequestBody RejectRequest request) {
        return adminService.reject(userId, request.reason());
    }

    public record RejectRequest(@Size(max = 300) String reason) {
    }
}
