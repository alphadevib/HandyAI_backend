package com.handyai.build;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.handyai.build.config.AdminAccountInitializer;
import com.handyai.build.domain.Role;
import com.handyai.build.domain.User;
import com.handyai.build.dto.LoginRequest;
import com.handyai.build.dto.RegisterRequest;
import com.handyai.build.exception.ForbiddenException;
import com.handyai.build.exception.TooManyRequestsException;
import com.handyai.build.exception.UnauthorizedException;
import com.handyai.build.repository.UserRepository;
import com.handyai.build.service.AdminStatsService;
import com.handyai.build.service.AuthService;
import com.handyai.build.exception.BadRequestException;
import com.handyai.build.service.PresenceService;
import com.handyai.build.service.SuggestionService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/** The single admin account and the live dashboard numbers. */
@SpringBootTest(properties = {
        "handyai.admin.email=owner@handyai.test",
        "handyai.admin.password=owner-secret-1"
})
class HandyAIAdminTests {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private AuthService authService;

    @Autowired
    private AdminStatsService statsService;

    @Autowired
    private PresenceService presenceService;

    @Autowired
    private AdminAccountInitializer adminInitializer;

    @Autowired
    private SuggestionService suggestionService;

    @Test
    void onlyTheConfiguredEmailIsAdmin() throws Exception {
        User admin = userRepository.findByEmailIgnoreCase("owner@handyai.test").orElseThrow();
        assertThat(admin.getRole()).isEqualTo(Role.ADMIN);
        assertThat(authService.login(new LoginRequest("owner@handyai.test", "owner-secret-1"))
                .user().role()).isEqualTo("ADMIN");

        // Someone else holding the role is demoted on the next start.
        Long intruder = authService.register(new RegisterRequest("Intruder", "intruder@handyai.test",
                "supersecret", "Student")).user().id();
        User other = userRepository.findById(intruder).orElseThrow();
        other.setRole(Role.ADMIN);
        userRepository.save(other);
        adminInitializer.run(null);
        assertThat(userRepository.findById(intruder).orElseThrow().getRole()).isEqualTo(Role.USER);

        assertThatThrownBy(() -> statsService.requireAdmin(intruder))
                .isInstanceOf(ForbiddenException.class);
        statsService.requireAdmin(admin.getId());
    }

    @Test
    void locksSignInAfterRepeatedWrongPasswords() {
        authService.register(new RegisterRequest("Guessed", "guessed@handyai.test", "supersecret",
                "Teacher"));
        for (int attempt = 0; attempt < 5; attempt++) {
            assertThatThrownBy(() -> authService.login(
                    new LoginRequest("guessed@handyai.test", "wrong-guess")))
                    .isInstanceOf(UnauthorizedException.class);
        }
        // Locked now, even with the right password.
        assertThatThrownBy(() -> authService.login(
                new LoginRequest("guessed@handyai.test", "supersecret")))
                .isInstanceOf(TooManyRequestsException.class)
                .hasMessageContaining("minutes");
    }

    @Test
    void suggestionsAreTakenFromAnyoneAndShownOnlyToTheAdmin() {
        suggestionService.submit("TOOL_REQUEST", "Please add Canva to the design category", null,
                "guest@example.com", "/marketplace", null, "203.0.113.7");
        assertThat(suggestionService.list("NEW"))
                .anySatisfy(item -> assertThat(item.message()).contains("Canva"));
        assertThat(statsService.stats().newSuggestions()).isGreaterThanOrEqualTo(1);

        assertThatThrownBy(() -> suggestionService.submit(null, "hi", null, null, null, null, "x"))
                .isInstanceOf(BadRequestException.class);

        // A sixth message from one address inside ten minutes is refused.
        for (int i = 0; i < 4; i++) {
            suggestionService.submit("IDEA", "Idea number " + i, null, null, "/", null, "198.51.100.9");
        }
        suggestionService.submit("IDEA", "Idea number five", null, null, "/", null, "198.51.100.9");
        assertThatThrownBy(() -> suggestionService.submit("IDEA", "One idea too many", null, null,
                "/", null, "198.51.100.9")).isInstanceOf(TooManyRequestsException.class);
    }

    @Test
    void countsLiveVisitorsAndRegistrations() {
        long before = statsService.stats().users().total();
        authService.register(new RegisterRequest("Counter", "counter@handyai.test", "supersecret",
                "Teacher"));

        assertThat(presenceService.heartbeat("visitor-guest-0001", null, "/")).isTrue();
        assertThat(presenceService.heartbeat("visitor-member-001", 1L, "/marketplace")).isTrue();
        assertThat(presenceService.heartbeat("bad id!", null, "/")).isFalse();

        AdminStatsService.Stats stats = statsService.stats();
        assertThat(stats.users().total()).isEqualTo(before + 1);
        assertThat(stats.users().today()).isGreaterThanOrEqualTo(1);
        assertThat(stats.live().total()).isGreaterThanOrEqualTo(2);
        assertThat(stats.live().signedIn()).isGreaterThanOrEqualTo(1);
    }
}
