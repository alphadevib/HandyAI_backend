package com.handyai.build.service;

import com.handyai.build.domain.AiTool;
import com.handyai.build.domain.PriceBook.Currency;
import com.handyai.build.domain.PricingModel;
import com.handyai.build.domain.ProfessionCatalog;
import com.handyai.build.dto.RecommendationRequest;
import com.handyai.build.dto.RecommendationResponse;
import com.handyai.build.dto.RecommendationResponse.Recommendation;
import com.handyai.build.dto.ToolResponse;
import com.handyai.build.exception.BadRequestException;
import com.handyai.build.repository.AiToolRepository;
import com.handyai.build.repository.FavoriteRepository;
import com.handyai.build.repository.ReviewRepository;
import com.handyai.build.repository.UserRepository;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Explainable, fully local scoring: the point of the product is to surface platforms people have
 * never heard of, so every suggestion comes back with the reasons that earned it a place.
 *
 * <p>Scoring is pure in-memory work over an already-loaded snapshot of the catalogue. No external
 * calls, no locks, no shared mutable state between requests.
 */
@Service
public class RecommendationService {

    private static final int DEFAULT_LIMIT = 6;
    private static final int MAX_LIMIT = 24;

    /** Words that carry no intent, dropped before matching. */
    private static final Set<String> STOP_WORDS = Set.of(
            "the", "and", "for", "with", "that", "this", "have", "has", "from", "your", "you",
            "our", "are", "want", "need", "help", "using", "use", "make", "get", "some", "about",
            "into", "more", "best", "good", "able", "work", "working", "would", "like", "can",
            "will", "any", "all", "what", "when", "how", "tool", "tools", "app", "apps", "ai",
            // Budget words are applied as filters, never matched against descriptions.
            "free", "cheap", "cheaper", "affordable", "paid", "price", "budget", "under", "only",
            "ones", "month", "monthly", "rupees", "dollars");

    /** Profession/intent hints mapped onto category slugs the seeder creates. */
    private static final Map<String, List<String>> INTENT_CATEGORIES = Map.ofEntries(
            Map.entry("developer", List.of("coding", "productivity")),
            Map.entry("engineer", List.of("coding", "productivity")),
            Map.entry("programmer", List.of("coding")),
            Map.entry("code", List.of("coding")),
            Map.entry("software", List.of("coding")),
            Map.entry("designer", List.of("design", "image")),
            Map.entry("design", List.of("design", "image")),
            Map.entry("ux", List.of("design")),
            Map.entry("marketer", List.of("marketing", "writing")),
            Map.entry("marketing", List.of("marketing", "writing")),
            Map.entry("seo", List.of("marketing")),
            Map.entry("writer", List.of("writing")),
            Map.entry("writing", List.of("writing")),
            Map.entry("blog", List.of("writing", "marketing")),
            Map.entry("copy", List.of("writing", "marketing")),
            Map.entry("student", List.of("research", "productivity", "writing")),
            Map.entry("teacher", List.of("research", "productivity")),
            Map.entry("research", List.of("research")),
            Map.entry("researcher", List.of("research")),
            Map.entry("analyst", List.of("data", "research")),
            Map.entry("data", List.of("data")),
            Map.entry("spreadsheet", List.of("data", "productivity")),
            Map.entry("founder", List.of("productivity", "marketing", "automation")),
            Map.entry("startup", List.of("productivity", "automation")),
            Map.entry("manager", List.of("productivity", "meetings")),
            Map.entry("meeting", List.of("meetings")),
            Map.entry("notes", List.of("meetings", "productivity")),
            Map.entry("video", List.of("video")),
            Map.entry("editor", List.of("video", "writing")),
            Map.entry("youtube", List.of("video")),
            Map.entry("podcast", List.of("audio", "video")),
            Map.entry("audio", List.of("audio")),
            Map.entry("music", List.of("audio")),
            Map.entry("voice", List.of("audio")),
            Map.entry("image", List.of("image", "design")),
            Map.entry("photo", List.of("image", "design")),
            Map.entry("logo", List.of("image", "design")),
            Map.entry("automate", List.of("automation")),
            Map.entry("automation", List.of("automation")),
            Map.entry("workflow", List.of("automation", "productivity")),
            Map.entry("support", List.of("automation", "chatbots")),
            Map.entry("chatbot", List.of("chatbots")),
            Map.entry("assistant", List.of("chatbots", "productivity")));

    private final AiToolRepository toolRepository;
    private final FavoriteRepository favoriteRepository;
    private final ReviewRepository reviewRepository;
    private final UserRepository userRepository;

