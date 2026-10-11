package com.handyai.build.service;

import com.handyai.build.domain.AiTool;
import com.handyai.build.domain.Review;
import com.handyai.build.domain.User;
import com.handyai.build.dto.ReviewRequest;
import com.handyai.build.dto.ReviewResponse;
import com.handyai.build.exception.ResourceNotFoundException;
import com.handyai.build.exception.UnauthorizedException;
import com.handyai.build.repository.AiToolRepository;
import com.handyai.build.repository.ReviewRepository;
import com.handyai.build.repository.UserRepository;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * One review per user per tool.
 *
 * <p>Every write touches the review row first and the tool row second — always in that order — so
 * two concurrent reviews on the same tool queue up instead of dead-locking each other. The cached
 * rating on the tool is re-derived with an aggregate query rather than incremented, which makes a
 * lost update impossible even if two transactions interleave.
 */
@Service
public class ReviewService {

    private final ReviewRepository reviewRepository;
    private final AiToolRepository toolRepository;
    private final UserRepository userRepository;

    public ReviewService(ReviewRepository reviewRepository, AiToolRepository toolRepository,
                         UserRepository userRepository) {
        this.reviewRepository = reviewRepository;
        this.toolRepository = toolRepository;
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public List<ReviewResponse> listForTool(String toolSlug) {
        AiTool tool = requireTool(toolSlug);
        return reviewRepository.findForTool(tool.getId()).stream()
                .map(ReviewResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public Optional<ReviewResponse> myReview(String toolSlug, Long userId) {
        AiTool tool = requireTool(toolSlug);
        return reviewRepository.findByUserIdAndToolId(userId, tool.getId())
                .map(ReviewResponse::from);
    }

    @Transactional
    public ReviewResponse upsert(String toolSlug, Long userId, ReviewRequest request) {
        AiTool tool = requireTool(toolSlug);
        User user = userRepository.findById(userId)
                .orElseThrow(() -> ResourceNotFoundException.of("User", userId));

        Review review = reviewRepository.findByUserIdAndToolId(userId, tool.getId())
                .orElseGet(() -> {
                    Review fresh = new Review();
                    fresh.setUser(user);
                    fresh.setTool(tool);
                    return fresh;
                });
        review.setRating(request.rating());
        review.setComment(request.comment() == null || request.comment().isBlank()
                ? null : request.comment().trim());
        Review saved = reviewRepository.saveAndFlush(review);

        refreshToolRating(tool);
        return ReviewResponse.from(saved);
    }

    @Transactional
    public void delete(String toolSlug, Long userId, Long reviewId) {
        AiTool tool = requireTool(toolSlug);
        Review review = reviewRepository.findById(reviewId)
                .orElseThrow(() -> ResourceNotFoundException.of("Review", reviewId));
        if (!review.getUser().getId().equals(userId)) {
            throw new UnauthorizedException("You can only remove your own review");
        }
        reviewRepository.delete(review);
        reviewRepository.flush();
        refreshToolRating(tool);
    }

    private void refreshToolRating(AiTool tool) {
        tool.setRatingSum(reviewRepository.sumRatings(tool.getId()));
        tool.setRatingCount((int) reviewRepository.countByToolId(tool.getId()));
        toolRepository.save(tool);
    }

    private AiTool requireTool(String slug) {
        return toolRepository.findBySlug(slug)
                .orElseThrow(() -> ResourceNotFoundException.of("Tool", slug));
    }
}
