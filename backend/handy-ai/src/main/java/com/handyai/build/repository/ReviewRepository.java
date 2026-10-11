package com.handyai.build.repository;

import com.handyai.build.domain.Review;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ReviewRepository extends JpaRepository<Review, Long> {

    @Query("""
            select r from Review r
            join fetch r.user
            where r.tool.id = :toolId
            order by r.createdAt desc
            """)
    List<Review> findForTool(@Param("toolId") Long toolId);

    Optional<Review> findByUserIdAndToolId(Long userId, Long toolId);

    /** Recomputed from scratch after each write so the cached rating can never drift. */
    @Query("select coalesce(sum(r.rating), 0) from Review r where r.tool.id = :toolId")
    int sumRatings(@Param("toolId") Long toolId);

    long countByToolId(Long toolId);

    @Query("select r.tool.category.id from Review r where r.user.id = :userId and r.rating >= 4")
    List<Long> findLikedCategoryIds(@Param("userId") Long userId);
}
