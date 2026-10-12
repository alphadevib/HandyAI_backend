package com.handyai.build.service;

import com.handyai.build.domain.AiTool;
import com.handyai.build.domain.Category;
import com.handyai.build.domain.User;
import com.handyai.build.domain.UserAiUsage;
import com.handyai.build.dto.CategoryResponse;
import com.handyai.build.dto.ToolResponse;
import com.handyai.build.exception.BadRequestException;
import com.handyai.build.exception.ResourceNotFoundException;
import com.handyai.build.repository.CategoryRepository;
import com.handyai.build.repository.UserAiUsageRepository;
import com.handyai.build.repository.UserRepository;
import java.time.Instant;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * What HandyAI knows about one signed-in user beyond their profile: the categories they follow,
 * the feed built from them, and how much of the AI assistant they have used.
 */
@Service
public class PersonalisationService {

    static final int MAX_INTERESTS = 20;
    private static final int MAX_FEED = 60;

    private final UserRepository userRepository;
    private final CategoryRepository categoryRepository;
    private final UserAiUsageRepository usageRepository;
    private final ToolService toolService;

    public PersonalisationService(UserRepository userRepository,
                                  CategoryRepository categoryRepository,
                                  UserAiUsageRepository usageRepository, ToolService toolService) {
        this.userRepository = userRepository;
        this.categoryRepository = categoryRepository;
        this.usageRepository = usageRepository;
        this.toolService = toolService;
    }

    /** The followed categories, by name, each with its number of tools. */
    @Transactional(readOnly = true)
    public List<CategoryResponse> interests(Long userId) {
        return describe(loadUser(userId).getInterests());
    }

    /** Replaces the followed categories; unknown slugs are refused rather than skipped. */
    @Transactional
    public List<CategoryResponse> setInterests(Long userId, List<String> slugs) {
        Set<String> wanted = new LinkedHashSet<>(slugs);
        if (wanted.size() > MAX_INTERESTS) {
            throw new BadRequestException("Follow at most " + MAX_INTERESTS + " categories");
        }
        List<Category> categories = categoryRepository.findBySlugIn(wanted);
        if (categories.size() != wanted.size()) {
            Set<String> unknown = new HashSet<>(wanted);
            categories.forEach(category -> unknown.remove(category.getSlug()));
            throw new BadRequestException("Unknown category: " + String.join(", ", unknown),
                    Map.of("slugs", "Choose categories from the list"));
        }
        User user = loadUser(userId);
        user.getInterests().clear();
        user.getInterests().addAll(categories);
        return describe(user.getInterests());
    }

    /**
     * The most popular tools across the followed categories, walking user to interests
     * (many-to-many) to tools (one-to-many). Empty until the user follows something.
     */
    @Transactional(readOnly = true)
    public List<ToolResponse> forYou(Long userId, int limit) {
        Set<Long> favorites = toolService.favoriteIds(userId);
        return loadUser(userId).getInterests().stream()
                .flatMap(category -> category.getTools().stream())
                .sorted(Comparator.comparingInt(AiTool::getPopularity).reversed()
                        .thenComparing(AiTool::getName))
                .limit(Math.max(1, Math.min(limit, MAX_FEED)))
                .map(tool -> ToolResponse.from(tool, favorites.contains(tool.getId())))
                .toList();
    }

    @Transactional(readOnly = true)
    public AiUsageView aiUsage(Long userId) {
        return usageRepository.findById(userId)
                .map(usage -> new AiUsageView(usage.getCalls(), usage.getInputTokens(),
                        usage.getOutputTokens(), usage.getCostUsd(), usage.getLastUsedAt()))
                .orElse(new AiUsageView(0, 0, 0, 0, null));
    }

    /**
     * Adds one AI-written reply to the user's totals: an atomic update when the row exists, otherwise
     * the user's first row. Two first replies at once can race on that insert; the loser gets a
     * DataIntegrityViolationException and the caller tries once more, which takes the update.
     */
    @Transactional
    public void recordAiUsage(Long userId, long inputTokens, long outputTokens, double costUsd) {
        Instant now = Instant.now();
        if (usageRepository.addTo(userId, inputTokens, outputTokens, costUsd, now) == 0) {
            UserAiUsage usage = new UserAiUsage(userRepository.getReferenceById(userId));
            usage.add(inputTokens, outputTokens, costUsd, now);
            usageRepository.saveAndFlush(usage);
        }
    }

    public record AiUsageView(long replies, long inputTokens, long outputTokens, double costUsd,
                              Instant lastUsedAt) {
    }

    private List<CategoryResponse> describe(Collection<Category> categories) {
        return categories.stream()
                .sorted(Comparator.comparing(Category::getName))
                .map(category -> CategoryResponse.from(category, category.getTools().size()))
                .toList();
    }

    private User loadUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> ResourceNotFoundException.of("User", userId));
    }
}
