package com.handyai.build.dto;

import com.handyai.build.domain.Review;
import java.time.Instant;

public record ReviewResponse(Long id, Long userId, String userName, int rating, String comment,
                             Instant createdAt) {

    public static ReviewResponse from(Review review) {
        return new ReviewResponse(review.getId(), review.getUser().getId(),
                review.getUser().getName(), review.getRating(), review.getComment(),
                review.getCreatedAt());
    }
}
