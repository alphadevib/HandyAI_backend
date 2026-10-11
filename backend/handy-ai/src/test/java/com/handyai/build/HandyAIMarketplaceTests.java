package com.handyai.build;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.handyai.build.domain.ProfessionCatalog;
import com.handyai.build.dto.AuthResponse;
import com.handyai.build.dto.ChatRequest;
import com.handyai.build.dto.ChatResponse;
import com.handyai.build.dto.RegisterRequest;
import com.handyai.build.dto.SubscriptionRequest;
import com.handyai.build.dto.SubscriptionResponse;
import com.handyai.build.exception.BadRequestException;
import com.handyai.build.exception.ConflictException;
import com.handyai.build.exception.ResourceNotFoundException;
import com.handyai.build.service.AdminService;
import com.handyai.build.service.AuthService;
import com.handyai.build.dto.ReviewRequest;
import com.handyai.build.service.ChatService;
import com.handyai.build.service.ReviewService;
import com.handyai.build.service.SubscriptionService;
import com.handyai.build.service.ToolService;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/** The marketplace, subscription, organisation and chat paths. */
@SpringBootTest
class HandyAIMarketplaceTests {

    @Autowired
    private AuthService authService;

    @Autowired
    private AdminService adminService;

    @Autowired
    private ToolService toolService;

    @Autowired
    private SubscriptionService subscriptionService;

    @Autowired
    private ChatService chatService;

    @Autowired
    private ReviewService reviewService;

    @Test
    void offersAtLeastFiftyProfessions() {
        assertThat(ProfessionCatalog.professions()).hasSizeGreaterThanOrEqualTo(50);
        assertThat(ProfessionCatalog.industries()).isNotEmpty();
        assertThat(ProfessionCatalog.mentionedIn("I'm a teacher at a school"))
                .get().extracting(ProfessionCatalog.Entry::name).isEqualTo("Teacher");
    }

    @Test
    void everyToolHasPricesForAllThreeBillingCycles() {
        var tools = toolService.search(null, null, null, "popular", 0, 60, null).content();
        assertThat(tools).allSatisfy(tool -> {
            assertThat(tool.plans()).isNotNull();
            assertThat(tool.plans().quarterly().inr()).isEqualTo(tool.plans().monthly().inr() * 3);
            assertThat(tool.logoUrl()).isEqualTo("/logos/" + tool.slug() + ".png");
        });
    }

    @Test
    void filtersTheMarketplaceByRupeePriceBand() {
        var band = toolService.search(null, null, null, false, 1000d, 2000d, "INR", null,
                "price_asc", 0, 60, null).content();
        assertThat(band).isNotEmpty().allSatisfy(tool ->
                assertThat(tool.plans().monthly().inr()).isBetween(1000L, 2000L));

        var byAnnual = toolService.search(null, null, null, false, null, null, "INR", "ANNUAL",
                "price_asc", 0, 60, null).content();
        assertThat(byAnnual).extracting(tool -> tool.plans().annual().inr()).isSorted();

        var free = toolService.search(null, null, null, true, null, null, "INR", null, "popular",
                0, 60, null).content();
        assertThat(free).isNotEmpty().allSatisfy(tool -> assertThat(tool.hasFreePlan()).isTrue());

        var ranges = toolService.priceRanges("INR");
        assertThat(ranges.ranges()).isNotEmpty();
        assertThat(ranges.ranges().get(ranges.ranges().size() - 1).max())
                .isGreaterThanOrEqualTo(ranges.highestMonthly());
    }

    @Test
    void bestRatedSortsByAverageNotTotalStars() {
        for (int i = 0; i < 3; i++) {
            Long voter = register("voter" + i + "@handyai.test", "Student").user().id();
            reviewService.upsert("rows", voter, new ReviewRequest(3, "fine"));
        }
        Long fan = register("fan@handyai.test", "Student").user().id();
        reviewService.upsert("mem", fan, new ReviewRequest(5, "great"));

        List<String> order = toolService.search(null, null, null, "rating", 0, 60, null).content()
                .stream().map(tool -> tool.slug()).toList();
        // Rows has more stars in total (9 against 5) but a lower average.
        assertThat(order.indexOf("mem")).isLessThan(order.indexOf("rows"));
    }

