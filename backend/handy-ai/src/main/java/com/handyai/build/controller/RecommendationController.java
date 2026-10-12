package com.handyai.build.controller;

import com.handyai.build.dto.RecommendationRequest;
import com.handyai.build.dto.RecommendationResponse;
import com.handyai.build.security.CurrentUserProvider;
import com.handyai.build.service.RecommendationService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "Recommendations", description = "Tools ranked for a described task, profession and budget.")
@RequestMapping("/api/recommendations")
public class RecommendationController {

    private final RecommendationService recommendationService;
    private final CurrentUserProvider currentUserProvider;

    public RecommendationController(RecommendationService recommendationService,
                                    CurrentUserProvider currentUserProvider) {
        this.recommendationService = recommendationService;
        this.currentUserProvider = currentUserProvider;
    }

    /** Open to anonymous visitors; signing in only sharpens the ranking. */
    @PostMapping
    public RecommendationResponse recommend(@Valid @RequestBody RecommendationRequest request) {
        return recommendationService.recommend(request,
                currentUserProvider.currentUserId().orElse(null));
    }
}
