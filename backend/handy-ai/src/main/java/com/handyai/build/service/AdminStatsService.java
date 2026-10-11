package com.handyai.build.service;

import com.handyai.build.domain.AccountType;
import com.handyai.build.domain.Role;
import com.handyai.build.domain.Subscription;
import com.handyai.build.domain.User;
import com.handyai.build.domain.VerificationStatus;
import com.handyai.build.exception.ForbiddenException;
import com.handyai.build.repository.OutboundClickRepository;
import com.handyai.build.repository.SubscriptionRepository;
import com.handyai.build.repository.SuggestionRepository;
import com.handyai.build.domain.Suggestion;
import com.handyai.build.repository.UserRepository;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** The numbers on the admin dashboard, computed fresh on every poll. */
@Service
public class AdminStatsService {

    /** "Today" means the day in India, where the platform's admin works. */
    private static final ZoneId ZONE = ZoneId.of("Asia/Kolkata");

    private final UserRepository userRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final OutboundClickRepository clickRepository;
    private final PresenceService presenceService;
    private final SuggestionRepository suggestionRepository;

    public AdminStatsService(UserRepository userRepository,
                             SubscriptionRepository subscriptionRepository,
                             OutboundClickRepository clickRepository,
                             PresenceService presenceService,
                             SuggestionRepository suggestionRepository) {
        this.userRepository = userRepository;
        this.subscriptionRepository = subscriptionRepository;
        this.clickRepository = clickRepository;
        this.presenceService = presenceService;
        this.suggestionRepository = suggestionRepository;
    }

    public record Users(long total, long today, long last7Days, long individuals,
                        long organisations, long pendingOrganisations) {
    }

    public record Redirects(long total, long today, long last24Hours, long bySignedInUsers,
                            List<TopTool> topTools) {
    }

    public record TopTool(String name, String slug, long clicks) {
    }

    public record TrackedSubscriptions(long active, long cancelled, long addedToday) {
    }

    public record Activity(String type, String text, Instant at) {
    }

    public record Stats(Instant generatedAt, PresenceService.LiveCount live, Users users,
                        Redirects redirects, TrackedSubscriptions subscriptions,
                        long newSuggestions, List<Activity> recent) {
    }

    /**
     * The admin check reads the role from the database rather than the token, so an account that
     * lost the admin role is locked out at once instead of when its token expires.
     */
    @Transactional(readOnly = true)
    public void requireAdmin(Long userId) {
        boolean admin = userRepository.findById(userId)
                .map(user -> user.getRole() == Role.ADMIN)
                .orElse(false);
        if (!admin) {
            throw new ForbiddenException("Only the HandyAI admin can see this");
        }
    }

    @Transactional(readOnly = true)
    public Stats stats() {
        Instant now = Instant.now();
        Instant startOfToday = LocalDate.now(ZONE).atStartOfDay(ZONE).toInstant();

        Users users = new Users(
                userRepository.count(),
                userRepository.countByCreatedAtAfter(startOfToday),
                userRepository.countByCreatedAtAfter(now.minus(Duration.ofDays(7))),
                userRepository.countByAccountType(AccountType.INDIVIDUAL),
                userRepository.countByAccountType(AccountType.ORGANISATION),
                userRepository.findByAccountTypeAndVerificationStatusOrderByVerificationSubmittedAtAsc(
                        AccountType.ORGANISATION, VerificationStatus.PENDING).size());

        List<TopTool> topTools = clickRepository
                .topTools(now.minus(Duration.ofDays(30)), PageRequest.of(0, 5)).stream()
                .map(row -> new TopTool((String) row[0], (String) row[1], (Long) row[2]))
                .toList();
        Redirects redirects = new Redirects(
                clickRepository.count(),
                clickRepository.countByCreatedAtAfter(startOfToday),
                clickRepository.countByCreatedAtAfter(now.minus(Duration.ofHours(24))),
                clickRepository.countByUserIdIsNotNull(),
                topTools);

        TrackedSubscriptions subscriptions = new TrackedSubscriptions(
                subscriptionRepository.countByStatus(Subscription.Status.ACTIVE),
                subscriptionRepository.countByStatus(Subscription.Status.CANCELLED),
                subscriptionRepository.countByCreatedAtAfter(startOfToday));

        return new Stats(now, presenceService.live(), users, redirects, subscriptions,
                suggestionRepository.countByStatus(Suggestion.Status.NEW), recent());
    }

    /** The latest sign-ups, redirects and tracked plans, newest first. */
    private List<Activity> recent() {
        List<Activity> items = new ArrayList<>();
        for (User user : userRepository.findTop8ByOrderByCreatedAtDesc()) {
            String who = user.getAccountType() == AccountType.ORGANISATION
                    && user.getOrganisationName() != null
                    ? user.getOrganisationName() + " (organisation)" : user.getName();
            items.add(new Activity("signup", who + " registered", user.getCreatedAt()));
        }
        clickRepository.findTop10ByOrderByCreatedAtDesc().forEach(click -> items.add(new Activity(
                "redirect",
                (click.getUserId() == null ? "A guest" : "A member") + " went to "
                        + click.getTool().getName() + " to buy or try it",
                click.getCreatedAt())));
        subscriptionRepository.findTop8ByOrderByCreatedAtDesc().forEach(sub -> items.add(
                new Activity("subscription", sub.getUser().getName() + " started tracking "
                        + sub.getTool().getName(), sub.getCreatedAt())));
        return items.stream()
                .sorted(Comparator.comparing(Activity::at).reversed())
                .limit(12)
                .toList();
    }
}
