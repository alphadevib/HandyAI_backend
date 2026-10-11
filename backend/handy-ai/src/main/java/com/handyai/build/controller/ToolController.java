package com.handyai.build.controller;

import com.handyai.build.dto.PageResponse;
import com.handyai.build.dto.ReviewRequest;
import com.handyai.build.dto.ReviewResponse;
import com.handyai.build.dto.ToolResponse;
import com.handyai.build.security.CurrentUserProvider;
import com.handyai.build.service.FavoriteService;
import com.handyai.build.service.ReviewService;
import com.handyai.build.service.ToolService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/tools")
public class ToolController {

    private final ToolService toolService;
    private final ReviewService reviewService;
    private final FavoriteService favoriteService;
    private final CurrentUserProvider currentUserProvider;

    public ToolController(ToolService toolService, ReviewService reviewService,
                          FavoriteService favoriteService,
                          CurrentUserProvider currentUserProvider) {
        this.toolService = toolService;
        this.reviewService = reviewService;
        this.favoriteService = favoriteService;
        this.currentUserProvider = currentUserProvider;
    }

    /** Public catalogue. A signed-in caller additionally gets their saved flag on every card. */
    @GetMapping
    public PageResponse<ToolResponse> search(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String pricing,
            @RequestParam(defaultValue = "false") boolean freePlan,
            @RequestParam(required = false) Double priceMin,
            @RequestParam(required = false) Double priceMax,
            @RequestParam(required = false) String currency,
            @RequestParam(required = false) String cycle,
            @RequestParam(required = false, defaultValue = "popular") String sort,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "12") int size) {
        return toolService.search(q, category, pricing, freePlan, priceMin, priceMax, currency,
                cycle, sort, page, size, currentUserId());
    }

    /** The price filter options for the marketplace, with how many tools fall in each. */
    @GetMapping("/price-ranges")
    public ToolService.PriceRanges priceRanges(
            @RequestParam(required = false) String currency) {
        return toolService.priceRanges(currency);
    }

    @GetMapping("/featured")
    public List<ToolResponse> featured(@RequestParam(defaultValue = "6") int limit) {
        return toolService.featured(limit, currentUserId());
    }

    @GetMapping("/trending")
    public List<ToolResponse> trending(@RequestParam(defaultValue = "8") int limit) {
        return toolService.trending(limit, currentUserId());
    }

    @GetMapping("/{slug}")
    public ToolResponse detail(@PathVariable String slug) {
        return toolService.getBySlug(slug, currentUserId());
    }

    @GetMapping("/{slug}/reviews")
    public List<ReviewResponse> reviews(@PathVariable String slug) {
        return reviewService.listForTool(slug);
    }

    @GetMapping("/{slug}/reviews/mine")
    public ResponseOrEmpty myReview(@PathVariable String slug) {
        return new ResponseOrEmpty(reviewService
                .myReview(slug, currentUserProvider.requireUserId())
                .orElse(null));
    }

    @PutMapping("/{slug}/reviews")
    public ReviewResponse review(@PathVariable String slug,
                                 @Valid @RequestBody ReviewRequest request) {
        return reviewService.upsert(slug, currentUserProvider.requireUserId(), request);
    }

    @DeleteMapping("/{slug}/reviews/{reviewId}")
    public Map<String, Object> deleteReview(@PathVariable String slug,
                                            @PathVariable Long reviewId) {
        reviewService.delete(slug, currentUserProvider.requireUserId(), reviewId);
        return Map.of("deleted", true);
    }

    @PostMapping("/{slug}/favorite")
    public Map<String, Object> toggleFavorite(@PathVariable String slug) {
        boolean saved = favoriteService.toggle(currentUserProvider.requireUserId(), slug);
        return Map.of("slug", slug, "favorite", saved);
    }

    private Long currentUserId() {
        return currentUserProvider.currentUserId().orElse(null);
    }

    /** Wrapper so "no review yet" is a 200 with a null body field instead of a 404. */
    public record ResponseOrEmpty(ReviewResponse review) {
    }
}
