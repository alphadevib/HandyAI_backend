package com.handyai.build;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.handyai.build.domain.UserAiUsage;
import com.handyai.build.dto.AuthResponse;
import com.handyai.build.dto.RegisterRequest;
import com.handyai.build.dto.ToolResponse;
import com.handyai.build.repository.UserAiUsageRepository;
import com.handyai.build.repository.UserRepository;
import com.handyai.build.service.AuthService;
import com.handyai.build.service.PersonalisationService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/** @ManyToMany interests, the @OneToMany category-to-tools side and the @OneToOne AI usage row. */
@SpringBootTest
@AutoConfigureMockMvc
class HandyAIRelationsTests {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private AuthService authService;

    @Autowired
    private PersonalisationService personalisationService;

    @Autowired
    private UserAiUsageRepository usageRepository;

    @Autowired
    private UserRepository userRepository;

    @Test
    void followedCategoriesDriveTheForYouFeed() throws Exception {
        String token = authService.register(new RegisterRequest("Follower", "follower@handyai.test",
                "supersecret", "Content creator")).token();

        mvc.perform(put("/api/me/interests").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"slugs\":[\"video\",\"audio\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].slug").value("audio"))
                .andExpect(jsonPath("$[0].toolCount").isNumber());

        Long userId = userRepository.findByEmailIgnoreCase("follower@handyai.test").orElseThrow().getId();
        List<ToolResponse> feed = personalisationService.forYou(userId, 50);
        assertThat(feed).isNotEmpty()
                .allMatch(tool -> List.of("video", "audio").contains(tool.categorySlug()));
        assertThat(feed).isSortedAccordingTo((a, b) -> Integer.compare(b.popularity(), a.popularity()));

        // Replacing the interests replaces the join rows rather than adding to them.
        personalisationService.setInterests(userId, List.of("coding"));
        assertThat(personalisationService.interests(userId)).singleElement()
                .satisfies(category -> assertThat(category.slug()).isEqualTo("coding"));
    }

    @Test
    void interestsRejectUnknownCategoriesAndGuests() throws Exception {
        AuthResponse auth = authService.register(new RegisterRequest("Picky", "picky@handyai.test",
                "supersecret", "Teacher"));
        mvc.perform(put("/api/me/interests").header("Authorization", "Bearer " + auth.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"slugs\":[\"video\",\"time-travel\"]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Unknown category: time-travel"));
        mvc.perform(get("/api/me/for-you")).andExpect(status().isUnauthorized());
    }

    @Test
    void aiUsageIsOneRowPerUserSharingItsId() {
        Long userId = authService.register(new RegisterRequest("Chatty", "chatty@handyai.test",
                "supersecret", "Marketer")).user().id();
        assertThat(personalisationService.aiUsage(userId).replies()).isZero();

        personalisationService.recordAiUsage(userId, 900, 150, 0.0066);
        personalisationService.recordAiUsage(userId, 1100, 250, 0.0094);

        UserAiUsage row = usageRepository.findById(userId).orElseThrow();
        assertThat(row.getUserId()).isEqualTo(userId);
        PersonalisationService.AiUsageView usage = personalisationService.aiUsage(userId);
        assertThat(usage.replies()).isEqualTo(2);
        assertThat(usage.inputTokens()).isEqualTo(2000);
        assertThat(usage.outputTokens()).isEqualTo(400);
        assertThat(usage.costUsd()).isCloseTo(0.016, org.assertj.core.data.Offset.offset(1e-9));
        assertThat(usage.lastUsedAt()).isNotNull();
    }
}
