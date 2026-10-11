package com.handyai.build.service;

import com.handyai.build.domain.AiTool;
import com.handyai.build.domain.PriceBook.Currency;
import com.handyai.build.domain.PricingModel;
import com.handyai.build.dto.PageResponse;
import com.handyai.build.dto.StatsResponse;
import com.handyai.build.dto.ToolResponse;
import com.handyai.build.exception.BadRequestException;
import com.handyai.build.exception.ResourceNotFoundException;
import com.handyai.build.repository.AiToolRepository;
import com.handyai.build.repository.CategoryRepository;
import com.handyai.build.repository.FavoriteRepository;
import com.handyai.build.repository.ReviewRepository;
import com.handyai.build.repository.UserRepository;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.data.core.TypedPropertyPath;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ToolService {

    private static final int MAX_PAGE_SIZE = 60;

    private final AiToolRepository toolRepository;
    private final CategoryRepository categoryRepository;
    private final FavoriteRepository favoriteRepository;
    private final ReviewRepository reviewRepository;
    private final UserRepository userRepository;

    public ToolService(AiToolRepository toolRepository, CategoryRepository categoryRepository,
                       FavoriteRepository favoriteRepository, ReviewRepository reviewRepository,
                       UserRepository userRepository) {
        this.toolRepository = toolRepository;
        this.categoryRepository = categoryRepository;
        this.favoriteRepository = favoriteRepository;
        this.reviewRepository = reviewRepository;
        this.userRepository = userRepository;
    }

    /** Browsing without the marketplace's price filters. */
    @Transactional(readOnly = true)
    public PageResponse<ToolResponse> search(String query, String categorySlug, String pricing,
                                             String sort, int page, int size, Long currentUserId) {
        return search(query, categorySlug, pricing, false, null, null, null, null, sort, page,
                size, currentUserId);
    }

    /**
     * The marketplace query. {@code priceMin}/{@code priceMax} bound the monthly price of a tool's
     * entry paid plan, in {@code currency} (rupees unless USD is asked for), both ends inclusive.
     */
    @Transactional(readOnly = true)
    public PageResponse<ToolResponse> search(String query, String categorySlug, String pricing,
                                             boolean freePlanOnly, Double priceMin,
                                             Double priceMax, String currency, String cycle,
                                             String sort, int page, int size, Long currentUserId) {
        Currency priceCurrency = parseCurrency(currency);
        if (priceMin != null && priceMax != null && priceMin > priceMax) {
            throw new BadRequestException("The minimum price is above the maximum price");
        }
        Pageable pageable = PageRequest.of(Math.max(page, 0),
                clamp(size, 1, MAX_PAGE_SIZE), sortFor(sort, priceCurrency, cycle));

        String likeQuery = (query == null || query.isBlank())
                ? null
                : "%" + query.trim().toLowerCase(Locale.ROOT) + "%";
        String category = (categorySlug == null || categorySlug.isBlank()) ? null
                : categorySlug.trim().toLowerCase(Locale.ROOT);
        if (category != null && !categoryRepository.existsBySlug(category)) {
            throw ResourceNotFoundException.of("Category", categorySlug);
        }

        boolean inr = priceCurrency == Currency.INR;
        Page<AiTool> result = toolRepository.search(likeQuery, category, parsePricing(pricing),
                freePlanOnly,
                inr && priceMin != null ? (long) Math.ceil(priceMin) : null,
                inr && priceMax != null ? (long) Math.floor(priceMax) : null,
                inr ? null : priceMin,
                inr ? null : priceMax,
                pageable);
        Set<Long> favorites = favoriteIds(currentUserId);
        return PageResponse.of(result, result.getContent().stream()
                .map(tool -> ToolResponse.from(tool, favorites.contains(tool.getId())))
                .toList());
    }

    @Transactional(readOnly = true)
    public ToolResponse getBySlug(String slug, Long currentUserId) {
        AiTool tool = toolRepository.findBySlug(slug)
                .orElseThrow(() -> ResourceNotFoundException.of("Tool", slug));
        return ToolResponse.from(tool, isFavorite(currentUserId, tool.getId()));
    }

    @Transactional(readOnly = true)
    public List<ToolResponse> featured(int limit, Long currentUserId) {
        Set<Long> favorites = favoriteIds(currentUserId);
        List<AiTool> tools = toolRepository.findByFeaturedTrueOrderByPopularityDesc(
                PageRequest.of(0, clamp(limit, 1, MAX_PAGE_SIZE)));
        if (tools.isEmpty()) {
            tools = toolRepository.findAllByOrderByPopularityDesc(
                    PageRequest.of(0, clamp(limit, 1, MAX_PAGE_SIZE)));
        }
        return tools.stream()
                .map(tool -> ToolResponse.from(tool, favorites.contains(tool.getId())))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<ToolResponse> trending(int limit, Long currentUserId) {
        Set<Long> favorites = favoriteIds(currentUserId);
        return toolRepository.findAllByOrderByPopularityDesc(
                        PageRequest.of(0, clamp(limit, 1, MAX_PAGE_SIZE))).stream()
                .map(tool -> ToolResponse.from(tool, favorites.contains(tool.getId())))
                .toList();
    }

    @Transactional(readOnly = true)
    public StatsResponse stats() {
        return new StatsResponse(toolRepository.count(), categoryRepository.count(),
                reviewRepository.count(), userRepository.count());
    }

    Set<Long> favoriteIds(Long currentUserId) {
        if (currentUserId == null) {
            return Set.of();
        }
        return new HashSet<>(favoriteRepository.findToolIdsByUser(currentUserId));
    }

    private boolean isFavorite(Long currentUserId, Long toolId) {
        return currentUserId != null
                && favoriteRepository.existsByUserIdAndToolId(currentUserId, toolId);
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

    /** Java 17 has no Math.clamp, and every list endpoint needs the same guard rails. */
    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    /**
     * The marketplace's price filter options for one currency: who has a free plan, then monthly
     * price bands that stop at the band holding the most expensive tool.
     */
    @Transactional(readOnly = true)
    public PriceRanges priceRanges(String currency) {
        Currency priceCurrency = parseCurrency(currency);
        boolean inr = priceCurrency == Currency.INR;
        List<AiTool> tools = toolRepository.findAll();
        List<Double> prices = tools.stream()
                .map(tool -> inr
                        ? (tool.getPriceMonthlyInr() == null ? null : tool.getPriceMonthlyInr().doubleValue())
                        : tool.getPriceMonthlyUsd())
                .filter(price -> price != null && price > 0)
                .toList();
        double highest = prices.stream().mapToDouble(Double::doubleValue).max().orElse(0);

        double[] bounds = inr
                ? new double[] {1000, 2000, 3000, 5000, 10000, 20000, 50000, 100000}
                : new double[] {10, 20, 30, 50, 100, 200, 500, 1000};
        double step = inr ? 1 : 0.01;
        List<PriceRange> ranges = new java.util.ArrayList<>();
        double lower = step;
        for (double upper : bounds) {
            double min = lower;
            long count = prices.stream().filter(price -> price >= min && price <= upper).count();
            ranges.add(new PriceRange(min, upper, count));
            if (upper >= highest) {
                break;
            }
            lower = upper + step;
        }
        long freePlanCount = tools.stream().filter(AiTool::hasFreePlan).count();
        return new PriceRanges(priceCurrency.name(), freePlanCount, highest, ranges);
    }

    public record PriceRange(double min, double max, long count) {
    }

    public record PriceRanges(String currency, long freePlanCount, double highestMonthly,
                              List<PriceRange> ranges) {
    }

    private Currency parseCurrency(String currency) {
        try {
            return Currency.parse(currency);
        } catch (IllegalArgumentException ex) {
            throw new BadRequestException("Currency must be INR or USD");
        }
    }

    /**
     * Price sorting follows the billing cycle the visitor is looking at: annual discounts differ
     * between vendors, so the cheapest per month is not always the cheapest per year. Quarterly
     * is three months at the monthly price, so it sorts like monthly.
     */
    private Sort sortFor(String sort, Currency currency, String cycle) {
        String key = sort == null ? "popular" : sort.trim().toLowerCase(Locale.ROOT);
        // Property references rather than strings, so a renamed field fails the build instead of
        // failing the first request that sorts by it.
        Sort byName = Sort.by(AiTool::getName);
        return switch (key) {
            case "price_asc" -> Sort.by(Sort.Direction.ASC, priceProperty(currency, cycle)).and(byName);
            case "price_desc" -> Sort.by(Sort.Direction.DESC, priceProperty(currency, cycle)).and(byName);
            case "name" -> byName;
            case "newest" -> Sort.by(Sort.Direction.DESC, AiTool::getCreatedAt, AiTool::getId);
            case "rating" -> Sort.by(Sort.Direction.DESC, AiTool::getRatingAverage,
                    AiTool::getRatingCount, AiTool::getPopularity);
            case "", "popular" -> Sort.by(Sort.Direction.DESC, AiTool::getPopularity).and(byName);
            default -> throw new BadRequestException("Unknown sort option: " + sort);
        };
    }

    private static TypedPropertyPath<AiTool, ? extends Number> priceProperty(Currency currency,
                                                                            String cycle) {
        boolean annual = "ANNUAL".equalsIgnoreCase(cycle);
        if (currency == Currency.INR) {
            return annual ? AiTool::getPriceAnnualInr : AiTool::getPriceMonthlyInr;
        }
        return annual ? AiTool::getPriceAnnualUsd : AiTool::getPriceMonthlyUsd;
    }
}
