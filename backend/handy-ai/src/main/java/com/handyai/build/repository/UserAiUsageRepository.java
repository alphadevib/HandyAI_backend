package com.handyai.build.repository;

import com.handyai.build.domain.UserAiUsage;
import java.time.Instant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserAiUsageRepository extends JpaRepository<UserAiUsage, Long> {

    /** Adds to the totals in the database itself, so parallel replies never lose an update. */
    @Modifying
    @Query("""
            update UserAiUsage u
            set u.calls = u.calls + 1,
                u.inputTokens = u.inputTokens + :input,
                u.outputTokens = u.outputTokens + :output,
                u.costUsd = u.costUsd + :cost,
                u.lastUsedAt = :at
            where u.userId = :userId
            """)
    int addTo(@Param("userId") Long userId, @Param("input") long input,
              @Param("output") long output, @Param("cost") double cost, @Param("at") Instant at);
}