    @Test
    void tracksSubscriptionsAndTheirRenewals() {
        Long userId = register("subs@handyai.test", "Student").user().id();

        SubscriptionResponse added = subscriptionService.add(userId, new SubscriptionRequest(
                "claude", "ANNUAL", "INR", null, LocalDate.now().minusMonths(13), null, null));
        assertThat(added.amount()).isEqualTo(204 * 88);
        assertThat(added.nextRenewal()).isAfterOrEqualTo(LocalDate.now());

        assertThatThrownBy(() -> subscriptionService.add(userId, new SubscriptionRequest(
                "claude", null, null, null, null, null, null)))
                .isInstanceOf(ConflictException.class);

        SubscriptionResponse monthly = subscriptionService.update(userId, added.id(),
                new SubscriptionRequest(null, "MONTHLY", null, null, null, null, null));
        assertThat(monthly.amount()).isEqualTo(20 * 88);

        var overview = subscriptionService.overview(userId);
        assertThat(overview.activeCount()).isEqualTo(1);
        assertThat(overview.monthlySpend().inr()).isEqualTo(1760);

        subscriptionService.update(userId, added.id(),
                new SubscriptionRequest(null, null, null, null, null, "CANCELLED", null));
        assertThat(subscriptionService.overview(userId).activeCount()).isZero();

        Long otherUser = register("other-subs@handyai.test", "Teacher").user().id();
        assertThatThrownBy(() -> subscriptionService.remove(otherUser, added.id()))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void organisationsAreCheckedThenReviewed() {
        assertThatThrownBy(() -> authService.register(new RegisterRequest("Acme", "ops@gmail.com",
                "supersecret", "Software & SaaS", "ORGANISATION", "Acme Pvt Ltd", "acme.in",
                "27AAPFU0939F1ZV")))
                .isInstanceOf(BadRequestException.class)
                .satisfies(ex -> assertThat(((BadRequestException) ex).getFieldErrors())
                        .containsKey("email"));

        assertThatThrownBy(() -> authService.register(new RegisterRequest("Acme", "ops@acme.in",
                "supersecret", "Software & SaaS", "ORGANISATION", "Acme Pvt Ltd", "acme.in",
                "NOT-AN-ID")))
                .isInstanceOf(BadRequestException.class);

        AuthResponse organisation = authService.register(new RegisterRequest("Acme", "ops@acme.in",
                "supersecret", "Software & SaaS", "ORGANISATION", "Acme Pvt Ltd",
                "https://www.acme.in", "27AAPFU0939F1ZV"));
        assertThat(organisation.user().verificationStatus()).isEqualTo("PENDING");
        assertThat(adminService.organisations("PENDING"))
                .extracting(user -> user.email()).contains("ops@acme.in");

        assertThat(adminService.approve(organisation.user().id()).verificationStatus())
                .isEqualTo("VERIFIED");
    }

    @Test
    void chatRefinesTheLastAnswerInsteadOfStartingOver() {
        ChatResponse first = chatService.reply(
                new ChatRequest("I want to edit my podcast audio", null), null);
        assertThat(first.recommendations()).isNotEmpty();
        assertThat(first.context().goal()).isEqualTo("I want to edit my podcast audio");

        ChatResponse free = chatService.reply(new ChatRequest("only free ones", first.context()),
                null);
        assertThat(free.context().goal()).isEqualTo(first.context().goal());
        assertThat(free.context().freePlanOnly()).isTrue();
        assertThat(free.recommendations()).allSatisfy(pick ->
                assertThat(pick.tool().hasFreePlan()).isTrue());

        ChatResponse more = chatService.reply(new ChatRequest("show me others", first.context()),
                null);
        assertThat(more.recommendations()).extracting(pick -> pick.tool().slug())
                .doesNotContainAnyElementsOf(first.context().shownSlugs());

        ChatResponse budget = chatService.reply(
                new ChatRequest("under 1000 rupees a month", first.context()), null);
        assertThat(budget.context().maxMonthlyPrice()).isEqualTo(1000d);

        ChatResponse profession = chatService.reply(new ChatRequest("I'm a teacher", null), null);
        assertThat(profession.context().profession()).isEqualTo("Teacher");
        assertThat(profession.recommendations()).isNotEmpty();
    }

    private AuthResponse register(String email, String profession) {
        return authService.register(new RegisterRequest("Tester", email, "supersecret",
                profession));
    }

    @SuppressWarnings("unused")
    private static List<String> slugs(ChatResponse response) {
        return response.recommendations().stream().map(pick -> pick.tool().slug()).toList();
    }
}
