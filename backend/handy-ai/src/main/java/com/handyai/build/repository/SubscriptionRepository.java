package com.handyai.build.repository;

import com.handyai.build.domain.Subscription;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SubscriptionRepository extends JpaRepository<Subscription, Long> {

    @EntityGraph(attributePaths = {"tool", "tool.category"})
    List<Subscription> findByUserIdOrderByCreatedAtDesc(Long userId);

    @EntityGraph(attributePaths = {"tool", "tool.category"})
    Optional<Subscription> findByIdAndUserId(Long id, Long userId);

    boolean existsByUserIdAndToolId(Long userId, Long toolId);

    long countByStatus(Subscription.Status status);

    long countByCreatedAtAfter(java.time.Instant since);

    @EntityGraph(attributePaths = {"tool", "user"})
    List<Subscription> findTop8ByOrderByCreatedAtDesc();
}