    public RecommendationService(AiToolRepository toolRepository,
                                 FavoriteRepository favoriteRepository,
                                 ReviewRepository reviewRepository,
                                 UserRepository userRepository) {
        this.toolRepository = toolRepository;
        this.favoriteRepository = favoriteRepository;
        this.reviewRepository = reviewRepository;
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public RecommendationResponse recommend(RecommendationRequest request, Long currentUserId) {
        int limit = request.limit() == null
                ? DEFAULT_LIMIT
                : Math.max(1, Math.min(MAX_LIMIT, request.limit()));

        String profession = request.profession() != null && !request.profession().isBlank()
                ? request.profession()
                : professionOf(currentUserId);

        String goal = request.goal() == null ? "" : request.goal();
        Set<String> goalKeywords = tokenise(goal);
        Set<String> keywords = new java.util.LinkedHashSet<>(goalKeywords);
        keywords.addAll(tokenise(profession));

        // What the visitor asked for outranks who they are: categories implied by the task get
        // the full boost, the profession's categories a smaller one, so "tools for designers"
        // still leads with design tools for someone whose profile says lawyer.
        Set<String> wantedCategories = new HashSet<>(normalise(request.categorySlugs()));
        wantedCategories.addAll(intentCategories(goalKeywords));
        Set<String> professionCategories = new HashSet<>(intentCategories(tokenise(profession)));
        ProfessionCatalog.find(profession)
                .ifPresent(entry -> professionCategories.addAll(entry.categories()));
        professionCategories.removeAll(wantedCategories);
        String professionLabel = ProfessionCatalog.find(profession).map(ProfessionCatalog.Entry::name)
                .orElse(profession);

        PricingModel pricing = parsePricing(request.pricing());
        boolean freePlanOnly = Boolean.TRUE.equals(request.freePlanOnly());
        Currency currency = parseCurrency(request.currency());
        Double maxMonthly = request.maxMonthlyPrice();
        Set<String> excluded = new HashSet<>(normalise(request.excludeSlugs()));
        Set<Long> favoriteToolIds = currentUserId == null
                ? Set.of()
                : new HashSet<>(favoriteRepository.findToolIdsByUser(currentUserId));
        Set<Long> affinityCategoryIds = currentUserId == null ? Set.of() : affinity(currentUserId);

        List<AiTool> catalogue = toolRepository.findAllByOrderByPopularityDesc(
                org.springframework.data.domain.PageRequest.of(0, 500));

        // A task none of whose words appear anywhere in the catalogue was not understood. Saying
        // so beats answering "asdkjh" with whatever suits the visitor's profession.
        boolean goalUnderstood = goalKeywords.isEmpty()
                || !intentCategories(goalKeywords).isEmpty()
                || !normalise(request.categorySlugs()).isEmpty()
                || catalogue.stream().anyMatch(tool -> mentionsAny(tool, goalKeywords));
        if (!goalUnderstood) {
            return new RecommendationResponse(noMatchSummary(goal, null, false), List.of());
        }

        // A stated budget is a filter, not a preference: it means the same thing here as it does
        // on the marketplace's filters.
        catalogue = catalogue.stream()
                .filter(tool -> pricing == null || tool.getPricingModel() == pricing)
                .filter(tool -> !freePlanOnly || tool.hasFreePlan())
                .filter(tool -> maxMonthly == null || withinBudget(tool, maxMonthly, currency))
                .filter(tool -> !excluded.contains(tool.getSlug()))
                .toList();

        List<Recommendation> ranked = catalogue.stream()
                .map(tool -> score(tool, keywords, wantedCategories, professionCategories,
                        professionLabel, pricing, favoriteToolIds, affinityCategoryIds))
                .filter(scored -> scored.matchScore() > 0)
                .sorted(Comparator.comparingInt(Recommendation::matchScore).reversed()
                        .thenComparing(item -> item.tool().name()))
                .limit(limit)
                .toList();

        boolean describedSomething = !keywords.isEmpty() || !wantedCategories.isEmpty()
                || !professionCategories.isEmpty();
        if (ranked.isEmpty() && describedSomething) {
            // The visitor asked for something specific and nothing fits: say so honestly instead
            // of padding the list with unrelated tools.
            boolean budgetFiltered = pricing != null || freePlanOnly || maxMonthly != null;
            return new RecommendationResponse(noMatchSummary(goal, pricing, budgetFiltered),
                    List.of());
        }

        if (ranked.isEmpty()) {
            // An empty request: offer the strongest all-round picks as a starting point.
            ranked = catalogue.stream()
                    .limit(limit)
                    .map(tool -> new Recommendation(
                            ToolResponse.from(tool, favoriteToolIds.contains(tool.getId())),
                            50,
                            List.of("A well-rounded starting point while you refine your search")))
                    .toList();
        }

        return new RecommendationResponse(summary(goal, profession, ranked.size()), ranked);
    }

    private Recommendation score(AiTool tool, Set<String> keywords, Set<String> wantedCategories,
                                 Set<String> professionCategories, String professionLabel,
                                 PricingModel pricing, Set<Long> favoriteToolIds,
                                 Set<Long> affinityCategoryIds) {
        int score = 0;
        List<String> reasons = new ArrayList<>();

        String name = tool.getName().toLowerCase(Locale.ROOT);
        String tagline = tool.getTagline().toLowerCase(Locale.ROOT);
        String description = tool.getDescription().toLowerCase(Locale.ROOT);
        Set<String> tags = new HashSet<>(tool.tagList().stream()
                .map(tag -> tag.toLowerCase(Locale.ROOT))
                .toList());

        int keywordHits = 0;
        List<String> matchedTags = new ArrayList<>();
        for (String keyword : keywords) {
            boolean hit = false;
            if (tags.contains(keyword)) {
                score += 14;
                matchedTags.add(keyword);
                hit = true;
            } else if (tags.stream().anyMatch(tag -> tag.contains(keyword))) {
                score += 9;
                matchedTags.add(keyword);
                hit = true;
            }
            if (name.contains(keyword)) {
                score += 10;
                hit = true;
            }
            if (tagline.contains(keyword)) {
                score += 7;
                hit = true;
            } else if (description.contains(keyword)) {
                score += 4;
                hit = true;
            }
            if (hit) {
                keywordHits++;
            }
        }
        if (!matchedTags.isEmpty()) {
            reasons.add("Built for " + String.join(", ", matchedTags.stream().distinct().limit(3).toList()));
        } else if (keywordHits > 0) {
            reasons.add("Matches what you described");
        }

        if (wantedCategories.contains(tool.getCategory().getSlug())) {
            score += 22;
            reasons.add("Top pick in " + tool.getCategory().getName());
        } else if (professionCategories.contains(tool.getCategory().getSlug())) {
            score += 12;
            reasons.add("Suits your work as a " + professionLabel.toLowerCase(Locale.ROOT));
        }

        // Everything below only boosts a tool that already fits what was asked for. The budget is
        // applied as a filter, so on its own it must never make an unrelated tool a "match".
        if (score == 0) {
            return new Recommendation(
                    ToolResponse.from(tool, favoriteToolIds.contains(tool.getId())), 0, List.of());
        }

        if (pricing != null && tool.getPricingModel() == pricing) {
            score += 12;
            reasons.add(pricingLabel(pricing));
        }

        if (affinityCategoryIds.contains(tool.getCategory().getId())) {
            score += 8;
            reasons.add("Close to tools you already rate highly");
        }

        if (tool.getRatingCount() > 0 && tool.averageRating() >= 4d) {
            score += 6;
            reasons.add("Rated " + tool.averageRating() + " by the community");
        }

        score += tool.getPopularity() / 10;
        if (tool.isFeatured()) {
            score += 3;
        }
        // A small nudge towards free tools, but only among those that already matched: it must
        // never be the only thing keeping a tool in the list.
        if (pricing == null && tool.getPricingModel() == PricingModel.FREE) {
            score += 4;
        }

        if (reasons.isEmpty()) {
            reasons.add("A strong general-purpose option");
        }

        return new Recommendation(
                ToolResponse.from(tool, favoriteToolIds.contains(tool.getId())),
                Math.min(score, 100),
                reasons.stream().distinct().limit(3).toList());
    }

    private Set<Long> affinity(Long userId) {
        Set<Long> categoryIds = new HashSet<>(favoriteRepository.findFavoriteCategoryIds(userId));
        categoryIds.addAll(reviewRepository.findLikedCategoryIds(userId));
        return categoryIds;
    }

    private String professionOf(Long userId) {
        if (userId == null) {
            return null;
        }
        return userRepository.findById(userId).map(user -> user.getProfession()).orElse(null);
    }

    private Set<String> intentCategories(Set<String> keywords) {
        Set<String> slugs = new HashSet<>();
        for (String keyword : keywords) {
            List<String> mapped = INTENT_CATEGORIES.get(keyword);
            if (mapped != null) {
                slugs.addAll(mapped);
            }
        }
        return slugs;
    }

    private Set<String> tokenise(String text) {
        if (text == null || text.isBlank()) {
            return Set.of();
        }
        return Arrays.stream(text.toLowerCase(Locale.ROOT).split("[^a-z0-9+#]+"))
                .map(String::trim)
                .filter(word -> word.length() > 2)
                .filter(word -> !STOP_WORDS.contains(word))
                // "designers" should match a "designer" intent and a "design" tag, so plurals
                // also contribute their singular form.
                .flatMap(word -> word.length() > 4 && word.endsWith("s") && !word.endsWith("ss")
                        ? java.util.stream.Stream.of(word, word.substring(0, word.length() - 1))
                        : java.util.stream.Stream.of(word))
                .limit(40)
                .collect(java.util.stream.Collectors.toCollection(java.util.LinkedHashSet::new));
    }

    private List<String> normalise(List<String> values) {
        if (values == null) {
            return List.of();
        }
        return values.stream()
                .filter(value -> value != null && !value.isBlank())
                .map(value -> value.trim().toLowerCase(Locale.ROOT))
                .toList();
    }

    private PricingModel parsePricing(String pricing) {
        if (pricing == null || pricing.isBlank() || "ALL".equalsIgnoreCase(pricing)) {
            return null;
        }
        try {
            return PricingModel.valueOf(pricing.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new BadRequestException("Unknown pricing filter: " + pricing);
        }
    }

    private String noMatchSummary(String goal, PricingModel pricing, boolean budgetFiltered) {
        StringBuilder summary = new StringBuilder("Nothing in the catalogue fits ");
        summary.append(goal.isBlank() ? "that" : "\"" + goal.trim() + "\"");
        if (pricing != null) {
            summary.append(" at the ").append(pricingFilterLabel(pricing)).append(" price");
        } else if (budgetFiltered) {
            summary.append(" within that budget");
        }
        summary.append(" yet. Try describing the task in other words");
        if (budgetFiltered) {
            summary.append(" or widen the price filter");
        }
        return summary.append('.').toString();
    }

    /**
     * The budget applies to the paid plan, as on the marketplace's price filter: a free tier does
     * not make a tool whose paid plan costs more "fit" the budget. A tool with no known price is
     * left out rather than guessed at.
     */
    private boolean withinBudget(AiTool tool, double maxMonthly, Currency currency) {
        Number price = currency == Currency.INR ? tool.getPriceMonthlyInr() : tool.getPriceMonthlyUsd();
        return price != null && price.doubleValue() <= maxMonthly;
    }

    /** Whether any of the words appear anywhere in what the catalogue says about the tool. */
    private boolean mentionsAny(AiTool tool, Set<String> words) {
        String text = (tool.getName() + " " + tool.getTagline() + " " + tool.getDescription() + " "
                + (tool.getTags() == null ? "" : tool.getTags())).toLowerCase(Locale.ROOT);
        return words.stream().anyMatch(text::contains);
    }

    private Currency parseCurrency(String currency) {
        try {
            return Currency.parse(currency);
        } catch (IllegalArgumentException ex) {
            throw new BadRequestException("Currency must be INR or USD");
        }
    }

    private String pricingFilterLabel(PricingModel pricing) {
        return switch (pricing) {
            case FREE -> "free";
            case FREEMIUM -> "free tier";
            case TRIAL -> "free trial";
            case PAID -> "paid";
        };
    }

    private String pricingLabel(PricingModel pricing) {
        return switch (pricing) {
            case FREE -> "Completely free to use";
            case FREEMIUM -> "Usable free, upgrade only if you need more";
            case TRIAL -> "Offers a trial before you commit";
            case PAID -> "Paid, and worth it for serious use";
        };
    }

    private String summary(String goal, String profession, int count) {
        StringBuilder summary = new StringBuilder(count + " tool");
        if (count != 1) {
            summary.append('s');
        }
        summary.append(" picked");
        boolean hasProfession = profession != null && !profession.isBlank();
        if (hasProfession) {
            summary.append(" for a ").append(profession.trim().toLowerCase(Locale.ROOT));
        }
        if (goal != null && !goal.isBlank()) {
            summary.append(hasProfession ? " who wants to " : " to help you ")
                    .append(goal.trim().toLowerCase(Locale.ROOT));
        }
        summary.append('.');
        return summary.toString();
    }
}
