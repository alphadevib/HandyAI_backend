package com.handyai.build;

import static org.assertj.core.api.Assertions.assertThat;

import com.handyai.build.dto.AuthResponse;
import com.handyai.build.dto.CategoryResponse;
import com.handyai.build.dto.LoginRequest;
import com.handyai.build.dto.RegisterRequest;
import com.handyai.build.security.AuthenticatedUser;
import com.handyai.build.security.JwtService;
import com.handyai.build.security.TokenVersions;
import com.handyai.build.service.AuthService;
import com.handyai.build.service.CategoryService;
import com.handyai.build.service.PresenceService;
import com.handyai.build.service.ToolService;
import com.handyai.build.support.RecentKeys;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/** Sign-out, abuse limits and the catalogue counts that back the public pages. */
@SpringBootTest
class HandyAISecurityTests {

    @Autowired
    private AuthService authService;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private TokenVersions tokenVersions;

    @Autowired
    private PresenceService presenceService;

    @Autowired
    private CategoryService categoryService;

    @Autowired
    private ToolService toolService;

    /** What the request filter does: a token counts only while its version is the current one. */
    private boolean accepted(String token) {
        return jwtService.verify(token)
                .filter(user -> tokenVersions.current(user.userId()).orElse(-1) == user.tokenVersion())
                .isPresent();
    }

    @Test
    void signingOutInvalidatesEveryEarlierToken() {
        AuthResponse registered = authService.register(new RegisterRequest(
                "Leaver", "leaver@handyai.test", "supersecret", "Designer"));
        String first = registered.token();
        String second = authService.login(new LoginRequest("leaver@handyai.test", "supersecret"),
                "192.0.2.40").token();
        assertThat(accepted(first)).isTrue();
        assertThat(accepted(second)).isTrue();

        authService.logout(registered.user().id());

        assertThat(accepted(first)).isFalse();
        assertThat(accepted(second)).isFalse();
        String fresh = authService.login(new LoginRequest("leaver@handyai.test", "supersecret"),
                "192.0.2.40").token();
        assertThat(accepted(fresh)).isTrue();
        assertThat(jwtService.verify(fresh)).get().extracting(AuthenticatedUser::tokenVersion)
                .isEqualTo(1);
    }

    @Test
    void oneAddressCannotFakeAnArmyOfLiveVisitors() {
        for (int i = 0; i < 20; i++) {
            assertThat(presenceService.heartbeat("bot-visitor-%04d".formatted(i), null, "/",
                    "203.0.113.99")).isTrue();
        }
        assertThat(presenceService.heartbeat("bot-visitor-9999", null, "/", "203.0.113.99"))
                .isFalse();
        // An existing visitor keeps beating, and other addresses are unaffected.
        assertThat(presenceService.heartbeat("bot-visitor-0001", null, "/", "203.0.113.99")).isTrue();
        assertThat(presenceService.heartbeat("real-visitor-0001", null, "/", "198.51.100.77"))
                .isTrue();
    }

    @Test
    void repeatClicksInsideTheWindowCountOnce() {
        RecentKeys keys = new RecentKeys(10);
        assertThat(keys.firstIn("visitor-1|7", Duration.ofMinutes(10))).isTrue();
        assertThat(keys.firstIn("visitor-1|7", Duration.ofMinutes(10))).isFalse();
        assertThat(keys.firstIn("visitor-1|8", Duration.ofMinutes(10))).isTrue();
        assertThat(keys.firstIn("visitor-2|7", Duration.ofMinutes(10))).isTrue();
    }

    @Test
    void categoryCountsAddUpToTheWholeCatalogue() {
        long counted = categoryService.listAll().stream().mapToLong(CategoryResponse::toolCount).sum();
        assertThat(counted).isEqualTo(toolService.stats().toolCount());
    }
}
