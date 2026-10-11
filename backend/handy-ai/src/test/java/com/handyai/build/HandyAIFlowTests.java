package com.handyai.build;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.handyai.build.dto.AuthResponse;
import com.handyai.build.dto.LoginRequest;
import com.handyai.build.dto.RecommendationRequest;
import com.handyai.build.dto.RecommendationResponse;
import com.handyai.build.dto.RegisterRequest;
import com.handyai.build.dto.ReviewRequest;
import com.handyai.build.exception.BadRequestException;
import com.handyai.build.exception.ConflictException;
import com.handyai.build.exception.UnauthorizedException;
import com.handyai.build.security.JwtService;
import com.handyai.build.service.AuthService;
import com.handyai.build.service.FavoriteService;
import com.handyai.build.service.RecommendationService;
import com.handyai.build.service.ReviewService;
import com.handyai.build.service.ToolService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/** End-to-end exercise of the paths the frontend actually calls. */
@SpringBootTest
class HandyAIFlowTests {

    @Autowired
    private AuthService authService;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private ToolService toolService;

    @Autowired
    private FavoriteService favoriteService;

    @Autowired
    private ReviewService reviewService;

    @Autowired
    private RecommendationService recommendationService;

    @Test
    void seedsTheCatalogue() {
        assertThat(toolService.stats().toolCount()).isGreaterThan(20);
        assertThat(toolService.stats().categoryCount()).isGreaterThan(5);
        assertThat(toolService.featured(6, null)).isNotEmpty();
    }

    @Test
    void registersLogsInAndIssuesAUsableToken() {
        AuthResponse registered = authService.register(new RegisterRequest(
                "Flow Tester", "flow@handyai.test", "supersecret", "Developer"));

        assertThat(jwtService.verify(registered.token()))
                .get()
                .satisfies(user -> assertThat(user.userId()).isEqualTo(registered.user().id()));

        AuthResponse loggedIn = authService.login(
                new LoginRequest("FLOW@handyai.test", "supersecret"));
        assertThat(loggedIn.user().id()).isEqualTo(registered.user().id());

        assertThatThrownBy(() -> authService.login(
                new LoginRequest("flow@handyai.test", "wrong-password")))
                .isInstanceOf(UnauthorizedException.class);

        assertThatThrownBy(() -> authService.register(new RegisterRequest(
                "Someone Else", "flow@handyai.test", "supersecret", null)))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void savingAndRatingAToolUpdatesWhatTheUserSees() {
        Long userId = authService.register(new RegisterRequest(
                "Rater", "rater@handyai.test", "supersecret", "Editor")).user().id();

        assertThat(favoriteService.toggle(userId, "descript")).isTrue();
        assertThat(favoriteService.listForUser(userId))
                .extracting("slug")
                .containsExactly("descript");

        reviewService.upsert("descript", userId, new ReviewRequest(5, "Saved me hours"));
        // Re-rating replaces the existing row instead of adding a second one.
        reviewService.upsert("descript", userId, new ReviewRequest(4, "Still good"));

        assertThat(toolService.getBySlug("descript", userId).rating()).isEqualTo(4.0);
        assertThat(toolService.getBySlug("descript", userId).ratingCount()).isEqualTo(1);
        assertThat(toolService.getBySlug("descript", userId).favorite()).isTrue();

        assertThat(favoriteService.toggle(userId, "descript")).isFalse();
        assertThat(favoriteService.listForUser(userId)).isEmpty();
    }

    @Test
    void recommendsToolsThatFitTheDescribedJob() {
        RecommendationResponse response = recommendationService.recommend(
                new RecommendationRequest(
                        "turn my long client calls into a summary and follow up email",
                        "Consultant", List.of(), "ALL", 5),
                null);

        assertThat(response.recommendations()).isNotEmpty();
        assertThat(response.recommendations())
                .allSatisfy(entry -> assertThat(entry.reasons()).isNotEmpty());
        assertThat(response.recommendations())
                .extracting(entry -> entry.tool().categorySlug())
                .contains("meetings");
    }

    @Test
    void aStatedBudgetFiltersTheRecommendations() {
        RecommendationResponse freeOnly = recommendationService.recommend(
                new RecommendationRequest("write and edit code", null, List.of(), "FREE", 5), null);

        assertThat(freeOnly.recommendations())
                .isNotEmpty()
                .allSatisfy(entry -> assertThat(entry.tool().pricingModel()).isEqualTo("FREE"));
    }

    @Test
    void anEmptyRequestStillReturnsTheStrongestPicks() {
        RecommendationResponse response = recommendationService.recommend(
                new RecommendationRequest(null, null, null, null, null), null);

        assertThat(response.recommendations()).hasSize(6);
        assertThat(response.recommendations())
                .extracting(entry -> entry.tool().name())
                .contains("Claude");
    }

    @Test
    void aBudgetNeverPadsTheListWithUnrelatedTools() {
        RecommendationResponse freeLogo = recommendationService.recommend(
                new RecommendationRequest("design a logo", null, List.of(), "FREE", 3), null);

        assertThat(freeLogo.recommendations())
                .allSatisfy(entry -> assertThat(entry.tool().categorySlug()).isIn("design", "image"));
        if (freeLogo.recommendations().isEmpty()) {
            assertThat(freeLogo.summary()).startsWith("Nothing in the catalogue fits");
        }
    }

    @Test
    void aDescriptionThatMatchesNothingSaysSo() {
        RecommendationResponse response = recommendationService.recommend(
                new RecommendationRequest("asdkjh qwe", null, List.of(), null, 3), null);

        assertThat(response.recommendations()).isEmpty();
        assertThat(response.summary()).startsWith("Nothing in the catalogue fits");
    }

    @Test
    void anUnknownBudgetIsRejected() {
        assertThatThrownBy(() -> recommendationService.recommend(
                new RecommendationRequest("edit a podcast", null, List.of(), "WRONG", 3), null))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void searchFiltersByKeywordAndPricing() {
        var freeTools = toolService.search(null, null, "FREE", "popular", 0, 20, null);
        assertThat(freeTools.content())
                .isNotEmpty()
                .allSatisfy(tool -> assertThat(tool.pricingModel()).isEqualTo("FREE"));

        var codingTools = toolService.search("code", "coding", null, "rating", 0, 20, null);
        assertThat(codingTools.content())
                .isNotEmpty()
                .allSatisfy(tool -> assertThat(tool.categorySlug()).isEqualTo("coding"));
    }
}
